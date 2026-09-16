const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const vm=require('node:vm');const ts=require('typescript');
function load(file,fetcher,stored){
 const map=new Map(stored?[['nexa-auth-session',JSON.stringify(stored)]]:[]);const events=[];
 const exports={};const window={NEXA_API_BASE_URL:'http://localhost:8088/api/v1',setTimeout,clearTimeout,dispatchEvent:e=>events.push(e.type),sessionStorage:{getItem:k=>map.get(k)||null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)}};
 vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText,{exports,window,require:name=>{if(name.endsWith("locale"))return load("src/services/locale.ts").api;if(name.endsWith("banking-content"))return load("src/services/banking-content.ts").api;throw Error(name);},fetch:fetcher,Headers,Response,AbortController,Event,URLSearchParams,BigInt,setTimeout,clearTimeout});return {api:exports,map,events,window};
}
const authFile='src/services/auth.ts';
const session={accessToken:'old',refreshToken:'refresh',user:{id:'owner',email:'test@example.com',role:'CUSTOMER'}};
const ok=value=>new Response(JSON.stringify(value),{status:200,headers:{'Content-Type':'application/json'}});
test('money validation and totals preserve paise',()=>{const {api}=load('src/features/banking/utils.ts');for(const value of ['0','-1','1e3','1.001','01','NaN',''])assert.equal(api.validAmount(value),false,value);for(const value of ['0.01','12','999.99'])assert.equal(api.validAmount(value),true,value);assert.equal(api.sumMoney(['0.10','0.20','-0.01']),'0.29');});
test('routes preserve account filters and reject invalid paths',()=>{const {api}=load('src/features/banking/utils.ts');assert.equal(api.parseRoute('#/transactions?account=12').account,'12');assert.equal(api.parseRoute('#/transactions/TX-123').id,'TX-123');assert.equal(api.parseRoute('#/unknown').page,'not-found');assert.equal(api.parseRoute('#/accounts/%E0').page,'not-found');});
test('concurrent unauthorized reads share one rotating refresh request',async()=>{let rotations=0;const {api,map}=load(authFile,async(url,init)=>{if(url.endsWith('/auth/refresh')){rotations++;await new Promise(r=>setTimeout(r,15));return ok({...session,accessToken:'new',refreshToken:'rotated'});}return init.headers.get('Authorization')==='Bearer new'?ok({balance:12}):new Response('{}',{status:401});},session);const results=await Promise.all([api.authenticatedRequest('/accounts','old'),api.authenticatedRequest('/accounts','old')]);assert.equal(rotations,1);assert.equal(results[0].balance,12);assert.equal(JSON.parse(map.get('nexa-auth-session')).refreshToken,'rotated');});
test('network errors never automatically replay a financial action',async()=>{let calls=0;const {api}=load(authFile,async()=>{calls++;throw new Error('network');},session);await assert.rejects(api.authenticatedCoreRequest('/transactions/transfer','old',{method:'POST',body:'{}'}),/could not be confirmed/);assert.equal(calls,1);});
test('management requests use the existing core API and preserve its errors',async()=>{let seen;const {api}=load(authFile,async(url)=>{seen=url;return new Response(JSON.stringify({error:'Insufficient balance'}),{status:400});},session);await assert.rejects(api.authenticatedCoreRequest('/transactions/transfer','old',{method:'POST',body:'{}'}),/Insufficient balance/);assert.equal(seen,'http://localhost:8088/api/transactions/transfer');});
test('temporary outages do not discard a restorable session',async()=>{const {api,map}=load(authFile,async()=>{throw new Error('offline');},session);await assert.rejects(api.restoreSession());assert.equal(map.has('nexa-auth-session'),true);});
test('an invalid refresh clears credentials and requests sign-in',async()=>{const {api,map,events}=load(authFile,async()=>new Response('{}',{status:401}),session);await assert.rejects(api.authenticatedRequest('/accounts','old'));assert.equal(map.has('nexa-auth-session'),false);assert.ok(events.includes('nexa-session-expired'));});
test('logout clears credentials immediately even when revocation fails',async()=>{const {api,map}=load(authFile,async()=>{throw new Error('offline');},session);const promise=api.logout();assert.equal(map.has('nexa-auth-session'),false);await assert.rejects(promise);});
test('a late refresh cannot resurrect a signed-out session',async()=>{let release;let started;const ready=new Promise(r=>started=r);const {api,map}=load(authFile,async(url)=>{if(url.endsWith('/auth/refresh')){started();await new Promise(r=>release=r);return ok({...session,accessToken:'late'});}if(url.endsWith('/auth/logout'))return new Response(null,{status:204});return new Response('{}',{status:401});},session);const request=api.authenticatedRequest('/accounts','old');await ready;await api.logout();release();await assert.rejects(request);assert.equal(map.has('nexa-auth-session'),false);});

test('permission denial after refresh keeps the valid session', async () => {
 const {api,map,events}=load(authFile,async(url,init)=>url.endsWith('/auth/refresh')?ok({...session,accessToken:'new'}):new Response('{}',{status:init.headers.get('Authorization')==='Bearer new'?403:401}),session);
 await assert.rejects(api.authenticatedCoreRequest('/accounts','old'),e=>e.status===403);
 assert.equal(JSON.parse(map.get('nexa-auth-session')).accessToken,'new');
 assert.deepEqual(events,[]);
});
test('a malformed successful mutation response remains uncertain and is not replayed',async()=>{
 let calls=0;const {api}=load(authFile,async()=>{calls++;return new Response('broken JSON',{status:200});},session);
 await assert.rejects(api.authenticatedCoreRequest('/transactions/transfer','old',{method:'POST'}),e=>e.status===0);
 assert.equal(calls,1);
});
test('the request timeout covers reading the response body',async()=>{
 const env=load(authFile,async(url,init)=>({ok:true,status:200,json:()=>new Promise((resolve,reject)=>init.signal.addEventListener('abort',()=>reject(new Error('aborted'))))}),session);
 env.window.setTimeout=callback=>setTimeout(callback,5);
 await assert.rejects(env.api.authenticatedRequest('/accounts','old'),e=>e.status===0);
});
test('a late sign-in cannot restore credentials after logout',async()=>{
 let release;const env=load(authFile,async(url)=>url.endsWith('/auth/login')?new Promise(r=>release=()=>r(ok(session))):new Response(null,{status:204}));
 const signingIn=env.api.login('test@example.com','password');
 await env.api.logout();release();await assert.rejects(signingIn);
 assert.equal(env.map.has('nexa-auth-session'),false);
});
test('routes reject extra path segments and unsupported detail screens',()=>{
 const {api}=load('src/features/banking/utils.ts');
 for(const route of ['#/accounts/1/extra','#/payments/123','#/login/other'])assert.equal(api.parseRoute(route).page,'not-found');
});
test('totals and display share rounding and do not hide invalid amounts',()=>{
 const {api}=load('src/features/banking/utils.ts');
 assert.equal(api.sumMoney(['1.005','2.005']),'3.02');
 assert.equal(api.sumMoney(['1','not money']),'Amount unavailable');
 const format=load('src/services/banking-content.ts').api;
 assert.equal(format.statusPresentation('SUCCESS').label,format.statusPresentation('POSTED').label);
 assert.equal(format.transactionDirection({amount:-1,status:'SUCCESS'}),'Spent');
 const previous=process.env.TZ;process.env.TZ='America/Los_Angeles';
 try { assert.match(format.formatDate('2026-09-11'),/^11 Sept/); }
 finally { if(previous===undefined)delete process.env.TZ;else process.env.TZ=previous; }
});

test('server failures show recovery guidance instead of internal error details',async()=>{const {api}=load(authFile,async()=>new Response(JSON.stringify({detail:'database stack trace',error:'internal exception'}),{status:503}),session);await assert.rejects(api.authenticatedRequest('/accounts','old'),e=>e.status===503 && /try again later/.test(e.message) && !/stack trace|exception/.test(e.message));});
