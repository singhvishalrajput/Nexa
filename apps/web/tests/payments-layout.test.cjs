const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const nodes = value => !value || typeof value !== 'object' ? [] : Array.isArray(value) ? value.flatMap(nodes) : [value, ...nodes(value.props?.children)];
const text = value => value == null || typeof value === 'boolean' ? '' : typeof value !== 'object' ? String(value) : Array.isArray(value) ? value.map(text).join(' ') : text(value.props?.children);

function harness(props = {}) {
  const slots = []; let cursor = 0, tree, allow = true, guardCalls = 0;
  const hooks = {useState(initial) {const i = cursor++; if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
    return [slots[i], value => {slots[i] = typeof value === 'function' ? value(slots[i]) : value;}];}, useRef(initial) {const i = cursor++; return slots[i] || (slots[i] = {current: initial});}};
  const dependencies = {
    'preact/hooks': hooks,
    'preact/jsx-runtime': {jsx: (type, props) => ({type, props}), jsxs: (type, props) => ({type, props})},
    '../../services/locale': loadSource('services/locale.ts'),
    '../../services/banking-content': loadSource('services/banking-content.ts'),
    '../../hooks/useNavigationGuard': {useNavigationGuard() {}, confirmNavigation() {guardCalls++; return allow;}},
    '../../components/BankingIcon': {BankingIcon: 'icon'},
    './MoneyTransfer': {MoneyTransfer: 'nexa-transfer'}, './ExternalTransfer': {ExternalTransfer: 'other-bank-transfer'},
    './utils': {validAmount: value => /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value) && Number(value) > 0},
    './api': {bankApi: {}}, './ui': {PageHeading: 'heading', Panel: 'panel', State: 'state', Status: 'status', Detail: 'detail', Modal: 'modal', useLoad() {throw Error('Payments must use the transfer workflows, not duplicate API loading');}}
  };
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/Payments.tsx', 'utf8'), {compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
  {exports, Error, require: name => {if (!(name in dependencies)) throw Error('Unexpected dependency: ' + name); return dependencies[name];}});
  function render() {cursor = 0; tree = exports.PaymentsPage({token: 'test-token', userId: 'customer-1', ...props}); return tree;}
  render();
  return {render, nodes: () => nodes(tree), guardCalls: () => guardCalls, deny() {allow = false;}, permit() {allow = true;},
    select(value) {const currentTarget = {value}; nodes(tree).find(node => node.type === 'select').props.onChange({currentTarget}); render(); return currentTarget;}};
}

test('Payments has one destination selector and embeds the existing Nexa transfer workflow', () => {
  const app = harness();
  assert.equal(app.nodes().filter(node => node.type === 'heading').length, 1);
  assert.equal(app.nodes().find(node => node.type === 'heading').props.title, 'Payments');
  const selector = app.nodes().find(node => node.type === 'select');
  assert.equal(selector.props.value, 'nexa');
  assert.deepEqual(nodes(selector).filter(node => node.type === 'option').map(node => [node.props.value, node.props.children]), [['nexa', 'Nexa account'], ['other-bank', 'Other bank']]);
  const transfer = app.nodes().find(node => node.type === 'nexa-transfer');
  assert.deepEqual({...transfer.props}, {token: 'test-token', userId: 'customer-1', embedded: true});
  assert.equal(app.nodes().some(node => node.type === 'other-bank-transfer'), false);
  assert.match(text(app.render()), /between your own accounts/);
});

test('Other bank switches in place and forwards selected payee with the same customer identity', () => {
  const app = harness({initialPayee: 'EP-selected'}); app.select('other-bank');
  assert.equal(app.nodes().find(node => node.type === 'select').props.value, 'other-bank');
  assert.equal(app.nodes().some(node => node.type === 'nexa-transfer'), false);
  assert.deepEqual({...app.nodes().find(node => node.type === 'other-bank-transfer').props}, {token: 'test-token', userId: 'customer-1', initialPayee: 'EP-selected', embedded: true});
  assert.match(text(app.render()), /bank account number and IFSC/);
  app.select('nexa'); assert.ok(app.nodes().some(node => node.type === 'nexa-transfer'));
  assert.equal(app.guardCalls(), 2);
});

test('destination switching respects unsaved work and pending-request navigation guards', () => {
  const app = harness(); app.deny();
  const rejected = app.select('other-bank');
  assert.equal(rejected.value, 'nexa'); assert.equal(app.nodes().find(node => node.type === 'select').props.value, 'nexa');
  assert.ok(app.nodes().some(node => node.type === 'nexa-transfer'));
  app.select('nexa'); assert.equal(app.guardCalls(), 1, 'unchanged selection should not ask to leave');
  app.permit(); app.select('other-bank'); assert.ok(app.nodes().some(node => node.type === 'other-bank-transfer'));
});

test('other-bank deep links open the corresponding embedded flow immediately', () => {
  const app = harness({initialDestination: 'other-bank', initialPayee: 'EP-linked'});
  assert.equal(app.nodes().find(node => node.type === 'select').props.value, 'other-bank');
  assert.equal(app.nodes().find(node => node.type === 'other-bank-transfer').props.initialPayee, 'EP-linked');
  assert.equal(app.guardCalls(), 0);
});

test('bill, saved payee, schedule, card and direct-debit workflows remain direct shortcuts', () => {
  const app = harness();
  const shortcuts = app.nodes().find(node => node.type === 'nav' && node.props['aria-label'] === 'Payment shortcuts');
  assert.deepEqual(nodes(shortcuts).filter(node => node.type === 'a').map(node => node.props.href), ['#/bills', '#/beneficiaries', '#/scheduled-payments', '#/cards', '#/mandates']);
  assert.equal(app.nodes().some(node => node.type === 'a' && ['#/send-money', '#/external-transfers'].includes(node.props.href)), false);
  assert.equal(app.nodes().some(node => ['form', 'modal', 'demo-action', 'history'].includes(node.type)), false);
});

test('unified transfer layout retains compact controls, responsive cards and keyboard focus', () => {
  const css = fs.readFileSync('src/styles/banking-forms.css', 'utf8');
  assert.match(css, /\.bank-payments-layout\s*\{[^}]*grid-template-columns: minmax\(0,1\.8fr\) minmax\(260px,1fr\);[^}]*align-items: start/);
  assert.match(css, /\.external-transfer-embedded \.external-transfer-layout\s*\{ grid-template-columns: minmax\(0,1fr\)/);
  assert.match(css, /@container payments-page \(max-width: 900px\)[\s\S]*?\.bank-payments-layout\s*\{ grid-template-columns: minmax\(0,1fr\)/);
  assert.match(css, /\.bank-payment-destination select:focus-visible\s*\{[^}]*outline:/);
  assert.match(css, /\.bank-payment-links a:focus-visible\s*\{[^}]*outline:/);
});
