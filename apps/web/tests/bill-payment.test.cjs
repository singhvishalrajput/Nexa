const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const uuid = '00000000-0000-4000-8000-000000000001';
const receipt = {id: 'BP-' + uuid, billId: 'B-electricity', billerName: 'Electricity', sourceAccountId: '1',
  sourceName: 'Savings', sourceMasked: '•••• 1234', payeeId: 'payee-1', recipientName: 'City Utility Treasury',
  destinationMasked: '•••• 5678', amount: '100.00', currencyCode: 'INR', status: 'READY',
  reference: null, expiresAt: '2026-09-26T14:05:00Z', completedAt: null, failureReason: null};

function harness(options = {}) {
  const slots = [], effects = [], calls = [], guards = [];
  const storage = options.storage || new Map();
  let cursor = 0, dirty = true, tree, paid = 0, changed = 0;
  class ApiRequestError extends Error { constructor(status, message) { super(message); this.status = status; } }
  const hooks = {
    useState(initial) { const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial};
      return [slots[at].value, value => { const next = typeof value === 'function' ? value(slots[at].value) : value;
        if (!Object.is(next, slots[at].value)) { slots[at].value = next; dirty = true; } }]; },
    useRef(initial) { const at = cursor++; return slots[at] || (slots[at] = {current: initial}); },
    useEffect(effect, deps) { const at = cursor++, old = slots[at];
      if (!old || deps.some((value, i) => value !== old.deps[i])) { slots[at] = {deps}; effects.push(() => { old?.cleanup?.(); slots[at].cleanup = effect(); }); } }
  };
  const defaults = {
    async accounts() { return [
      {id: '1', displayName: 'Savings', accountNumberMasked: '•••• 1234', accountType: 'SAVINGS', currencyCode: 'INR', status: 'ACTIVE', availableBalance: '500.00'},
      {id: '2', displayName: 'Closed', accountType: 'SAVINGS', currencyCode: 'INR', status: 'CLOSED'},
      {id: '3', displayName: 'Loan', accountType: 'LOAN', currencyCode: 'INR', status: 'ACTIVE'}
    ]; },
    async products() { return [
      {id: 'payee-1', displayName: 'Electric company nickname', accountNumberMasked: '•••• 5678', bankName: 'Nexa', status: 'ACTIVE'},
      {id: 'external', displayName: 'Other bank', bankName: 'External', status: 'ACTIVE'},
      {id: 'external-nexa-name', displayName: 'Other bank pretending to be Nexa', transferType: 'EXTERNAL_BANK', bankName: 'Nexa', status: 'ACTIVE'},
      {id: 'inactive', displayName: 'Inactive', bankName: 'Nexa', status: 'CLOSED'}
    ]; },
    async billPaymentHistory() { return []; },
    async prepareBillPayment(_token, request) { return {...receipt, id: 'BP-' + request.requestKey, amount: request.amount || '100.00'}; },
    async confirmBillPayment() { return {...receipt, status: 'COMPLETED', reference: 'TX-bill-payment'}; },
    async cancelBillPayment() { return {...receipt, status: 'CANCELLED'}; },
    async billPayment() { return {...receipt}; }
  };
  const handlers = {...defaults, ...options.api};
  const api = Object.fromEntries(Object.keys(defaults).map(method => [method, async (...args) => {
    calls.push({method, args}); return handlers[method](...args);
  }]));
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  let ui;
  function load(file) {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/' + file, 'utf8'), {compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
      {exports, Error, crypto: {randomUUID: () => uuid}, window: {sessionStorage: {
        getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key)}},
      require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content
        : name.endsWith('/auth') ? {ApiRequestError} : name.endsWith('/useNavigationGuard') ? {useNavigationGuard: (...args) => guards.push(args)}
        : name === './api' ? {bankApi: api} : name === './ui' ? ui
        : name === './utils' ? {validAmount: value => /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value) && Number(value) > 0} : {}});
    return exports;
  }
  ui = load('ui.tsx');
  const {BillPayment} = load('BillPayment.tsx');
  const bill = {id: receipt.billId, billerName: 'Electricity', amount: '150.00', paidAmount: '50.00', outstandingAmount: '100.00', minimumAmount: '20.00', status: 'OVERDUE', currencyCode: 'INR', ...options.bill};
  const props = {token: 'synthetic', bill, onPaid() { paid++; options.onPaid?.(); }, onBillChanged() { changed++; options.onBillChanged?.(bill); }};
  function render() { let attempts = 0; do { dirty = false; cursor = 0; tree = BillPayment(props); effects.splice(0).forEach(run => run());
    if (++attempts > 30) throw new Error('Page did not settle'); } while (dirty); return tree; }
  async function ready() { for (let i = 0; i < 4; i++) { render(); await new Promise(setImmediate); } return render(); }
  function button(label) { return nodes(render()).find(node => node.type === 'button' && text(node) === label); }
  function select(label) { const field = nodes(render()).find(node => node.type === 'label' && text(node).startsWith(label)); return nodes(field).find(node => node.type === 'select'); }
  return {calls, handlers, guards, storage, ApiRequestError, bill, ready, render, button, select, paid: () => paid, changed: () => changed,
    async click(label) { const node = button(label); assert.ok(node, 'Missing button: ' + label); node.props.onClick?.(); return ready(); },
    change(label, value) { select(label).props.onChange({currentTarget: {value}}); return render(); },
    amount(value) { nodes(render()).find(node => node.type === 'input').props.onInput({currentTarget: {value}}); return render(); },
    async submit() { nodes(render()).find(node => node.type === 'form').props.onSubmit({preventDefault() {}}); return ready(); },
    dispose() { slots.forEach(slot => slot.cleanup?.()); }};
}

async function reviewed(app) {
  await app.ready(); await app.click('Pay now');
  if (!app.bill.payeeId) app.change('Saved Nexa payee', 'payee-1');
  await app.submit();
}

test('bill payment reviews an active Nexa recipient and outstanding amount before any posting', async () => {
  const app = harness(); await app.ready();
  assert.match(text(app.render()), /Payments are made only after you confirm. Automatic debit is off/);
  await app.click('Pay now');
  assert.deepEqual(nodes(app.select('From account')).filter(n => n.type === 'option').map(n => n.props.value), ['', '1']);
  assert.deepEqual(nodes(app.select('Saved Nexa payee')).filter(n => n.type === 'option').map(n => n.props.value), ['', 'payee-1']);
  assert.equal(nodes(app.render()).find(n => n.type === 'input').props.value, '100.00');
  app.change('Saved Nexa payee', 'payee-1'); await app.submit();
  const request = app.calls.find(call => call.method === 'prepareBillPayment').args[1];
  assert.deepEqual(JSON.parse(JSON.stringify(request)), {requestKey: uuid, billId: 'B-electricity', sourceAccountId: '1', amount: '100.00', payeeId: 'payee-1'});
  assert.match(text(app.render()), /City Utility Treasury/);
  assert.doesNotMatch(text(app.render()), /Electric company nickname/);
  assert.equal(app.calls.filter(call => call.method === 'confirmBillPayment').length, 0);
  assert.ok(app.button('Confirm payment')); assert.ok(app.button('Cancel review'));
  app.dispose();
});

test('duplicate confirmations send one request and completed payment refreshes balances and bill once', async () => {
  let release;
  const app = harness({api: {confirmBillPayment: async () => {
    await new Promise(resolve => { release = resolve; }); return {...receipt, status: 'COMPLETED', reference: 'TX-bill-payment'};
  }}});
  await reviewed(app);
  const confirm = app.button('Confirm payment').props.onClick;
  confirm(); confirm(); await app.ready();
  assert.equal(app.calls.filter(call => call.method === 'confirmBillPayment').length, 1);
  assert.ok(app.guards.at(-1)[1]);
  release(); await app.ready();
  assert.equal(app.paid(), 1); assert.match(text(app.render()), /Payment completed/);
  assert.ok(nodes(app.render()).some(n => n.type === 'a' && n.props.href === '#/transactions/TX-bill-payment'));
  assert.equal(JSON.parse(app.storage.get('nexa-bill-review:B-electricity')).notified, true);
  const restored = harness({storage: app.storage, bill: {status: 'PAID', outstandingAmount: '0.00'}, api: {billPayment: async () => ({...receipt, status: 'COMPLETED', reference: 'TX-bill-payment'})}});
  await restored.ready(); assert.equal(restored.paid(), 0, 'restoration must not trigger an endless bill reload');
  assert.match(text(restored.render()), /Payment completed/);
  app.dispose(); restored.dispose();
});

test('amount bounds block invalid, insufficient and under-minimum reviews while permitting final balance', async () => {
  const app = harness(); await app.ready(); await app.click('Pay now'); app.change('Saved Nexa payee', 'payee-1');
  for (const amount of ['0', '-1', '0.001', '100.01', '19.99', 'invalid']) {
    app.amount(amount); assert.equal(app.button('Review payment').props.disabled, true); await app.submit();
  }
  assert.equal(app.calls.filter(call => call.method === 'prepareBillPayment').length, 0);
  app.bill.outstandingAmount = '10.00'; app.amount('10.00');
  assert.equal(app.button('Review payment').props.disabled, false);
  app.dispose();
});

test('lost confirmation keeps the same review and prevents a second payment until status recovery', async () => {
  const app = harness(); await reviewed(app);
  app.handlers.confirmBillPayment = async () => { throw new app.ApiRequestError(0, 'Connection lost'); };
  await app.click('Confirm payment');
  assert.ok(app.button('Check payment status')); assert.ok(app.button('Retry same payment'));
  assert.equal(app.button('Cancel review'), undefined); assert.equal(app.button('Pay now'), undefined);
  await app.click('Check payment status'); assert.ok(app.button('Retry same payment'));
  app.handlers.confirmBillPayment = async () => ({...receipt, status: 'COMPLETED', reference: 'TX-bill-payment'});
  await app.click('Retry same payment');
  assert.deepEqual(app.calls.filter(call => call.method === 'confirmBillPayment').map(call => call.args[1]), [receipt.id, receipt.id]);
  assert.equal(app.paid(), 1); app.dispose();
});

test('lost prepare response restores and retries the exact persisted request key and values', async () => {
  const storage = new Map();
  const first = harness({storage, api: {prepareBillPayment: async () => { throw new Error('Response lost'); }}});
  await reviewed(first);
  const original = first.calls.find(call => call.method === 'prepareBillPayment').args[1];
  assert.ok(first.button('Retry same review')); assert.equal(first.button('Pay now'), undefined);
  first.dispose();
  const app = harness({storage});
  app.handlers.billPayment = async () => { throw new app.ApiRequestError(404, 'Not found'); };
  await app.ready(); assert.ok(app.button('Retry same review'));
  await app.click('Retry same review');
  assert.equal(JSON.stringify(app.calls.find(call => call.method === 'prepareBillPayment').args[1]), JSON.stringify(original));
  assert.ok(app.button('Confirm payment')); app.dispose();
});

test('a confirmed failed attempt shows its reason and allows a fresh review without marking the bill paid', async () => {
  const app = harness({api: {confirmBillPayment: async () => ({...receipt, status: 'FAILED', failureReason: 'Insufficient balance.'})}});
  await reviewed(app); await app.click('Confirm payment');
  assert.match(text(app.render()), /Insufficient balance/); assert.equal(app.paid(), 0);
  await app.click('Start a new review'); assert.ok(app.button('Pay now')); assert.equal(app.storage.size, 0);
  app.dispose();
});

test('bound bills omit recipient edits, paid bills cannot be paid, and legacy paid labels stay truthful', async () => {
  const linked = harness({bill: {payeeId: 'payee-1', recipientName: 'City Utility Treasury', recipientAccountMasked: '•••• 5678'}});
  await reviewed(linked);
  assert.equal(linked.calls.find(call => call.method === 'prepareBillPayment').args[1].payeeId, undefined);
  assert.equal(linked.calls.filter(call => call.method === 'products').length, 0);
  const paid = harness({bill: {status: 'PAID', outstandingAmount: '0.00', paidAmount: '0.00'}}); await paid.ready();
  assert.equal(paid.button('Pay now'), undefined); assert.match(text(paid.render()), /Previously recorded paid/);
  const unavailable = harness({api: {products: async () => []}}); await unavailable.ready(); await unavailable.click('Pay now');
  assert.match(text(unavailable.render()), /No active Nexa payee/); assert.equal(unavailable.button('Review payment').props.disabled, true);
  linked.dispose(); paid.dispose(); unavailable.dispose();
});

test('cancelling a review records cancellation without confirming or refreshing balances', async () => {
  const app = harness(); await reviewed(app); await app.click('Cancel review');
  assert.equal(app.calls.filter(call => call.method === 'cancelBillPayment').length, 1);
  assert.equal(app.calls.filter(call => call.method === 'confirmBillPayment').length, 0);
  assert.equal(app.paid(), 0); assert.equal(app.changed(), 1); assert.match(text(app.render()), /Payment cancelled/);
  app.dispose();
});

test('linked recipient can be corrected before payment but is locked after partial settlement', async () => {
  const unpaid = harness({bill: {payeeId: 'payee-1', recipientName: 'Old recipient', paidAmount: '0.00'}});
  await unpaid.ready(); await unpaid.click('Pay now'); await unpaid.click('Change recipient');
  unpaid.change('Saved Nexa payee', 'payee-1'); await unpaid.submit();
  assert.equal(unpaid.calls.find(call => call.method === 'prepareBillPayment').args[1].payeeId, 'payee-1');
  const partial = harness({bill: {payeeId: 'payee-1', paidAmount: '50.00'}});
  await partial.ready(); await partial.click('Pay now'); assert.equal(partial.button('Change recipient'), undefined);
  unpaid.dispose(); partial.dispose();
});

test('stale failed payment refreshes bill totals before the next review', async () => {
  const app = harness({api: {confirmBillPayment: async () => ({...receipt, status: 'FAILED', failureReason: 'The outstanding amount changed.'})},
    onBillChanged: bill => { bill.outstandingAmount = '40.00'; }});
  await reviewed(app); await app.click('Confirm payment');
  assert.equal(app.changed(), 1); assert.equal(app.paid(), 0);
  await app.click('Start a new review'); await app.click('Pay now');
  assert.equal(nodes(app.render()).find(node => node.type === 'input').props.value, '40.00');
  app.dispose();
});

test('render refresh failure cannot relabel a completed payment as uncertain or repeat confirmation', async () => {
  const app = harness({onPaid() { throw Error('Page reload failed'); }});
  await reviewed(app); await app.click('Confirm payment');
  assert.match(text(app.render()), /Payment completed/);
  assert.equal(app.button('Retry same payment'), undefined);
  assert.equal(app.calls.filter(call => call.method === 'confirmBillPayment').length, 1);
  app.dispose();
});
