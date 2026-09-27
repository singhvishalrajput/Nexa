const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const {loadSource}=require('./source-loader.cjs');
const content=loadSource('services/banking-content.ts');
const nodes=node=>!node||typeof node!=='object'?[]:Array.isArray(node)?node.flatMap(nodes):[node,...nodes(node.props?.children)];
const text=node=>node==null||typeof node==='boolean'?'':typeof node!=='object'?String(node):Array.isArray(node)?node.map(text).join(' '):text(node.props?.children);
const uuid='11111111-1111-4111-8111-111111111111';
const receipt={id:'SP-'+uuid,payee:'Synthetic recipient',amount:'125.00',currencyCode:'INR',dueAt:'2026-09-27',status:'READY',accountId:'1',reference:null,managed:true,sourceName:'Current',sourceMasked:'•••• 1234',payeeId:'internal',recipientName:'Synthetic recipient',destinationMasked:'•••• 5678',createdAt:'2026-09-26T10:00:00Z',expiresAt:'2026-09-26T10:05:00Z',authorizedAt:null,completedAt:null,failureReason:null,timezone:'Asia/Kolkata'};
const policy={businessDate:'2026-09-26',earliestDueDate:'2026-09-27',latestDueDate:'2027-09-26',timezone:'Asia/Kolkata',executionEnabled:true};
class ApiRequestError extends Error{constructor(status,message){super(message);this.status=status;}}
function harness(options={}){
  const slots=[],effects=[],calls=[],guards=[];let cursor=0,dirty=true,tree,reloads=0;
  const hooks={useState(initial){const at=cursor++;if(!(at in slots))slots[at]={value:typeof initial==='function'?initial():initial};return[slots[at].value,value=>{const next=typeof value==='function'?value(slots[at].value):value;if(!Object.is(next,slots[at].value)){slots[at].value=next;dirty=true;}}];},useRef(initial){const at=cursor++;return slots[at]||(slots[at]={current:initial});},useEffect(effect,deps){const at=cursor++,old=slots[at];if(!old||deps.some((value,i)=>value!==old.deps[i])){slots[at]={deps};effects.push(()=>{old?.cleanup?.();slots[at].cleanup=effect();});}}};
  const handlers={requirements:async()=>({...policy}),prepare:async(_token,request)=>({...receipt,id:'SP-'+request.requestKey}),confirm:async()=>({...receipt,status:'SCHEDULED',authorizedAt:'2026-09-26T10:01:00Z'}),cancel:async()=>({...receipt,status:'CANCELLED'}),detail:async()=>({...receipt}),...options.api};
  const api=Object.fromEntries(Object.keys(handlers).map(method=>[method,async(...args)=>{calls.push({method,args});return handlers[method](...args);}]));
  const bankApi={accounts:async()=>[{id:'1',displayName:'Current',accountType:'CURRENT',currencyCode:'INR',status:'ACTIVE',accountNumberMasked:'•••• 1234',availableBalance:'1.00'},{id:'2',displayName:'Closed',accountType:'SAVINGS',currencyCode:'INR',status:'CLOSED'}],products:async()=>[{id:'internal',displayName:'Internal recipient',bankName:'Nexa',status:'ACTIVE',transferType:'INTERNAL',accountNumberMasked:'•••• 5678'},{id:'external',displayName:'Other bank',bankName:'Nexa',status:'ACTIVE',transferType:'EXTERNAL_BANK'},{id:'inactive',displayName:'Inactive',bankName:'Nexa',status:'SUSPENDED'}]};
  const jsx=(type,props)=>typeof type==='function'?type(props||{}):{type,props:props||{}};let ui;
  function load(file){const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/'+file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,Error,crypto:{randomUUID:()=>uuid},require:name=>name==='preact/hooks'?hooks:name==='preact/jsx-runtime'?{jsx,jsxs:jsx,Fragment:'fragment'}:name.endsWith('/auth')?{ApiRequestError}:name.endsWith('/locale')?{t:value=>value,getLocale:()=> 'en-IN'}:name.endsWith('/banking-content')?content:name.endsWith('/useNavigationGuard')?{useNavigationGuard:(...args)=>guards.push(args)}:name==='./api'?{bankApi}:name==='./scheduled-payments'?{scheduledPayments:api}:name==='./ui'?ui:name==='./utils'?{validAmount:value=>/^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(value)&&Number(value)>0}:{}});return exports;}
  ui=load('ui.tsx');const component=load('ScheduledPayment.tsx')[options.details?'ScheduledPaymentDetails':'ScheduledPaymentCreate'];
  const props=options.details?{token:'fixture',id:receipt.id,onChanged(){reloads++;}}:{token:'fixture',initiallyOpen:true,reload(){reloads++;}};
  function render(){let count=0;do{dirty=false;cursor=0;tree=component(props);effects.splice(0).forEach(run=>run());if(++count>30)throw Error('Render did not settle');}while(dirty);return tree;}
  async function ready(){for(let i=0;i<5;i++){render();await new Promise(setImmediate);}return render();}
  const button=label=>nodes(render()).find(node=>node.type==='button'&&text(node)===label);
  const field=label=>nodes(nodes(render()).find(node=>node.type==='label'&&text(node).trim().startsWith(label))).find(node=>['input','select'].includes(node.type));
  return{calls,guards,handlers,props,render,ready,button,field,reloads:()=>reloads,text:()=>text(render()),set(label,value){const item=field(label);assert.ok(item,label);(item.props.onInput||item.props.onChange)({currentTarget:{value,checked:value}});render();},async click(label){const item=button(label);assert.ok(item,label);item.props.onClick();await ready();},async submit(){nodes(render()).find(node=>node.type==='form').props.onSubmit({preventDefault(){}});await ready();},dispose(){slots.forEach(slot=>slot?.cleanup?.());}};
}
async function reviewed(app){await app.ready();app.set('Saved Nexa recipient','internal');app.set('Amount','125');app.set('Payment date','2026-09-27');await app.submit();}

test('scheduling permits future funding but requires a valid future date and linked Nexa recipient',async()=>{
  const app=harness();await app.ready();assert.deepEqual(nodes(app.field('Saved Nexa recipient')).filter(node=>node.type==='option').map(node=>node.props.value),['','internal']);
  app.set('Saved Nexa recipient','internal');app.set('Amount','125');
  for(const date of ['','2026-09-25','2026-09-26','2027-09-27']){app.set('Payment date',date);assert.equal(app.button('Review scheduled payment').props.disabled,true);await app.submit();}
  assert.equal(app.calls.filter(call=>call.method==='prepare').length,0);
  app.set('Payment date','2026-09-27');assert.equal(app.button('Review scheduled payment').props.disabled,false);await app.submit();
  assert.equal(app.calls.filter(call=>call.method==='prepare').length,1);assert.equal(app.calls.filter(call=>call.method==='confirm').length,0);
  assert.match(app.text(),/No money is reserved now/);assert.match(app.text(),/Synthetic recipient/);assert.equal(app.button('Authorize scheduled payment').props.disabled,true);app.dispose();
});

test('explicit authorization and a submission lock prevent duplicate schedule confirmations',async()=>{
  let release;const app=harness({api:{confirm:async()=>{await new Promise(resolve=>release=resolve);return {...receipt,status:'SCHEDULED'};}}});await reviewed(app);
  await app.click('Authorize scheduled payment');assert.equal(app.calls.filter(call=>call.method==='confirm').length,0);
  app.set('I authorize this one-time',true);const submit=app.button('Authorize scheduled payment').props.onClick;submit();submit();await app.ready();
  assert.equal(app.calls.filter(call=>call.method==='confirm').length,1);assert.equal(app.guards.at(-1)[1],true);
  release();await app.ready();assert.match(app.text(),/Your payment is scheduled. No money has been transferred yet/);assert.equal(app.reloads(),1);app.dispose();
});

test('a lost prepare response recovers the exact original request key and unchanged details',async()=>{
  let attempts=0;const app=harness({api:{prepare:async()=>{if(++attempts===1)throw new ApiRequestError(0,'Lost response');return {...receipt};}}});
  await reviewed(app);assert.ok(app.button('Recover this schedule review'));assert.equal(app.guards.at(-1)[1],true);
  const first=app.calls.find(call=>call.method==='prepare');await app.click('Recover this schedule review');
  assert.deepEqual(app.calls.filter(call=>call.method==='prepare')[1].args,first.args);assert.ok(app.button('Authorize scheduled payment'));assert.equal(app.calls.filter(call=>call.method==='confirm').length,0);app.dispose();
});

test('lost authorization checks the same schedule before allowing further action',async()=>{
  const app=harness({api:{confirm:async()=>{throw new ApiRequestError(0,'Lost response');},detail:async()=>({...receipt,status:'SCHEDULED'})}});await reviewed(app);app.set('I authorize this one-time',true);await app.click('Authorize scheduled payment');
  assert.equal(app.button('Authorize scheduled payment').props.disabled,true);assert.equal(app.button('Create another schedule'),undefined);
  await app.click('Check schedule status');assert.equal(app.calls.filter(call=>call.method==='confirm').length,1);assert.equal(app.calls.filter(call=>call.method==='detail')[0].args[1],receipt.id);
  assert.match(app.text(),/Your payment is scheduled/);app.dispose();
});

test('checking an unchanged review shows accessible feedback without authorizing it',async()=>{
  const app=harness();await reviewed(app);const before=app.calls.length;
  await app.click('Check schedule status');
  assert.deepEqual(app.calls.slice(before).map(call=>call.method),['detail']);
  assert.equal(app.calls.at(-1).args[1],receipt.id);
  assert.equal(app.button('Authorize scheduled payment').props.disabled,true);
  const feedback=nodes(app.render()).find(node=>node.props?.role==='status'&&text(node).startsWith('Status checked '));
  assert.ok(feedback);assert.equal(feedback.props['aria-atomic'],'true');assert.match(text(feedback),/:\s+Ready\s*\.\s*$/);
  assert.match(app.text(),/does not authorize the payment or send it early/);app.dispose();
});

test('checking unchanged scheduled details acknowledges the GET without resetting the parent',async()=>{
  const app=harness({details:true,api:{detail:async()=>({...receipt,status:'SCHEDULED'})}});await app.ready();const before=app.calls.length;
  await app.click('Check schedule status');await app.click('Check schedule status');
  assert.ok(app.calls.length>before);assert.ok(app.calls.slice(before).every(call=>call.method==='detail'&&call.args[1]===receipt.id));
  assert.equal(app.reloads(),0);assert.match(app.text(),/Status checked .*:\s+Scheduled\s*\./);
  assert.match(app.text(),/automatic processing starts from midnight on the scheduled date \(India time\)/);
  assert.match(app.text(),/server must be running/);
  app.handlers.detail=async()=>{throw new ApiRequestError(0,'Status could not be loaded');};
  await app.click('Check schedule status');assert.match(app.text(),/Status could not be loaded/);assert.doesNotMatch(app.text(),/Status checked /);app.dispose();
});

test('historical reminders have no execution or cancellation controls',async()=>{
  const app=harness({details:true,api:{detail:async()=>({...receipt,id:'legacy',managed:false,status:'SCHEDULED'})}});await app.ready();
  assert.match(app.text(),/no automatic payment authorization and will not execute/);assert.equal(app.button('Authorize scheduled payment'),undefined);assert.equal(app.button('Cancel scheduled payment'),undefined);app.dispose();
});

test('scheduled details cancel the existing instruction and changing detail resets consent',async()=>{
  const app=harness({details:true});await app.ready();app.set('I authorize this one-time',true);assert.equal(app.button('Authorize scheduled payment').props.disabled,false);
  app.props.id='SP-22222222-2222-4222-8222-222222222222';app.handlers.detail=async()=>({...receipt,id:app.props.id});await app.ready();assert.equal(app.button('Authorize scheduled payment').props.disabled,true);
  app.handlers.detail=async()=>({...receipt,id:app.props.id,status:'SCHEDULED'});app.handlers.cancel=async()=>({...receipt,id:app.props.id,status:'CANCELLED'});
  await app.click('Check schedule status');assert.ok(app.button('Cancel scheduled payment'));await app.click('Cancel scheduled payment');
  assert.equal(app.calls.filter(call=>call.method==='cancel')[0].args[1],app.props.id);app.dispose();
});

test('schedule API uses the explicit authorization contract and rejects malformed managed results',async()=>{
  const calls=[];let response={...receipt};const exports={};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/scheduled-payments.ts','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021}}).outputText,{exports,require:()=>({ApiRequestError,authenticatedRequest:async(...args)=>{calls.push(args);return response;}})});
  await exports.scheduledPayments.confirm('token',receipt.id);assert.equal(calls[0][0],'/scheduled-payments/'+receipt.id+'/confirm');assert.deepEqual(JSON.parse(calls[0][2].body),{authorizationAccepted:true});
  for(const status of [undefined,'UNKNOWN','PAID']){response={...receipt,status};await assert.rejects(()=>exports.scheduledPayments.confirm('token',receipt.id),error=>error.status===0);}
  response={...receipt,managed:false,status:'HISTORICAL'};assert.equal((await exports.scheduledPayments.detail('token','legacy')).status,'HISTORICAL');
  const detail=calls.at(-1);assert.equal(detail[0],'/scheduled-payments/legacy');assert.equal(detail[2].method??'GET','GET');assert.equal(detail[2].cache,'no-store');assert.equal(detail[2].body,undefined);
});
