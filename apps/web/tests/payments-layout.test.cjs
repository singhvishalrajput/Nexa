const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');

function nodes(value) {
  if (!value || typeof value !== 'object') return [];
  if (Array.isArray(value)) return value.flatMap(nodes);
  return [value, ...nodes(value.props?.children)];
}
function harness(prepare = async (_token, request) => ({...request, status:'PREPARED', currencyCode:'INR'}), targetState = {}) {
  const slots = []; let cursor = 0, tree;
  const calls = [];
  const hooks = {
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = initial;
      return [slots[i], value => { slots[i] = typeof value === 'function' ? value(slots[i]) : value; }];
    },
    useRef(initial) { const i = cursor++; return slots[i] || (slots[i] = {current:initial}); }
  };
  const dependencies = {
    'preact/hooks':hooks,
    'preact/jsx-runtime':{jsx:(type, props) => ({type, props}), jsxs:(type, props) => ({type, props})},
    '../../services/locale':loadSource('services/locale.ts'),
    '../../services/banking-content':loadSource('services/banking-content.ts'),
    '../../hooks/useNavigationGuard':{useNavigationGuard() {}},
    '../../components/BankingIcon':{BankingIcon:'icon'},
    './Showcase':{DemoAction:'demo-action', DemoHistory:'history'},
    './Products':{productTitle:product => product.displayName},
    './utils':{validAmount:value => /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value) && Number(value) > 0},
    './api':{bankApi:{prepare:async (token, request) => { calls.push(JSON.parse(JSON.stringify(request))); return prepare(token, request); }}},
    './ui':{PageHeading:'heading', Panel:'panel', State:'state', Status:'status', Detail:'detail', Modal:'modal',
      useLoad:(_load, dependencies) => ({data:[{id:'target-1', displayName:'Test recipient', status:'ACTIVE'}], loading:false, error:'', kind:dependencies[1], reload() {}, ...targetState})}
  };
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/Payments.tsx','utf8'), {
    compilerOptions:{module:ts.ModuleKind.CommonJS, target:ts.ScriptTarget.ES2021, jsx:ts.JsxEmit.ReactJSX, jsxImportSource:'preact'}
  }).outputText, {exports, Error, require:name => { if (!(name in dependencies)) throw Error('Unexpected dependency: ' + name); return dependencies[name]; }});
  function render() {
    cursor = 0;
    tree = exports.PaymentsPage({token:'test-token', accounts:[
      {id:'account-1', status:'ACTIVE', displayName:'Primary', accountNumberMasked:'•••• 1234'},
      {id:'closed-account', status:'CLOSED', displayName:'Closed', accountNumberMasked:'•••• 9999'}
    ]});
    return nodes(tree);
  }
  function select(index, value) { nodes(tree).filter(n => n.type === 'select')[index].props.onChange({currentTarget:{value}}); render(); }
  function amount(value) { nodes(tree).find(n => n.type === 'input').props.onInput({currentTarget:{value}}); render(); }
  function submit() { return nodes(tree).find(n => n.type === 'form').props.onSubmit({preventDefault() {}}); }
  function button() { return nodes(tree).find(n => n.type === 'button' && /Check payment details|Checking details/.test(n.props.children)); }
  render();
  return {render, select, amount, submit, button, calls, nodes:() => nodes(tree)};
}

test('Payments uses the shared compact form scale and groups four fields independently of its footer', () => {
  const app = harness();
  assert.ok(app.nodes().some(n => n.props?.class === 'bank-service-page bank-payments-page'));
  const fields = app.nodes().find(n => n.props?.class === 'bank-fields-grid bank-payment-fields');
  const labels = nodes(fields).filter(n => n.type === 'label');
  assert.equal(labels.length, 4);
  assert.equal(nodes(fields).some(n => n.type === 'button' && n.props.children === 'Check payment details'), false);
  const input = nodes(fields).find(n => n.type === 'input');
  assert.ok(nodes(fields).some(n => n.props?.id === input.props['aria-describedby']));
  assert.equal(app.nodes().some(n => n.type === 'option' && n.props.value === 'closed-account'), false);
  const links = app.nodes().find(n => n.type === 'nav' && n.props['aria-label'] === 'Payment shortcuts');
  assert.deepEqual(nodes(links).filter(n => n.type === 'a').map(n => n.props.href), ['#/beneficiaries', '#/scheduled-payments']);
});

test('payment types retain their target controls and cancellation omits amount', () => {
  const app = harness();
  for (const [operation, label] of [['PAY_BILL','Bill'], ['PAY_CARD','Card'], ['CANCEL_MANDATE','Direct debit'], ['START_TRANSFER','Payee']]) {
    app.select(0, operation);
    assert.ok(app.nodes().some(n => n.type === 'label' && n.props.children[0] === label));
    assert.equal(app.nodes().some(n => n.type === 'input'), operation !== 'CANCEL_MANDATE');
    assert.equal(app.nodes().some(n => n.props?.class === 'bank-pagination'), operation !== 'START_TRANSFER');
    assert.equal(app.button().props.disabled, true);
  }
});

test('review keeps validation and the duplicate-request lock without executing a payment', async () => {
  let release;
  const app = harness(async (_token, request) => { await new Promise(resolve => { release = resolve; }); return {...request,status:'PREPARED'}; });
  await app.submit();
  assert.equal(app.calls.length, 0);
  app.select(2, 'target-1');
  for (const amount of ['0','-1','1.001','invalid']) {
    app.amount(amount); assert.equal(app.button().props.disabled, true); await app.submit();
  }
  assert.equal(app.calls.length, 0);
  app.amount('100.50');
  const request = app.submit(); app.render();
  assert.equal(app.button().props.disabled, true);
  assert.ok(app.nodes().filter(n => ['input','select'].includes(n.type)).every(n => n.props.disabled));
  await app.submit();
  assert.equal(app.calls.length, 1);
  assert.deepEqual(app.calls[0], {operation:'START_TRANSFER',accountId:'account-1',targetId:'target-1',amount:'100.50'});
  release(); await request; app.render();
  assert.ok(app.nodes().some(n => n.type === 'modal'));
  assert.ok(app.nodes().some(n => n.type === 'demo-action'));
});

test('cancellation review does not send a stale amount, and review errors remain recoverable', async () => {
  const app = harness(async () => { throw new Error('Review unavailable'); });
  app.amount('100'); app.select(0, 'CANCEL_MANDATE'); app.select(2, 'target-1');
  await app.submit(); app.render();
  assert.deepEqual(app.calls[0], {operation:'CANCEL_MANDATE',accountId:'account-1',targetId:'target-1'});
  assert.ok(app.nodes().some(n => n.props?.role === 'alert' && n.props.children === 'Review unavailable'));
  assert.equal(app.button().props.disabled, false);
});

test('loading and error states retain the target recovery control inside the field grid', () => {
  for (const targetState of [{loading:true}, {error:'Unavailable',loading:false}]) {
    const app = harness(undefined, targetState);
    const target = app.nodes().find(n => n.props?.class === 'bank-payment-target');
    const state = nodes(target).find(n => n.type === 'state');
    assert.equal(state.props.loading, !!targetState.loading);
    assert.equal(state.props.error, targetState.error || '');
    assert.equal(typeof state.props.retry, 'function');
  }
});

test('payment layout has equal-height desktop cards and narrow-container/mobile fallbacks', () => {
  const css = fs.readFileSync('src/styles/banking-forms.css','utf8');
  assert.match(css, /\.bank-payments-layout\s*\{[^}]*grid-template-columns: minmax\(0,1\.8fr\) minmax\(260px,1fr\);[^}]*align-items: stretch/);
  assert.match(css, /@container payment-review \(max-width: 520px\)[\s\S]*?\.bank-payment-fields\s*\{ grid-template-columns: minmax\(0,1fr\)/);
  assert.match(css, /@container payments-page \(max-width: 900px\)[\s\S]*?\.bank-payments-layout\s*\{ grid-template-columns: minmax\(0,1fr\)/);
  assert.match(css, /\.bank-payment-links a:focus-visible\s*\{[^}]*outline:/);
});
