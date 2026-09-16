const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const vm=require('node:vm');const ts=require('typescript');
const accounts=[{id:'1',displayName:'Savings',accountNumberMasked:'•••• 1234',availableBalance:'1000.00',currencyCode:'INR',status:'ACTIVE'},{id:'2',displayName:'Reserve',accountNumberMasked:'•••• 5678',availableBalance:'0',currencyCode:'INR',status:'ACTIVE'}];
const ready={id:'review-1',sourceAccountId:'1',sourceName:'Savings',sourceMasked:'•••• 1234',recipientName:'Recipient',destinationMasked:'•••• 5678',amount:'25.00',currencyCode:'INR',status:'READY',reference:null,expiresAt:new Date(Date.now()+300000).toISOString()};
function nodes(n){if(!n||typeof n!=='object')return [];if(Array.isArray(n))return n.flatMap(nodes);return [n,...nodes(n.props?.children)];}
function harness(overrides={}, saved=null){
  const slots=[];let cursor=0,effects=[],tree;const storage=new Map(saved?[['nexa-transfer-review:user',saved]]:[]);const calls=[];
  class ApiRequestError extends Error {constructor(status,message){super(message);this.status=status;}}
  const api={prepare:async(t,d)=>{calls.push(['prepare',d]);return ready;},confirm:async(t,id)=>{calls.push(['confirm',id]);return {...ready,status:'COMPLETED',reference:'TX-1'};},status:async()=>ready,...overrides};
  const hooks={useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},useRef(initial){const i=cursor++;return slots[i]||(slots[i]={current:initial});},useEffect(fn,deps){const i=cursor++;const old=slots[i];if(!old||deps.some((d,j)=>d!==old[j])){effects.push(fn);slots[i]=deps;}}};
  const exports={};const jsx=(type,props)=>({type,props});
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/MoneyTransfer.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,window:{sessionStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v),removeItem:k=>storage.delete(k)}},require:n=>n.endsWith('/locale')?require('./source-loader.cjs').loadSource('services/locale.ts'):n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n==='./money-transfers'?{moneyTransfers:api}:n.endsWith('/auth')?{ApiRequestError}:n.endsWith('useNavigationGuard')?{useNavigationGuard:()=>{}}:n==='./utils'?{validAmount:v=>/^(0|[1-9]\d{0,12})(\.\d{1,2})?$/.test(v)&&Number(v)>0}:n.endsWith('banking-content')?{formatMoney:v=>'₹'+v,moneyInMinorUnits:v=>/^\d+(\.\d{1,2})?$/.test(v)?BigInt(Math.round(Number(v)*100)):null}:n==='./ui'?{useLoad:()=>({data:accounts,loading:false,error:'',reload:()=>{}}),PageHeading:'heading',Panel:'panel',State:'state',Detail:'detail'}:{}});
  function render(){cursor=0;tree=exports.MoneyTransfer({token:'token',userId:'user'});const pending=effects;effects=[];pending.forEach(fn=>fn());return tree;}
  function input(i,value){nodes(tree).filter(n=>n.type==='input')[i].props.onInput({currentTarget:{value}});render();}
  function button(text){return nodes(tree).find(n=>n.type==='button'&&n.props.children===text);}
  render();render();return {render,input,button,calls,storage,ApiRequestError,api,nodes:()=>nodes(tree)};
}
async function reviewed(app){app.input(0,'123456789012');app.input(1,'25');await app.nodes().find(n=>n.type==='form').props.onSubmit({preventDefault(){}});app.render();}
test('review never sends money; double confirmations reuse a single locked request',async()=>{
  let release;const wait=new Promise(r=>release=r);let sends=0;
  const app=harness({confirm:async(t,id)=>{sends++;assert.equal(id,'review-1');await wait;return {...ready,status:'COMPLETED',reference:'TX-1'};}});
  await reviewed(app);assert.equal(sends,0);assert.equal(app.storage.get('nexa-transfer-review:user'),'review-1');
  const send=app.button('Send ₹25.00');const first=send.props.onClick();await send.props.onClick();assert.equal(sends,1);release();await first;app.render();assert.ok(app.button('Make another transfer'));
});
test('invalid amounts and excessive amounts cannot request review',async()=>{
  const app=harness();app.input(0,'123456789012');for(const amount of ['0','-1','1.001','1001']){app.input(1,amount);assert.equal(app.button('Review transfer').props.disabled,true);await app.nodes().find(n=>n.type==='form').props.onSubmit({preventDefault(){}});}assert.equal(app.calls.length,0);
});
test('uncertain confirmation preserves its ID and prevents editing into a duplicate transfer',async()=>{
  const app=harness();app.api.confirm=async()=>{throw new app.ApiRequestError(0,'lost response');};await reviewed(app);await app.button('Send ₹25.00').props.onClick();app.render();assert.ok(app.button('Check transfer status'));assert.equal(app.button('Edit details'),undefined);assert.equal(app.storage.get('nexa-transfer-review:user'),'review-1');
  let checked;app.api.confirm=async(t,id)=>{checked=id;return {...ready,status:'COMPLETED',reference:'TX-1'};};await app.button('Send this transfer again').props.onClick();app.render();assert.equal(checked,'review-1');assert.ok(app.button('Make another transfer'));
});
test('reopening a saved review checks its status before allowing a new transfer',async()=>{
  const app=harness({},'review-1');await new Promise(r=>setImmediate(r));app.render();assert.equal(app.button('Review transfer'),undefined);assert.equal(app.button('Edit details'),undefined);assert.ok(app.button('Send this transfer again'));
});
test('editing an unsubmitted review preserves the amount and recipient',async()=>{
  const app=harness();await reviewed(app);app.button('Edit details').props.onClick();app.render();assert.deepEqual(app.nodes().filter(n=>n.type==='input').map(n=>n.props.value),['123456789012','25']);assert.equal(app.storage.size,0);
});
