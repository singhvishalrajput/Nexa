const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const receipt = {id:'demo-1', operation:'PAY_BILL', targetId:'bill-1', amount:'100', currencyCode:'INR', status:'REVIEW', expiresAt:new Date(Date.now()+60000).toISOString(), simulated:true};
function nodes(n) { return !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap(nodes) : [n, ...nodes(n.props?.children)]; }
function setup(api, initial=receipt) {
  const slots=[]; let cursor=0;
  const hooks={useState(value){const i=cursor++;if(!(i in slots))slots[i]=value;return [slots[i],v=>slots[i]=v];},useRef(value){const i=cursor++;return slots[i]||(slots[i]={current:value});}};
  const exports={}; const jsx=(type,props)=>({type,props});
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/Showcase.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{
    exports, require:n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n==='./demo-api'?{demoApi:api}:n==='./ui'?{Detail:'detail',Modal:'modal',Panel:'panel',State:'state'}:{humanize:v=>v,formatMoney:v=>v}
  });
  let tree;
  const render=()=>{cursor=0;tree=exports.DemoConfirmation({token:'owner',receipt:initial,onClose(){}});return tree;};
  render();
  return {render,button:label=>nodes(tree).find(n=>n.type==='button'&&n.props.children===label),nodes:()=>nodes(tree)};
}
test('simulations require explicit confirmation and suppress duplicate clicks', async()=>{
  let calls=0,release;
  const app=setup({confirm:async(token,id)=>{calls++;assert.equal(id,receipt.id);await new Promise(r=>release=r);return {...receipt,status:'SIMULATED',reference:'DEMO-1'};}});
  assert.equal(calls,0);
  const click=app.button('Confirm request').props.onClick;
  const pending=click(); await click(); assert.equal(calls,1);
  release();await pending;app.render();
  assert.equal(app.button('Confirm request'),undefined);
  assert.ok(app.nodes().some(n=>n.props?.role==='status'));
});
test('a lost response retries the same review and cancellation never confirms',async()=>{
  const ids=[];
  const app=setup({confirm:async(t,id)=>{ids.push(id);throw Error('Connection lost');},cancel:async()=>({...receipt,status:'CANCELLED'})});
  await app.button('Confirm request').props.onClick();app.render();
  assert.ok(app.nodes().some(n=>n.props?.role==='alert'));
  await app.button('Confirm request').props.onClick();app.render();
  assert.deepEqual(ids,['demo-1','demo-1']);
  await app.button('Cancel').props.onClick();app.render();
  assert.equal(app.button('Confirm request'),undefined);
  assert.equal(ids.length,2);
});
test('expired demo reviews cannot be confirmed from the UI',()=>{
  const app=setup({}, {...receipt,expiresAt:'2000-01-01T00:00:00Z'});
  assert.equal(app.button('Confirm request').props.disabled,true);
});
