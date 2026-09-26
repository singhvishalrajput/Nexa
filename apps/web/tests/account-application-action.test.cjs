const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

class ApiRequestError extends Error { constructor(status, message) { super(message); this.status = status; } }
function harness({enabled = true, cryptoAvailable = true, onResult, availability, omitAvailability = false} = {}) {
  const slots = [], effects = [], guards = [], results = [];
  let cursor = 0, ids = 0, state, mounted = true;
  let currentAvailability = availability === undefined ? () => enabled : availability;
  const hooks = {
    useState(initial) { const index = cursor++; if (!(index in slots)) slots[index] = typeof initial === 'function' ? initial() : initial;
      return [slots[index], value => { slots[index] = typeof value === 'function' ? value(slots[index]) : value; }]; },
    useRef(initial) { const index = cursor++; return slots[index] || (slots[index] = {current: initial}); },
    useEffect(effect, deps) { const index = cursor++; if (!slots[index] || deps.some((value, i) => value !== slots[index].deps[i])) {
      const old = slots[index]; effects.push(() => { old?.cleanup?.(); slots[index] = {deps, cleanup: effect()}; });
    } }
  };
  const exports = {};
  const source = fs.readFileSync('src/hooks/useApplicationAction.ts', 'utf8');
  vm.runInNewContext(ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021}}).outputText, {
    exports, Error, crypto: cryptoAvailable ? {randomUUID: () => `00000000-0000-4000-8000-${String(++ids).padStart(12, '0')}`} : undefined,
    require: name => name === 'preact/hooks' ? hooks : name.endsWith('/auth') ? {ApiRequestError}
      : name.endsWith('/account-applications') ? {}
      : {useNavigationGuard: (dirty, pending) => guards.push({dirty, pending})}
  });
  function render() { cursor = 0;
    const receive = result => { if (!mounted) assert.fail('Late response reached an unmounted page'); results.push(result); onResult?.(result); };
    state = omitAvailability ? exports.useApplicationAction(receive) : exports.useApplicationAction(receive, currentAvailability);
    effects.splice(0).forEach(effect => effect()); return state; }
  return {render, results, guards, setEnabled(value) {enabled = value;}, setAvailability(value) {currentAvailability = value;}, ids: () => ids, source,
    dispose() {mounted = false; slots.forEach(slot => slot?.cleanup?.());}};
}
function deferred() { let resolve, reject; const promise = new Promise((res, rej) => {resolve = res; reject = rej;}); return {promise, resolve, reject}; }

test('omitting server readiness fails closed without generating a request identifier', async () => {
  const h = harness({omitAvailability:true}); let calls = 0;
  await h.render().run('Create', async () => {calls++; return {};});
  assert.equal(calls, 0); assert.equal(h.ids(), 0); assert.match(h.render().error, /unavailable/);
  assert.doesNotMatch(h.source, /localStorage|sessionStorage|console\./);
});

test('synchronous double click sends one request before a rerender can disable controls', async () => {
  const h = harness(), reply = deferred(), keys = [], state = h.render();
  const first = state.run('Open', key => {keys.push(key); return reply.promise;});
  const duplicate = state.run('Open', key => {keys.push(key); return reply.promise;});
  assert.equal(keys.length, 1); assert.equal(h.render().busy, true); assert.equal(h.guards.at(-1).pending, true);
  reply.resolve({id:'app',status:'OPENED'}); await Promise.all([first,duplicate]);
  assert.equal(h.results.length, 1); assert.equal(h.render().busy, false); assert.equal(h.render().uncertain, false);
});

test('unknown response freezes new writes and retries the original request key, version and payload', async () => {
  const h = harness(), calls = [], snapshot = {expectedVersion: 7, receiptNumber: 'TEST-7', amount: '1000.00'};
  let failure = true;
  const send = async key => {calls.push({key,...snapshot}); if (failure) throw new ApiRequestError(0,'timeout'); return {status:'CASH_RECEIVED'};};
  await h.render().run('Receive cash', send);
  assert.equal(h.render().uncertain, true); assert.equal(h.guards.at(-1).pending, true);
  await h.render().run('Different action', async () => assert.fail('New write while uncertain'));
  h.render().clearError(); assert.match(h.render().error, /outcome/);
  failure = false; await h.render().retry();
  assert.equal(calls.length, 2); assert.deepEqual(calls[0],calls[1]); assert.equal(h.ids(),1);
  assert.equal(h.render().uncertain,false); assert.equal(h.results.length,1);
});

test('server failures and malformed success responses remain uncertain, not a cosmetic completion', async () => {
  for (const cause of [new ApiRequestError(503,'unavailable'), new Error('invalid response')]) {
    const h = harness(); await h.render().run('Upload',async()=>{throw cause;});
    assert.equal(h.render().uncertain,true); assert.equal(h.results.length,0);
  }
});

test('definitive validation/conflict response allows correction with a new key and no false success', async () => {
  const h = harness(), keys=[];
  await h.render().run('Review',async key=>{keys.push(key);throw new ApiRequestError(409,'Refresh this application.');});
  assert.equal(h.render().uncertain,false); assert.match(h.render().error,/Refresh/); assert.equal(h.results.length,0);
  h.render().clearError(); assert.equal(h.render().error,'');
  await h.render().run('Corrected review',async key=>{keys.push(key);return {version:9};});
  assert.notEqual(keys[0],keys[1]); assert.equal(h.results.length,1);
});

test('late responses after unmount cannot write another customer screen', async () => {
  const h=harness(), reply=deferred(); const pending=h.render().run('Submit',()=>reply.promise);
  h.dispose(); reply.resolve({id:'old-application'}); await pending; assert.equal(h.results.length,0);
});

test('secure UUID support is required; there is no random or timestamp fallback',async()=>{
  const h=harness({cryptoAvailable:false}); await h.render().run('Create',async()=>assert.fail('No secure UUID'));
  assert.match(h.render().error,/Secure request/); assert.equal(h.render().uncertain,false);
});

test('withdrawn server readiness prevents same-key recovery until that operation is restored',async()=>{
  const h=harness(); let calls=0;
  const keys=[];
  await h.render().run('Open',async key=>{keys.push(key);calls++;if(calls===1)throw new ApiRequestError(0,'timeout');return {status:'OPENED'};});
  h.setEnabled(false); await h.render().retry(); assert.equal(calls,1); assert.equal(h.render().uncertain,true);
  assert.equal(h.guards.at(-1).pending,true);
  h.setEnabled(true); await h.render().retry(); assert.equal(calls,2); assert.equal(keys[0],keys[1]); assert.equal(h.ids(),1);
  assert.equal(h.render().uncertain,false);
});

test('stale action closures consult the latest rendered readiness, not an old captured boolean',async()=>{
  const h=harness({availability:true}), stale=h.render();
  h.setAvailability(false); h.render();
  await stale.run('Submit',async()=>assert.fail('Stale allowed closure must not write'));
  assert.equal(h.ids(),0); assert.equal(h.render().uncertain,false);
  h.setAvailability(true); h.render(); await stale.run('Submit',async()=>({status:'PENDING_REVIEW'}));
  assert.equal(h.results.length,1);
});

test('per-operation readiness distinguishes reviews, cash intake and independent refunds',async()=>{
  const h=harness({availability:label=>label==='Review'||label==='Refund'}), sent=[];
  await h.render().run('Receive cash',async()=>{sent.push('cash');return {};});
  assert.equal(h.ids(),0);
  await h.render().run('Review',async()=>{sent.push('review');return {};});
  await h.render().run('Refund',async()=>{sent.push('refund');return {};});
  assert.deepEqual(sent,['review','refund']); assert.equal(h.ids(),2);
});

test('retry checks its original operation label, not readiness for another available action',async()=>{
  const h=harness({availability:()=>true});let sends=0;
  await h.render().run('Receive cash',async()=>{sends++;throw new ApiRequestError(0,'unknown');});
  h.setAvailability(label=>label==='Review');h.render();await h.render().retry();
  assert.equal(sends,1);assert.equal(h.render().uncertain,true);assert.equal(h.ids(),1);
});

test('malformed or failing readiness predicates cannot enable a write',async()=>{
  for(const availability of ['true',1,null,{},()=> 'true',()=>{throw new Error('readiness absent');}]){
    const h=harness({availability});await h.render().run('Open',async()=>assert.fail('Unverified readiness'));
    assert.equal(h.ids(),0);assert.equal(h.render().uncertain,false);assert.match(h.render().error,/readiness/);
  }
});

test('a readiness change before dispatch releases an unsent key without creating false uncertainty',async()=>{
  let checks=0;const h=harness({availability:()=>++checks===1});
  await h.render().run('Review',async()=>assert.fail('Readiness withdrawn before sending'));
  assert.equal(h.render().uncertain,false);
  h.setAvailability(true);h.render();await h.render().run('Review',async()=>({status:'PENDING_REVIEW'}));
  assert.equal(h.results.length,1);
});

test('a post-success rendering callback error cannot turn a confirmed request into an unknown financial retry',async()=>{
  const h=harness({onResult(){throw new Error('display failure');}});
  await h.render().run('Open',async()=>({status:'OPENED'}));
  assert.equal(h.render().uncertain,false); assert.match(h.render().error,/request completed/);
  await h.render().retry(); assert.equal(h.results.length,1);
});

test('application routes reject malformed admin IDs and keep customer application selection out of URLs',()=>{
  const exports={}; const source=fs.readFileSync('src/features/banking/utils.ts','utf8');
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText,{exports,URLSearchParams,require:()=>({})});
  const parse=exports.parseRoute;
  assert.equal(parse('#/account-applications').page,'accounts');
  assert.equal(parse('#/admin/applications').section,'applications');
  assert.equal(parse('#/admin/applications/12345678-1234-1234-1234-123456789abc').id,'12345678-1234-1234-1234-123456789abc');
  for(const path of ['#/admin/applications/not-a-uuid','#/admin/applications/12345678-1234-1234-1234-123456789abc/extra','#/account-applications/1','#/admin/applications/%2e%2e']) assert.equal(parse(path).page,'not-found');
});
