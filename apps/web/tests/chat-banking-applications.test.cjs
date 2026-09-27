const {test} = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript');
const clientId = '11111111-1111-4111-8111-111111111111', applicationKey = 'chat-mandate-' + clientId;
const account = {id:'12',displayName:'Savings',accountNumberMasked:'•••• 1234',accountType:'SAVINGS',status:'ACTIVE',currencyCode:'INR'};
const payee = {id:'P-1',displayName:'Electricity office',bankName:'Nexa',status:'ACTIVE'};
const draft = {applicationKey,sourceAccountId:12,payeeId:'P-1',limit:'1000',startDate:'2026-09-27',endDate:null};
const saved = (value = draft, status = 'PENDING') => ({ID:'M-chat-1',STATUS:status,APPLICATION_KEY:value.applicationKey,SOURCE_ACCOUNT_ID:value.sourceAccountId,AMOUNT:value.limit,EFFECTIVE_DATE:value.startDate,END_DATE:value.endDate,TARGET_ID:value.payeeId || null});
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node,...nodes(node.props?.action),...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);
class FormDataFixture extends FormData {constructor(form) {super(); for (const [key,value] of form?.fields || []) this.append(key,value);}}
const fields = () => ({fields:[['account','12'],['payeeId','P-1'],['amount','1000'],['start','2026-09-27'],['end','']]});

function harness(options = {}) {
  const instances = new Map(), effects = [], calls = [], cache = new Map();
  let current, cursor = 0, dirty = true, tree, visited;
  const props = {clientId,accessToken:'owner-token',active:true,...options.props};
  class ApiRequestError extends Error {constructor(status,message) {super(message); this.status=status;}}
  const hooks = {
    useState(initial) {const slots=current,at=cursor++; if (!(at in slots)) slots[at]={value:typeof initial==='function'?initial():initial}; return [slots[at].value,value=>{const next=typeof value==='function'?value(slots[at].value):value;if(!Object.is(next,slots[at].value)){slots[at].value=next;dirty=true;}}];},
    useRef(initial) {const at=cursor++;return current[at]||(current[at]={current:initial});},
    useEffect(effect,deps) {const slots=current,at=cursor++,old=slots[at];if(!old||deps.some((value,i)=>value!==old.deps[i])){slots[at]={deps};effects.push(()=>{old?.cleanup?.();slots[at].cleanup=effect();});}}
  };
  const handlers = {request: async (url,_token,init) => {
    if(url.startsWith('/mandates/applications/by-request/')) {if(options.existing)return options.existing;throw new ApiRequestError(404,'Mandate not found');}
    if(url==='/accounts')return options.accounts || [account];
    if(url==='/beneficiaries')return [payee];
    if(url==='/mandates' && init?.method==='POST')return saved(JSON.parse(init.body));
    throw Error('Unexpected API call: '+url);
  }};
  const request=async(url,token,init)=>{calls.push({url,token,method:init?.method,body:init?.body});return handlers.request(url,token,init);};
  const jsx=(type,props,key)=>({type,key,props:props||{}});
  const load=relative=>{
    if(cache.has(relative))return cache.get(relative);
    const exports={};cache.set(relative,exports);
    vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.resolve(__dirname,'../src',relative),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{
      exports,Error,Date,FormData:FormDataFixture,Blob,crypto:{randomUUID:()=> 'standalone-key'},window:{alert(){},confirm:()=>true},
      require:name=>name==='preact/hooks'?hooks:name==='preact/jsx-runtime'?{jsx,jsxs:jsx,Fragment:'fragment'}
        :name.endsWith('/locale')?{t:value=>value,getLocale:()=> 'en-IN'}
        :name.endsWith('/auth')?{ApiRequestError,authenticatedRequest:request}
        :name.endsWith('/api')?{bankApi:{accounts:token=>request('/accounts',token)}}
        :name.endsWith('/banking')?{getAccounts:token=>request('/accounts',token)}
        :name.endsWith('/banking-content')?load('services/banking-content.ts')
        :name.endsWith('/mandate-applications')?load('services/mandate-applications.ts')
        :name.endsWith('/loan-applications')?load('services/loan-applications.ts')
        :name.endsWith('/useNavigationGuard')?load('hooks/useNavigationGuard.ts')
        :name.endsWith('/ProductOperations')?load('features/banking/ProductOperations.tsx')
        :name.endsWith('/AccountApplications')?{CustomerAccountApplications:props=>jsx('account-application-form',props)}
        :name.endsWith('/ui')?load('features/banking/ui.tsx'):{}
    });return exports;
  };
  const Component=load('components/chat/BankingApplicationCard.tsx')[options.account?'AccountApplicationCard':'MandateApplicationCard'];
  function expand(node,key){if(!node||typeof node!=='object')return node;if(Array.isArray(node))return node.map((child,i)=>expand(child,key+'.'+i));if(typeof node.type==='function'){const id=key+'/'+node.type.name+':'+(node.key||'');visited.add(id);current=instances.get(id)||[];instances.set(id,current);cursor=0;return expand(node.type(node.props),id);}return {...node,props:{...node.props,action:expand(node.props.action,key+'.action'),children:expand(node.props.children,key+'.children')}};}
  function render(){let attempts=0;do{dirty=false;visited=new Set();tree=expand(jsx(Component,props),'root');for(const[id,slots]of instances){if(!visited.has(id)){slots.forEach(slot=>slot.cleanup?.());instances.delete(id);}}effects.splice(0).forEach(run=>run());if(++attempts>40)throw Error('Render did not settle');}while(dirty);return tree;}
  async function ready(){for(let i=0;i<6;i++){render();await new Promise(setImmediate);}return render();}
  const button=label=>nodes(render()).find(node=>node.type==='button'&&text(node).trim()===label);
  return {props,calls,handlers,ApiRequestError,api:load('services/mandate-applications.ts'),guard:load('hooks/useNavigationGuard.ts'),ready,render,button,
    posts:()=>calls.filter(call=>call.method==='POST'),
    async submit(values=fields()){const form=nodes(render()).find(node=>node.type==='form');assert.ok(form,'Inline mandate form');await form.props.onSubmit({preventDefault(){},currentTarget:values});return ready();},
    async click(label){const action=button(label);assert.ok(action,label);action.props.onClick();return ready();},
    dispose(){for(const slots of instances.values())slots.forEach(slot=>slot.cleanup?.());}
  };
}

test('mandate chat opens the existing authenticated form only after lookup and never auto-creates or activates',async()=>{
  const app=harness();assert.equal(nodes(app.render()).some(node=>node.type==='form'),false);await app.ready();
  assert.equal(app.calls[0].url,'/mandates/applications/by-request/'+applicationKey);assert.equal(app.calls[0].token,'owner-token');
  assert.ok(app.button('Create'));assert.equal(app.button('Check mandate status'),undefined);assert.equal(app.posts().length,0);
  await app.submit();assert.equal(app.posts().length,1);assert.equal(app.posts()[0].url,'/mandates');assert.deepEqual(JSON.parse(app.posts()[0].body),draft);
  assert.match(text(app.render()),/Mandate saved/);assert.match(text(app.render()),/authorization is pending/);assert.match(text(app.render()),/does not send money/);
  assert.ok(nodes(app.render()).some(node=>node.props?.href==='#/mandates/M-chat-1'));assert.equal(nodes(app.render()).some(node=>node.type==='form'),false);app.dispose();
});

test('mandate duplicate submit posts once and holds the workspace lock until verified success',async()=>{
  const locks=[],app=harness({props:{onSubmissionLocked:value=>locks.push(value)}});await app.ready();const original=app.handlers.request;let complete;
  app.handlers.request=(url,token,init)=>init?.method==='POST'?new Promise(resolve=>{complete=()=>resolve(saved());}):original(url,token,init);
  const form=nodes(app.render()).find(node=>node.type==='form'),event={preventDefault(){},currentTarget:fields()};
  const first=form.props.onSubmit(event),second=form.props.onSubmit(event);assert.equal(locks.at(-1),true);await app.ready();
  assert.equal(app.posts().length,1);assert.equal(app.guard.confirmNavigation(),false);complete();await Promise.all([first,second]);await app.ready();assert.equal(locks.at(-1),false);app.dispose();
});

test('unknown mandate creation keeps the exact payload/key and lock across404 before explicit same-request retry',async()=>{
  const locks=[],app=harness({props:{onSubmissionLocked:value=>locks.push(value)}});await app.ready();const original=app.handlers.request;
  app.handlers.request=(url,token,init)=>init?.method==='POST'?Promise.reject(new app.ApiRequestError(503,'Response lost')):original(url,token,init);
  await app.submit();const body=app.posts()[0].body;assert.equal(locks.at(-1),true);assert.ok(app.button('Retry same mandate'));assert.equal(app.button('Close').props.disabled,true);
  await app.click('Check mandate status');assert.equal(app.posts().length,1);assert.equal(locks.at(-1),true);assert.equal(app.guard.confirmNavigation(),false);
  app.handlers.request=original;const changed=fields();changed.fields[2]=['amount','9000'];await app.submit(changed);
  assert.equal(app.posts()[1].body,body);assert.equal(locks.at(-1),false);assert.match(text(app.render()),/Mandate saved/);app.dispose();
});

test('existing active mandate recovery is read-only and never reactivates or opens another create form',async()=>{
  const app=harness({existing:saved(draft,'ACTIVE')});await app.ready();assert.match(text(app.render()),/Active/);assert.equal(app.calls.length,1);assert.equal(app.posts().length,0);assert.equal(nodes(app.render()).some(node=>node.type==='form'),false);app.dispose();
});

test('lost mandate response can recover by lookup without another POST',async()=>{
  const locks=[],app=harness({props:{onSubmissionLocked:value=>locks.push(value)}});await app.ready();const original=app.handlers.request;
  app.handlers.request=(url,token,init)=>init?.method==='POST'?Promise.reject(new app.ApiRequestError(503,'Unknown')):original(url,token,init);await app.submit();
  app.handlers.request=async()=>saved(draft,'CANCELLED');await app.click('Check mandate status');assert.equal(app.posts().length,1);assert.equal(locks.at(-1),false);assert.match(text(app.render()),/Cancelled/);app.dispose();
});

test('failed or mismatched mandate lookups do not expose a new form or claim success',async()=>{
  for(const value of [new Error('Unavailable'),saved({...draft,applicationKey:'someone-else'}),{...saved(),STATUS:'FUTURE'}]){
    const app=harness();app.handlers.request=async()=>{if(value instanceof Error)throw value;return value;};await app.ready();
    assert.ok(app.button('Check mandate status'));assert.equal(nodes(app.render()).some(node=>node.type==='form'),false);assert.equal(app.posts().length,0);app.dispose();
  }
});

test('mismatched mandate POST details remain uncertain and an initial rejection permits correction',async()=>{
  const app=harness();await app.ready();const original=app.handlers.request;
  app.handlers.request=(url,token,init)=>init?.method==='POST'?saved({...draft,sourceAccountId:99}):original(url,token,init);await app.submit();
  assert.ok(app.button('Retry same mandate'));assert.doesNotMatch(text(app.render()),/Mandate saved/);app.dispose();
  const locks=[],rejected=harness({props:{onSubmissionLocked:value=>locks.push(value)}});await rejected.ready();const read=rejected.handlers.request;
  rejected.handlers.request=(url,token,init)=>init?.method==='POST'?Promise.reject(new rejected.ApiRequestError(400,'Correct the date')):read(url,token,init);await rejected.submit();
  assert.equal(locks.at(-1),false);assert.equal(rejected.button('Close').props.disabled,false);assert.equal(rejected.button('Check mandate status'),undefined);rejected.dispose();
});

test('old mandate prompts and malformed keys cannot mount another live form',async()=>{
  const app=harness({props:{active:false}});await app.ready();assert.match(text(app.render()),/Earlier mandate request/);assert.equal(nodes(app.render()).some(node=>node.type==='form'),false);app.dispose();
  const invalid=harness({props:{clientId:'bad/key'}});await invalid.ready();assert.equal(invalid.calls.length,0);assert.equal(invalid.api.chatMandateApplicationKey('a'.repeat(67)).length,80);assert.equal(invalid.api.chatMandateApplicationKey('a'.repeat(68)),null);invalid.dispose();
});

test('mandate form makes missing eligible account and failed payee loading visible',async()=>{
  const app=harness({accounts:[{...account,currencyCode:'USD'}]});await app.ready();assert.match(text(app.render()),/active INR savings or current account is required/);assert.equal(app.button('Create').props.disabled,true);await app.submit();assert.equal(app.posts().length,0);app.dispose();
  const failed=harness();const original=failed.handlers.request;failed.handlers.request=(url,token,init)=>url==='/beneficiaries'?Promise.reject(Error('Unavailable')):original(url,token,init);await failed.ready();assert.ok(failed.button('Retry payees'));assert.equal(failed.button('Create').props.disabled,true);failed.dispose();
});

test('account entry checks owned accounts before reusing eligibility, identity and submission controls',async()=>{
  const profile={id:'customer-1'},locks=[],app=harness({account:true,props:{profile,onSubmissionLocked:value=>locks.push(value)}});assert.equal(nodes(app.render()).some(node=>node.type==='account-application-form'),false);await app.ready();
  const form=nodes(app.render()).find(node=>node.type==='account-application-form');assert.equal(form.props.token,'owner-token');assert.equal(form.props.profile,profile);assert.deepEqual(form.props.accounts,[account]);assert.equal(form.props.initiallyOpen,true);assert.equal(form.props.embedded,true);
  form.props.onSubmissionLocked(true);assert.deepEqual(locks,[true]);assert.equal(app.posts().length,0);app.props.busy=true;assert.equal(nodes(app.render()).find(node=>node.type==='account-application-form').props.disabled,true);app.dispose();
});

test('historical account requests and failed ownership lookup never mount duplicate account forms',async()=>{
  const app=harness({account:true,props:{profile:{id:'customer-1'},active:false}});await app.ready();assert.equal(app.calls.length,0);assert.match(text(app.render()),/Earlier account opening request/);assert.equal(nodes(app.render()).some(node=>node.type==='account-application-form'),false);app.dispose();
  const failed=harness({account:true,props:{profile:{id:'customer-1'}}});failed.handlers.request=async()=>{throw Error('Account lookup failed');};await failed.ready();assert.match(text(failed.render()),/Account lookup failed/);assert.equal(nodes(failed.render()).some(node=>node.type==='account-application-form'),false);failed.dispose();
});
