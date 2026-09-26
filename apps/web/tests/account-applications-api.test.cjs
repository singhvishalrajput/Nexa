const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

// Isolated transport fixtures only: no API server, real documents, credentials or database.
const ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const DOC = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const KEY = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
const EVENT = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
const WHEN = '2026-09-23T09:00:00Z';
const LIMIT = 10 * 1024 * 1024;
const session = {accessToken: 'test-old', refreshToken: 'test-refresh', user: {id: ID, role: 'CUSTOMER'}};
const action = {requestKey: KEY, expectedVersion: 0};
const requirements = {
  minimumOpeningAmount: '1000.00', maximumOpeningAmount: '10000000.00', currencyCode: 'INR',
  businessDate: '2026-09-23', latestDateOfBirth: '2008-09-23', allowedAccountTypes: ['SAVINGS'],
  identityTypes: ['AADHAAR', 'PAN', 'PASSPORT'], verificationMethod: 'IN_PERSON_ORIGINAL', consentVersion: 'in-person-identity-v1', consentNotice: 'Synthetic manual review notice.',
  applicationsAvailable: true, identityDetailsAvailable: true, cashReceiptAvailable: false, governmentVerification: false
};
const adminReadiness = {checkedAt: WHEN, applicationsAvailable: true, identityDetailsAvailable: false, reviewsAvailable: false,
  cashReceiptAvailable: false, accountOpeningAvailable: false, cashRefundAvailable: true, blockers: ['IDENTITY_CONFIGURATION_UNAVAILABLE']};
const summary = {
  id: ID, accountType: 'SAVINGS', currencyCode: 'INR', fullName: 'Synthetic Customer',
  email: 'synthetic@example.invalid', phoneNumber: null, dateOfBirth: '2000-01-01', status: 'DRAFT',
  reviewDecision: 'PENDING', reviewReason: null, version: 0, createdAt: WHEN, updatedAt: WHEN,
  submittedAt: null, reviewedAt: null, openedAt: null, endedAt: null, endReasonCode: null, accountId: null, openingAmount: '1000.00', identityType: 'PAN', identityMasked: '••••234F'
};
const application = {...summary, documents: [], events: [], receipts: []};
const doc = {id: DOC, documentType: 'PAN', mediaType: 'application/pdf', byteSize: 5,
  storageStatus: 'AVAILABLE', safetyStatus: 'CLEAN', reviewStatus: 'PENDING', reviewReason: null, uploadedAt: WHEN, reviewedAt: null};
const event = {id: EVENT, eventType: 'APPLICATION_CREATED', applicationVersion: 0, actorRole: 'CUSTOMER',
  fromStatus: null, toStatus: 'DRAFT', reasonCode: null, reviewReason: null, documentId: null, receiptId: null, createdAt: WHEN};
const receipt = {id: KEY, receiptNumber: 'TEST-1', currencyCode: 'INR', amount: '1000.00', status: 'RECEIVED',
  cashReceivedAt: WHEN, recordedAt: WHEN, allocatedAt: null, refundReason: null, refundedAt: null};
const createBody = {requestKey: KEY, accountType: 'SAVINGS', currencyCode: 'INR', dateOfBirth: '2000-01-01',
  openingAmount: '1000.00', consentVersion: 'in-person-identity-v1', consentAccepted: true, identityType: 'PAN', identityNumber: 'ABCPD1234F'};
const ok = value => new Response(JSON.stringify(value), {status: 200, headers: {'Content-Type': 'application/json'}});

function load(fetcher = async () => ok(application), options = {}) {
  const requests = [], saved = new Map(), emitted = [], links = [], revoked = [], created = [], deferred = [];
  if (options.session) saved.set('nexa-auth-session', JSON.stringify(options.session));
  const browser = {
    sessionStorage: {getItem: k => saved.get(k) || null, setItem: (k,v) => saved.set(k,v), removeItem: k => saved.delete(k)},
    dispatchEvent: event => emitted.push(event.type),
    setTimeout: (fn, delay) => {
      if (delay === 1000) {deferred.push(fn); return 0;}
      const timer = setTimeout(fn, delay); timer.unref(); return timer;
    }, clearTimeout
  };
  if ('enabled' in options) browser.NEXA_ACCOUNT_APPLICATIONS_ENABLED = options.enabled;
  const urlApi = {createObjectURL: blob => {created.push(blob); return 'blob:synthetic-private';}, revokeObjectURL: url => revoked.push(url)};
  const document = {
    createElement: tag => {
      assert.equal(tag, 'a'); const link = {click() {this.clicked = true;}, remove() {this.removed = true;}};
      links.push(link); return link;
    }, body: {appendChild: link => {link.attached = true;}}
  };
  const modules = {};
  const compile = name => {
    if (modules[name]) return modules[name];
    const exports = modules[name] = {};
    const code = ts.transpileModule(fs.readFileSync(`src/services/${name}.ts`, 'utf8'), {compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021
    }}).outputText;
    vm.runInNewContext(code, {exports, require: name => compile(name.replace('./', '')), window: browser,
      document, Headers, FormData, Blob, File, AbortController, Event, URL: urlApi, URLSearchParams, TextEncoder,
      fetch: async (url, init) => {requests.push({url, init}); return fetcher(url, init);}
    });
    return exports;
  };
  return {api: compile('account-applications'), auth: compile('auth'), browser, requests, saved, emitted, links, revoked, created,
    flushDownloads() {deferred.splice(0).forEach(fn => fn());}};
}

test('transport has no hidden window readiness flag or global cached availability', () => {
  const h=load();assert.equal(h.api.onboardingUiEnabled,undefined);
  const source=fs.readFileSync('src/services/account-applications.ts','utf8');
  assert.doesNotMatch(source,/NEXA_ACCOUNT_APPLICATIONS_ENABLED|requireWritesEnabled|onboardingUiEnabled/);
});

test('strict decimal paise parser keeps every boundary and never rounds user money', () => {
  const {api} = load();
  for (const [raw, canonical] of [['1000', '1000.00'], ['1000.1', '1000.10'], ['1000.01', '1000.01'], ['10000000', '10000000.00']]) assert.equal(api.normalizeOpeningAmount(raw), canonical);
  for (const raw of ['999.99', '10000000.01', '1e3', '+1000', '-1000', '01000', ' 1000', '1000 ', '1,000', '1000.001', '1000.', '', 'NaN', 'Infinity', 1000, null]) assert.equal(api.normalizeOpeningAmount(raw), null, String(raw));
  assert.equal(api.normalizeOpeningAmount('1000', {...requirements, minimumOpeningAmount: '2000.00'}), null);
  assert.equal(api.normalizeOpeningAmount('2000', {...requirements, minimumOpeningAmount: '2000.00'}), '2000.00');
  assert.equal(api.normalizeOpeningAmount('1000', {minimumOpeningAmount: '0', maximumOpeningAmount: '10000000'}), null);
});

test('requirements guard validates actual camelcase wire contract and allows existing savings exclusion', () => {
  const {api} = load();
  assert.equal(api.validateRequirements(requirements), requirements);
  assert.ok(api.validateRequirements({...requirements, allowedAccountTypes: []}));
  assert.ok(api.validateRequirements({...requirements, applicationsAvailable: false, identityDetailsAvailable: false, cashReceiptAvailable: false}));
  for (const change of [
    {minimumOpeningAmount: 1000}, {maximumOpeningAmount: '1e7'}, {minimumOpeningAmount: '999.99'},
    {currencyCode: 'USD'}, {governmentVerification: true}, {applicationsAvailable: 'false'},
    {latestDateOfBirth: '2026-09-22'}, {businessDate: '2026-02-30'}, {identityDetailsAvailable: 'true'},
    {verificationMethod: 'REMOTE'}, {identityTypes: []}, {identityTypes: ['AADHAAR', 'AADHAAR']},
    {allowedAccountTypes: ['CURRENT']}, {consentVersion: 'bad/version'}, {consentNotice: ''}
  ]) assert.equal(api.validateRequirements({...requirements, ...change}), null, JSON.stringify(change));
});

test('DOB uses the bank date, rejects today/future/underage and validates calendar/leap-day rules', () => {
  const {api} = load();
  assert.equal(api.validateDOB('2008-09-23', requirements), '');
  assert.equal(api.validateDOB('0001-01-01', requirements), '');
  for (const value of ['2008-09-24', '2026-09-23', '2030-01-01', '2000-02-30', '1900-02-29', '0000-01-01', '1-01-01', '2000-1-01']) assert.ok(api.validateDOB(value, requirements), value);
  assert.equal(api.validateDOB('2000-02-29', requirements), '');
  const feb = {...requirements, businessDate: '2026-02-28', latestDateOfBirth: '2008-02-28'};
  assert.ok(api.validateRequirements(feb)); assert.ok(api.validateDOB('2008-02-29', feb));
  assert.equal(api.validateDOB('2008-02-29', {...feb, businessDate: '2026-03-01', latestDateOfBirth: '2008-03-01'}), '');
  assert.ok(api.validateRequirements({...requirements, businessDate: '2024-02-29', latestDateOfBirth: '2006-02-28'}));
});

test('review reasons reject control characters, UTF-8 overflow and raw Aadhaar/PAN numbers', () => {
  const {api} = load();
  assert.equal(api.validateReviewReason('The uploaded copy is readable and matches the profile.'), '');
  assert.equal(api.validateReviewReason('é'.repeat(250)), '');
  for (const reason of ['', '  ', 'a'.repeat(501), 'é'.repeat(251), 'Review\ncomplete', 'bad\u0085control',
    'PAN ABCDE1234F reviewed', 'aadhaar 1234 5678 9012 seen', '1234-5678-9012', 'number 123456789012']) assert.ok(api.validateReviewReason(reason), reason);
});

test('receipt numbers are bounded plain receipt references, not arbitrary text or IDs', () => {
  const {api} = load();
  for (const value of ['RCPT/2026-01', 'Counter_1/ABC-99', 'A'.repeat(64)]) assert.equal(api.validReceiptNumber(value), true);
  for (const value of ['', '/ABC', 'Receipt 1', 'A'.repeat(65), '<html>', 'a\nb', null]) assert.equal(api.validReceiptNumber(value), false);
  assert.equal(api.applicationStatusLabel('OPENED'), 'Account opened');
  assert.equal(api.applicationStatusLabel('<script>'), 'Unknown status');
});

test('customer reads use authenticated no-store requests and exact endpoints', async () => {
  const h = load(async url => ok(url.endsWith('/requirements') ? requirements : url.endsWith(ID) ? application : [summary]));
  assert.equal((await h.api.applicationApi.requirements('test-token')).consentVersion, requirements.consentVersion);
  assert.equal((await h.api.applicationApi.listMine('test-token'))[0].id, ID);
  assert.equal((await h.api.applicationApi.getMine('test-token', ID)).id, ID);
  assert.deepEqual(h.requests.map(r => r.url), [
    'http://localhost:8088/api/v1/account-applications/requirements',
    'http://localhost:8088/api/v1/account-applications',
    `http://localhost:8088/api/v1/account-applications/${ID}`
  ]);
  for (const {init} of h.requests) {
    assert.equal(init.headers.get('Authorization'), 'Bearer test-token');
    assert.equal(init.cache, 'no-store'); assert.equal(init.credentials, 'omit'); assert.equal(init.method, 'GET');
  }
});

test('adapter is validated transport without a hidden flag or an extra readiness read before POST', async () => {
  const h = load(); const api = h.api.applicationApi;
  const calls = [
    () => api.create('token', createBody), () => api.updateIdentity('token', ID, {...action, identityType: 'AADHAAR', identityNumber: '1234'}),
    () => api.submit('token', ID, action), () => api.cancel('token', ID, action),
    () => api.review('token', ID, {...action, decision: 'APPROVED', reason: 'Original checked in person.', inPersonChecked: true}),
    () => api.receiveCash('token', ID, {...action, cashReceivedConfirmed: true}),
    () => api.open('token', ID, action), () => api.requestRefund('token', ID, {...action, reason: 'Opening cancelled.'}),
    () => api.refund('token', ID, {...action, cashReturnedConfirmed: true})
  ];
  for (const call of calls) await call();
  assert.equal(h.requests.length,calls.length);
  assert.ok(h.requests.every(request=>request.init.method==='POST'));
});

test('admin readiness uses its authenticated no-store GET endpoint and does not cache across tokens', async () => {
  const h=load(async()=>ok(adminReadiness));
  assert.deepEqual(JSON.parse(JSON.stringify(await h.api.applicationApi.adminReadiness('first-token'))),adminReadiness);
  await h.api.applicationApi.adminReadiness('second-token');
  assert.equal(h.requests.length,2);
  for(const [i,{url,init}] of h.requests.entries()){
    assert.equal(url,'http://localhost:8088/api/v1/admin/account-applications/readiness');
    assert.equal(init.method,'GET');assert.equal(init.cache,'no-store');assert.equal(init.credentials,'omit');
    assert.equal(init.headers.get('Authorization'),`Bearer ${i===0?'first':'second'}-token`);
  }
});

test('readiness parser validates strict booleans, UTC timestamp and bounded distinct safe blocker codes', () => {
  const {api}=load();assert.ok(api.validateAdminReadiness(adminReadiness));
  assert.ok(api.validateAdminReadiness({...adminReadiness,blockers:['NEW_BACKEND_CHECK_2']}));
  assert.ok(api.validateAdminReadiness({...adminReadiness,checkedAt:'2026-09-23T09:00:00.123456789Z'}));
  for(const change of [
    {applicationsAvailable:'true'},{identityDetailsAvailable:undefined},{identityDetailsAvailable:'true'},{identityDetailsAvailable:null},
    {reviewsAvailable:1},{cashReceiptAvailable:null},{accountOpeningAvailable:undefined},{cashRefundAvailable:'false'},
    {checkedAt:'2026-09-23T09:00:00'},{checkedAt:'2026-09-23T09:00:00+05:30'},{checkedAt:'2026-02-30T09:00:00Z'},
    {checkedAt:'2026-09-23T24:00:00Z'},{checkedAt:'2026-09-23T09:61:00Z'},
    {blockers:'BLOCKED'},{blockers:['lowercase']},{blockers:['CHECK','CHECK']},{blockers:['C:/private/file']},
    {blockers:['<html>']},{blockers:['A'.repeat(65)]},{blockers:[null]},{blockers:Array.from({length:17},(_,i)=>'CODE_'+i)}
  ])assert.equal(api.validateAdminReadiness({...adminReadiness,...change}),null,JSON.stringify(change));
  const result=api.validateAdminReadiness({...adminReadiness,privatePath:'must not expose'});
  assert.equal(result.privatePath,undefined);
  assert.equal(result.identityDetailsAvailable,false);
  assert.equal(api.validateAdminReadiness({...adminReadiness,identityDetailsAvailable:true,blockers:[]}).identityDetailsAvailable,true);
});

test('malformed readiness fails closed and server permission failures are not changed into posting uncertainty', async () => {
  for(const response of [null,{}, {...adminReadiness,reviewsAvailable:'true'}]){
    const h=load(async()=>ok(response));await assert.rejects(h.api.applicationApi.adminReadiness('token'),error=>error.status===0);
    assert.equal(h.requests.length,1);assert.equal(h.requests[0].init.method,'GET');
  }
  const forbidden=load(async()=>new Response('{}',{status:403}));
  await assert.rejects(forbidden.api.applicationApi.adminReadiness('customer-token'),error=>error.status===403);
  const unavailable=load(async()=>new Response(JSON.stringify({detail:'Cash intake is unavailable.'}),{status:409}));
  await assert.rejects(unavailable.api.applicationApi.receiveCash('token',ID,{...action,cashReceivedConfirmed:true}),error=>error.status===409);
  assert.equal(unavailable.requests.length,1);assert.equal(unavailable.requests[0].init.method,'POST');
});

test('create sends only customer-owned fields with explicit consent and decimal strings', async () => {
  const h = load(undefined, {enabled: true});
  await h.api.applicationApi.create('token', {...createBody, customerId: 'forged', status: 'OPENED', reviewedBy: 'attacker'});
  assert.deepEqual(JSON.parse(h.requests[0].init.body), createBody);
  assert.equal(h.requests[0].init.headers.get('Content-Type'), 'application/json');
  for (const change of [{consentAccepted: false}, {consentAccepted: 'true'}, {openingAmount: 1000}, {accountType: 'CURRENT'}, {dateOfBirth: '2000-02-30'}, {requestKey: '../../bad'}]) {
    await assert.rejects(h.api.applicationApi.create('token', {...createBody, ...change}), error => error.status === 400);
  }
  assert.equal(h.requests.length, 1);
});

test('customer actions keep caller request keys and versions without invented privileges', async () => {
  const h = load(undefined, {enabled: true});
  await h.api.applicationApi.submit('token', ID, {...action, status: 'APPROVED'});
  await h.api.applicationApi.cancel('token', ID, action);
  assert.ok(h.requests[0].url.endsWith(`/${ID}/submit`)); assert.ok(h.requests[1].url.endsWith(`/${ID}/cancel`));
  for (const call of h.requests) assert.deepEqual(JSON.parse(call.init.body), action);
  for (const expectedVersion of [-1, 0.5, '0', Infinity, Number.MAX_SAFE_INTEGER + 1]) await assert.rejects(h.api.applicationApi.submit('token', ID, {...action, expectedVersion}), error => error.status === 400);
});

test('admin queue encodes filters and validates page/response limits', async () => {
  const h = load(async url => ok(url.includes('?') ? {items: [summary], total: 1, page: 0, size: 20} : application));
  const result = await h.api.applicationApi.listAdmin('token', {status: 'PENDING_REVIEW', page: 0, size: 20});
  assert.equal(result.total, 1); assert.ok(h.requests[0].url.endsWith('/admin/account-applications?page=0&size=20&status=PENDING_REVIEW'));
  await h.api.applicationApi.getAdmin('token', ID); assert.ok(h.requests[1].url.endsWith(`/admin/account-applications/${ID}`));
  for (const query of [{page: -1, size: 20}, {page: 100001, size: 20}, {page: 0, size: 101}, {page: 0, size: 0}, {page: 0, size: 20, status: 'APPROVED'}]) await assert.rejects(h.api.applicationApi.listAdmin('token', query), error => error.status === 400);
});

test('admin commands use the approved exact route and DTO contract', async () => {
  const h = load(undefined, {enabled: true}); const api = h.api.applicationApi;
  const cases = [
    ['review', `${ID}/review`, {...action, decision: 'APPROVED', reason: 'Original checked in person.', inPersonChecked: true}],
    ['receiveCash', `${ID}/cash-receipts`, {...action, cashReceivedConfirmed: true}],
    ['open', `${ID}/open`, action],
    ['requestRefund', `${ID}/refund-request`, {...action, reason: 'Customer withdrew the request.'}],
    ['refund', `${ID}/refund`, {...action, cashReturnedConfirmed: true}]
  ];
  for (const [method, route, body] of cases) {
    await api[method]('token', ID, ...(method === 'reviewDocument' ? [DOC] : []), {...body, actorId: 'forged', transactionId: 123, amount: '9999.99', receiptNumber: 'FORGED-1'});
    const request = h.requests.at(-1);
    assert.ok(request.url.endsWith(`/admin/account-applications/${route}`));
    assert.deepEqual(JSON.parse(request.init.body), body); assert.equal(request.init.method, 'POST');
  }
});

test('cash/refund acknowledgement cannot be missing, false, or string-coerced', async () => {
  const h = load(undefined, {enabled: true});
  for (const confirmed of [undefined, false, 'true', 1]) {
    await assert.rejects(h.api.applicationApi.receiveCash('token', ID, {...action, cashReceivedConfirmed: confirmed}), error => error.status === 400);
    await assert.rejects(h.api.applicationApi.refund('token', ID, {...action, cashReturnedConfirmed: confirmed}), error => error.status === 400);
  }
  assert.equal(h.requests.length, 0);
});

test('application/document UUIDs never become arbitrary URL paths', async () => {
  const h = load(undefined, {enabled: true});
  for (const id of ['../../admin', `${ID}?other=true`, '', 'a', null]) {
    await assert.rejects(h.api.applicationApi.getMine('token', id), error => error.status === 400);
    await assert.rejects(h.api.applicationApi.revealIdentity('token', id), error => error.status === 400);
  }
  assert.equal(h.requests.length, 0);
});

test('untrusted summary/detail responses fail closed without pretending a write failed', async () => {
  const changes = [{id: 'not-a-uuid'}, {id: DOC}, {version: -1}, {version: '0'}, {version: Number.MAX_SAFE_INTEGER + 1},
    {openingAmount: 1000}, {openingAmount: '1000.001'}, {openingAmount: '999.99'}, {accountId: '123'}, {status: 'APPROVED'},
    {dateOfBirth: '2000-02-30'}, {documents: [doc, doc]}, {documents: [{...doc, byteSize: LIMIT + 1}]},
    {documents: [{...doc, storageStatus: 'PUBLIC'}]}, {events: [{...event, applicationVersion: 1}]}, {receipts: [receipt, receipt]},
    {receipts: [{...receipt, amount: 'Infinity'}]}, {updatedAt: 'not-a-date'}];
  for (const change of changes) {
    const h = load(async () => ok({...application, ...change}), {enabled: true});
    await assert.rejects(h.api.applicationApi.submit('token', ID, action), error => error.status === 0, JSON.stringify(change));
    assert.equal(h.requests.length, 1);
  }
});

test('valid private document/event/receipt metadata is preserved without storage paths', async () => {
  const h = load(async () => ok({...application, documents: [doc], events: [event], receipts: [receipt]}));
  const result = await h.api.applicationApi.getMine('token', ID);
  assert.equal(result.documents[0].documentType, 'PAN'); assert.equal(result.events[0].actorRole, 'CUSTOMER'); assert.equal(result.receipts[0].amount, '1000.00');
  assert.equal(result.documents[0].storageKey, undefined);
});

test('duplicate/oversized lists and malformed requirements/queue responses are rejected', async () => {
  for (const value of [[summary, summary], Array(101).fill(summary), {}, [{...summary, currencyCode: 'USD'}]]) {
    await assert.rejects(load(async () => ok(value)).api.applicationApi.listMine('token'), error => error.status === 0);
  }
  await assert.rejects(load(async () => ok({...requirements, consentNotice: null})).api.applicationApi.requirements('token'), error => error.status === 0);
  for (const change of [{page: 1}, {size: 100}, {total: -1}, {total: '1'}]) await assert.rejects(load(async () => ok({items: [summary], total: 1, page: 0, size: 20, ...change})).api.applicationApi.listAdmin('token', {page: 0, size: 20}), error => error.status === 0);
});

test('network and server errors never automatically replay a financial mutation', async () => {
  for (const fetcher of [async () => {throw new TypeError('network fixture');}, async () => new Response('{}', {status: 503}), async () => new Response('<html>bad</html>', {status: 200})]) {
    const h = load(fetcher, {enabled: true});
    await assert.rejects(h.api.applicationApi.open('token', ID, action)); assert.equal(h.requests.length, 1);
  }
});


test('identifier syntax normalizes case and padding but never accepts a full Aadhaar number', () => {
  const {api}=load();
  for(const [type,raw,value] of [['AADHAAR','1234','1234'],['PAN',' abcpd1234f ','ABCPD1234F'],['PASSPORT','a1234567','A1234567'],['PASSPORT','ab123456','AB123456']])
    assert.equal(api.normalizeIdentityNumber(type,raw),value);
  for(const [type,value] of [['AADHAAR','123456789012'],['AADHAAR','123'],['AADHAAR','abcd'],['PAN','ABCDE1234F'],['PAN','ABCPD12345'],['PASSPORT','ABCDEFGH'],['PASSPORT','12345678'],['PAN',null],['OTHER','1234'],['AADHAAR',1234]])
    assert.equal(api.normalizeIdentityNumber(type,value),null);
});

test('no upload, document-review or download entry point remains in the application adapter', () => {
  const h=load();for(const name of ['upload','reviewDocument','download'])assert.equal(h.api.applicationApi[name],undefined);
  assert.doesNotMatch(fs.readFileSync('src/services/account-applications.ts','utf8'),/new FormData|createObjectURL|\/documents.*POST/);
  assert.equal(h.requests.length,0);
});

test('identity updates send exact caller key/version and canonical identifier only in a no-store body', async () => {
  const h=load();
  await h.api.applicationApi.updateIdentity('token',ID,{...action,identityType:'PAN',identityNumber:' abcpd1234f ',approved:true});
  const call=h.requests[0];assert.equal(call.url,'http://localhost:8088/api/v1/account-applications/'+ID+'/identity');
  assert.deepEqual(JSON.parse(call.init.body),{...action,identityType:'PAN',identityNumber:'ABCPD1234F'});
  assert.equal(call.init.cache,'no-store');assert.equal(call.init.credentials,'omit');
  assert.doesNotMatch(call.url,/ABCPD|1234/);assert.equal(h.saved.size,0);
  for(const value of ['123456789012','1234-5678-9012']) await assert.rejects(h.api.applicationApi.updateIdentity('token',ID,{...action,identityType:'AADHAAR',identityNumber:value}),e=>e.status===400&&!e.message.includes(value));
  assert.equal(h.requests.length,1);
});

test('application reads reject unmasked or raw identity fields and malformed masks',async()=>{
  for(const change of [{identityType:'OTHER'},{identityMasked:'ABCPD1234F'},{identityMasked:'••••2345'},{identityNumber:'ABCPD1234F'},{identityCiphertext:'private'}]){
    const h=load(async()=>ok({...application,...change}));
    await assert.rejects(h.api.applicationApi.getMine('token',ID),e=>e.status===0);
  }
  for(const identity of [{identityType:'AADHAAR',identityMasked:'••••1234'},{identityType:'PASSPORT',identityMasked:'••••A123'}]){
    const h=load(async()=>ok({...application,...identity}));assert.equal((await h.api.applicationApi.getMine('token',ID)).identityMasked,identity.identityMasked);
  }
});

test('admin reveal is authenticated no-store, explicitly called and validates strict returned identity',async()=>{
  const h=load(async()=>ok({identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'IN_PERSON_ORIGINAL',privatePath:'not exposed'}));
  assert.equal(h.requests.length,0);
  assert.deepEqual(JSON.parse(JSON.stringify(await h.api.applicationApi.revealIdentity('admin-token',ID))),{identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'IN_PERSON_ORIGINAL'});
  assert.equal(h.requests[0].url,'http://localhost:8088/api/v1/admin/account-applications/'+ID+'/identity');
  assert.equal(h.requests[0].init.method,'GET');assert.equal(h.requests[0].init.cache,'no-store');assert.equal(h.requests[0].init.headers.get('Authorization'),'Bearer admin-token');
  assert.equal(h.saved.size,0);
  for(const body of [{identityType:'AADHAAR',identityNumber:'123456789012',verificationMethod:'IN_PERSON_ORIGINAL'},
      {identityType:'PAN',identityNumber:null,verificationMethod:'IN_PERSON_ORIGINAL'},
      {identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'GOVERNMENT'}]){
    await assert.rejects(load(async()=>ok(body)).api.applicationApi.revealIdentity('admin',ID),e=>e.status===0);
  }
});

test('approval requires explicit original-check true and all non-approval decisions send false',async()=>{
  const h=load();
  for(const checked of [undefined,false,'true',1])await assert.rejects(h.api.applicationApi.review('admin',ID,{...action,decision:'APPROVED',reason:'Original checked in person.',inPersonChecked:checked}),e=>e.status===400);
  for(const decision of ['REJECTED','CHANGES_REQUESTED']){
    await assert.rejects(h.api.applicationApi.review('admin',ID,{...action,decision,reason:'Details need correction.',inPersonChecked:true}),e=>e.status===400);
    await h.api.applicationApi.review('admin',ID,{...action,decision,reason:'Details need correction.',inPersonChecked:false});
  }
  assert.equal(h.requests.length,2);
  for(const call of h.requests)assert.equal(JSON.parse(call.init.body).inPersonChecked,false);
});

test('review reasons reject both accepted passport shapes without echoing them',()=>{
  const h=load();for(const value of ['A1234567','AB123456']){
    const error=h.api.validateReviewReason('Compared '+value+' in person');assert.ok(error);assert.ok(!error.includes(value));
  }
});
