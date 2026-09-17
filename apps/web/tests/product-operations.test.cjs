const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function nodes(n){return !n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];}
function setup(api,kind='mandates',status='ACTIVE'){
 const slots=[];let cursor=0,serial=0;
 const hooks={useState(initial){let i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},useRef(initial){let i=cursor++;return slots[i]||(slots[i]={current:initial});}};
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/ProductOperations.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,crypto:{randomUUID:()=>`request-${++serial}`},require:n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n==='../../services/auth'?{authenticatedRequest:api}:n==='./ui'?{Panel:'panel',useLoad:()=>({data:null})}: {bankApi:{}}});
 let tree;const render=()=>{cursor=0;tree=exports.ProductOperations({token:'owner',kind,product:{id:'product-1',status},reload(){}});};render();
 return {render,nodes:()=>nodes(tree),button:text=>nodes(tree).find(n=>n.type==='button'&&n.props.children===text),amount:value=>{nodes(tree).find(n=>n.type==='input').props.onInput({currentTarget:{value}});render();}};
}

test('mandate execution requires review and duplicate confirmations are suppressed',async()=>{
 let release;const calls=[];const app=setup(async(path,token,options)=>{calls.push({path,body:JSON.parse(options.body)});await new Promise(r=>release=r);return {transactionId:'TX-1'};});
 app.amount('40');assert.equal(calls.length,0);app.button('Review mandate payment').props.onClick();app.render();assert.equal(calls.length,0);
 const confirm=app.button('Confirm payment').props.onClick;const pending=confirm();await confirm();assert.equal(calls.length,1);assert.equal(calls[0].path,'/mandates/product-1/execute');assert.equal(calls[0].body.amount,'40');release();await pending;app.render();assert.ok(app.nodes().some(n=>n.props?.role==='status'));
});
test('repayment retries keep the same request ID after a lost response',async()=>{
 const bodies=[];const app=setup(async(p,t,options)=>{bodies.push(JSON.parse(options.body));throw Error('Connection lost');},'loans');app.amount('25');app.button('Review repayment').props.onClick();app.render();await app.button('Confirm payment').props.onClick();app.render();await app.button('Confirm payment').props.onClick();assert.equal(bodies.length,2);assert.equal(bodies[0].requestId,bodies[1].requestId);
});
test('revocation calls the real mandate endpoint and closed mandates offer no execution',async()=>{
 const calls=[];const app=setup(async p=>{calls.push(p);return {};});await app.button('Revoke mandate').props.onClick();assert.deepEqual(calls,['/mandates/product-1/revoke']);const closed=setup(async()=>assert.fail('No request expected'),'mandates','CANCELLED');assert.equal(closed.button('Review mandate payment'),undefined);assert.equal(closed.button('Revoke mandate'),undefined);
});
