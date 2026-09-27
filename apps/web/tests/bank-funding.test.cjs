const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const overview = {reserveBalance: '25000.00', cashBalance: '50000.00', currencyCode: 'INR', ready: true, reason: null, receipts: []};
const receiptFor = request => ({...request, id: 'FUNDING-1', receiptNumber: 'FCR-0001', currencyCode: 'INR', transactionId: 'TX-1', recordedBy: 'Fixture Administrator', recordedAt: '2026-09-27T05:30:00Z', reserveBalanceAfter: '25100.50', cashBalanceAfter: '50100.50'});
let uuidSequence = 0;

function harness(options = {}) {
  const slots = [], effects = [], calls = [], guards = [], storage = options.storage || new Map();
  const userId = options.userId || 'admin-1', key = 'nexa-bank-funding-recovery:' + userId;
  let cursor = 0, dirty = true, tree, api, ui;
  class ApiRequestError extends Error {constructor(status, message) {super(message); this.status = status;}}
  const hooks = {
    useState(initial) {const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial}; return [slots[at].value, value => {const next = typeof value === 'function' ? value(slots[at].value) : value; if (!Object.is(next, slots[at].value)) {slots[at].value = next; dirty = true;}}];},
    useRef(initial) {const at = cursor++; return slots[at] || (slots[at] = {current: initial});},
    useEffect(effect, deps) {const at = cursor++, old = slots[at]; if (!old || deps.some((value, i) => value !== old.deps[i])) {slots[at] = {deps}; effects.push(() => {old?.cleanup?.(); slots[at].cleanup = effect();});}}
  };
  const handlers = {request: async (path, _token, init) => {
    if (path === '/admin/bank-funding') return options.overview || overview;
    if (init?.method === 'POST') return receiptFor(JSON.parse(init.body));
    throw new ApiRequestError(404, 'Receipt not found');
  }};
  const request = async (path, token, init) => {calls.push({path, token, init, body: init?.body ? JSON.parse(init.body) : undefined}); return handlers.request(path, token, init);};
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  const load = relative => {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/' + relative, 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
      {exports, Error, TextEncoder, Date, crypto: {randomUUID: () => '11111111-1111-4111-8111-' + String(++uuidSequence).padStart(12, '0')},
        window: {sessionStorage: {getItem: name => storage.get(name) || null, setItem(name, value) {if (options.storageFails) throw Error('Storage disabled'); storage.set(name, value);}, removeItem: name => storage.delete(name)}},
        require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
          : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content
          : name.endsWith('/auth') ? {ApiRequestError, authenticatedRequest: request} : name.endsWith('/bank-funding') ? api
          : name.endsWith('/useNavigationGuard') ? {useNavigationGuard: (...args) => guards.push(args)} : name === './ui' ? ui : {}});
    return exports;
  };
  api = load('services/bank-funding.ts'); ui = load('features/banking/ui.tsx');
  const Component = load('features/banking/AdminBankFunding.tsx').AdminBankFunding;
  function render() {let attempts = 0; do {dirty = false; cursor = 0; tree = Component({token: 'admin-token', userId}); effects.splice(0).forEach(run => run()); if (++attempts > 30) throw Error('Render did not settle');} while (dirty); return tree;}
  async function ready() {for (let i = 0; i < 4; i++) {render(); await new Promise(setImmediate);} return render();}
  const button = label => nodes(render()).find(node => node.type === 'button' && text(node).replace(/\s+/g, ' ').trim() === label);
  return {api, ApiRequestError, calls, storage, key, guards, handlers, render, ready, button,
    input(label, value) {const node = nodes(nodes(render()).find(node => node.type === 'label' && text(node).startsWith(label))).find(node => ['input', 'textarea'].includes(node.type)); assert.ok(node, label); node.props.onInput({currentTarget: {value}}); render();},
    acknowledge(value = true) {const node = nodes(render()).find(node => node.type === 'input' && node.props.type === 'checkbox'); assert.ok(node); node.props.onChange({currentTarget: {checked: value}}); render();},
    async click(label) {const node = button(label); assert.ok(node, 'Missing button ' + label); node.props.onClick?.(); return ready();},
    async submit() {const form = nodes(render()).find(node => node.type === 'form'); assert.ok(form); form.props.onSubmit({preventDefault() {}}); return ready();},
    dispose() {slots.forEach(slot => slot.cleanup?.());}}
}
async function prepare(app) {
  await app.ready(); app.input('Amount', '100.5'); app.input('Source of cash', '  Bank   owner capital ');
  app.input('Source document reference', ' cash / 2026-01 '); app.input('Reason', '  Capital   received for lending '); await app.submit();
}

test('funding page displays authenticated bank balances and clearly describes actual cash recording', async () => {
  const app = harness(); await app.ready();
  assert.equal(app.calls[0].path, '/admin/bank-funding'); assert.equal(app.calls[0].token, 'admin-token');
  assert.match(text(app.render()), /Lending reserve ₹25,000/); assert.match(text(app.render()), /Bank cash account ₹50,000/);
  assert.match(text(app.render()), /bank-owned cash already received/); assert.match(text(app.render()), /does not start an external bank transfer or take money from customer deposits/);
  assert.match(text(app.render()), /No bank cash receipts recorded yet/); app.dispose();
});

test('cash receipt requires immutable review plus explicit acknowledgement before posting normalized values', async () => {
  const app = harness(); await prepare(app);
  assert.equal(app.calls.filter(call => call.init?.method === 'POST').length, 0);
  assert.equal(app.button('Confirm cash received').props.disabled, true);
  assert.equal(nodes(app.render()).filter(node => node.type === 'input' && node.props.type !== 'checkbox').length, 0);
  assert.match(text(app.render()), /CASH\/2026-01/);
  app.acknowledge(); await app.click('Confirm cash received');
  const post = app.calls.find(call => call.init?.method === 'POST');
  assert.equal(post.path, '/admin/bank-funding/receipts');
  assert.deepEqual({...post.body, requestId: undefined}, {requestId: undefined, amount: '100.50', source: 'Bank owner capital', reference: 'CASH/2026-01', reason: 'Capital received for lending', confirmed: true});
  assert.match(text(app.render()), /Cash receipt recorded/); assert.match(text(app.render()), /FCR-0001/);
  assert.match(text(app.render()), /Recorded by Fixture Administrator/); assert.match(text(app.render()), /Transaction reference TX-1/);
  assert.equal(app.storage.has(app.key), false); assert.ok(app.button('Record another receipt')); app.dispose();
});

test('double confirmation sends one request and saves its recovery envelope before sending', async () => {
  const app = harness(); await prepare(app); app.acknowledge();
  let complete;
  app.handlers.request = async (path, _token, init) => {
    if (!init?.method) return overview;
    const body = JSON.parse(init.body); assert.deepEqual(JSON.parse(app.storage.get(app.key)), {version: 1, userId: 'admin-1', request: body});
    return new Promise(resolve => {complete = () => resolve(receiptFor(body));});
  };
  const button = app.button('Confirm cash received'); button.props.onClick(); button.props.onClick(); await app.ready();
  assert.equal(app.calls.filter(call => call.init?.method === 'POST').length, 1);
  assert.equal(app.guards.at(-1)[1], true); complete(); await app.ready(); assert.ok(app.button('Record another receipt')); app.dispose();
});

test('invalid funding amounts and text bounds cannot be reviewed or posted', async () => {
  const app = harness(); await app.ready(); app.input('Source of cash', 'Owner'); app.input('Source document reference', 'CAP-1'); app.input('Reason', 'Capital');
  for (const amount of ['0', '-1', '1e3', '1.001', '10000000.01', '010']) {app.input('Amount', amount); assert.equal(app.button('Review cash receipt').props.disabled, true); await app.submit();}
  app.input('Amount', '10000000'); assert.equal(app.button('Review cash receipt').props.disabled, false);
  app.input('Source of cash', 'क'.repeat(54)); assert.equal(app.button('Review cash receipt').props.disabled, true); assert.match(text(app.render()), /Enter a shorter source name/);
  app.input('Source of cash', 'Owner'); app.input('Reason', 'क'.repeat(167)); assert.equal(app.button('Review cash receipt').props.disabled, true); assert.match(text(app.render()), /Enter a shorter reason/);
  app.input('Reason', 'Capital');
  for (const reference of ['/NO', 'A'.repeat(81), 'cash:1', 'भारत', 'ß', 'a\uFEFF']) {app.input('Source document reference', reference); assert.equal(app.button('Review cash receipt').props.disabled, true); await app.submit();}
  app.input('Source document reference', 'CAP-1');
  for (const source of ['Owner\u200D', 'Owner\u0086', '\uFEFFOwner']) {app.input('Source of cash', source); assert.equal(app.button('Review cash receipt').props.disabled, true);}
  assert.equal(app.calls.filter(call => call.init?.method === 'POST').length, 0); app.dispose();
});

test('Unicode whitespace normalizes like the server without rewriting hidden format characters', () => {
  const app = harness();
  const request = app.api.fundingRequest({amount: '1', source: ' Owner\u0085capital ', reference: ' cap\u0085- 1 ', reason: 'Cash\u00a0received'});
  assert.equal(request.source, 'Owner capital'); assert.equal(request.reference, 'CAP-1'); assert.equal(request.reason, 'Cash received');
  assert.equal(app.api.verifiedFundingReceipt(receiptFor(request), request), true);
  assert.equal(app.api.validFundingFields({...request, source: '\uFEFFOwner'}), false);
  assert.equal(app.api.validFundingFields({...request, reference: 'ß'}), false); app.dispose();
});

test('unknown results freeze the same payload and key even after a not-found check and a later rejection', async () => {
  const app = harness(); await prepare(app); app.acknowledge();
  app.handlers.request = async () => {throw new app.ApiRequestError(503, 'Connection interrupted');};
  await app.click('Confirm cash received'); const original = app.calls.at(-1).body;
  assert.equal(app.button('Edit receipt'), undefined); assert.equal(app.button('Record another receipt'), undefined);
  assert.ok(app.storage.has(app.key));
  app.handlers.request = async () => {throw new app.ApiRequestError(404, 'Not found');};
  await app.click('Check receipt status'); assert.match(text(app.render()), /No receipt was found yet/);
  assert.equal(app.calls.at(-1).path, '/admin/bank-funding/receipts/by-request/' + original.requestId);
  app.handlers.request = async () => {throw new app.ApiRequestError(400, 'Account unavailable');};
  await app.click('Retry same receipt'); assert.deepEqual(app.calls.at(-1).body, original); assert.equal(app.button('Edit receipt'), undefined);
  app.handlers.request = async (_path, _token, init) => init?.method ? receiptFor(JSON.parse(init.body)) : overview;
  await app.click('Retry same receipt'); assert.deepEqual(app.calls.findLast(call => call.init?.method === 'POST').body, original);
  assert.match(text(app.render()), /Cash receipt recorded/); assert.equal(app.storage.has(app.key), false); app.dispose();
});

test('reload restores the same administrator request and performs only a status lookup until reconfirmed', async () => {
  const app = harness(); await prepare(app); app.acknowledge(); app.handlers.request = async () => {throw new app.ApiRequestError(0, 'Lost response');};
  await app.click('Confirm cash received'); const original = app.calls.at(-1).body; app.dispose();
  const restored = harness({storage: app.storage}); await restored.ready();
  assert.ok(restored.calls.some(call => call.path.endsWith(original.requestId)));
  assert.equal(restored.calls.some(call => call.init?.method === 'POST'), false);
  assert.equal(restored.button('Retry same receipt').props.disabled, true);
  restored.acknowledge(); await restored.click('Retry same receipt');
  assert.deepEqual(restored.calls.find(call => call.init?.method === 'POST').body, original); restored.dispose();
});

test('saved funding recovery is isolated between signed-in administrators', async () => {
  const first = harness(); await prepare(first); first.acknowledge(); first.handlers.request = async () => {throw Error('Unknown');};
  await first.click('Confirm cash received'); first.dispose();
  const second = harness({userId: 'admin-2', storage: first.storage}); await second.ready();
  assert.ok(second.button('Review cash receipt')); assert.equal(second.calls.length, 1);
  assert.ok(second.storage.has(first.key)); second.dispose();
});

test('a mismatched success payload never claims that the reviewed receipt was recorded', async () => {
  const app = harness(); await prepare(app); app.acknowledge();
  app.handlers.request = async (_path, _token, init) => ({...receiptFor(JSON.parse(init.body)), amount: '999.00'});
  await app.click('Confirm cash received'); assert.doesNotMatch(text(app.render()), /Cash receipt recorded/);
  assert.match(text(app.render()), /did not match this request/); assert.ok(app.storage.has(app.key)); assert.ok(app.button('Retry same receipt')); app.dispose();
});

test('unavailable session storage blocks the POST before money can be recorded', async () => {
  const app = harness({storageFails: true}); await prepare(app); app.acknowledge(); await app.click('Confirm cash received');
  assert.equal(app.calls.some(call => call.init?.method === 'POST'), false); assert.match(text(app.render()), /No request was sent/); app.dispose();
});

test('a definitive first-attempt validation failure permits correction without leaving a pending receipt', async () => {
  const app = harness(); await prepare(app); app.acknowledge(); app.handlers.request = async () => {throw new app.ApiRequestError(400, 'Enter a valid source document reference');};
  await app.click('Confirm cash received'); assert.ok(app.button('Review cash receipt')); assert.equal(app.storage.has(app.key), false);
  assert.match(text(app.render()), /Enter a valid source document reference/); app.dispose();
});

test('a first duplicate-reference rejection releases correction with guidance and does not issue a replacement automatically', async () => {
  const app = harness(); await prepare(app); app.acknowledge(); app.handlers.request = async () => {throw new app.ApiRequestError(409, 'This cash receipt reference has already been recorded');};
  await app.click('Confirm cash received'); assert.ok(app.button('Review cash receipt')); assert.equal(app.storage.has(app.key), false);
  assert.match(text(app.render()), /Check recent receipts/); assert.match(text(app.render()), /Do not record the same cash under another reference/);
  assert.equal(app.calls.filter(call => call.init?.method === 'POST').length, 1); app.dispose();
});

test('a conflict after an uncertain result preserves the exact request instead of enabling replacement', async () => {
  const app = harness(); await prepare(app); app.acknowledge(); app.handlers.request = async () => {throw Error('Unknown response');};
  await app.click('Confirm cash received'); const saved = app.storage.get(app.key);
  app.handlers.request = async () => {throw new app.ApiRequestError(409, 'Conflicting request');};
  await app.click('Retry same receipt'); assert.equal(app.storage.get(app.key), saved);
  assert.equal(app.button('Edit receipt'), undefined); assert.equal(app.button('Review cash receipt'), undefined); app.dispose();
});

test('missing bank accounts show unavailable balances and prevent a receipt review', async () => {
  const app = harness({overview: {...overview, reserveBalance: null, cashBalance: null, ready: false, reason: 'Funding accounts need setup'}}); await app.ready();
  assert.match(text(app.render()), /Lending reserve Unavailable/); assert.match(text(app.render()), /Funding accounts need setup/);
  assert.equal(app.button('Review cash receipt').props.disabled, true); assert.doesNotMatch(text(app.render()), /₹0/); app.dispose();
});

test('malformed saved recovery details cannot silently become a new funding request', async () => {
  const storage = new Map([['nexa-bank-funding-recovery:admin-1', '{broken']]);
  const app = harness({storage}); await app.ready(); assert.equal(app.button('Review cash receipt'), undefined);
  assert.match(text(app.render()), /Do not record a replacement/); assert.equal(app.calls.length, 1); assert.equal(storage.get(app.key), '{broken'); app.dispose();
});

test('restored receipt lookup resolves a previously posted request without another POST', async () => {
  const app = harness(); await prepare(app); app.acknowledge(); app.handlers.request = async () => {throw Error('Lost');};
  await app.click('Confirm cash received'); const original = app.calls.at(-1).body; app.dispose();
  const restored = harness({storage: app.storage});
  restored.handlers.request = async path => path.includes('/by-request/') ? receiptFor(original) : {...overview, receipts: [receiptFor(original)]};
  await restored.ready(); assert.match(text(restored.render()), /Cash receipt recorded/); assert.equal(restored.calls.some(call => call.init?.method === 'POST'), false);
  assert.match(text(restored.render()), /Receipt and audit details/); assert.equal(restored.storage.has(restored.key), false); restored.dispose();
});
