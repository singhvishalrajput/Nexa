const {test} = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const nodes = n => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap(nodes) : [n, ...nodes(n.props?.children)];
const bundle = {requiredMonths: ['2026-06','2026-07','2026-08'], documents:
  ['2026-06','2026-07','2026-08'].map((month,i) => ({id: 'D-'+i, month, fileName: month+'.pdf', size: 100}))};
function setup(data = bundle) {
  const slots = []; let cursor = 0, tree, release;
  const calls = [], done = [];
  const hooks = {
    useState(v) {const i = cursor++; if (!(i in slots)) slots[i] = v; return [slots[i], v => slots[i] = typeof v === 'function' ? v(slots[i]) : v];},
    useRef(v) {const i = cursor++; return slots[i] || (slots[i] = {current:v});}
  };
  const jsx = (type,props) => ({type,props}), documents = {}, exports = {};
  const api = async (p,t,o) => {calls.push({p,t,body:JSON.parse(o.body)}); await new Promise(r => release = r); return {};};
  const dependencies = n => n === 'preact/hooks' ? hooks : n === 'preact/jsx-runtime' ? {jsx,jsxs:jsx} :
    n.endsWith('/auth') ? {authenticatedRequest:api} : n.endsWith('/banking-content') ? {formatMoney:v=>v} :
    n.endsWith('/useNavigationGuard') ? {useNavigationGuard(){}} :
    n === './LoanSalarySlips' ? documents : {Modal:'modal', Detail:'detail', State:'state', useLoad:()=>({data,loading:false,error:'',reload(){}})};
  for (const [file, target] of [['LoanSalarySlips',documents],['AdminLoanQueue',exports]]) {
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/'+file+'.tsx','utf8'), {
      compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}
    }).outputText, {exports:target, require:dependencies});
  }
  const render = () => {cursor=0; tree=exports.LoanDecision({token:'admin',request:{PRODUCT_ID:'L-1',FULL_NAME:'Asha',PRINCIPAL_AMOUNT:'5000',INTEREST_RATE:'14.5'},close(){},done:m=>done.push(m)});};
  const button = text => nodes(tree).find(n=>n.type==='button'&&(Array.isArray(n.props.children)?n.props.children.join(''):n.props.children)===text);
  render();
  return {render,button,calls,done,release:()=>release(), nodes:()=>nodes(tree),
    reason(value) {nodes(tree).find(n=>n.type==='textarea').props.onInput({currentTarget:{value}}); render();},
    verify(ids) {nodes(tree).find(n=>n.type===documents.SalarySlipDocuments).props.onVerify(ids); render();}};
}
test('approval requires three verified salary slips, a reason and explicit review; duplicate confirms are suppressed',async()=>{
  const app=setup();
  assert.equal(app.button('Review decision').props.disabled,true);
  app.reason('Verified income');
  assert.equal(app.button('Review decision').props.disabled,true);
  app.verify(['D-0','D-1']);
  assert.equal(app.button('Review decision').props.disabled,true);
  app.verify(['D-0','D-1','D-2']);
  assert.equal(app.button('Review decision').props.disabled,false);
  app.button('Review decision').props.onClick(); app.render();
  assert.equal(app.calls.length,0);
  const confirm=app.button('Confirm approval').props.onClick, pending=confirm();
  await confirm();
  assert.equal(app.calls.length,1);
  assert.equal(app.calls[0].p,'/admin/loans/L-1/approve');
  assert.deepEqual(Array.from(app.calls[0].body.verifiedSalarySlipIds),['D-0','D-1','D-2']);
  app.release(); await pending; assert.equal(app.done.length,1);
});
test('missing documents block approval but allow rejection with a reason',async()=>{
  const app=setup({...bundle,documents:[]}); app.reason('Missing salary proof');
  assert.equal(app.button('Review decision').props.disabled,true);
  app.nodes().find(n=>n.type==='select').props.onChange({currentTarget:{value:'reject'}}); app.render();
  assert.equal(app.button('Review decision').props.disabled,false);
  app.button('Review decision').props.onClick(); app.render();
  const pending=app.button('Confirm rejection').props.onClick();
  assert.equal(app.calls[0].p,'/admin/loans/L-1/reject');
  assert.equal(app.calls[0].body.verifiedSalarySlipIds,undefined);
  app.release(); await pending;
});
test('wrong-month documents cannot satisfy approval and changing verification clears review',()=>{
  const invalid=setup({...bundle,documents:bundle.documents.map(d=>({...d,month:'2026-05'}))});
  invalid.reason('Income checked'); invalid.verify(['D-0','D-1','D-2']);
  assert.equal(invalid.button('Review decision').props.disabled,true);
  const app=setup(); app.reason('Income checked'); app.verify(['D-0','D-1','D-2']);
  app.button('Review decision').props.onClick(); app.render();
  app.verify(['D-0','D-1']);
  assert.equal(app.button('Confirm approval'),undefined);
  assert.equal(app.button('Review decision').props.disabled,true);
});
