const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const nodes=n=>!n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];
function harness(api){
 const slots=[];let cursor=0;const done=[];
 const hooks={useState(v){const i=cursor++;if(!(i in slots))slots[i]=typeof v==='function'?v():v;return[slots[i],v=>slots[i]=v];},useRef(v){const i=cursor++;return slots[i]||(slots[i]={current:v});}};
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/AdminApp.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,crypto:{randomUUID:()=> 'fixed-request-id'},require:n=>n.endsWith('/useNavigationGuard')?{useNavigationGuard(){}}:n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n.endsWith('/auth')?{authenticatedRequest:api}:n.endsWith('/banking-content')?{formatMoney:v=>v,formatDate:v=>v}:n==='./utils'?{validAmount:v=>/^\d+(\.\d{1,2})?$/.test(v)&&Number(v)>0}:{Modal:'modal',Detail:'detail',State:'state',useLoad:()=>({data:[],loading:false})}});
 let tree;const render=()=>{cursor=0;tree=exports.AccountEditor({token:'admin-token',account:{id:42,name:'Savings',number:'1234567890',type:'SAVINGS',category:'CUSTOMER',status:'ACTIVE',balance:'100',currency:'INR',version:3},close(){},done:m=>done.push(m)});};render();
 return{render,done,nodes:()=>nodes(tree),button:text=>nodes(tree).find(n=>n.type==='button'&&n.props.children===text),input(type,index,value){nodes(tree).filter(n=>n.type===type)[index].props.onInput({currentTarget:{value}});render();}};
}
test('admin edits send version and reason; financial adjustment waits for review',async()=>{
 const calls=[];const a=harness(async(p,t,o)=>{calls.push({p,t,body:JSON.parse(o.body)});return {};});
 assert.equal(a.button('Save account details').props.disabled,true);
 a.input('textarea',0,'Customer requested rename');a.input('input',0,'Household');
 await a.button('Save account details').props.onClick();assert.equal(calls[0].p,'/admin/accounts/42');assert.equal(calls[0].body.version,3);assert.equal(calls[0].body.name,'Household');assert.equal(calls[0].body.reason,'Customer requested rename');
 a.input('input',1,'25');a.button('Review adjustment').props.onClick();a.render();assert.equal(calls.length,1);assert.ok(a.button('Confirm adjustment'));
});
test('lost admin adjustment response freezes details and retries the same request',async()=>{
 const calls=[];const a=harness(async(p,t,o)=>{calls.push(JSON.parse(o.body));throw Object.assign(Error('Connection lost'),{status:0});});
 a.input('textarea',0,'Cash received');a.input('input',1,'20');a.button('Review adjustment').props.onClick();a.render();await a.button('Confirm adjustment').props.onClick();a.render();
 assert.equal(a.nodes().find(n=>n.type==='fieldset').props.disabled,true);
 await a.button('Retry same adjustment').props.onClick();assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(a.done.length,0);
});

test('account directory searches across customer and account fields and combines filters',()=>{
 const exports={};vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/AdminApp.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,require:()=>({})});
 const accounts=[{id:1,name:'Household',number:'123456',customerName:'Asha Rao',customerEmail:'asha@example.com',type:'SAVINGS',status:'ACTIVE'},{id:2,name:'Home loan',number:'456789',customerName:'Asha Rao',customerEmail:'asha@example.com',type:'LOAN',status:'ACTIVE'},{id:3,name:'Reserve',number:'900000',customerName:'Sam',type:'SAVINGS',status:'BLOCKED'}];
 const ids=(search,status='',type='')=>Array.from(exports.filterAccounts(accounts,search,status,type),a=>a.id);
 assert.deepEqual(ids(' ASHA household '),[1]);assert.deepEqual(ids('456'),[1,2]);assert.deepEqual(ids('asha@example.com','','LOAN'),[2]);assert.deepEqual(ids('','BLOCKED','SAVINGS'),[3]);assert.deepEqual(ids('Asha','BLOCKED'),[]);
});
