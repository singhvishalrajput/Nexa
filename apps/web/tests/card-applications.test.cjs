const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const card = {id: 'CARD-1', displayName: 'Everyday card', cardType: 'DEBIT', status: 'ACTIVE', accountId: '1', creditLimit: '0', currencyCode: 'INR'};
const application = {id: 'CARD-2', displayName: 'Credit request', cardType: 'CREDIT', status: 'PENDING_APPROVAL', accountId: '1', applicantName: 'Sample Customer', applicantUserId: 'customer-1', creditLimit: '0', createdAt: '2026-09-26T10:00:00Z', reviewedAt: null, reviewReason: null};

function harness(mode = 'create', options = {}) {
  const slots = [], effects = [], calls = [], notices = [], guards = [];
  let cursor = 0, dirty = true, tree, ui, api, reloads = 0, requests = options.requests || [application];
  class ApiRequestError extends Error {constructor(status, message) {super(message); this.status = status;}}
  const hooks = {
    useState(initial) {const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial}; return [slots[at].value, value => {const next = typeof value === 'function' ? value(slots[at].value) : value; if (!Object.is(next, slots[at].value)) {slots[at].value = next; dirty = true;}}];},
    useRef(initial) {const at = cursor++; return slots[at] || (slots[at] = {current: initial});},
    useEffect(effect, deps) {const at = cursor++, old = slots[at]; if (!old || deps.some((value, i) => value !== old.deps[i])) {slots[at] = {deps}; effects.push(() => {old?.cleanup?.(); slots[at].cleanup = effect();});}}
  };
  const handlers = {request: async (path, _token, init) => {
    const body = init?.body ? JSON.parse(init.body) : null;
    if (path === '/cards/applications') return {...card, cardType: body.cardType, status: body.cardType === 'CREDIT' ? 'PENDING_APPROVAL' : 'ACTIVE'};
    if (path.endsWith('/block')) return {...card, status: 'BLOCKED'};
    if (path.endsWith('/unblock')) return {...card, status: 'ACTIVE'};
    if (path.endsWith('/approve')) return {...application, status: 'ACTIVE', creditLimit: body.creditLimit};
    if (path.endsWith('/reject')) return {...application, status: 'REJECTED', reviewReason: body.reason};
    if (path.startsWith('/admin/card-applications?')) return requests;
    if (path.startsWith('/admin/card-applications/')) return application;
    return card;
  }, ...options.handlers};
  const request = async (...args) => {calls.push({path: args[0], token: args[1], init: args[2], body: args[2]?.body ? JSON.parse(args[2].body) : undefined}); return handlers.request(...args);};
  const bankApi = {accounts: async () => options.accounts || [
    {id: '1', displayName: 'Savings', accountNumberMasked: '•••• 1111', status: 'ACTIVE', accountType: 'SAVINGS', currencyCode: 'INR'},
    {id: '2', status: 'BLOCKED', accountType: 'SAVINGS', currencyCode: 'INR'}, {id: '3', status: 'ACTIVE', accountType: 'LOAN', currencyCode: 'INR'}, {id: '4', status: 'ACTIVE', accountType: 'CURRENT', currencyCode: 'USD'}]};
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  const load = relative => {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/' + relative, 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
      {exports, Error, TextEncoder, URLSearchParams, crypto: {randomUUID: () => '11111111-1111-4111-8111-111111111111'}, require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content : name.endsWith('/auth') ? {ApiRequestError, authenticatedRequest: request}
        : name.endsWith('/card-applications') ? api : name.endsWith('/useNavigationGuard') ? {useNavigationGuard: (...args) => guards.push(args)} : name === './api' ? {bankApi}
        : name === './ui' ? ui : name === './utils' ? {validAmount: value => /^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value) && Number(value) > 0} : {}});
    return exports;
  };
  api = load('services/card-applications.ts'); ui = {...load('features/banking/ui.tsx'), Modal: props => ({type: 'dialog', props})};
  const customer = load('features/banking/CardApplications.tsx'), admin = load('features/banking/AdminCardQueue.tsx');
  const Component = {create: customer.CardApplications, status: customer.CardStatusControl, queue: admin.AdminCardQueue, decision: admin.AdminCardDecision}[mode];
  const props = {token: 'synthetic-token', initiallyOpen: options.initiallyOpen ?? true, product: options.product || card, request: options.application || application,
    reload() {reloads++;}, onPosted: message => notices.push(message), done: message => notices.push(message), close() {notices.push('closed');}};
  function render() {let attempts = 0; do {dirty = false; cursor = 0; tree = Component(props); effects.splice(0).forEach(run => run()); if (++attempts > 30) throw Error('Render did not settle');} while (dirty); return tree;}
  async function ready() {for (let i = 0; i < 4; i++) {render(); await new Promise(setImmediate);} return render();}
  const button = label => nodes(render()).find(node => node.type === 'button' && text(node) === label);
  function control(label, type) {return nodes(nodes(render()).find(node => node.type === 'label' && text(node).startsWith(label))).find(node => node.type === type);}
  return {calls, notices, handlers, guards, ApiRequestError, ready, render, button, reloads: () => reloads, select: label => control(label, 'select'),
    async click(label) {const target = button(label); assert.ok(target, 'Missing button: ' + label); target.props.onClick?.(); return ready();},
    change(label, value) {control(label, 'select').props.onChange({currentTarget: {value}}); return render();},
    input(label, value, type = 'input') {control(label, type).props.onInput({currentTarget: {value}}); return render();},
    async submit() {nodes(render()).find(node => node.type === 'form').props.onSubmit({preventDefault() {}}); return ready();},
    dispose() {slots.forEach(slot => slot.cleanup?.());}};
}

test('card requests show only eligible owned deposit accounts and create a persisted debit record', async () => {
  const app = harness(); await app.ready();
  assert.deepEqual(nodes(app.select('Linked Nexa account')).filter(node => node.type === 'option').map(node => node.props.value), ['', '1']);
  assert.equal(app.button('Request debit card').props.disabled, true);
  app.change('Linked Nexa account', '1'); app.input('Card name', ' Daily debit '); await app.submit();
  assert.deepEqual(app.calls[0].body, {accountId: 1, cardType: 'DEBIT', requestId: '11111111-1111-4111-8111-111111111111', displayName: 'Daily debit'});
  assert.equal(app.calls[0].path, '/cards/applications'); assert.equal(app.calls[0].token, 'synthetic-token');
  assert.equal(app.reloads(), 1); assert.match(text(app.render()), /card record is active/);
  assert.match(text(app.render()), /No account balance was changed/); app.dispose();
});

test('credit applications remain pending bank approval and customers cannot choose a credit limit', async () => {
  const app = harness(); await app.ready(); app.change('Card type', 'CREDIT'); app.change('Linked Nexa account', '1');
  assert.equal(nodes(app.render()).filter(node => node.type === 'input').length, 1);
  await app.submit(); assert.equal(app.calls[0].body.cardType, 'CREDIT'); assert.equal('creditLimit' in app.calls[0].body, false);
  assert.match(text(app.render()), /awaiting bank review. No credit limit is available yet/);
  assert.match(text(app.render()), /Physical cards and card-network payments are not issued here/); app.dispose();
});

test('lost card creation preserves the exact request and prevents duplicate submissions', async () => {
  const app = harness(); await app.ready(); app.change('Linked Nexa account', '1');
  app.handlers.request = async () => {throw new app.ApiRequestError(0, 'Connection lost');};
  await app.submit(); const original = app.calls[0].body;
  assert.equal(nodes(app.render()).find(node => node.type === 'fieldset').props.disabled, true);
  assert.equal(app.button('Close').props.disabled, true); assert.equal(app.guards.at(-1)[1], true);
  app.handlers.request = async () => card; await app.click('Retry same card request');
  assert.deepEqual(app.calls[1].body, original); assert.equal(app.reloads(), 1); app.dispose();
});

test('card validation rejects long names and missing eligible accounts without writing', async () => {
  const app = harness(); await app.ready(); app.change('Linked Nexa account', '1');
  for (const name of ['A'.repeat(101), 'क'.repeat(41)]) {app.input('Card name', name); assert.equal(app.button('Request debit card').props.disabled, true); await app.submit();}
  assert.equal(app.calls.length, 0); app.dispose();
  const empty = harness('create', {accounts: []}); await empty.ready();
  assert.match(text(empty.render()), /need an active Nexa INR savings or current account/); assert.equal(empty.button('Request debit card'), undefined); empty.dispose();
});

test('card controls review and persist block, then safely reconcile an uncertain unblock', async () => {
  const app = harness('status'); await app.ready(); await app.click('Block card'); assert.equal(app.calls.length, 0);
  await app.click('Confirm block'); assert.equal(app.calls[0].path, '/cards/CARD-1/block'); assert.equal(app.calls[0].init.method, 'POST');
  assert.deepEqual(app.notices, ['Card blocked in Nexa.']); app.dispose();
  const blocked = harness('status', {product: {...card, status: 'BLOCKED'}}); await blocked.ready(); await blocked.click('Unblock card');
  blocked.handlers.request = async () => {throw new blocked.ApiRequestError(0, 'Lost response');};
  await blocked.click('Confirm unblock'); assert.equal(blocked.button('Cancel'), undefined);
  blocked.handlers.request = async () => card; await blocked.click('Check card status');
  assert.equal(blocked.calls[0].path, '/cards/CARD-1/unblock'); assert.equal(blocked.calls[1].path, '/cards/CARD-1');
  assert.deepEqual(blocked.notices, ['Card unblocked in Nexa.']); blocked.dispose();
});

test('unapproved or rejected cards offer no block, payment or replacement actions', async () => {
  for (const status of ['PENDING_APPROVAL', 'REJECTED', 'CLOSED']) {
    const app = harness('status', {product: {...card, cardType: 'CREDIT', status}}); await app.ready();
    assert.equal(nodes(app.render()).some(node => node.type === 'button'), false);
    assert.equal(app.calls.length, 0); app.dispose();
  }
});

test('admin approval requires a reason and valid bank-set limit, then explicit confirmation', async () => {
  const app = harness('decision'); await app.ready();
  assert.equal(app.button('Review decision').props.disabled, true); app.input('Reason for decision', 'Reviewed application', 'textarea');
  for (const value of ['0', '-1', '1.001', '10000000000000', '1e3']) {app.input('Approved credit limit', value); assert.equal(app.button('Review decision').props.disabled, true);}
  app.input('Approved credit limit', '50000.25'); await app.click('Review decision'); assert.equal(app.calls.length, 0);
  await app.click('Confirm approval'); assert.equal(app.calls[0].path, '/admin/card-applications/CARD-2/approve');
  assert.deepEqual(app.calls[0].body, {reason: 'Reviewed application', creditLimit: '50000.25'});
  assert.match(app.notices[0], /approved with a limit of ₹50,000.25/); app.dispose();
});

test('admin rejection excludes stale limit and lost outcomes retry an unchanged decision', async () => {
  const app = harness('decision'); await app.ready(); app.input('Approved credit limit', '50000'); app.change('Decision', 'reject'); app.input('Reason for decision', 'Eligibility not met', 'textarea');
  await app.click('Review decision'); app.handlers.request = async () => {throw new app.ApiRequestError(0, 'Lost decision response');};
  await app.click('Confirm rejection'); const original = app.calls[0].body;
  assert.deepEqual(original, {reason: 'Eligibility not met'}); assert.equal(nodes(app.render()).find(node => node.type === 'dialog').props.locked, true);
  assert.equal(app.button('Edit decision'), undefined);
  app.handlers.request = async () => ({...application, status: 'REJECTED'}); await app.click('Retry same decision');
  assert.deepEqual(app.calls[1].body, original); assert.match(app.notices[0], /Card request rejected/); app.dispose();
});

test('admin card queue filters persisted requests and known decisions have no review form', async () => {
  const app = harness('queue'); await app.ready(); assert.equal(app.calls[0].path, '/admin/card-applications?status=PENDING_APPROVAL');
  app.change('Application status', 'ALL'); await app.ready(); assert.equal(app.calls.at(-1).path, '/admin/card-applications?status=ALL'); app.dispose();
  const decided = harness('decision', {application: {...application, status: 'ACTIVE', creditLimit: '50000', reviewReason: 'Approved after review'}}); await decided.ready();
  assert.equal(decided.button('Review decision'), undefined); assert.match(text(decided.render()), /Approved after review/); decided.dispose();
});

test('definitive bank restrictions after a lost card action allow recovery without inventing success', async () => {
  const app = harness('status'); await app.ready(); await app.click('Block card');
  app.handlers.request = async () => {throw new app.ApiRequestError(0, 'Lost response');}; await app.click('Confirm block');
  assert.equal(app.button('Cancel'), undefined);
  app.handlers.request = async () => {throw new app.ApiRequestError(409, 'This card has a bank restriction. Contact the administrator.');};
  await app.click('Retry same card action'); assert.ok(app.button('Cancel')); assert.equal(app.notices.length, 0); assert.equal(app.reloads(), 0);
  assert.match(text(app.render()), /bank restriction/); app.dispose();
});

test('duplicate clicks submit a card application only once while the server is responding', async () => {
  let release;
  const app = harness(); await app.ready(); app.change('Linked Nexa account', '1');
  app.handlers.request = async () => {await new Promise(resolve => {release = resolve;}); return card;};
  await app.submit(); await app.submit(); assert.equal(app.calls.length, 1);
  release(); await app.ready(); assert.equal(app.reloads(), 1); app.dispose();
});
