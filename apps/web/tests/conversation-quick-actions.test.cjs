const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
const conversation = {id: 'chat-1', title: 'Banking', createdAt: '2026-09-27T05:00:00Z'};
const makeTurn = (id, userText, extra = {}) => ({id, clientId: 'client-' + id, source: 'TEXT', intent: 'UNKNOWN', userText, assistantText: 'Choose an option to continue.', createdAt: '2026-09-27T05:00:00Z', ...extra});
let uuidSequence = 0;

function harness(options = {}) {
  const slots = [], effects = [], calls = [], confirmations = [], guards = [];
  let cursor = 0, dirty = true, tree, api, workflow, locale = options.locale || 'en-IN';
  class ApiRequestError extends Error {constructor(status, message) {super(message); this.status = status;}}
  const hooks = {
    useState(initial) {const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial}; return [slots[at].value, value => {const next = typeof value === 'function' ? value(slots[at].value) : value; if (!Object.is(next, slots[at].value)) {slots[at].value = next; dirty = true;}}];},
    useRef(initial) {const at = cursor++; return slots[at] || (slots[at] = {current: initial});},
    useEffect(effect, deps) {const at = cursor++, old = slots[at]; if (!old || deps.some((value, i) => value !== old.deps[i])) {slots[at] = {deps}; effects.push(() => {old?.cleanup?.(); slots[at].cleanup = effect();});}}
  };
  hooks.useLayoutEffect = hooks.useEffect;
  const browser = {
    location: {hash: '#/assistant', reload() {}},
    matchMedia: () => ({matches: true, addEventListener() {}, removeEventListener() {}}),
    addEventListener() {}, removeEventListener() {}, setTimeout: () => 1, clearTimeout() {},
    confirm(message) {confirmations.push(message); return options.acceptDiscard !== false;}
  };
  const components = Object.fromEntries(['Action', 'BankingIcon', 'SidebarBrand', 'SidebarFooter', 'SidebarNavigation', 'AccountContext', 'QuickAction', 'Modal', 'ConnectionNotice', 'ChatIcon', 'MessageBubble', 'AssistantResponse', 'ConversationHistoryItem'].map(name => [name, () => null]));
  const handlers = {request: async (url, _token, init) => {
    if (url === '/conversations?page=0') return options.turns ? [conversation] : [];
    if (url === '/conversations' && init?.method === 'POST') return conversation;
    if (url.endsWith('/turns') && !init?.method) return [...(options.turns || [])].reverse();
    if (init?.method === 'POST') {const body = JSON.parse(init.body); return makeTurn('reply-' + calls.length, body.text || 'Chosen account');}
    throw Error('Unexpected request: ' + url);
  }};
  const request = async (url, token, init) => {calls.push({url, token, method: init?.method, body: init?.body ? JSON.parse(init.body) : undefined}); return handlers.request(url, token, init);};
  const jsx = (type, props) => ({type, props: props || {}});
  const load = relative => {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.resolve(__dirname, '../src', relative), 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText, {
      exports, Error, Date, crypto: {randomUUID: () => '11111111-1111-4111-8111-' + String(++uuidSequence).padStart(12, '0')}, window: browser,
      document: {body: {style: {overflow: ''}}, documentElement: {style: {setProperty() {}, removeProperty() {}}}},
      require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => locale, setLocale: value => {locale = value;}}
        : name.endsWith('/reply-localization') ? {localizeReply: value => value}
        : name.endsWith('/auth') ? {ApiRequestError, authenticatedRequest: request}
        : name.endsWith('/conversations') ? api : name.endsWith('/WorkflowCard') ? workflow
        : name.endsWith('/chat-suggestions') ? {getFollowUpSuggestions: () => []}
        : name.endsWith('/useNavigationGuard') ? {useNavigationGuard: (...value) => guards.push(value)}
        : name.endsWith('/useVoiceInput') ? {useVoiceInput: () => ({phase: options.listening ? 'listening' : 'idle', transcript: '', seconds: 0, cancel() {}})}
        : name.endsWith('/useReadAloud') ? {useReadAloud: () => ({speak() {}, cancel() {}, clearError() {}, error: ''})}
        : components
    });
    return exports;
  };
  api = load('services/conversations.ts'); workflow = load('components/chat/WorkflowCard.tsx');
  const Component = load('components/chat/ConversationWorkspace.tsx').ConversationWorkspace;
  function render() {let attempts = 0; do {dirty = false; cursor = 0; tree = Component({session: {accessToken: 'customer-token', user: {id: 'customer-1', role: 'CUSTOMER'}}, onClose() {}, onLogout() {}}); effects.splice(0).forEach(run => run()); if (++attempts > 30) throw Error('Render did not settle');} while (dirty); return tree;}
  async function ready() {for (let i = 0; i < 5; i++) {render(); await new Promise(setImmediate);} return render();}
  const button = label => nodes(render()).find(node => node.type === 'button' && text(node).replace(/\s+/g, ' ').trim() === label);
  const composer = () => nodes(render()).find(node => node.type === 'textarea' && node.props.id === 'nexa-conversation-input');
  return {calls, browser, confirmations, guards, handlers, ApiRequestError, components, workflow, render, ready, button, composer,
    posts: () => calls.filter(call => call.method === 'POST' && call.url !== '/conversations'),
    input(value) {composer().props.onInput({currentTarget: {value}}); render();},
    async click(label) {const node = button(label); assert.ok(node, 'Missing button: ' + label); node.props.onClick(); return ready();},
    async submit() {const form = nodes(render()).find(node => node.type === 'form' && node.props.class === 'messenger-composer'); form.props.onSubmit({preventDefault() {}}); return ready();},
    dispose() {slots.forEach(slot => slot.cleanup?.());}
  };
}

test('predefined Send Money submits the same chat request as typed send money without navigating or confirming a transfer', async () => {
  const quick = harness(), typed = harness(); await quick.ready(); await typed.ready();
  await quick.click('Send Money'); typed.input('send money'); await typed.submit();
  const first = quick.posts()[0], second = typed.posts()[0];
  assert.equal(quick.posts().length, 1); assert.equal(first.url, '/conversations/chat-1/turns'); assert.equal(first.token, 'customer-token');
  assert.deepEqual({...first.body, clientId: undefined}, {...second.body, clientId: undefined});
  assert.deepEqual({...first.body, clientId: undefined}, {clientId: undefined, source: 'TEXT', text: 'send money'});
  assert.equal(quick.browser.location.hash, '#/assistant'); assert.ok(first.body.clientId); quick.dispose(); typed.dispose();
});

test('Hindi Send Money stays in chat and submits the supported Hindi transfer prompt', async () => {
  const app = harness({locale: 'hi-IN'}); await app.ready(); await app.click('पैसे भेजें');
  assert.equal(app.posts()[0].body.text, 'पैसे भेजें'); assert.equal(app.posts()[0].body.source, 'TEXT');
  assert.equal(app.browser.location.hash, '#/assistant'); app.dispose();
});
test('Open account and Create mandate suggestions submit the same text as typed requests without creating products',async()=>{
  for(const [label,prompt] of [['Open account','open account'],['Create mandate','create mandate']]){
    const quick=harness(),typed=harness();await quick.ready();await typed.ready();await quick.click(label);typed.input(prompt);await typed.submit();
    assert.equal(quick.posts().length,1);assert.equal(quick.posts()[0].url,'/conversations/chat-1/turns');
    assert.deepEqual({...quick.posts()[0].body,clientId:undefined},{...typed.posts()[0].body,clientId:undefined});
    assert.equal(quick.posts()[0].body.text,prompt);assert.equal(quick.browser.location.hash,'#/assistant');quick.dispose();typed.dispose();
  }
});
test('only the latest supported form across account, loan and mandate entries remains live',async()=>{
  const app=harness({turns:[makeTurn('account','open account',{banking:{version:1,type:'ACCOUNT_APPLICATION'}}),makeTurn('loan','apply loan',{banking:{version:1,type:'LOAN_APPLICATION'}}),makeTurn('mandate','create mandate',{banking:{version:1,type:'MANDATE_APPLICATION'}}),makeTurn('future','open account',{banking:{version:2,type:'ACCOUNT_APPLICATION'}})]});await app.ready();
  const replies=nodes(app.render()).filter(node=>node.type===app.components.AssistantResponse);
  assert.deepEqual(replies.filter(node=>node.props.applicationActive).map(node=>node.props.turn.id),['mandate']);
  replies.find(node=>node.props.turn.id==='mandate').props.onApplicationLocked(true);await app.ready();
  assert.equal(app.button('Open account').props.disabled,true);assert.equal(app.button('Create mandate').props.disabled,true);assert.equal(app.composer().props.readOnly,true);
  assert.equal(nodes(app.render()).find(node=>node.type===app.components.AssistantResponse&&node.props.turn.id==='mandate').props.applicationBusy,false);
  app.input('open account');await app.submit();assert.equal(app.posts().length,0);app.dispose();
});

test('Send Money respects an existing typed draft before starting the chat workflow', async () => {
  const app = harness({acceptDiscard: false}); await app.ready(); app.input('Pay my electricity bill'); await app.click('Send Money');
  assert.equal(app.posts().length, 0); assert.equal(app.calls.some(call => call.method === 'POST'), false);
  assert.equal(app.composer().props.value, 'Pay my electricity bill'); assert.equal(app.confirmations.length, 1);
  assert.equal(app.browser.location.hash, '#/assistant'); app.dispose();
});

test('rapid repeated Send Money clicks create one pending turn and disable new actions while it is sent', async () => {
  const app = harness({turns: []}); await app.ready(); let complete;
  const initial = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? new Promise(resolve => {complete = () => resolve(makeTurn('sent', JSON.parse(init.body).text));}) : initial(url, token, init);
  const action = app.button('Send Money'); action.props.onClick(); action.props.onClick(); await app.ready();
  assert.equal(app.posts().length, 1); assert.equal(app.button('Send Money').props.disabled, true);
  complete(); await app.ready(); assert.equal(app.button('Send Money').props.disabled, false); app.dispose();
});

test('unknown quick-action outcomes retain the original request key for retry and prevent a replacement turn', async () => {
  const app = harness({turns: []}); await app.ready(); const initial = app.handlers.request;
  app.handlers.request = (url, token, init) => init?.method === 'POST' ? Promise.reject(new app.ApiRequestError(503, 'Connection interrupted')) : initial(url, token, init);
  await app.click('Send Money'); const original = app.posts()[0].body;
  assert.equal(app.button('Send Money').props.disabled, true); await app.click('Send Money'); assert.equal(app.posts().length, 1);
  app.handlers.request = initial; await app.click('Try again');
  assert.equal(app.posts().length, 2); assert.deepEqual(app.posts()[1].body, original); assert.equal(app.button('Send Money').props.disabled, false); app.dispose();
});

test('the separate welcome action still starts an own-account transfer through chat', async () => {
  const app = harness(); await app.ready();
  const action = nodes(app.render()).find(node => node.type === app.components.QuickAction && node.props.title === 'Transfer between accounts');
  assert.ok(action); action.props.onClick(); await app.ready();
  assert.equal(app.posts()[0].body.text, 'Transfer between my accounts'); assert.equal(app.browser.location.hash, '#/assistant'); app.dispose();
});

test('typing pay bill after a loan response forwards the new intent unchanged without attaching stale workflow context', async () => {
  const app = harness({turns: [makeTurn('loan', 'show my loans', {banking: {version: 1, type: 'LOANS', loans: []}})]}); await app.ready();
  app.input('pay bill'); await app.submit();
  assert.equal(app.posts()[0].url, '/conversations/chat-1/turns');
  assert.deepEqual({...app.posts()[0].body, clientId: undefined}, {clientId: undefined, source: 'TEXT', text: 'pay bill'}); app.dispose();
});

test('bill account choices after a loan response send the bill action ID and selected account ID', async () => {
  const bill = {version: 1, id: 'bill-action', operation: 'PAY_BILL', status: 'COLLECTING', field: 'accountId', message: 'Which account should pay the electricity bill?', choices: [{id: 'customer-savings-2', label: 'Savings · 1234'}], expiresAt: new Date(Date.now() + 60000).toISOString(), confirmationRequired: false, executionAvailable: true};
  const app = harness({turns: [makeTurn('loan', 'show my loans', {banking: {version: 1, type: 'LOANS', loans: []}}), makeTurn('bill', 'pay bill', {workflow: bill})]}); await app.ready();
  const response = nodes(app.render()).find(node => node.type === app.components.AssistantResponse && node.props.turn.id === 'bill');
  assert.equal(response.props.active, true);
  const card = app.workflow.WorkflowCard({...response.props, workflow: response.props.turn.workflow});
  const choice = nodes(card).find(node => node.type === 'button' && node.props.children[0] === 'Savings · 1234'); assert.ok(choice); choice.props.onClick(); await app.ready();
  assert.equal(app.posts()[0].url, '/conversations/chat-1/actions/bill-action');
  assert.deepEqual({...app.posts()[0].body, clientId: undefined}, {clientId: undefined, type: 'SELECT', value: 'customer-savings-2'}); app.dispose();
});

test('only the latest supported loan application prompt is active without disabling the current payment workflow', async () => {
  const payment = {version: 1, id: 'payment-action', operation: 'PAY_BILL', status: 'COLLECTING', choices: [], expiresAt: new Date(Date.now() + 60000).toISOString()};
  const app = harness({turns: [makeTurn('loan-1', 'apply loan', {banking: {version: 1, type: 'LOAN_APPLICATION'}}), makeTurn('payment', 'pay bill', {workflow: payment}), makeTurn('loan-2', 'apply loan', {banking: {version: 1, type: 'LOAN_APPLICATION'}}), makeTurn('future-loan', 'future form', {banking: {version: 2, type: 'LOAN_APPLICATION'}})]}); await app.ready();
  const replies = nodes(app.render()).filter(node => node.type === app.components.AssistantResponse);
  assert.equal(replies.find(node => node.props.turn.id === 'loan-1').props.applicationActive, false);
  assert.equal(replies.find(node => node.props.turn.id === 'loan-2').props.applicationActive, true);
  assert.equal(replies.find(node => node.props.turn.id === 'payment').props.active, true); assert.equal(app.posts().length, 0); app.dispose();
});

test('an unresolved inline loan blocks new chat requests and conversation replacement while its recovery controls remain usable', async () => {
  const app = harness({turns: [makeTurn('earlier', 'apply loan', {banking: {version: 1, type: 'LOAN_APPLICATION'}}), makeTurn('latest', 'apply loan', {banking: {version: 1, type: 'LOAN_APPLICATION'}})]}); await app.ready();
  const replies = () => nodes(app.render()).filter(node => node.type === app.components.AssistantResponse);
  const latest = replies().find(node => node.props.turn.id === 'latest'); const oldAction = app.button('Send Money');
  latest.props.onApplicationLocked(true); await app.ready();
  assert.equal(app.button('Send Money').props.disabled, true); assert.equal(app.composer().props.readOnly, true); assert.deepEqual(app.guards.at(-1), [true, true]);
  assert.equal(replies().find(node => node.props.turn.id === 'latest').props.applicationBusy, false);
  assert.equal(replies().find(node => node.props.turn.id === 'latest').props.busy, true);
  oldAction.props.onClick(); app.input('apply for another loan'); await app.submit();
  const history = nodes(app.render()).find(node => node.type === app.components.ConversationHistoryItem); assert.equal(history.props.disabled, true); await history.props.onSelect(); await history.props.onDelete();
  const fresh = nodes(app.render()).find(node => node.type === app.components.Action); assert.equal(fresh.props.disabled, true); fresh.props.onAction(); await app.ready();
  assert.equal(app.posts().length, 0); assert.equal(app.calls.some(call => call.method === 'DELETE'), false);
  replies().find(node => node.props.turn.id === 'earlier').props.onApplicationLocked(false); await app.ready(); assert.equal(app.composer().props.readOnly, true);
  replies().find(node => node.props.turn.id === 'latest').props.onApplicationLocked(false); await app.ready(); assert.equal(app.composer().props.readOnly, false);
  app.input('show my loans'); await app.submit(); assert.equal(app.posts().length, 1); app.dispose();
});
