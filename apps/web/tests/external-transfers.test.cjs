const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const uuid = '11111111-1111-4111-8111-111111111111';
const receipt = {id: 'XT-' + uuid, environment: 'SANDBOX', provider: 'CASHFREE', sourceAccountId: '1', sourceName: 'Savings', sourceMasked: '•••• 1234',
  payeeId: 'EP-1', recipientName: 'Synthetic Recipient', bankName: 'Test Bank', destinationMasked: '•••• 6789', ifsc: 'HDFC0001234', amount: '25.00', currencyCode: 'INR',
  status: 'READY', providerTransferId: null, providerStatus: null, statusCode: null, utr: null, expiresAt: '2026-09-27T10:05:00Z', completedAt: null, failureReason: null, updatedAt: '2026-09-27T10:00:00Z'};

function harness(options = {}) {
  const slots = [], effects = [], calls = [], guards = [], storage = options.storage || new Map();
  let cursor = 0, dirty = true, tree, reloads = 0;
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
    async readiness() { return {ready: true, environment: 'SANDBOX', provider: 'CASHFREE', reason: null}; },
    async history() { return []; },
    async prepare(_token, request) { return {...receipt, id: 'XT-' + request.requestKey, amount: request.amount}; },
    async confirm() { return {...receipt, status: 'PENDING', providerTransferId: 'test-provider-1'}; },
    async cancel() { return {...receipt, status: 'CANCELLED'}; },
    async status() { return {...receipt}; },
    async refresh() { return {...receipt, status: 'COMPLETED', providerTransferId: 'test-provider-1', utr: 'TEST-UTR'}; }
  };
  const handlers = {...defaults, ...options.api};
  const external = Object.fromEntries(Object.keys(defaults).map(method => [method, async (...args) => { calls.push({method, args}); return handlers[method](...args); }]));
  const bankApi = {
    async accounts() { return [{id: '1', displayName: 'Savings', accountNumberMasked: '•••• 1234', accountType: 'SAVINGS', currencyCode: 'INR', status: 'ACTIVE', availableBalance: '1.00'},
      {id: '2', displayName: 'Closed', accountType: 'SAVINGS', currencyCode: 'INR', status: 'CLOSED'}]; },
    async products() { return [{id: 'EP-1', transferType: 'EXTERNAL_BANK', displayName: 'Other bank recipient', bankName: 'Test Bank', accountNumberMasked: '•••• 6789', status: 'ACTIVE'},
      {id: 'nexa-1', transferType: 'INTERNAL', displayName: 'Internal', bankName: 'Nexa', status: 'ACTIVE'}]; }
  };
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  let ui;
  const load = file => {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/' + file, 'utf8'), {compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText, {
      exports, Error, crypto: {randomUUID: () => uuid}, window: {sessionStorage: {getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key)}},
      FormData: class { constructor(values) { this.values = values; } get(key) { return this.values[key] ?? null; } },
      require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content
        : name.endsWith('/auth') ? {ApiRequestError, authenticatedRequest: async (...args) => { calls.push({method: 'request', args}); return options.request ? options.request(...args) : {}; }}
        : name.endsWith('/useNavigationGuard') ? {useNavigationGuard: (...args) => guards.push(args)}
        : name === './api' ? {bankApi} : name === './external-transfers' ? {externalTransfers: external} : name === './ui' ? ui
        : name === './utils' ? {validAmount: value => /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value) && Number(value) > 0} : {}
    }); return exports;
  };
  ui = load('ui.tsx');
  const Component = options.payeeForm ? load('PayeeForm.tsx').PayeeForm : load('ExternalTransfer.tsx').ExternalTransfer;
  const props = options.payeeForm ? {token: 'synthetic', id: options.id, reload() { reloads++; }} : {token: 'synthetic', userId: 'owner', initialPayee: options.initialPayee, embedded: options.embedded};
  function render() { let attempts = 0; do { dirty = false; cursor = 0; tree = Component(props); effects.splice(0).forEach(run => run());
    if (++attempts > 30) throw new Error('Page did not settle'); } while (dirty); return tree; }
  async function ready() { for (let i = 0; i < 4; i++) { render(); await new Promise(setImmediate); } return render(); }
  function button(label) { return nodes(render()).find(node => node.type === 'button' && text(node) === label); }
  function select(label) { const field = nodes(render()).find(node => node.type === 'label' && text(node).startsWith(label)); return nodes(field).find(node => node.type === 'select'); }
  return {calls, handlers, guards, storage, ApiRequestError, ready, render, button, select, reloads: () => reloads,
    async click(label) { const node = button(label); assert.ok(node, 'Missing button: ' + label); node.props.onClick?.(); return ready(); },
    change(label, value) { select(label).props.onChange({currentTarget: {value}}); return render(); },
    amount(value) { nodes(render()).find(node => node.type === 'input').props.onInput({currentTarget: {value}}); return render(); },
    async submit(values = {}) { await nodes(render()).find(node => node.type === 'form').props.onSubmit({preventDefault() {}, currentTarget: values}); return ready(); },
    dispose() { slots.forEach(slot => slot.cleanup?.()); }};
}
async function reviewed(app) { await app.ready(); app.change('Other-bank payee', 'EP-1'); app.amount('25'); await app.submit(); }

test('external transfer is explicitly a sandbox review and does not post before confirmation', async () => {
  const app = harness(); await app.ready();
  assert.deepEqual(nodes(app.select('Other-bank payee')).filter(node => node.type === 'option').map(node => node.props.value), ['', 'EP-1']);
  for (const amount of ['0', '0.99', '-1', '1.001', '10000000000000', '1e2']) {
    app.change('Other-bank payee', 'EP-1'); app.amount(amount); assert.equal(app.button('Review transfer').props.disabled, true); await app.submit();
  }
  assert.equal(app.calls.filter(call => call.method === 'prepare').length, 0);
  app.amount('25'); await app.submit();
  assert.match(text(app.render()), /Cashfree sandbox: no real money moves. Nexa balances and bills remain unchanged/);
  assert.match(text(app.render()), /Synthetic Recipient/); assert.match(text(app.render()), /HDFC0001234/);
  assert.equal(app.calls.filter(call => call.method === 'confirm').length, 0);
  assert.ok(app.button('Confirm transfer')); app.dispose();
});

test('missing configuration and LIVE environment fail closed while retaining the payee setup link', async () => {
  for (const state of [{ready: false, environment: 'DISABLED', reason: 'Sandbox credentials are missing.'}, {ready: true, environment: 'LIVE', reason: null}]) {
    const app = harness({api: {readiness: async () => state}}); await app.ready();
    app.change('Other-bank payee', 'EP-1'); app.amount('25'); await app.submit();
    assert.equal(app.button('Review transfer').props.disabled, true);
    assert.equal(app.calls.filter(call => call.method === 'prepare').length, 0);
    assert.ok(nodes(app.render()).some(node => node.type === 'a' && node.props.href === '#/beneficiaries'));
    app.dispose();
  }
});

test('double confirmations submit once and pending results only offer provider refresh', async () => {
  let release;
  const pendingMessage = 'The sandbox provider has not confirmed the result. Refresh this transfer; do not submit a replacement.';
  const app = harness({api: {confirm: async () => { await new Promise(resolve => { release = resolve; }); return {...receipt, status: 'PENDING', failureReason: pendingMessage}; }}});
  await reviewed(app); const confirm = app.button('Confirm transfer').props.onClick; confirm(); confirm(); await app.ready();
  assert.equal(app.calls.filter(call => call.method === 'confirm').length, 1); assert.equal(app.guards.at(-1)[1], true);
  release(); await app.ready(); assert.equal(app.button('Confirm transfer'), undefined); assert.equal(app.button('New transfer'), undefined);
  assert.equal(text(app.render()).split(pendingMessage).length - 1, 1);
  assert.doesNotMatch(text(app.render()), /The provider has not returned a final result/);
  await app.click('Refresh provider status');
  assert.equal(app.calls.filter(call => call.method === 'refresh').length, 1); assert.equal(app.calls.filter(call => call.method === 'confirm').length, 1);
  assert.match(text(app.render()), /Cashfree confirmed this sandbox request. No real money moved/);
  assert.match(text(app.render()), /TEST-UTR/); app.dispose();
});

test('lost confirmation must check the existing request before any further submission', async () => {
  const app = harness(); await reviewed(app);
  app.handlers.confirm = async () => { throw new app.ApiRequestError(0, 'Connection lost'); };
  await app.click('Confirm transfer');
  assert.equal(app.button('Confirm transfer'), undefined); assert.equal(app.button('Cancel review'), undefined);
  app.handlers.status = async () => ({...receipt, status: 'PENDING'});
  await app.click('Check transfer status');
  assert.ok(app.button('Refresh provider status')); assert.equal(app.calls.filter(call => call.method === 'confirm').length, 1);
  app.dispose();
});

test('lost preparation is restored with the same key and excludes bank numbers from session storage', async () => {
  const storage = new Map();
  const first = harness({storage, api: {prepare: async () => { throw Error('Lost response'); }}}); await reviewed(first);
  const original = first.calls.find(call => call.method === 'prepare').args[1];
  assert.ok(first.button('Retry same review')); assert.doesNotMatch([...storage.values()].join(' '), /6789|HDFC|recipientName|accountNumber/); first.dispose();
  const app = harness({storage}); app.handlers.status = async () => { throw new app.ApiRequestError(404, 'Not found'); };
  await app.ready(); await app.click('Retry same review');
  assert.equal(JSON.stringify(app.calls.find(call => call.method === 'prepare').args[1]), JSON.stringify(original));
  app.dispose();
});

test('test reversal and failure remain truthful and cancelled review never confirms', async () => {
  const app = harness(); await reviewed(app); await app.click('Cancel review');
  assert.equal(app.calls.filter(call => call.method === 'confirm').length, 0); assert.ok(app.button('New transfer')); app.dispose();
  for (const status of ['FAILED', 'REVERSED']) {
    const state = harness({api: {confirm: async () => ({...receipt, status, failureReason: 'Synthetic provider result.'})}}); await reviewed(state); await state.click('Confirm transfer');
    assert.match(text(state.render()), /Nexa balances and bills remain unchanged/); assert.match(text(state.render()), /Synthetic provider result/);
    assert.ok(state.button('New transfer')); state.dispose();
  }
});

const payee = {name: ' Test payee ', recipient: ' Recipient Name ', bank: ' HDFC Bank ', number: 'ab123456789', confirmation: ' AB123456789 ', ifsc: 'hdfc0001234'};
test('external payee uses its dedicated create API with canonical confirmation and IFSC', async () => {
  const app = harness({payeeForm: true}); await app.ready(); await app.click('Add payee'); app.change('Payee bank', 'external');
  app.change('Bank name', 'HDFC Bank');
  await app.submit(payee);
  const request = app.calls.find(call => call.method === 'request');
  assert.equal(request.args[0], '/external-payees'); assert.equal(request.args[2].method, 'POST');
  assert.deepEqual(JSON.parse(request.args[2].body), {displayName: 'Test payee', recipientName: 'Recipient Name', bankName: 'HDFC Bank', accountNumber: 'AB123456789', accountNumberConfirmation: 'AB123456789', ifsc: 'HDFC0001234'});
  assert.equal(app.storage.size, 0); assert.equal(app.reloads(), 1); app.dispose();
});

test('an unlisted bank requires its name and sends that name instead of the dropdown sentinel', async () => {
  const app = harness({payeeForm: true}); await app.ready(); await app.click('Add payee'); app.change('Payee bank', 'external');
  const options = nodes(app.select('Bank name')).filter(node => node.type === 'option').map(node => node.props.value);
  for (const bank of ['State Bank of India', 'HDFC Bank', 'ICICI Bank', 'Axis Bank', 'OTHER']) assert.ok(options.includes(bank));
  assert.equal(nodes(app.render()).some(node => node.props?.name === 'customBank'), false);
  app.change('Bank name', 'OTHER');
  assert.ok(nodes(app.render()).find(node => node.props?.name === 'customBank').props.required);
  await app.submit({...payee, bank: 'OTHER', customBank: ' '});
  assert.equal(app.calls.filter(call => call.method === 'request').length, 0);
  await app.submit({...payee, bank: 'OTHER', customBank: ' Example Cooperative Bank '});
  assert.equal(JSON.parse(app.calls.find(call => call.method === 'request').args[2].body).bankName, 'Example Cooperative Bank');
  app.dispose();
});

test('external account confirmation, provider bounds and IFSC prevent invalid requests', async () => {
  const app = harness({payeeForm: true}); await app.ready(); await app.click('Add payee'); app.change('Payee bank', 'external');
  for (const invalid of [{confirmation: '999999999'}, {number: '12345678', confirmation: '12345678'}, {number: '1234567890123456789', confirmation: '1234567890123456789'}, {ifsc: 'HDFC1001234'}, {recipient: ''}, {recipient: 'Name123'}, {recipient: 'A'.repeat(101)}]) {
    await app.submit({...payee, ...invalid}); assert.ok(nodes(app.render()).some(node => node.props?.role === 'alert'));
  }
  assert.equal(app.calls.filter(call => call.method === 'request').length, 0); app.dispose();
});

test('internal payee creation retains the existing numeric account contract and endpoint', async () => {
  const app = harness({payeeForm: true}); await app.ready(); await app.click('Add payee'); await app.submit({name: 'Nexa recipient', number: '123456789012'});
  const request = app.calls.find(call => call.method === 'request'); assert.equal(request.args[0], '/beneficiaries');
  assert.deepEqual(JSON.parse(request.args[2].body), {displayName: 'Nexa recipient', accountNumber: '123456789012'}); app.dispose();
});

test('payee lists combine dedicated endpoints and external detail never calls internal mutation routes', async () => {
  const calls = [], exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/api.ts', 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021}}).outputText, {
    exports, URLSearchParams, require: name => name.endsWith('/auth') ? {authenticatedRequest: async (path) => { calls.push(path); return path === '/beneficiaries' ? [{id: 'B-1'}] : path === '/external-payees' ? [{id: 'EP-1'}] : {}; }} : {}
  });
  const rows = await exports.bankApi.products('synthetic', 'beneficiaries'); assert.deepEqual(Array.from(rows, row => row.id), ['B-1', 'EP-1']);
  await exports.bankApi.product('synthetic', 'beneficiaries', 'EP-1'); await exports.bankApi.product('synthetic', 'beneficiaries', 'B-1');
  assert.deepEqual(calls, ['/beneficiaries', '/external-payees', '/external-payees/EP-1', '/beneficiaries/B-1']);
});

test('completed test transfers can refresh into a later provider reversal without reposting', async () => {
  const app = harness({api: {confirm: async () => ({...receipt, status: 'COMPLETED'}), refresh: async () => ({...receipt, status: 'REVERSED', failureReason: 'Test reversal.'})}});
  await reviewed(app); await app.click('Confirm transfer'); await app.click('Refresh provider status');
  assert.match(text(app.render()), /Cashfree reversed this sandbox request/);
  assert.equal(app.calls.filter(call => call.method === 'confirm').length, 1);
  assert.equal(app.calls.filter(call => call.method === 'refresh').length, 1); app.dispose();
});

test('embedded other-bank flow has no duplicate page title and keeps sandbox disclosure beside ordinary transfer actions', async () => {
  const app = harness({embedded: true, initialPayee: 'EP-1'}); await app.ready();
  assert.equal(nodes(app.render()).some(node => node.type === 'h1'), false);
  assert.match(text(app.render()), /Cashfree sandbox: no real money moves/);
  assert.equal(app.select('Other-bank payee').props.value, 'EP-1');
  assert.ok(app.button('Review transfer'));
  assert.equal(nodes(app.render()).some(node => ['button', 'h1', 'h2'].includes(node.type) && /test/i.test(text(node))), false);
  app.amount('25'); await app.submit(); assert.ok(app.button('Confirm transfer'));
  assert.match(text(app.render()), /Nexa balances and bills remain unchanged/);
  app.dispose();
});
