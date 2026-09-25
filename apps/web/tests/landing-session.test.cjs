const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function compile(file,deps,globals={}) {
 const exports={};const jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,URLSearchParams,...globals,require:name=>name==='preact/jsx-runtime'?{jsx,jsxs:jsx,Fragment:'Fragment'}:deps(name)});return exports;
}
function hookHarness() {
 const slots=[],effects=[];let cursor=0;
 return {hooks:{useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return[slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},useRef(value){const i=cursor++;return slots[i]||(slots[i]={current:value});},useEffect(fn,deps){const i=cursor++;if(!slots[i]||deps.some((v,j)=>v!==slots[i][j])){slots[i]=deps;effects.push(fn);}}},render(fn){cursor=0;const tree=fn();while(effects.length)effects.shift()();return tree;}};
}
const session=role=>({user:{id:'test',role},profile:{fullName:'Test Customer'}});
async function appHarness(initialSession,hash='#home') {
 const h=hookHarness(),events={};let allow=true,logoutCalls=0,tree;
 const window={location:{hash,pathname:'/',search:''},history:{replaceState(_,__,url){window.location.hash=url.slice(url.indexOf('#'));}},addEventListener:(name,fn)=>events[name]=fn,removeEventListener(){}};
 const utils=compile('src/features/banking/utils.ts',()=>({}));
 const deps=name=>name==='preact/hooks'?h.hooks:name.endsWith('/auth')?{restoreSession:async()=>initialSession,logout:async()=>{logoutCalls++;}}:name.endsWith('/locale')?{getLocale:()=> 'en-IN',t:x=>x}:name.endsWith('/utils')?utils:name.endsWith('/account-transition')?{transitionToAccount:(_,update)=>update()}:name.endsWith('/useNavigationGuard')?{confirmNavigation:()=>allow,hasUnsavedWork:()=>false}:new Proxy({},{get:(_,key)=>key});
 const {BankingApp}=compile('src/features/banking/BankingApp.tsx',deps,{window,document:{documentElement:{}}});
 const render=()=>tree=h.render(()=>BankingApp());render();await new Promise(setImmediate);render();
 return {get tree(){return tree;},get hash(){return window.location.hash;},get logoutCalls(){return logoutCalls;},navigate(hash){window.location.hash=hash;events.hashchange();render();},deny(){allow=false;},expire(){events['nexa-session-expired']();render();},async signOut(){await tree.props.onSignOut();render();}};
}
function allNodes(node){if(!node||typeof node!=='object')return[];if(Array.isArray(node))return node.flatMap(allNodes);if(typeof node.type==='function')return allNodes(node.type(node.props));return[node,...allNodes(node.props?.children)];}
function landing(session) {
 const h=hookHarness();const {LandingPage}=compile('src/components/landing/LandingPage.tsx',name=>name==='preact/hooks'?{...h.hooks,useEffect(){}}:name.endsWith('/Action')?{Action:'Action'}:{},{window:{matchMedia:()=>({matches:true})}});
 return allNodes(h.render(()=>LandingPage({session,onSignOut:async()=>{}})));
}
test('landing and its section anchors remain public with guest, customer and admin sessions',async()=>{
 for(const current of [null,session('CUSTOMER'),session('ADMIN')]) {
  const app=await appHarness(current,'');
  for(const hash of ['', '#home','#the-nexa-way','#possibilities','#whats-next','#main']) {app.navigate(hash);assert.equal(app.tree.type,'LandingPage');assert.equal(app.tree.props.session,current);}
  app.navigate(current?.user.role==='ADMIN'?'#/admin':'#/assistant');assert.notEqual(app.tree.type,'LandingPage');
 }
});
test('landing navigation still respects unsaved-work guards',async()=>{
 const app=await appHarness(session('CUSTOMER'),'#/send-money');app.deny();app.navigate('#home');assert.equal(app.hash,'#/send-money');assert.equal(app.tree.props.route.page,'send-money');
});
test('landing sign-out and session expiry switch to guest actions without leaving the public page',async()=>{
 const app=await appHarness(session('CUSTOMER'));await app.signOut();assert.equal(app.logoutCalls,1);assert.equal(app.hash,'#home');assert.equal(app.tree.type,'LandingPage');assert.equal(app.tree.props.session,null);
 const expired=await appHarness(session('ADMIN'),'#possibilities');expired.expire();assert.equal(expired.tree.type,'LandingPage');assert.equal(expired.tree.props.session,null);assert.match(expired.tree.props.notice,/expired/);
 const protectedPage=await appHarness(session('CUSTOMER'),'#/accounts');protectedPage.expire();assert.equal(protectedPage.hash,'#/login');assert.notEqual(protectedPage.tree.type,'LandingPage');
});
test('landing calls to action adapt to guest, customer and administrator capabilities',()=>{
 const guest=landing(null),customer=landing(session('CUSTOMER')),admin=landing(session('ADMIN'));
 const hrefs=nodes=>nodes.filter(n=>n.type==='a').map(n=>n.props.href);
 assert.ok(hrefs(guest).includes('#/login'));assert.ok(hrefs(guest).includes('#/register'));
 assert.ok(hrefs(customer).includes('#/assistant'));assert.ok(hrefs(customer).includes('#/accounts'));assert.ok(hrefs(customer).includes('#/settings'));
 assert.ok(hrefs(admin).includes('#/admin'));assert.ok(hrefs(admin).includes('#/admin/loans'));assert.ok(!hrefs(admin).includes('#/assistant'));
 for(const nodes of [customer,admin]){assert.ok(!hrefs(nodes).includes('#/login'));assert.ok(!hrefs(nodes).includes('#/register'));assert.ok(nodes.some(n=>n.type==='Action'&&n.props.children==='Sign out'));}
});

