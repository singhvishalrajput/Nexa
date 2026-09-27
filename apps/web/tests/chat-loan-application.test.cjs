const {test} = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript');
const {File} = require('node:buffer');
const months = ['2026-06', '2026-07', '2026-08'];
const clientId = '11111111-1111-4111-8111-111111111111', applicationKey = 'chat-loan-' + clientId;
const account = {id: '12', displayName: 'Savings', accountNumberMasked: '•••• 1234', accountType: 'SAVINGS', status: 'ACTIVE', currencyCode: 'INR'};
const draft = {applicationKey, accountId: 12, purpose: 'Education loan', amount: '100000', tenureMonths: 12};
const saved = (value = draft, status = 'PENDING_APPROVAL') => ({id: 'LN-chat-1', displayName: value.purpose, accountId: String(value.accountId), status, currencyCode: 'INR', outstanding: '0', nextEmi: '0', terms: {...value}});
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.action), ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
class FormDataFixture extends FormData {
  constructor(form) {super(); for (const [key, value] of form?.fields || []) this.append(key, value);}
}
function fields() {return {fields: [['name', draft.purpose], ['account', String(draft.accountId)], ['amount', draft.amount], ['tenure', String(draft.tenureMonths)], ...months.map(month => ['slip-' + month, new File(['%PDF-1.7 salary for ' + month], month + '.pdf', {type: 'application/pdf'})])]};}

function harness(options = {}) {
  const instances = new Map(), effects = [], calls = [], cache = new Map();
  let current, cursor = 0, dirty = true, tree, visited;
  const props = {clientId, accessToken: 'owner-token', active: true, ...options.props};
  class ApiRequestError extends Error {constructor(status, message) {super(message); this.status = status;}}
  const hooks = {
    useState(initial) {const slots = current, at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial}; return [slots[at].value, value => {const next = typeof value === 'function' ? value(slots[at].value) : value; if (!Object.is(next, slots[at].value)) {slots[at].value = next; dirty = true;}}];},
    useRef(initial) {const at = cursor++; return current[at] || (current[at] = {current: initial});},
    useEffect(effect, deps) {const slots = current, at = cursor++, old = slots[at]; if (!old || deps.some((value, i) => value !== old.deps[i])) {slots[at] = {deps}; effects.push(() => {old?.cleanup?.(); slots[at].cleanup = effect();});}}
  };
  const handlers = {request: async (url, _token, init) => {
    if (url.startsWith('/loans/applications/by-request/')) {if (options.existing) return options.existing; throw new ApiRequestError(404, 'Application not found');}
    if (url === '/accounts') return options.accounts || [account];
    if (url === '/loans/salary-slip-requirements') return months;
    if (url === '/loans' && init?.method === 'POST') return saved(JSON.parse(await init.body.get('application').text()));
    throw Error('Unexpected API call: ' + url);
  }};
  const request = async (url, token, init) => {calls.push({url, token, method: init?.method, body: init?.body}); return handlers.request(url, token, init);};
  const jsx = (type, props, key) => ({type, key, props: props || {}});
  const load = relative => {
    if (cache.has(relative)) return cache.get(relative);
    const exports = {}; cache.set(relative, exports);
    vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.resolve(__dirname, '../src', relative), 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText, {
      exports, Error, Date, FormData: FormDataFixture, Blob, File, crypto: {randomUUID: () => 'standalone-random-key'}, window: {alert() {}, confirm: () => true},
      require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'}
        : name.endsWith('/auth') ? {ApiRequestError, authenticatedRequest: request}
        : name.endsWith('/api') ? {bankApi: {accounts: token => request('/accounts', token)}}
        : name.endsWith('/banking-content') ? load('services/banking-content.ts')
        : name.endsWith('/loan-applications') ? load('services/loan-applications.ts')
        : name.endsWith('/useNavigationGuard') ? load('hooks/useNavigationGuard.ts')
        : name.endsWith('/ProductOperations') ? load('features/banking/ProductOperations.tsx')
        : name.endsWith('/LoanSalarySlips') ? load('features/banking/LoanSalarySlips.tsx')
        : name.endsWith('/ui') ? load('features/banking/ui.tsx') : {}
    });
    return exports;
  };
  const Component = load('components/chat/LoanApplicationCard.tsx').LoanApplicationCard;
  function expand(node, key) {
    if (!node || typeof node !== 'object') return node;
    if (Array.isArray(node)) return node.map((child, i) => expand(child, key + '.' + i));
    if (typeof node.type === 'function') {
      const id = key + '/' + node.type.name + ':' + (node.key || ''); visited.add(id);
      current = instances.get(id) || []; instances.set(id, current); cursor = 0;
      return expand(node.type(node.props), id);
    }
    return {...node, props: {...node.props, action: expand(node.props.action, key + '.action'), children: expand(node.props.children, key + '.children')}};
  }
  function render() {let attempts = 0; do {dirty = false; visited = new Set(); tree = expand(jsx(Component, props), 'root'); for (const [id, slots] of instances) {if (!visited.has(id)) {slots.forEach(slot => slot.cleanup?.()); instances.delete(id);}} effects.splice(0).forEach(run => run()); if (++attempts > 40) throw Error('Render did not settle');} while (dirty); return tree;}
  async function ready() {for (let i = 0; i < 6; i++) {render(); await new Promise(setImmediate);} return render();}
  const button = label => nodes(render()).find(node => node.type === 'button' && text(node).trim() === label);
  return {props, calls, handlers, ApiRequestError, api: load('services/loan-applications.ts'), guard: load('hooks/useNavigationGuard.ts'), ready, render, button,
    posts: () => calls.filter(call => call.method === 'POST'),
    async submit(values = fields()) {const form = nodes(render()).find(node => node.type === 'form'); assert.ok(form, 'Inline form'); const pending = form.props.onSubmit({preventDefault() {}, currentTarget: values}); await pending; return ready();},
    async click(label) {const action = button(label); assert.ok(action, label); action.props.onClick(); return ready();},
    dispose() {for (const slots of instances.values()) slots.forEach(slot => slot.cleanup?.());}
  };
}

test('chat loan entry checks for an existing application before exposing the real salary-slip form and never auto-posts', async () => {
  const app = harness(); assert.equal(nodes(app.render()).some(node => node.type === 'form'), false); await app.ready();
  assert.equal(app.calls[0].url, '/loans/applications/by-request/' + applicationKey); assert.equal(app.calls[0].token, 'owner-token');
  assert.equal(nodes(app.render()).filter(node => node.type === 'input' && node.props.type === 'file').length, 3);
  assert.equal(app.button('Check application status'), undefined);
  assert.match(text(app.render()), /No money moves until approval and your acceptance/); assert.equal(app.posts().length, 0); app.dispose();
});

test('explicit inline submission uploads actual multipart files and shows only the verified application result', async () => {
  const app = harness(); await app.ready(); await app.submit(); const post = app.posts()[0];
  assert.equal(app.posts().length, 1); assert.equal(post.url, '/loans'); assert.ok(post.body instanceof FormData);
  assert.deepEqual(JSON.parse(await post.body.get('application').text()), draft);
  assert.deepEqual(post.body.getAll('months'), months); const files = post.body.getAll('files');
  assert.equal(files.length, 3); assert.ok(files.every(file => file instanceof File && file.type === 'application/pdf'));
  assert.match(await files[1].text(), /2026-07/); assert.match(text(app.render()), /Loan application received/);
  assert.ok(nodes(app.render()).some(node => node.type === 'a' && node.props.href === '#/loans/LN-chat-1' && text(node) === 'Track application'));
  assert.equal(nodes(app.render()).some(node => node.type === 'form'), false); app.dispose();
});

test('duplicate inline submissions share one in-flight POST', async () => {
  const app = harness(); await app.ready(); const original = app.handlers.request; let complete;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? new Promise(resolve => {complete = () => resolve(saved());}) : original(url, token, init);
  const form = nodes(app.render()).find(node => node.type === 'form'), event = {preventDefault() {}, currentTarget: fields()};
  const first = form.props.onSubmit(event), second = form.props.onSubmit(event); await app.ready();
  assert.equal(app.posts().length, 1); assert.equal(app.button('Saving…').props.disabled, true); complete(); await Promise.all([first, second]); await app.ready(); app.dispose();
});

test('unknown outcomes and a not-found lookup retain identical application details, files and key for explicit retry', async () => {
  const app = harness(); await app.ready(); const original = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? Promise.reject(new app.ApiRequestError(503, 'Response lost')) : original(url, token, init);
  await app.submit(); const body = app.posts()[0].body;
  assert.ok(app.button('Retry same application')); assert.equal(app.button('Close').props.disabled, true);
  assert.equal(nodes(app.render()).find(node => node.type === 'fieldset' && node.props.class === 'loan-application-fields bank-fields-grid').props.disabled, true);
  assert.equal(app.posts().length, 1); await app.click('Check application status'); assert.equal(app.posts().length, 1);
  const changed = fields(); changed.fields[2] = ['amount', '999999']; app.handlers.request = original; await app.submit(changed);
  assert.equal(app.posts()[1].body, body); assert.deepEqual(JSON.parse(await body.get('application').text()), draft); assert.match(text(app.render()), /Loan application received/); app.dispose();
});

test('reopening chat recovers an approved application without requesting files or creating another application', async () => {
  const app = harness({existing: saved(draft, 'APPROVED')}); await app.ready();
  assert.match(text(app.render()), /Approved/); assert.ok(nodes(app.render()).some(node => node.props?.href === '#/loans/LN-chat-1'));
  assert.equal(app.calls.length, 1); assert.equal(app.posts().length, 0); assert.equal(nodes(app.render()).some(node => node.type === 'form'), false); app.dispose();
});

test('an uncertain submission can be recovered by read-only lookup after the server commits', async () => {
  const app = harness(); await app.ready(); const original = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? Promise.reject(new app.ApiRequestError(503, 'Unknown')) : original(url, token, init);
  await app.submit(); app.handlers.request = url => url.includes('/by-request/') ? saved(draft, 'ACTIVE') : Promise.reject(Error('Unexpected request'));
  await app.click('Check application status'); assert.match(text(app.render()), /Active/); assert.equal(app.posts().length, 1); app.dispose();
});

test('failed or mismatched recovery responses never open the form or claim application success', async () => {
  for (const result of [new Error('Cannot check application'), saved({...draft, applicationKey: 'unrelated-key'}), {...saved(), status: 'FUTURE_STATUS'}]) {
    const app = harness(); app.handlers.request = async () => {if (result instanceof Error) throw result; return result;}; await app.ready();
    assert.ok(app.button('Check application status')); assert.equal(nodes(app.render()).some(node => node.type === 'form'), false);
    assert.doesNotMatch(text(app.render()), /Loan application received/); assert.equal(app.posts().length, 0); app.dispose();
  }
});

test('mismatched POST details remain uncertain and preserve the original multipart request', async () => {
  const app = harness(); await app.ready(); const original = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? saved({...draft, accountId: 99}) : original(url, token, init);
  await app.submit(); assert.doesNotMatch(text(app.render()), /Loan application received/); assert.ok(app.button('Retry same application'));
  app.handlers.request = original; await app.submit(); assert.equal(app.posts()[1].body, app.posts()[0].body); app.dispose();
});

test('older application prompts offer tracking while only the active prompt may contain a form', async () => {
  const app = harness({props: {active: false}}); await app.ready();
  assert.match(text(app.render()), /Earlier loan application request/); assert.equal(nodes(app.render()).some(node => node.type === 'form'), false);
  assert.equal(app.calls.length, 1); app.props.active = true; await app.ready(); assert.ok(nodes(app.render()).some(node => node.type === 'form')); app.dispose();
});

test('loan entry reports unavailable accounts and accepts only active INR deposit accounts', async () => {
  const app = harness({accounts: [{...account, id: '1', currencyCode: 'USD'}, {...account, id: '2', status: 'BLOCKED'}, {...account, id: '3', accountType: 'LOAN'}]}); await app.ready();
  assert.match(text(app.render()), /An active INR savings or current account is required/); assert.equal(app.button('Submit loan application').props.disabled, true);
  await app.submit(); assert.equal(app.posts().length, 0); app.dispose();
  const failed = harness(); const original = failed.handlers.request; failed.handlers.request = (url, token, init) => url === '/accounts' ? Promise.reject(Error('Account lookup failed')) : original(url, token, init);
  await failed.ready(); assert.ok(failed.button('Retry accounts')); assert.equal(failed.button('Submit loan application').props.disabled, true);
  failed.handlers.request = original; await failed.click('Retry accounts'); assert.equal(failed.button('Submit loan application').props.disabled, false); failed.dispose();
});

test('application keys are stable, bounded and never regenerated for historical malformed prompts', async () => {
  const app = harness({props: {clientId: 'bad/value'}}); await app.ready(); assert.equal(app.calls.length, 0); assert.equal(app.api.chatLoanApplicationKey('bad/value'), null);
  assert.equal(app.api.chatLoanApplicationKey('a'.repeat(70)).length, 80); assert.equal(app.api.chatLoanApplicationKey('a'.repeat(71)), null);
  assert.equal(app.api.chatLoanApplicationKey(clientId), applicationKey); app.dispose();
});

test('changing signed-in context performs fresh ownership lookup before exposing a prior application', async () => {
  const app = harness({existing: saved()}); await app.ready(); assert.match(text(app.render()), /Loan application received/);
  app.props.accessToken = 'different-owner'; app.handlers.request = async () => {throw new app.ApiRequestError(403, 'Not available');};
  assert.doesNotMatch(text(app.render()), /Loan application received/); await app.ready(); assert.equal(app.button('Check application status').props.disabled, undefined);
  assert.equal(app.posts().length, 0); app.dispose();
});

test('unknown submission locks chat and route navigation through not-found checks until a verified result is recovered', async () => {
  const locks = [], app = harness({props: {onSubmissionLocked: value => locks.push(value)}}); await app.ready();
  const original = app.handlers.request; let fail = true;
  app.handlers.request = (url, token, init) => init?.method === 'POST' && fail ? Promise.reject(new app.ApiRequestError(503, 'Unknown result')) : original(url, token, init);
  await app.submit(); assert.equal(locks.at(-1), true); assert.equal(app.guard.confirmNavigation(), false);
  await app.click('Check application status'); assert.equal(locks.at(-1), true); assert.equal(app.guard.confirmNavigation(), false);
  assert.equal(app.button('Retry same application').props.disabled, false); assert.equal(app.button('Check application status').props.disabled, false);
  fail = false; await app.submit(); assert.equal(locks.at(-1), false); assert.equal(app.guard.hasUnsavedWork(), false); app.dispose();
});

test('a definitive initial validation failure releases the submission lock and lets the customer correct the form', async () => {
  const locks = [], app = harness({props: {onSubmissionLocked: value => locks.push(value)}}); await app.ready(); const original = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? Promise.reject(new app.ApiRequestError(400, 'Check salary slips')) : original(url, token, init);
  await app.submit(); assert.equal(locks.at(-1), false); assert.equal(app.guard.hasUnsavedWork(), false);
  assert.ok(app.button('Submit loan application')); assert.equal(app.button('Close').props.disabled, false);
  assert.equal(app.button('Check application status'), undefined); app.dispose();
});
