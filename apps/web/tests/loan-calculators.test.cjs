const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const nodes = n => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap(nodes) : [n, ...nodes(n.props?.children)];
function harness(file, api) {
  let cursor = 0;
  const slots = [];
  const hooks = {
    useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial; return [slots[i], v => slots[i] = typeof v === 'function' ? v(slots[i]) : v]; },
    useRef(initial) { const i = cursor++; return slots[i] || (slots[i] = {current: initial}); },
    useEffect() {}
  };
  const exports = {}, jsx = (type, props) => ({type, props});
  const compiled = ts.transpileModule(fs.readFileSync('src/features/banking/' + file + '.tsx', 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText;
  vm.runInNewContext(compiled, {exports, Error, require: name =>
    name === 'preact/hooks' ? hooks :
    name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx} :
    name === '../../services/auth' ? {authenticatedRequest: api} :
    name === '../../services/banking-content' ? loadSource('services/banking-content.ts') :
    name === './ui' ? {Panel: 'section', Detail: 'detail', PageHeading: 'heading', State: 'state', useLoad: () => ({})} : {bankApi: {}}
  });
  return {exports, render(component, props) {cursor = 0; return nodes(exports[component](props));}};
}
const limits = {minimumAmount: 100, maximumAmount: 5000, regularEmi: 100, minimumExtraPrincipal: 100, principalOnly: false};

test('minimum applies to the extra portion, not EMI multiples, with payoff exceptions', () => {
  const {validRepayment, isPartialPrepayment} = harness('LoanPrepayment').exports;
  for (const value of ['99.99', '100.001', '-1', 'NaN', 'Infinity', '150', '5000.01'])
    assert.equal(validRepayment(value, limits), false, value);
  for (const value of ['100', '200', '250.75', '5000'])
    assert.equal(validRepayment(value, limits), true, value);
  assert.equal(isPartialPrepayment('100', limits), false);
  assert.equal(isPartialPrepayment('250.75', limits), true);
  assert.equal(isPartialPrepayment('5000', limits), false);
  assert.equal(validRepayment('99', {...limits, principalOnly: true}), false);
  assert.equal(validRepayment('100', {...limits, principalOnly: true}), true);
  assert.equal(validRepayment('42.50', {...limits, principalOnly: true, minimumAmount: 42.5, maximumAmount: 42.5}), true);
});

test('prepayment exploration calls only the preview API and hides results after a balance revision', async () => {
  const calls = [], quote = {previewToken: 'token', options: []};
  const app = harness('LoanPrepayment', async (path, token, request) => {calls.push({path, body: JSON.parse(request.body)}); return quote;});
  const props = {token: 'owner', loanId: 'L-1', limits, revision: 'balance-1'};
  let tree = app.render('PrepaymentCalculator', props);
  tree.find(n => n.type === 'input').props.onInput({currentTarget: {value: '250.75'}});
  tree = app.render('PrepaymentCalculator', props);
  await tree.find(n => n.type === 'form').props.onSubmit({preventDefault() {}});
  tree = app.render('PrepaymentCalculator', props);
  assert.deepEqual(calls, [{path: '/loans/L-1/repayment-preview', body: {amount: '250.75'}}]);
  assert.ok(tree.some(n => n.type === app.exports.PrepaymentComparison));
  tree = app.render('PrepaymentCalculator', {...props, revision: 'balance-2'});
  assert.equal(tree.some(n => n.type === app.exports.PrepaymentComparison), false);
  assert.equal(calls.length, 1);
});

test('comparison explains both schedules and disables unavailable options without preselecting one', () => {
  const app = harness('LoanPrepayment');
  const preview = {annualInterestRate: 14.5, remainingPrincipal: 500, extraPrincipalAmount: 200, baselineInstallments: 6, baselineFinalDueDate: '2027-01-31', baselineFutureInterest: 30, options: [
    {option: 'REDUCE_TENURE', available: true, regularEmi: 100, finalEmi: 50, remainingInstallments: 5, finalDueDate: '2026-12-31', futureInterest: 20, interestSaved: 10, installmentsSaved: 1, schedule: []},
    {option: 'REDUCE_EMI', available: false, unavailableReason: 'Balance too small'}
  ]};
  const tree = app.render('PrepaymentComparison', {preview, onSelect() {}});
  const radios = tree.filter(n => n.type === 'input');
  assert.equal(radios.length, 2);
  assert.equal(radios[0].props.checked, false);
  assert.equal(radios[1].props.checked, false);
  assert.equal(radios[1].props.disabled, true);
  const text = JSON.stringify(tree);
  assert.match(text, /Keep EMI/); assert.match(text, /Reduce EMI/);
  assert.match(text, /The annual rate does not change/); assert.match(text, /not daily interest accrual/);
});

test('EMI calculator uses server quote and clears old numbers when inputs change', async () => {
  const calls = [];
  const app = harness('LoanTools', async (path, token, request) => {
    calls.push({path, body: JSON.parse(request.body)});
    return {amount: 100000, currencyCode: 'INR', annualInterestRate: 14.5, tenureMonths: 12, emiAmount: 9000, finalEmiAmount: 8999, totalInterest: 7999, totalRepayment: 107999};
  });
  let tree = app.render('LoanCalculatorPage', {token: 'owner'});
  await tree.find(n => n.type === 'form').props.onSubmit({preventDefault() {}});
  tree = app.render('LoanCalculatorPage', {token: 'owner'});
  assert.deepEqual(calls, [{path: '/loans/quote', body: {amount: '100000', tenureMonths: 12}}]);
  assert.match(JSON.stringify(tree), /₹9,000/);
  tree.find(n => n.type === 'input' && n.props.name === 'amount').props.onInput({currentTarget: {value: '200000'}});
  tree = app.render('LoanCalculatorPage', {token: 'owner'});
  assert.doesNotMatch(JSON.stringify(tree), /₹9,000/);
});

test('prepayment selection has compact labelled radios and a visible selected state', () => {
  const app = harness('LoanPrepayment'), choices = [];
  const preview = {annualInterestRate: 14.5, remainingPrincipal: 500, extraPrincipalAmount: 200,
    baselineInstallments: 6, baselineFutureInterest: 30, currentEmi: 100, options: [
      {option: 'REDUCE_TENURE', available: true, regularEmi: 100, finalEmi: 50, remainingInstallments: 5, futureInterest: 20, interestSaved: 10, installmentsSaved: 1, schedule: []},
      {option: 'REDUCE_EMI', available: true, regularEmi: 90, finalEmi: 40, remainingInstallments: 6, futureInterest: 25, interestSaved: 5, installmentsSaved: 0, schedule: []}
    ]};
  let tree = app.render('PrepaymentComparison', {preview, onSelect: value => choices.push(value)});
  const labels = tree.filter(n => n.type === 'label' && n.props.class === 'loan-prepayment-choice');
  assert.equal(labels.length, 2);
  for (const label of labels) {
    assert.equal(nodes(label).filter(n => n.type === 'input' && n.props.type === 'radio').length, 1);
    assert.ok(nodes(label).some(n => n.props?.class === 'loan-prepayment-choice-copy'));
  }
  assert.equal(tree.some(n => n.props?.class === 'loan-prepayment-selected'), false);
  tree.find(n => n.type === 'input' && n.props.value === 'REDUCE_EMI').props.onChange();
  assert.deepEqual(choices, ['REDUCE_EMI']);
  tree = app.render('PrepaymentComparison', {preview, selected: 'REDUCE_EMI', onSelect() {}});
  assert.equal(tree.filter(n => n.props?.class === 'loan-prepayment-selected').length, 1);
  assert.equal(tree.find(n => n.type === 'input' && n.props.value === 'REDUCE_EMI').props.checked, true);
  assert.match(JSON.stringify(tree), /Finish earlier · same monthly payment/);
  assert.match(JSON.stringify(tree), /Pay less monthly · keep the end date/);
});

test('repayment amount styles cannot stretch the option radios to full width', () => {
  const css = fs.readFileSync('src/styles/banking.css', 'utf8');
  for (const form of ['loan-repayment-form', 'loan-prepayment-form']) {
    assert.doesNotMatch(css, new RegExp('\\.' + form + '\\s+input\\s*\\{'));
    assert.ok(css.includes('.' + form + ' input[type="number"]'));
  }
  const radio = css.match(/\.loan-prepayment-choice input\[type="radio"\]\s*\{([^}]+)\}/)?.[1];
  assert.ok(radio);
  assert.match(radio, /width:\s*20px/);
  assert.match(radio, /padding:\s*0/);
  assert.match(css, /\.loan-prepayment-choice\s*\{[^}]*grid-template-columns:\s*20px minmax\(0,1fr\)/);
});

test('quote boundaries and application status labels are explicit', () => {
  const {validQuoteInputs, loanApplicationStatus} = harness('LoanTools').exports;
  assert.equal(validQuoteInputs('1000', '1'), true);
  assert.equal(validQuoteInputs('1000000', '60'), true);
  assert.equal(validQuoteInputs('999.99', '12'), false);
  assert.equal(validQuoteInputs('1000.001', '12'), false);
  assert.equal(validQuoteInputs('1000', '61'), false);
  assert.match(loanApplicationStatus('PENDING_APPROVAL'), /Pending/);
  assert.match(loanApplicationStatus('APPROVED'), /awaiting your acceptance/);
  assert.equal(loanApplicationStatus('REJECTED'), 'Rejected');
  assert.equal(loanApplicationStatus('ACTIVE'), 'Amount disbursed');
  assert.equal(loanApplicationStatus('UNKNOWN'), 'Status unavailable');
});
