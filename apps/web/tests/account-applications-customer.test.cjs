// Real component/action-hook harness. Synthetic responses only: no server, database, real ID, file upload or cash.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const {randomUUID} = require('node:crypto');
const root = path.resolve(__dirname, '../src');
const policy = {minimumOpeningAmount: '1000.00', maximumOpeningAmount: '10000000.00', currencyCode: 'INR',
  businessDate: '2026-09-23', latestDateOfBirth: '2008-09-23', allowedAccountTypes: ['SAVINGS'],
  identityTypes: ['AADHAAR', 'PAN', 'PASSPORT'], verificationMethod: 'IN_PERSON_ORIGINAL',
  consentVersion: 'in-person-identity-v1', consentNotice: 'Synthetic manual-review consent for component tests.',
  applicationsAvailable: true, identityDetailsAvailable: true, cashReceiptAvailable: false, governmentVerification: false};
const profile = {id: 'test-profile', userId: 'test-user', fullName: 'Synthetic Test Customer', email: 'synthetic@example.invalid',
  phoneNumber: null, status: 'ACTIVE', role: 'CUSTOMER'};
const applicationId = '11111111-1111-4111-8111-111111111111';
const documentId = '22222222-2222-4222-8222-222222222222';
function record(changes = {}) {
  return {id: applicationId, accountType: 'SAVINGS', currencyCode: 'INR', fullName: profile.fullName, email: profile.email,
    phoneNumber: null, dateOfBirth: '1990-01-01', status: 'DRAFT', reviewDecision: 'PENDING', reviewReason: null, version: 0,
    createdAt: '2026-09-23T10:00:00Z', updatedAt: '2026-09-23T10:00:00Z', submittedAt: null, reviewedAt: null,
    openedAt: null, endedAt: null, endReasonCode: null, accountId: null, openingAmount: '1000.00', identityType: 'PAN', identityMasked: '••••234F', documents: [], events: [], receipts: [], ...changes};
}
function document(changes = {}) {
  return {id: documentId, documentType: 'PAN', mediaType: 'application/pdf', byteSize: 100, storageStatus: 'AVAILABLE',
    safetyStatus: 'CLEAN', reviewStatus: 'PENDING', reviewReason: null, uploadedAt: '2026-09-23T10:00:00Z', reviewedAt: null, ...changes};
}
const allNodes = node => !node || typeof node !== 'object' ? [] : [node, ...[node.props?.children].flat(Infinity).flatMap(allNodes)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : [node.props?.children].flat(Infinity).map(text).join(' ');
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return {promise, resolve, reject}; };
function harness(options = {}) {
  const slots = [], calls = [], effects = [], cache = new Map();
  let cursor = 0, dirty = true, tree, confirm = true, refreshes = 0;
  let currentPolicy = {...policy, ...options.policy, ...(options.enabled === false ? {applicationsAvailable: false} : {})}, rows = options.rows || [];
  const records = new Map(rows.map(item => [item.id, item]));
  const props = {token: 'synthetic-token', profile: {...profile}, accounts: options.accounts || [], onAccountsChanged: () => { refreshes++; }};
  const browser = {NEXA_ACCOUNT_APPLICATIONS_ENABLED: options.browserFlag, confirm: () => confirm, alert() {}, setTimeout, clearTimeout};
  const hooks = {
    useState(initial) { const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial};
      return [slots[at].value, value => { const next = typeof value === 'function' ? value(slots[at].value) : value;
        if (!Object.is(next, slots[at].value)) { slots[at].value = next; dirty = true; } }]; },
    useRef(initial) { const at = cursor++; return slots[at] || (slots[at] = {current: initial}); },
    useEffect(effect, deps) { const at = cursor++, old = slots[at];
      if (!old || !deps || deps.some((value, i) => value !== old.deps[i])) { slots[at] = {deps, cleanup: old?.cleanup};
        effects.push(() => { old?.cleanup?.(); slots[at].cleanup = effect(); }); } }
  };
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : ({type, props: props || {}});
  class ApiRequestError extends Error { constructor(status, message) { super(message); this.status = status; } }
  const auth = {ApiRequestError, authenticatedRequest: async () => { throw new Error('Unexpected live API request'); }};
  async function invoke(name, args, fallback) { calls.push({name, args}); return options.handlers?.[name] ? options.handlers[name](...args) : fallback(); }
  function save(result) { records.set(result.id, result); rows = [result, ...rows.filter(item => item.id !== result.id)]; return result; }
  const api = {
    requirements: (...args) => invoke('requirements', args, () => currentPolicy), listMine: (...args) => invoke('listMine', args, () => rows),
    getMine: (...args) => invoke('getMine', args, () => records.get(args[1])),
    create: (...args) => invoke('create', args, () => save(record({openingAmount: args[1].openingAmount, dateOfBirth: args[1].dateOfBirth, identityType:args[1].identityType, identityMasked:'••••'+args[1].identityNumber.slice(-4)}))),
    updateIdentity: (...args) => invoke('updateIdentity',args,()=>{const old=records.get(args[1]); return save({...old,version:old.version+1,identityType:args[2].identityType,identityMasked:'••••'+args[2].identityNumber.slice(-4)});}),
    submit: (...args) => invoke('submit', args, () => { const old = records.get(args[1]); return save({...old, version: old.version + 1, status: 'PENDING_REVIEW'}); }),
    cancel: (...args) => invoke('cancel', args, () => { const old = records.get(args[1]); return save({...old, version: old.version + 1, status: 'CANCELLED'}); })
  };
  function load(relative) {
    const filename = path.resolve(root, relative); if (cache.has(filename)) return cache.get(filename);
    const exports = {}; cache.set(filename, exports);
    const code = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021,
      jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText;
    const requireModule = name => {
      if (name === 'preact/hooks') return hooks;
      if (name === 'preact/jsx-runtime') return {jsx, jsxs: jsx, Fragment: 'fragment'};
      if (name.endsWith('/auth') || name === './auth') return auth;
      if (name.endsWith('/locale') || name === './locale') return {t: value => value, getLocale: () => 'en-IN'};
      if (name === './AccountApplicationShared') return {
        ApplicationRecord: ({application}) => jsx('div', {class: 'shared-record', application, children: application.status}),
        AvailabilityNotice: ({onRefresh, refreshing}) => jsx('button', {disabled: refreshing, onClick: onRefresh, children: 'Check service readiness'})};
      if (name.startsWith('.')) { const base = path.resolve(path.dirname(filename), name), target = fs.existsSync(base + '.ts') ? base + '.ts' : base + '.tsx';
        const result = load(path.relative(root, target)); return name.endsWith('account-applications') ? {...result, applicationApi: api} : result; }
      return {};
    };
    vm.runInNewContext(code, {exports, require: requireModule, window: browser, Event, Error, console, URLSearchParams, TextEncoder,
      crypto: {randomUUID}, setTimeout, clearTimeout, Headers, Blob, FormData, AbortController, Uint8Array});
    return exports;
  }
  const component = load('features/banking/AccountApplications.tsx').CustomerAccountApplications;
  const navigation = load('hooks/useNavigationGuard.ts');
  function render() { let count = 0; do { dirty = false; cursor = 0; tree = component(props); effects.splice(0).forEach(run => run());
    if (++count > 25) throw new Error('Component failed to settle'); } while (dirty); return tree; }
  async function ready() { for (let i = 0; i < 5; i++) { render(); await new Promise(setImmediate); } return render(); }
  const nodes = () => allNodes(render()), input = id => nodes().find(node => node.props.id === id);
  const button = label => nodes().find(node => node.type === 'button' && text(node) === label);
  return {calls, props, ApiRequestError, ready, render, nodes, input, button, text: () => text(render()),
    disabled(id) {
      let result;
      function inspect(node, inherited = false) {
        if (!node || typeof node !== 'object') return;
        const blocked = inherited || (node.type === 'fieldset' && node.props.disabled === true);
        if (node.props?.id === id) result = blocked || node.props.disabled === true;
        [node.props?.children].flat(Infinity).forEach(child => inspect(child, blocked));
      }
      inspect(render()); return result;
    },
    mutationCalls: () => calls.filter(call => ['create', 'updateIdentity', 'submit', 'cancel'].includes(call.name)),
    set(id, value) { const node = input(id); assert.ok(node, id); (node.props.onInput || node.props.onChange)({currentTarget: {value, checked: value}}); render(); },
    chooseFile(file) { const target = {files: file ? [file] : [], value: 'synthetic-path'}; input('application-document-file').props.onChange({currentTarget: target}); render(); return target; },
    form(className) { return nodes().find(node => node.type === 'form' && node.props.class.includes(className)); },
    async submit(className) { const form = this.form(className); assert.ok(form, className); await form.props.onSubmit({preventDefault() {}}); await ready(); },
    async select(id = applicationId) { const choice = nodes().find(node => node.type === 'button' && text(node).includes(id)); assert.ok(choice, id); choice.props.onClick(); await ready(); },
    async click(label) { const node = button(label); assert.ok(node, label); node.props.onClick(); await ready(); },
    setPolicy(changes) { currentPolicy = {...currentPolicy, ...changes}; }, async setEnabled(enabled) { currentPolicy = {...currentPolicy, applicationsAvailable: enabled}; await this.click('Check service readiness'); },
    setServerRows(next) { rows=next; records.clear(); next.forEach(item=>records.set(item.id,item)); },
    allowNavigation(value) { confirm = value; }, navigation, refreshCount: () => refreshes,
    dispose() { slots.forEach(slot => slot?.cleanup?.()); }
  };
}
async function filled(app, amount = '1000.00', birth = '1990-01-01') {
  await app.ready(); await app.click('Open account');
  app.set('application-birth', birth); app.set('application-opening-amount', amount); app.set('application-consent', true); app.set('application-identity-number','1234');
}
const syntheticFile = (changes = {}) => ({name: 'synthetic-proof.pdf', type: 'application/pdf', size: 100, ...changes});

test('server disabled permits reads but no draft collection, picker or writes even with an old browser flag true', async () => {
  const app = harness({enabled: false, browserFlag: true, rows: [record()]}); await app.ready(); await app.select();
  assert.equal(app.nodes().some(node => node.type === 'form'), false);
  assert.equal(app.nodes().some(node => node.type === 'input' && node.props.type !== 'checkbox'), false);
  assert.equal(app.mutationCalls().length, 0); assert.ok(app.calls.some(call => call.name === 'getMine'));
  assert.equal(app.button('Cancel application').props.disabled, true); app.dispose();
});
for (const availability of [{applicationsAvailable: false}, {identityDetailsAvailable: false}]) {
  test('unready services allow a disabled preview, never collection or draft creation: ' + JSON.stringify(availability), async () => {
    const app = harness({policy: availability}); await app.ready();
    assert.equal(app.form('application-create-form'), undefined); await app.click('Open account');
    assert.ok(app.form('application-create-form')); assert.equal(app.input('application-document-file'), undefined);
    for (const id of ['application-birth','application-account-type','application-opening-amount','application-identity-type','application-identity-number','application-consent'])
      assert.equal(app.disabled(id),true,id);
    assert.equal(app.button('Save application details').props.disabled,true);
    await app.submit('application-create-form');
    assert.equal(app.mutationCalls().length, 0); app.dispose();
  });
}
test('requirements/list read failures fail closed and expose read retry', async () => {
  for (const method of ['requirements', 'listMine']) {
    const app = harness({handlers: {[method]: () => { throw new Error('Synthetic unavailable response'); }}}); await app.ready();
    assert.equal(app.form('application-create-form'), undefined); assert.match(app.text(), /Synthetic unavailable response/);
    assert.ok(app.button('Try again')); assert.equal(app.mutationCalls().length, 0); app.dispose();
  }
});
test('profile is display-only, account type is savings only, consent starts unchecked', async () => {
  const app = harness(); await app.ready(); await app.click('Open account');
  assert.match(app.text(), /Synthetic Test Customer/); assert.match(app.text(), /synthetic@example.invalid/);
  assert.equal(app.input('application-consent').props.checked, false); assert.equal(app.input('application-opening-amount').props.value, '1000.00');
  assert.equal(app.input('application-birth').props.max, policy.latestDateOfBirth);
  assert.deepEqual(app.nodes().filter(node => node.type === 'input').map(node => node.props.id).sort(), ['application-birth', 'application-opening-amount', 'application-consent', 'application-identity-number'].sort());
  assert.equal(app.input('application-account-type').props.value,'SAVINGS');
  const current=app.nodes().find(node=>node.type==='option'&&node.props.value==='CURRENT');
  assert.ok(current);assert.equal(current.props.disabled,true);
  assert.equal(app.input('application-identity-number').props.autoComplete,'off');app.dispose();
});
test('new customer sees one Open account CTA without a duplicate empty applications card or automatic write',async()=>{
  const app=harness();await app.ready();
  assert.equal(app.nodes().filter(node=>node.type==='button'&&text(node)==='Open account').length,1);
  assert.equal(app.form('application-create-form'),undefined);
  assert.equal(app.nodes().some(node=>String(node.props.class||'').split(' ').includes('application-list-panel')),false);
  assert.doesNotMatch(app.text(),/No account applications yet/);
  await app.click('Open account');assert.ok(app.form('application-create-form'));
  assert.equal(app.mutationCalls().length,0);app.dispose();
});
test('create sends canonical increased cash amount, minimal body and generated request key', async () => {
  const app = harness(); await filled(app, '2500.5'); await app.submit('application-create-form');
  const [call] = app.mutationCalls(); assert.equal(call.name, 'create'); const body = JSON.parse(JSON.stringify(call.args[1]));
  assert.match(body.requestKey, /^[0-9a-f-]{36}$/); delete body.requestKey;
  assert.deepEqual(body, {accountType: 'SAVINGS', currencyCode: 'INR', dateOfBirth: '1990-01-01', openingAmount: '2500.50', consentVersion: 'in-person-identity-v1', consentAccepted: true, identityType:'AADHAAR', identityNumber:'1234'});
  assert.equal(app.form('application-create-form'), undefined);
  assert.equal(app.nodes().find(node => node.props.class === 'shared-record').props.application.status, 'DRAFT');
  assert.equal(app.refreshCount(), 0); app.dispose();
});
test('unknown existing applications cannot be bypassed by directly pressing Open account',async()=>{
  const pending=deferred(),app=harness({handlers:{listMine:()=>pending.promise}});await app.ready();
  let open=app.button('Open account');if(open){assert.equal(open.props.disabled,true);open.props.onClick();}
  assert.equal(app.form('application-create-form'),undefined);
  pending.reject(new Error('Synthetic application list unavailable'));await app.ready();
  open=app.button('Open account');if(open){assert.equal(open.props.disabled,true);open.props.onClick();}
  assert.equal(app.form('application-create-form'),undefined);assert.equal(app.mutationCalls().length,0);app.dispose();
});
for (const bad of ['999.99', '10000000.01', '1000.001', '-1000', '1e3', '1,000', 'NaN', '']) {
  test('opening amount rejected before POST: ' + JSON.stringify(bad), async () => {
    const app = harness(); await filled(app, bad); await app.submit('application-create-form');
    assert.equal(app.mutationCalls().length, 0); assert.equal(app.input('application-opening-amount').props['aria-invalid'], true);
    assert.match(app.text(), /at most two decimal places/); app.dispose();
  });
}
for (const bad of ['2026-09-23', '2027-01-01', '2008-09-24', '2007-02-29', '0000-01-01', '']) {
  test('DOB rejected against server business date: ' + JSON.stringify(bad), async () => {
    const app = harness(); await filled(app, '1000', bad); await app.submit('application-create-form');
    assert.equal(app.mutationCalls().length, 0); assert.equal(app.input('application-birth').props['aria-invalid'], true); app.dispose();
  });
}
test('exact eighteenth birthday and one-crore maximum are inclusive', async () => {
  const app = harness(); await filled(app, '10000000.00', '2008-09-23'); await app.submit('application-create-form');
  assert.equal(app.mutationCalls().length, 1); app.dispose();
});
test('consent is required and cannot carry over silently to changed policy version', async () => {
  const app = harness(); await app.ready(); await app.click('Open account'); app.set('application-birth', '1990-01-01'); await app.submit('application-create-form');
  assert.equal(app.mutationCalls().length, 0); assert.match(app.text(),/Read and accept the current consent/);
  app.set('application-consent', true); app.setPolicy({consentVersion: 'manual-review-v2'}); await app.click('Refresh status');
  assert.equal(app.input('application-consent').props.checked, false); await app.submit('application-create-form');
  assert.equal(app.mutationCalls().length, 0); app.dispose();
});
test('existing closed savings, server ineligibility or active application block another draft', async () => {
  for (const options of [{accounts: [{id: '42', accountType: 'SAVINGS', status: 'CLOSED'}]}, {policy: {allowedAccountTypes: []}}, {rows: [record({status: 'PENDING_REVIEW'})]}]) {
    const app = harness(options); await app.ready(); assert.equal(app.form('application-create-form'), undefined); assert.equal(app.mutationCalls().length, 0); app.dispose();
  }
});
test('DOB drafts are navigation-guarded; declining selection preserves unsent input', async () => {
  const app = harness({rows: [record({status: 'CANCELLED'})]}); await filled(app); assert.equal(app.navigation.hasUnsavedWork(), true);
  app.allowNavigation(false); await app.select(); assert.equal(app.nodes().some(node => node.props.class === 'shared-record'), false);
  assert.equal(app.input('application-birth').props.value, '1990-01-01');
  app.allowNavigation(true); await app.select();
  assert.equal(app.form('application-create-form'), undefined);
  assert.equal(app.navigation.hasUnsavedWork(), false);
  await app.click('Open account');
  assert.equal(app.input('application-birth').props.value, ''); app.dispose();
});
test('cancel needs unchecked explicit acknowledgement and remains possible with identity processing unavailable', async () => {
  const app = harness({policy: {identityDetailsAvailable: false}, rows: [record({status: 'APPROVED_AWAITING_CASH'})]}); await app.ready(); await app.select();
  const check = app.nodes().find(node => node.type === 'input' && node.props.type === 'checkbox');
  assert.equal(check.props.checked, false); assert.equal(app.button('Cancel application').props.disabled, true);
  check.props.onChange({currentTarget: {checked: true}}); await app.click('Cancel application');
  assert.equal(app.mutationCalls()[0].name, 'cancel'); assert.equal(app.mutationCalls()[0].args[2].expectedVersion, 0); app.dispose();
});
test('recorded cash and terminal states never offer cancellation or falsely claim account opening', async () => {
  for (const current of [record({status: 'APPROVED_AWAITING_CASH', receipts: [{id: 'synthetic-receipt'}]}), record({status: 'CASH_RECEIVED'}), record({status: 'REFUND_PENDING'}), record({status: 'REFUNDED'}), record({status: 'REJECTED'})]) {
    const app = harness({rows: [current]}); await app.ready(); await app.select(); assert.equal(app.button('Cancel application'), undefined);
    assert.equal(app.nodes().some(node => node.type === 'a' && text(node) === 'View your opened account'), false);
    assert.equal(app.refreshCount(), 0); app.dispose();
  }
});
test('only server OPENED plus account reference refreshes accounts and supplies an account link', async () => {
  const app = harness({rows: [record({status: 'OPENED', accountId: 42})]}); await app.ready(); await app.select();
  const link = app.nodes().find(node => node.type === 'a' && text(node) === 'View your opened account');
  assert.equal(link.props.href, '#/accounts/42'); assert.equal(app.refreshCount(), 1);
  app.render(); await app.click('Refresh status'); assert.equal(app.refreshCount(), 1); app.dispose();
});

test('opened summary refreshes missing bank cards without selecting the request and does not repeat',async()=>{
  const app=harness({rows:[record({status:'OPENED',accountId:42})]});await app.ready();
  assert.equal(app.refreshCount(),1);
  assert.equal(app.nodes().some(node=>node.props.class==='shared-record'),false);
  await app.click('Refresh status');assert.equal(app.refreshCount(),1);assert.equal(app.mutationCalls().length,0);app.dispose();
});

test('customer Refresh status discovers an account opened by another session without requiring selection',async()=>{
  const app=harness({rows:[record({status:'PENDING_REVIEW'})]});await app.ready();assert.equal(app.refreshCount(),0);
  app.setServerRows([record({status:'OPENED',accountId:42,version:4})]);await app.click('Refresh status');
  assert.equal(app.refreshCount(),1);assert.match(app.text(),/Account opened/);assert.equal(app.mutationCalls().length,0);app.dispose();
});

test('obsolete unsent cancellation acknowledgement cannot block an authoritatively opened account refresh',async()=>{
  const app=harness({rows:[record({status:'APPROVED_AWAITING_CASH'})]});await app.ready();await app.select();
  const acknowledgement=app.nodes().find(node=>node.type==='input'&&node.props.type==='checkbox');
  acknowledgement.props.onChange({currentTarget:{checked:true}});app.render();assert.equal(app.navigation.hasUnsavedWork(),true);
  app.setServerRows([record({status:'OPENED',accountId:42,version:4})]);await app.click('Refresh status');
  assert.equal(app.button('Cancel application'),undefined);assert.equal(app.refreshCount(),1);
  assert.equal(app.navigation.hasUnsavedWork(),false);assert.equal(app.mutationCalls().length,0);app.dispose();
});

test('already-loaded account and OPENED without an account reference do not trigger redundant parent refresh',async()=>{
  for(const options of [{rows:[record({status:'OPENED',accountId:42})],accounts:[{id:'42',accountType:'SAVINGS'}]},
    {rows:[record({status:'OPENED',accountId:null})]}]){
    const app=harness(options);await app.ready();assert.equal(app.refreshCount(),0);app.dispose();
  }
});

test('opened-summary refresh waits until unsent application input is deliberately cleared',async()=>{
  const app=harness();await filled(app,'1500.00');
  app.setServerRows([record({status:'OPENED',accountId:42,version:4})]);await app.click('Refresh status');
  assert.equal(app.refreshCount(),0);assert.equal(app.navigation.hasUnsavedWork(),true);
  await app.select();assert.equal(app.refreshCount(),1);app.dispose();
});

test('late previous-owner list response cannot refresh the new owner bank cards',async()=>{
  const previous=deferred();let phase='initial';
  const app=harness({handlers:{listMine:()=>phase==='initial'?[record()]:phase==='previous'?previous.promise:[]}});
  await app.ready();phase='previous';await app.click('Refresh status');
  phase='next';app.props.profile={...profile,id:'different-owner',userId:'different-user'};app.props.token='different-synthetic-token';await app.ready();
  previous.resolve([record({status:'OPENED',accountId:42})]);await app.ready();
  assert.equal(app.refreshCount(),0);assert.doesNotMatch(app.text(),/Account opened/);app.dispose();
});

test('newly observed opened summary does not unmount an uncertain request or discard its original key',async()=>{
  let attempts=0;
  const opened=record({status:'OPENED',accountId:42,version:5});
  const app=harness({handlers:{create:async()=>{if(++attempts===1)throw new Error('Synthetic lost creation response');return opened;}}});
  await filled(app);await app.submit('application-create-form');const original=app.mutationCalls()[0];
  app.setServerRows([opened]);app.props.token='renewed-synthetic-token';await app.ready();
  assert.equal(app.refreshCount(),0);assert.equal(app.navigation.confirmNavigation(),false);
  await app.click('Retry the same request');
  assert.deepEqual(app.mutationCalls()[1].args,original.args);assert.equal(app.refreshCount(),1);app.dispose();
});

for(const available of [false,true])test('approved cash guidance follows the server cash readiness: '+available,async()=>{
  const app=harness({policy:{cashReceiptAvailable:available},rows:[record({status:'APPROVED_AWAITING_CASH'})]});await app.ready();await app.select();
  if(available)assert.match(app.text(),/Confirm the cashier process is ready before arranging/);
  else {assert.match(app.text(),/Do not hand over cash/);assert.doesNotMatch(app.text(),/Arrange the exact opening amount/);}
  assert.equal(app.mutationCalls().length,0);app.dispose();
});

test('pending double click sends once and locks new actions and navigation until the response', async () => {
  const pending = deferred(), app = harness({handlers: {create: () => pending.promise}}); await filled(app);
  const handler = app.form('application-create-form').props.onSubmit, first = handler({preventDefault() {}}), second = handler({preventDefault() {}});
  app.render(); assert.equal(app.mutationCalls().length, 1); assert.equal(app.button('Save application details').props.disabled, true);
  assert.equal(app.navigation.confirmNavigation(), false); pending.resolve(record()); await Promise.all([first, second]); await app.ready(); app.dispose();
});
test('uncertain creation retains exact immutable payload and key for retry without another draft', async () => {
  let attempts = 0; const app = harness({handlers: {create: async () => { if (++attempts === 1) throw new Error('Synthetic lost response'); return record(); }}});
  await filled(app, '1250.5'); await app.submit('application-create-form'); assert.ok(app.button('Retry the same request'));
  assert.equal(app.button('Save application details').props.disabled, true); assert.equal(app.button('Refresh status').props.disabled, true);
  assert.equal(app.navigation.confirmNavigation(), false); await app.click('Retry the same request');
  const calls = app.mutationCalls(); assert.equal(calls.length, 2); assert.deepEqual(calls[0].args[1], calls[1].args[1]);
  assert.equal(calls[1].args[1].openingAmount, '1250.50'); assert.equal(app.form('application-create-form'), undefined); app.dispose();
});
test('server readiness withdrawal blocks even a stale form handler at the real action hook', async () => {
  const app = harness(); await filled(app); const handler = app.form('application-create-form').props.onSubmit;
  await app.setEnabled(false); await handler({preventDefault() {}}); await app.ready();
  assert.equal(app.mutationCalls().length, 0); assert.ok(app.form('application-create-form'));
  assert.equal(app.disabled('application-birth'),true);assert.equal(app.button('Save application details').props.disabled,true);app.dispose();
});
test('ready server permits a customer draft without the obsolete hidden browser flag', async () => {
  const app = harness({browserFlag: false}); await filled(app); await app.submit('application-create-form');
  assert.equal(app.mutationCalls().length, 1); assert.equal(app.mutationCalls()[0].name, 'create'); app.dispose();
});
test('uncertain draft survives readiness off/on and read-only refresh recovers the same key', async () => {
  let attempts = 0;
  const app = harness({handlers: {create: async () => { if (++attempts === 1) throw new Error('Synthetic lost response'); return record(); }}});
  await filled(app, '1750.25'); await app.submit('application-create-form');
  const original = app.mutationCalls()[0].args[1];
  assert.equal(app.button('Check service readiness').props.disabled, false);
  assert.equal(app.button('Refresh status').props.disabled, true);
  await app.setEnabled(false);
  assert.equal(app.button('Retry the same request').props.disabled, true);
  await app.click('Retry the same request'); assert.equal(app.mutationCalls().length, 1);
  assert.equal(app.navigation.confirmNavigation(), false);
  await app.setEnabled(true);
  assert.equal(app.button('Retry the same request').props.disabled, false);
  await app.click('Retry the same request');
  assert.equal(app.mutationCalls().length, 2); assert.deepEqual(app.mutationCalls()[1].args[1], original);
  assert.equal(app.mutationCalls()[1].args[1].openingAmount, '1750.25'); app.dispose();
});
test('failed readiness refresh keeps an unknown customer action locked but allows read-only recovery', async () => {
  let unavailable = false, creates = 0;
  const app = harness({handlers: {
    requirements: async () => { if (unavailable) throw new Error('Synthetic readiness unavailable'); return {...policy}; },
    create: async () => { if (++creates === 1) throw new Error('Synthetic lost response'); return record(); }
  }});
  await filled(app); await app.submit('application-create-form'); const key = app.mutationCalls()[0].args[1].requestKey;
  unavailable = true; await app.click('Check service readiness');
  assert.equal(app.button('Retry the same request').props.disabled, true);
  assert.equal(app.button('Check service readiness').props.disabled, false);
  assert.match(app.text(), /Synthetic readiness unavailable/);
  unavailable = false; await app.click('Check service readiness'); await app.click('Retry the same request');
  assert.equal(app.mutationCalls().length, 2); assert.equal(app.mutationCalls()[1].args[1].requestKey, key); app.dispose();
});
test('component never persists documents or drafts or invokes admin cash/government operations', () => {
  const source = fs.readFileSync(path.join(root, 'features/banking/AccountApplications.tsx'), 'utf8');
  assert.doesNotMatch(source, /localStorage|sessionStorage|FileReader|createObjectURL|atob\(/);
  assert.doesNotMatch(source, /applicationApi\.(approve|open|receiveCash|refundCash)/);
  assert.doesNotMatch(source,/type=\"file\"|applicationApi\.(upload|download)/);
});

test('new identity choices never offer file upload and full Aadhaar paste is rejected rather than truncated',async()=>{
  const app=harness();await filled(app);
  assert.deepEqual(allNodes(app.input('application-identity-type')).filter(n=>n.type==='option').map(n=>n.props.value),['AADHAAR','PAN','PASSPORT']);
  app.set('application-identity-number','123456789012');assert.equal(app.input('application-identity-number').props.value,'');
  await app.submit('application-create-form');assert.equal(app.mutationCalls().length,0);assert.match(app.text(),/only its last four digits/);
  assert.equal(app.nodes().some(n=>n.type==='input'&&n.props.type==='file'),false);app.dispose();
});
for(const [kind,value] of [['PAN',' abcpd1234f '],['PASSPORT','a1234567'],['PASSPORT','ab123456']])test('identifier canonicalization on create '+kind+' '+value,async()=>{
  const app=harness();await filled(app);app.set('application-identity-type',kind);app.set('application-identity-number',value);
  await app.submit('application-create-form');assert.equal(app.mutationCalls()[0].args[1].identityNumber,value.trim().toUpperCase());
  assert.equal(app.input('application-identity-number'),undefined);assert.equal(app.mutationCalls().length,1);app.dispose();
});
for(const [kind,value] of [['PAN','ABCDE1234F'],['PAN','ABCPD12345'],['PASSPORT','ABCDEFGH'],['PASSPORT','12345678'],['AADHAAR','123']])test('invalid identity never creates a draft '+kind+' '+value,async()=>{
  const app=harness();await filled(app);app.set('application-identity-type',kind);app.set('application-identity-number',value);
  await app.submit('application-create-form');assert.equal(app.mutationCalls().length,0);assert.equal(app.input('application-identity-number').props['aria-invalid'],true);app.dispose();
});
test('create saves only a masked draft and submit is a separate deliberate action without documents',async()=>{
  const app=harness();await filled(app);await app.submit('application-create-form');
  assert.deepEqual(app.mutationCalls().map(c=>c.name),['create']);assert.equal(app.nodes().find(n=>n.props.class==='shared-record').props.application.identityMasked,'••••1234');
  assert.ok(app.button('Change identity details'));await app.click('Submit for review');
  assert.deepEqual(app.mutationCalls().map(c=>c.name),['create','submit']);assert.match(app.text(),/Bring the original/);app.dispose();
});
for(const status of ['DRAFT','CHANGES_REQUESTED'])test('identity correction '+status+' saves exact version and stays separate from submission',async()=>{
  const app=harness({rows:[record({status,version:7})]});await app.ready();await app.select();await app.click('Change identity details');
  assert.equal(app.input('application-identity-number').props.value,'');app.set('application-identity-number','ABCPD5678H');
  assert.equal(app.button(status==='DRAFT'?'Submit for review':'Resubmit for review').props.disabled,true);
  assert.equal(app.input('application-birth'),undefined);assert.equal(app.input('application-opening-amount'),undefined);
  await app.submit('application-identity-form');assert.equal(app.mutationCalls()[0].name,'updateIdentity');assert.equal(app.mutationCalls()[0].args[2].expectedVersion,7);
  assert.equal(app.input('application-identity-number'),undefined);assert.equal(app.mutationCalls().length,1);app.dispose();
});
test('uncertain identity correction retains exact normalized identifier, key and version during readiness outage',async()=>{
  let attempts=0;const app=harness({rows:[record({version:7})],handlers:{updateIdentity:async()=>{if(++attempts===1)throw Error('Synthetic lost response');return record({version:8});}}});
  await app.ready();await app.select();await app.click('Change identity details');app.set('application-identity-number',' abcpd1234f ');await app.submit('application-identity-form');
  const first=app.mutationCalls()[0];app.set('application-identity-number','ABCPD5678H');
  app.setPolicy({identityDetailsAvailable:false});await app.click('Check service readiness');assert.equal(app.button('Retry the same request').props.disabled,true);
  app.setPolicy({identityDetailsAvailable:true});await app.click('Check service readiness');await app.click('Retry the same request');
  assert.deepEqual(app.mutationCalls()[1].args,first.args);assert.equal(app.input('application-identity-number'),undefined);app.dispose();
});
test('readiness refresh preserves an unsent identifier and withdrawal clears it without a POST',async()=>{
  const pending=deferred();let checking=false;const app=harness({rows:[record()],handlers:{requirements:()=>checking?pending.promise:{...policy}}});
  await app.ready();await app.select();await app.click('Change identity details');app.set('application-identity-number','ABCPD1234F');checking=true;
  await app.click('Check service readiness');assert.equal(app.input('application-identity-number').props.value,'ABCPD1234F');assert.equal(app.navigation.hasUnsavedWork(),true);
  pending.resolve({...policy,identityDetailsAvailable:false});await app.ready();assert.equal(app.input('application-identity-number'),undefined);assert.equal(app.mutationCalls().length,0);app.dispose();
});
test('changing identity category clears the previous private value and discarded edits clear navigation guard',async()=>{
  const app=harness({rows:[record()]});await app.ready();await app.select();await app.click('Change identity details');app.set('application-identity-number','ABCPD1234F');
  app.set('application-identity-type','AADHAAR');assert.equal(app.input('application-identity-number').props.value,'');
  app.set('application-identity-number','1234');await app.click('Discard identity change');assert.equal(app.input('application-identity-number'),undefined);assert.equal(app.navigation.hasUnsavedWork(),false);app.dispose();
});
test('identity correction cannot be offered after submission, approval, cash or terminal decisions',async()=>{
  for(const status of ['PENDING_REVIEW','APPROVED_AWAITING_CASH','CASH_RECEIVED','OPENED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED']){
    const app=harness({rows:[record({status})]});await app.ready();await app.select();assert.equal(app.button('Change identity details'),undefined);assert.equal(app.input('application-identity-number'),undefined);app.dispose();
  }
});
