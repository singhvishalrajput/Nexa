// Isolated UI interaction fixtures only. No runtime API, document or cash simulation is installed.
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const {randomUUID} = require('node:crypto');

const appId = '03f9a6e1-b9d1-4ef8-a3af-c2be729cb800';
const docId = '41d25c2e-f297-4840-afb3-818a1c39b735';
const receiptId = '4f9f435d-93ec-4337-af08-2343c2a7863d';
const ready = {checkedAt:'2026-09-23T10:00:00Z',applicationsAvailable:true,identityDetailsAvailable:true,reviewsAvailable:true,
  cashReceiptAvailable:true,accountOpeningAvailable:true,cashRefundAvailable:true,blockers:[]};
const fixture = (status, overrides = {}) => ({
  id:appId,accountType:'SAVINGS',currencyCode:'INR',fullName:'Synthetic applicant',email:'fixture@example.test',
  phoneNumber:null,dateOfBirth:'1990-01-01',status,reviewDecision:'APPROVED',reviewReason:null,version:7,
  createdAt:'2026-09-23T10:00:00Z',updatedAt:'2026-09-23T10:00:00Z',submittedAt:null,reviewedAt:null,
  openedAt:null,endedAt:null,endReasonCode:null,accountId:null,openingAmount:'2500.00',identityType:'PAN',identityMasked:'••••234F',events:[],
  documents:[{id:docId,documentType:'PAN',mediaType:'application/pdf',byteSize:25,storageStatus:'AVAILABLE',
    safetyStatus:'CLEAN',reviewStatus:status==='PENDING_REVIEW'?'PENDING':'ACCEPTED',reviewReason:null,
    uploadedAt:'2026-09-23T10:00:00Z',reviewedAt:null}],
  receipts:['CASH_RECEIVED','REFUND_PENDING','REFUNDED','OPENED'].includes(status)?[{
    id:receiptId,receiptNumber:'ACTUAL-17',currencyCode:'INR',amount:'2500.00',
    status:status==='CASH_RECEIVED'?'RECEIVED':status==='OPENED'?'APPLIED':status,
    cashReceivedAt:'2026-09-23T10:00:00Z',recordedAt:'2026-09-23T10:00:00Z',allocatedAt:null,refundReason:null,refundedAt:null
  }]:[],...overrides
});
const nodes = node => !node || typeof node!=='object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node,...nodes(node.props?.children),...nodes(node.props?.action)];
const content = node => node==null || typeof node==='boolean' ? '' : typeof node!=='object' ? String(node)
  : Array.isArray(node) ? node.map(content).join('') : content(node.props?.children);
const settle = () => new Promise(resolve => setImmediate(resolve));

function harness(application, options={}) {
  const slots=[],setters=[],effects=[],calls=[],results=[],navigation=[],loads=[];
  let cursor=0,tree,changed=false,token='synthetic-admin-token';
  let readiness='readiness' in options?options.readiness:{...ready,...(options.enabled===false?{applicationsAvailable:false,identityDetailsAvailable:false,reviewsAvailable:false,cashReceiptAvailable:false,accountOpeningAvailable:false,cashRefundAvailable:false}: {})};
  let serverReadiness=readiness;
  let readinessState=options.readinessState||{loading:false,error:'',data:readiness};
  const window={NEXA_ACCOUNT_APPLICATIONS_ENABLED:options.browserFlag};
  class ApiRequestError extends Error { constructor(status,message){super(message);this.status=status;} }
  const hooks={
    useState(initial){const index=cursor++;if(!(index in slots))slots[index]=typeof initial==='function'?initial():initial;
      if(!setters[index])setters[index]=next=>{const value=typeof next==='function'?next(slots[index]):next;if(!Object.is(value,slots[index])){slots[index]=value;changed=true;}};
      return [slots[index],setters[index]];},
    useRef(initial){const index=cursor++;if(!(index in slots))slots[index]={current:initial};return slots[index];},
    useEffect(effect,deps){const index=cursor++,old=slots[index];if(!old||!deps||deps.some((value,i)=>value!==old.deps[i])){
      slots[index]={deps,cleanup:old?.cleanup};effects.push(()=>{old?.cleanup?.();slots[index].cleanup=effect();});}}
  };
  const jsx=(type,props)=>({type,props});
  const guard={useNavigationGuard:(dirty,pending)=>navigation.push({dirty,pending}),confirmNavigation:()=>true};
  let service,actionHook;
  function load(file){
    const exports={};
    const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,
      target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText;
    vm.runInNewContext(code,{exports,window,Error,TextEncoder,URLSearchParams,crypto:{randomUUID},require:name=>{
      if(name==='preact/hooks')return hooks;
      if(name==='preact/jsx-runtime')return {jsx,jsxs:jsx,Fragment:'fragment'};
      if(name.endsWith('/auth'))return {ApiRequestError,authenticatedRequest(){throw Error('Unexpected runtime request in isolated test');}};
      if(name.endsWith('/account-applications'))return service;
      if(name.endsWith('/useApplicationAction'))return actionHook;
      if(name.endsWith('/useNavigationGuard'))return guard;
      if(name.endsWith('/banking-content'))return {formatMoney:value=>'INR '+value,formatDate:value=>value};
      if(name==='./AccountApplicationShared')return {ApplicationRecord:'application-record',ApplicationStatus:'application-status'};
      if(name==='./ui')return {Panel:'panel',State:'state',PageHeading:'heading',useLoad:(load,deps)=>{
        const kind=deps.length===1?'readiness':deps.length===2?'detail':'queue';loads.push({load,deps,kind});
        if(kind==='readiness')return {...readinessState,reload:()=>{
          readinessState={loading:true,error:'',data:undefined};changed=true;
          return load().then(result=>{readiness=result;readinessState={loading:false,error:'',data:result};changed=true;})
            .catch(error=>{readiness=undefined;readinessState={loading:false,error:error.message,data:undefined};changed=true;});
        }};
        return options.loadState||{loading:false,error:'',data:kind==='detail'?application:{items:[],total:0,page:0,size:20},reload(){}};
      }};
      throw Error('Unexpected dependency '+name);
    }});
    return exports;
  }
  service=load('src/services/account-applications.ts');
  service.applicationApi=Object.fromEntries(['adminReadiness','listAdmin','getAdmin','revealIdentity','review','receiveCash','open','requestRefund','refund'].map(name=>[name,async(...args)=>{
    calls.push({name,args:JSON.parse(JSON.stringify(args))});
    if(name==='adminReadiness')return serverReadiness;
    return options.respond?options.respond(name,args,calls.length):name==='revealIdentity'?{identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'IN_PERSON_ORIGINAL'}:application;
  }]));
  actionHook=load('src/hooks/useApplicationAction.ts');
  const component=load('src/features/banking/AdminAccountApplications.tsx');
  function expand(node){if(node==null||typeof node!=='object')return node;if(Array.isArray(node))return node.map(expand);
    if(typeof node.type==='function')return expand(node.type(node.props));
    return {...node,props:{...node.props,children:expand(node.props?.children),action:expand(node.props?.action)}};}
  function render(){let attempts=0;do{cursor=0;changed=false;
    tree=options.queue||options.detail?component.AdminAccountApplications({token,...(options.detail?{applicationId:appId}:{})}):component.AdminApplicationActions({token,application,onResult:result=>results.push(result),readiness});
    tree=expand(tree);effects.splice(0).forEach(effect=>effect());
    if(++attempts>25)throw Error('Unstable admin harness render');}while(changed);return tree;
  }
  function button(label){return nodes(tree).find(node=>node.type==='button'&&content(node)===label);}
  function field(label){const wrapper=nodes(tree).find(node=>node.type==='label'&&content(node).startsWith(label));
    assert.ok(wrapper,'Missing field '+label);return nodes(wrapper).find(node=>['input','textarea','select'].includes(node.type));}
  function input(label,value){const element=field(label);const event={currentTarget:{value,checked:value}};
    (element.props.onInput||element.props.onChange)(event);render();}
  function press(label){const element=button(label);assert.ok(element,'Missing button '+label);
    if(element.props.onClick)return element.props.onClick();
    const form=nodes(tree).find(node=>node.type==='form'&&nodes(node).includes(element));
    assert.ok(form,'Missing form for '+label);return form.props.onSubmit({preventDefault(){}});}
  render();
  return {render,button,field,input,press,calls,results,navigation,loads,window,ApiRequestError,
    setReadiness(value){readiness=value;readinessState={loading:false,error:'',data:value};render();},
    setServerReadiness(value){serverReadiness=value;},setApplication(value){application=value;render();},setToken(value){token=value;render();},dispose(){slots.forEach(slot=>slot?.cleanup?.());},get tree(){return tree;}};
}

for(const status of ['PENDING_REVIEW','APPROVED_AWAITING_CASH','CASH_RECEIVED','REFUND_PENDING']){
  test('disabled server workflow cannot post '+status+' actions even through direct handlers',async()=>{
    const h=harness(fixture(status),{enabled:false});
    for(const element of nodes(h.tree).filter(node=>node.type==='button')){
      assert.equal(element.props.disabled,true);
      h.press(content(element));
    }
    await settle();assert.equal(h.calls.length,0);assert.equal(h.results.length,0);
  });
}

test('explicit in-person original check permits approval but approval does not call open or credit APIs',async()=>{
  const app=fixture('PENDING_REVIEW');app.documents[0].reviewStatus='ACCEPTED';
  const h=harness(app);
  h.input('Application review reason','Application reviewed and eligible for cash collection');
  h.input('I checked the original identity document in person',true);
  h.press('Save application decision');await settle();
  assert.deepEqual(h.calls.map(call=>call.name),['review']);assert.equal(h.calls[0].args[2].decision,'APPROVED');
});

for(const reason of ['Synthetic ABCDE0000F','Synthetic 0000 0000 0000','न'.repeat(180),'line one\nline two']){
  test('unsafe or over-byte-limit review reason is rejected before a POST: '+reason.slice(0,20),async()=>{
    const app=fixture('PENDING_REVIEW');app.documents[0].reviewStatus='ACCEPTED';
    const h=harness(app);h.input('I checked the original identity document in person',true);h.input('Application review reason',reason);
    assert.equal(h.button('Save application decision').props.disabled,true);
    h.press('Save application decision');await settle();assert.equal(h.calls.length,0);
  });
}

test('cash receipt uses the customer amount, generates its number on the server, and requires confirmation',async()=>{
  const h=harness(fixture('APPROVED_AWAITING_CASH'));
  assert.equal(h.field('Opening amount (INR)').props.value,'2500.00');
  assert.equal(h.field('Opening amount (INR)').props.readOnly,true);
  assert.equal(h.field('Receipt number').props.value,'Generated when deposit is recorded');
  assert.equal(h.field('Receipt number').props.readOnly,true);
  assert.equal(h.field('I physically received and counted this exact opening amount').props.checked,false);
  assert.equal(h.button('Record actual cash receipt').props.disabled,true);
  h.press('Record actual cash receipt');await settle();assert.equal(h.calls.length,0);
  h.input('I physically received and counted this exact opening amount',true);
  h.press('Record actual cash receipt');await settle();
  assert.equal(h.calls[0].name,'receiveCash');
  assert.deepEqual(Object.keys(h.calls[0].args[2]).sort(),['cashReceivedConfirmed','expectedVersion','requestKey']);
  assert.equal(h.calls[0].args[2].cashReceivedConfirmed,true);
});

test('a matching unused receipt is required before opening and success waits for server response',async()=>{
  let release;const app=fixture('CASH_RECEIVED');
  const h=harness(app,{respond:()=>new Promise(resolve=>release=resolve)});
  const initial=h.button('Create account from received cash').props.onClick;
  initial();initial();assert.equal(h.calls.length,1);assert.equal(h.results.length,0);
  h.render();assert.equal(h.button('Create account from received cash').props.disabled,true);
  release({...app,status:'OPENED',accountId:81,version:8});await settle();
  assert.equal(h.results[0].status,'OPENED');assert.equal(h.results[0].accountId,81);
  assert.equal(h.calls[0].name,'open');assert.deepEqual(Object.keys(h.calls[0].args[2]).sort(),['expectedVersion','requestKey']);
});

for(const kind of ['missing','mismatch','duplicate','already-applied']){
  test('opening rejects '+kind+' receipt without a POST',async()=>{
    const app=fixture('CASH_RECEIVED');
    if(kind==='missing')app.receipts=[];
    if(kind==='mismatch')app.receipts[0].amount='3000.00';
    if(kind==='duplicate')app.receipts.push({...app.receipts[0],id:randomUUID()});
    if(kind==='already-applied')app.receipts[0].status='APPLIED';
    const h=harness(app);assert.equal(h.button('Create account from received cash').props.disabled,true);
    h.press('Create account from received cash');await settle();assert.equal(h.calls.length,0);
  });
}

test('refund request is separate from an explicitly acknowledged actual full cash return',async()=>{
  const request=harness(fixture('CASH_RECEIVED'));
  request.input('Reason for cash refund','Applicant withdrew; return unallocated funds');
  request.press('Request full cash refund');await settle();
  assert.equal(request.calls[0].name,'requestRefund');assert.equal(request.button('Confirm actual cash return'),undefined);
  const refund=harness(fixture('REFUND_PENDING'));
  assert.equal(refund.field('I physically returned the full recorded cash amount').props.checked,false);
  assert.equal(refund.button('Confirm actual cash return').props.disabled,true);
  refund.press('Confirm actual cash return');await settle();assert.equal(refund.calls.length,0);
  refund.input('I physically returned the full recorded cash amount',true);
  refund.press('Confirm actual cash return');await settle();
  assert.equal(refund.calls[0].name,'refund');assert.equal(refund.calls[0].args[2].cashReturnedConfirmed,true);
});

test('uncertain cash outcome freezes new actions and retries the original key and version',async()=>{
  const app=fixture('APPROVED_AWAITING_CASH');
  const h=harness(app,{respond:(_name,_args,count)=>count===1?Promise.reject(new Error('connection lost')):Promise.resolve({...app,status:'CASH_RECEIVED',version:8})});
  h.input('I physically received and counted this exact opening amount',true);
  h.press('Record actual cash receipt');await settle();h.render();
  assert.equal(h.results.length,0);assert.equal(h.button('Record actual cash receipt').props.disabled,true);
  assert.match(content(h.tree),/Do not collect or return cash again/);
  // Even synthetic manipulation of disabled inputs cannot replace the saved retry closure.
  h.input('I physically received and counted this exact opening amount',false);
  h.press('Record actual cash receipt');await settle();assert.equal(h.calls.length,1);
  h.press('Retry the same request');await settle();
  assert.equal(h.calls.length,2);assert.deepEqual(h.calls[1].args,h.calls[0].args);
  assert.equal(h.results.length,1);assert.equal(h.results[0].status,'CASH_RECEIVED');
});

test('server validation rejection never fabricates an opened account',async()=>{
  const app=fixture('CASH_RECEIVED');let h;
  h=harness(app,{respond:()=>Promise.reject(new h.ApiRequestError(409,'Application changed; refresh before opening.'))});
  h.press('Create account from received cash');await settle();h.render();
  assert.equal(h.results.length,0);assert.match(content(h.tree),/Application changed/);
  assert.equal(h.button('Retry the same request'),undefined);
});

for(const status of ['DRAFT','CHANGES_REQUESTED','OPENED','REJECTED','CANCELLED','REFUNDED']){
  test(status+' state exposes no admin financial mutation controls',()=>{
    const h=harness(fixture(status));assert.equal(nodes(h.tree).filter(node=>node.type==='button').length,0);assert.equal(h.calls.length,0);
  });
}

test('server readiness withdrawn after rendering blocks a stale action handler',async()=>{
  const h=harness(fixture('CASH_RECEIVED'));
  const stale=h.button('Create account from received cash').props.onClick;
  h.setReadiness({...ready,accountOpeningAvailable:false});stale();await settle();assert.equal(h.calls.length,0);
});

test('admin queue uses admin-only API, filters reset paging, and rows link to application details',async()=>{
  const app=fixture('PENDING_REVIEW');
  const h=harness(app,{queue:true,enabled:false,loadState:{loading:false,error:'',data:{items:[app],total:45,page:0,size:20},reload(){}}});
  await h.loads.at(-1).load();assert.equal(h.calls[0].name,'listAdmin');
  assert.equal(h.calls[0].args[1].status,'PENDING_REVIEW');assert.equal(h.calls[0].args[1].page,0);
  assert.ok(nodes(h.tree).some(node=>node.type==='a'&&node.props.href===`#/admin/applications/${appId}`));
  h.press('Next page');h.render();await h.loads.at(-1).load();assert.equal(h.calls[1].args[1].page,1);
  h.input('Application status','CASH_RECEIVED');await h.loads.at(-1).load();
  assert.equal(h.calls[2].args[1].page,0);assert.equal(h.calls[2].args[1].status,'CASH_RECEIVED');
  assert.equal(h.calls.some(call=>call.name==='requirements'),false);
});

test('admin readiness is an independent admin-only queue read, not a customer eligibility request',async()=>{
  const h=harness(fixture('PENDING_REVIEW'),{queue:true});
  const checks=h.loads.filter(load=>load.kind==='readiness'),queues=h.loads.filter(load=>load.kind==='queue');
  assert.ok(checks.length);assert.ok(queues.length);
  await checks.at(-1).load();await queues.at(-1).load();
  assert.deepEqual(h.calls.map(call=>call.name),['adminReadiness','listAdmin']);
  assert.equal(h.calls[0].args[0],'synthetic-admin-token');assert.ok(h.button('Check service readiness'));
});

test('unknown admin readiness fails closed for every mutation even when the obsolete browser flag is true',async()=>{
  for(const status of ['PENDING_REVIEW','APPROVED_AWAITING_CASH','CASH_RECEIVED','REFUND_PENDING']){
    const h=harness(fixture(status),{readiness:undefined,browserFlag:true});
    for(const button of nodes(h.tree).filter(node=>node.type==='button')){
      assert.equal(button.props.disabled,true);h.press(content(button));
    }
    await settle();assert.equal(h.calls.length,0);
  }
});

test('readiness loading/errors disable admin actions while read-only readiness recovery remains available',async()=>{
  for(const readinessState of [{loading:true,error:'',data:undefined},{loading:false,error:'Synthetic readiness failure',data:undefined}]){
    const h=harness(fixture('CASH_RECEIVED'),{detail:true,readinessState});
    assert.equal(h.button('Create account from received cash').props.disabled,true);
    h.press('Create account from received cash');await settle();assert.equal(h.calls.length,0);
    assert.equal(h.button('Check service readiness').props.disabled,readinessState.loading);
    if(readinessState.error)assert.match(content(h.tree),/Readiness could not be confirmed/);
  }
});

test('holding/cash unavailability blocks intake and funded opening even through valid direct handlers',async()=>{
  const readiness={...ready,cashReceiptAvailable:false,accountOpeningAvailable:false,blockers:['OPENING_HOLD_UNAVAILABLE']};
  const cash=harness(fixture('APPROVED_AWAITING_CASH'),{readiness});
  cash.input('I physically received and counted this exact opening amount',true);
  assert.equal(cash.button('Record actual cash receipt').props.disabled,true);cash.press('Record actual cash receipt');
  const opening=harness(fixture('CASH_RECEIVED'),{readiness});
  assert.equal(opening.button('Create account from received cash').props.disabled,true);opening.press('Create account from received cash');
  await settle();assert.equal(cash.calls.length,0);assert.equal(opening.calls.length,0);
  const approved=fixture('PENDING_REVIEW');approved.documents[0].reviewStatus='ACCEPTED';
  const review=harness(approved,{readiness});review.input('Application review reason','Private evidence reviewed; cash intake is currently unavailable');
  review.input('I checked the original identity document in person',true);review.press('Save application decision');await settle();assert.deepEqual(review.calls.map(call=>call.name),['review']);
});

test('identity encryption outage does not block an independently ready cash refund or invent physical return',async()=>{
  const readiness={...ready,reviewsAvailable:false,cashReceiptAvailable:false,accountOpeningAvailable:false,cashRefundAvailable:true,blockers:['IDENTITY_CONFIGURATION_UNAVAILABLE']};
  const request=harness(fixture('CASH_RECEIVED'),{readiness});
  assert.equal(request.button('Create account from received cash').props.disabled,true);
  request.input('Reason for cash refund','Applicant withdrew; return the recorded unallocated cash');request.press('Request full cash refund');await settle();
  assert.deepEqual(request.calls.map(call=>call.name),['requestRefund']);
  const refund=harness(fixture('REFUND_PENDING'),{readiness});
  assert.equal(refund.field('I physically returned the full recorded cash amount').props.checked,false);
  refund.press('Confirm actual cash return');await settle();assert.equal(refund.calls.length,0);
  refund.input('I physically returned the full recorded cash amount',true);refund.press('Confirm actual cash return');await settle();
  assert.deepEqual(refund.calls.map(call=>call.name),['refund']);assert.equal(refund.calls[0].args[2].cashReturnedConfirmed,true);
});

for(const decision of ['CHANGES_REQUESTED','REJECTED'])test('identity processing outage still permits reasoned '+decision+' without opening or cash',async()=>{
  const h=harness(fixture('PENDING_REVIEW'),{readiness:{...ready,identityDetailsAvailable:false,reviewsAvailable:false,
    cashReceiptAvailable:false,accountOpeningAvailable:false,cashRefundAvailable:false}});
  h.input('Application decision',decision);h.input('Application review reason','Private evidence cannot be accepted; please contact the bank');
  assert.equal(h.button('Save application decision').props.disabled,false);
  h.press('Save application decision');await settle();assert.deepEqual(h.calls.map(call=>call.name),['review']);
  assert.equal(h.calls[0].args[2].decision,decision);
});

test('unavailable cash infrastructure permits a refund request but never actual cash return or opening',async()=>{
  const readiness={...ready,identityDetailsAvailable:false,reviewsAvailable:false,cashReceiptAvailable:false,accountOpeningAvailable:false,cashRefundAvailable:false};
  const request=harness(fixture('CASH_RECEIVED'),{readiness});
  request.input('Reason for cash refund','Applicant withdrew; queue the full return when the bank is ready');
  assert.equal(request.button('Request full cash refund').props.disabled,false);
  request.press('Create account from received cash');request.press('Request full cash refund');await settle();
  assert.deepEqual(request.calls.map(call=>call.name),['requestRefund']);
  const refund=harness(fixture('REFUND_PENDING'),{readiness});
  refund.input('I physically returned the full recorded cash amount',true);
  assert.equal(refund.button('Confirm actual cash return').props.disabled,true);
  refund.press('Confirm actual cash return');await settle();assert.equal(refund.calls.length,0);
});

test('refund-request retry preserves the exact key and reason while cash readiness is unavailable',async()=>{
  let count=0;const app=fixture('CASH_RECEIVED');
  const h=harness(app,{readiness:{...ready,cashRefundAvailable:false},respond:()=>++count===1
    ?Promise.reject(new Error('Synthetic refund request response lost')):Promise.resolve({...app,status:'REFUND_PENDING',version:8})});
  h.input('Reason for cash refund','Return unallocated cash once the authorised process is ready');
  h.press('Request full cash refund');await settle();h.render();
  assert.equal(h.button('Retry the same request').props.disabled,false);
  h.press('Retry the same request');await settle();assert.equal(h.calls.length,2);
  assert.deepEqual(h.calls[1].args,h.calls[0].args);assert.equal(h.results[0].status,'REFUND_PENDING');
});

test('server-ready admin action does not require an obsolete hidden window flag',async()=>{
  const h=harness(fixture('CASH_RECEIVED'),{readiness:{...ready},browserFlag:false});
  h.press('Create account from received cash');await settle();assert.deepEqual(h.calls.map(call=>call.name),['open']);
});

test('read-only admin readiness refresh stays usable during unknown cash outcome and preserves exact retry',async()=>{
  const app=fixture('APPROVED_AWAITING_CASH');let attempts=0;
  const h=harness(app,{detail:true,respond:name=>{
    if(name==='receiveCash'&&++attempts===1)return Promise.reject(new Error('Synthetic lost cash response'));
    return {...app,status:'CASH_RECEIVED',version:8};
  }});
  h.input('I physically received and counted this exact opening amount',true);
  h.press('Record actual cash receipt');await settle();h.render();
  const original=h.calls.find(call=>call.name==='receiveCash');
  assert.ok(original);assert.equal(h.button('Refresh application').props.disabled,true);
  assert.equal(h.button('Check service readiness').props.disabled,false);
  h.setServerReadiness({...ready,cashReceiptAvailable:false,blockers:['OPENING_HOLD_UNAVAILABLE']});
  await h.press('Check service readiness');h.render();assert.equal(h.button('Retry the same request').props.disabled,true);
  h.press('Retry the same request');await settle();assert.equal(h.calls.filter(call=>call.name==='receiveCash').length,1);
  h.setServerReadiness({...ready});await h.press('Check service readiness');h.render();
  assert.equal(h.button('Retry the same request').props.disabled,false);
  h.press('Retry the same request');await settle();h.render();
  const attemptsSent=h.calls.filter(call=>call.name==='receiveCash');assert.equal(attemptsSent.length,2);
  assert.deepEqual(attemptsSent[1].args,original.args);
  const displayed=nodes(h.tree).find(node=>node.type==='application-record');assert.equal(displayed.props.application.status,'CASH_RECEIVED');
});

test('approval requires an explicit unchecked original-document acknowledgement, not mere syntax or reveal',async()=>{
  const h=harness(fixture('PENDING_REVIEW'));assert.equal(h.field('I checked the original identity document in person').props.checked,false);
  h.input('Application review reason','Original details match this applicant');h.press('Save application decision');await settle();assert.equal(h.calls.length,0);
  h.press('Reveal identifier for in-person check');await settle();h.render();assert.match(content(h.tree),/ABCPD1234F/);
  assert.equal(h.button('Save application decision').props.disabled,true);
  h.input('I checked the original identity document in person',true);h.press('Save application decision');await settle();h.render();
  assert.equal(h.calls.at(-1).name,'review');assert.equal(h.calls.at(-1).args[2].inPersonChecked,true);assert.doesNotMatch(content(h.tree),/ABCPD1234F/);
});
for(const decision of ['REJECTED','CHANGES_REQUESTED'])test('non-approval '+decision+' sends false original-check and no identity reveal',async()=>{
  const h=harness(fixture('PENDING_REVIEW'));h.input('I checked the original identity document in person',true);
  h.input('Application decision',decision);h.input('Application review reason','Applicant details need correction');h.press('Save application decision');await settle();
  assert.deepEqual(h.calls.map(c=>c.name),['review']);assert.equal(h.calls[0].args[2].inPersonChecked,false);
});
test('identity outage blocks reveal and approval but still permits correction decisions',async()=>{
  const h=harness(fixture('PENDING_REVIEW'),{readiness:{...ready,identityDetailsAvailable:false,reviewsAvailable:false}});
  h.input('Application review reason','Applicant details need correction');h.input('I checked the original identity document in person',true);
  h.press('Reveal identifier for in-person check');h.press('Save application decision');await settle();assert.equal(h.calls.length,0);
  h.input('Application decision','CHANGES_REQUESTED');h.press('Save application decision');await settle();assert.equal(h.calls[0].name,'review');
});
test('admin readiness uses identity prerequisites without file or scanning instructions',()=>{
  const h=harness(fixture('PENDING_REVIEW'),{queue:true,readiness:{...ready,identityDetailsAvailable:false,blockers:['IDENTITY_CONFIGURATION_UNAVAILABLE']}});
  assert.match(content(h.tree),/Private identity details: Unavailable/);assert.match(content(h.tree),/encryption configuration/);
  assert.match(content(h.tree),/Last server check/);assert.doesNotMatch(content(h.tree),/scanner|scanning|upload/i);
});
test('reveal is explicit, can be hidden, and never renders identifiers in links or hidden fields',async()=>{
  const h=harness(fixture('PENDING_REVIEW'));assert.equal(h.calls.length,0);assert.doesNotMatch(content(h.tree),/ABCPD1234F/);
  h.press('Reveal identifier for in-person check');await settle();h.render();assert.match(content(h.tree),/ABCPD1234F/);
  assert.deepEqual(h.calls.map(c=>c.name),['revealIdentity']);
  for(const node of nodes(h.tree))for(const prop of ['href','src','value','title'])assert.ok(!String(node.props?.[prop]||'').includes('ABCPD1234F'));
  h.press('Hide identifier');h.render();assert.doesNotMatch(content(h.tree),/ABCPD1234F/);
});
for(const change of ['application','status','version','token','readiness','unmount'])test('late identifier reveal is discarded after '+change,async()=>{
  let finish;const h=harness(fixture('PENDING_REVIEW'),{respond:()=>new Promise(resolve=>finish=resolve)});
  const handler=h.button('Reveal identifier for in-person check').props.onClick;handler();handler();h.render();assert.equal(h.calls.length,1);
  if(change==='application')h.setApplication(fixture('PENDING_REVIEW',{id:'13f9a6e1-b9d1-4ef8-a3af-c2be729cb800'}));
  if(change==='status')h.setApplication(fixture('REJECTED'));
  if(change==='version')h.setApplication(fixture('PENDING_REVIEW',{version:8}));
  if(change==='token')h.setToken('different-admin-session');
  if(change==='readiness')h.setReadiness({...ready,identityDetailsAvailable:false});
  if(change==='unmount')h.dispose();
  finish({identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'IN_PERSON_ORIGINAL'});await settle();
  if(change!=='unmount')h.render();assert.doesNotMatch(content(h.tree),/ABCPD1234F/);
});
test('cancelled reveal and mismatched response never expose raw identifier or raw failure text',async()=>{
  let finish;const h=harness(fixture('PENDING_REVIEW'),{respond:()=>new Promise(resolve=>finish=resolve)});
  h.press('Reveal identifier for in-person check');h.render();h.press('Cancel reveal');h.render();
  finish({identityType:'PAN',identityNumber:'ABCPD1234F',verificationMethod:'IN_PERSON_ORIGINAL'});await settle();h.render();assert.doesNotMatch(content(h.tree),/ABCPD1234F/);
  const mismatch=harness(fixture('PENDING_REVIEW'),{respond:async()=>({identityType:'PAN',identityNumber:'ABCPD9999F',verificationMethod:'IN_PERSON_ORIGINAL'})});
  mismatch.press('Reveal identifier for in-person check');await settle();mismatch.render();assert.doesNotMatch(content(mismatch.tree),/ABCPD9999F/);assert.match(content(mismatch.tree),/could not be revealed/);
  const fail=harness(fixture('PENDING_REVIEW'),{respond:async()=>{throw Error('Do not display ABCPD1234F');}});
  fail.press('Reveal identifier for in-person check');await settle();fail.render();assert.doesNotMatch(content(fail.tree),/ABCPD1234F/);
});
test('uncertain approval preserves the exact original-check attestation, reason, version and retry key',async()=>{
  let count=0;const h=harness(fixture('PENDING_REVIEW'),{respond:async()=>{if(++count===1)throw Error('Synthetic lost result');return fixture('APPROVED_AWAITING_CASH',{version:8});}});
  h.input('Application review reason','Original checked in person against applicant');h.input('I checked the original identity document in person',true);
  h.press('Save application decision');await settle();h.render();const first=h.calls[0];
  h.input('I checked the original identity document in person',false);h.input('Application review reason','Different unsent reason');
  h.press('Retry the same request');await settle();assert.deepEqual(h.calls[1].args,first.args);assert.equal(h.calls[1].args[2].inPersonChecked,true);
});
test('no document mutation or download control remains in the review workflow',()=>{
  const source=fs.readFileSync('src/features/banking/AdminAccountApplications.tsx','utf8');
  const shared=fs.readFileSync('src/features/banking/AccountApplicationShared.tsx','utf8');
  assert.doesNotMatch(source+shared,/applicationApi\.(upload|download|reviewDocument)|type="file"|Download private copy|Save document decision/);
});
