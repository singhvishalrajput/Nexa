const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const nodes=n=>!n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];
test('loan decisions require a reason and explicit review; duplicate confirms are suppressed',async()=>{
 const slots=[];let cursor=0,tree,release;const calls=[],done=[];
 const hooks={useState(v){const i=cursor++;if(!(i in slots))slots[i]=v;return[slots[i],v=>slots[i]=v];},useRef(v){const i=cursor++;return slots[i]||(slots[i]={current:v});}};
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/AdminLoanQueue.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,require:n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n.endsWith('/auth')?{authenticatedRequest:async(p,t,o)=>{calls.push({p,t,body:JSON.parse(o.body)});await new Promise(r=>release=r);return {};}}:n.endsWith('/banking-content')?{formatMoney:v=>v}:n.endsWith('/useNavigationGuard')?{useNavigationGuard(){}}:{Modal:'modal',Detail:'detail'}});
 const render=()=>{cursor=0;tree=exports.LoanDecision({token:'admin',request:{PRODUCT_ID:'L-1',FULL_NAME:'Asha',PRINCIPAL_AMOUNT:'5000',INTEREST_RATE:'14.5'},close(){},done:m=>done.push(m)});};
 const button=text=>nodes(tree).find(n=>n.type==='button'&&(Array.isArray(n.props.children)?n.props.children.join(''):n.props.children)===text);
 render();assert.equal(button('Review decision').props.disabled,true);
 nodes(tree).find(n=>n.type==='textarea').props.onInput({currentTarget:{value:'Verified income'}});render();button('Review decision').props.onClick();render();assert.equal(calls.length,0);
 const confirm=button('Confirm approval').props.onClick,pending=confirm();await confirm();assert.equal(calls.length,1);assert.equal(calls[0].p,'/admin/loans/L-1/approve');assert.equal(calls[0].body.reason,'Verified income');release();await pending;assert.equal(done.length,1);
});
