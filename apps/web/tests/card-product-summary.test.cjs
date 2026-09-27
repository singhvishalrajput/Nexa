const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const credit = {id: 'C-1', displayName: 'Nexa credit card', cardType: 'CREDIT', status: 'ACTIVE', outstanding: '0.00', availableLimit: '50000.00', creditLimit: '50000.00', minimumPayment: '0.00', currencyCode: 'INR', numberMasked: 'LOCAL 1234'};

function render(product, detail = false, kind = 'cards') {
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  const empty = () => null;
  const stubs = {
    'preact/hooks': {useState: initial => [initial, () => {}]},
    'preact/jsx-runtime': {jsx, jsxs: jsx, Fragment: 'fragment'},
    '../../services/locale': {t: value => value},
    '../../services/banking-content': content,
    '../../components/BankingIcon': {BankingIcon: empty},
    './api': {bankApi: {}},
    './PayeeForm': {PayeeForm: empty}, './BillPayment': {BillPayment: empty},
    './LoanTools': {LoanNavigation: empty, LoanApplicationTimeline: empty},
    './LoanSalarySlips': {LoanSalarySlipPanel: empty},
    './ProductOperations': {BillCreate: empty, ProductCreate: empty, ProductOperations: empty, ProductStatusControl: empty},
    './CardApplications': {CardApplications: empty, CardStatusControl: empty},
    './ScheduledPayment': {ScheduledPaymentCreate: empty, ScheduledPaymentDetails: empty},
    './ui': {
      useLoad: () => ({data: detail ? product : [product], loading: false, error: null, reload() {}}),
      PageHeading: props => jsx('h1', {children: props.title}),
      Panel: props => jsx('section', {class: props.className, children: [props.title, props.children]}),
      State: props => props.children,
      Status: props => jsx('span', {children: content.humanize(props.value)}),
      Detail: props => jsx('div', {children: [jsx('dt', {children: props.label}), jsx('dd', {children: props.children})]})
    }
  };
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/Products.tsx', 'utf8'), {compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'
  }}).outputText, {exports, require: name => {assert.ok(name in stubs, `Unexpected dependency ${name}`); return stubs[name];}});
  const tree = exports.ProductsPage({token: 'render-only', kind, ...(detail ? {id: product.id} : {})});
  const summary = nodes(tree).find(node => detail ? node.props?.class === 'bank-product-summary' : node.type === 'a' && node.props.class?.includes('bank-product '));
  return {tree, summary, balance: nodes(summary).find(node => node.props?.class === 'bank-product-amount')};
}

test('credit card list and detail lead with the approved available limit, and label zero outstanding separately', () => {
  for (const detail of [false, true]) {
    const view = render(credit, detail);
    assert.match(text(view.balance), /Available limit ₹50,000.*Outstanding\s+·\s+₹0/);
    assert.equal(text(nodes(view.balance).find(node => node.type === 'strong')), '₹50,000');
    assert.match(text(view.summary), /Active/);
  }
});

test('used credit shows the actual remaining limit and outstanding amount without substituting the approved limit', () => {
  for (const detail of [false, true]) {
    const {balance} = render({...credit, outstanding: '12500.75', availableLimit: '37499.25'}, detail);
    assert.match(text(balance), /Available limit ₹37,499\.25.*Outstanding\s+·\s+₹12,500\.75/);
    assert.doesNotMatch(text(balance), /₹50,000/);
  }
});

test('fully used credit preserves a real zero available limit', () => {
  const {balance} = render({...credit, outstanding: '50000.00', availableLimit: '0.00'}, true);
  assert.equal(text(nodes(balance).find(node => node.type === 'strong')), '₹0');
  assert.match(text(balance), /Outstanding\s+·\s+₹50,000/);
});

test('debit cards display no credit, minimum-payment or outstanding figures', () => {
  for (const detail of [false, true]) {
    const {tree, balance} = render({...credit, cardType: 'DEBIT', displayName: 'Nexa debit card', availableLimit: '0', creditLimit: '0'}, detail);
    assert.equal(balance, undefined);
    assert.doesNotMatch(text(tree), /Available limit|Credit limit|Minimum payment|Outstanding|₹/);
  }
});

test('pending and rejected credit applications never present zero as an approved credit limit', () => {
  for (const status of ['PENDING_APPROVAL', 'REJECTED']) for (const detail of [false, true]) {
    const {tree, summary, balance} = render({...credit, status, availableLimit: '0', creditLimit: '0'}, detail);
    assert.equal(balance, undefined);
    assert.match(text(summary), status === 'PENDING_APPROVAL' ? /Credit limit awaits bank approval/ : /No credit limit was approved/);
    assert.doesNotMatch(text(tree), /Available limit|Minimum payment|₹/);
  }
});

test('closed cards retain labelled debt rather than claiming available credit in the summary', () => {
  const {balance} = render({...credit, status: 'CLOSED'}, true);
  assert.match(text(balance), /Outstanding ₹0/);
  assert.doesNotMatch(text(balance), /Available limit/);
});

test('a missing available-limit field never falls back to the full approved credit limit', () => {
  const {balance} = render({...credit, outstanding: '12500', availableLimit: undefined}, true);
  assert.match(text(balance), /Outstanding ₹12,500/);
  assert.doesNotMatch(text(balance), /Available limit|₹50,000/);
});

test('loan detail retains the existing outstanding-principal explanation and interest labels', () => {
  const {tree, summary} = render({id: 'L-1', displayName: 'Personal loan', status: 'ACTIVE', outstanding: '80000', currencyCode: 'INR', interestRate: '12', nextEmi: '5000'}, true, 'loans');
  assert.match(text(summary), /Outstanding principal ₹80,000 Borrowed amount still unpaid · excludes future interest/);
  assert.match(text(tree), /Annual interest rate 12 %/);
  assert.match(text(tree), /Next EMI \(includes interest\) ₹5,000/);
  assert.doesNotMatch(text(summary), /Available limit/);
});

test('bill tiles show a readable category without changing the saved biller or category', () => {
  const bill = {id: 'B-1', billerName: 'Municipal supplier', category: 'Water', status: 'DUE', amount: '250', currencyCode: 'INR'};
  const {summary} = render(bill, false, 'bills');
  assert.match(text(summary), /Municipal supplier Water bill/);
  assert.equal(bill.category, 'Water'); assert.equal(bill.billerName, 'Municipal supplier');
  const referenced = render({...bill, reference: 'SUP-123'}, false, 'bills');
  assert.match(text(referenced.summary), /SUP-123/);
});
