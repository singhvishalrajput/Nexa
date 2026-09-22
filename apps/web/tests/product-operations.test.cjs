const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const {loadSource}=require('./source-loader.cjs');
function nodes(n){return !n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];}
function setup(api,kind='mandates',status='ACTIVE',repayment={minimumAmount:10,maximumAmount:1000,interestAmount:2,principalOnly:false,regularEmi:10,remainingInstallments:6,finalDueDate:'2027-03-22',nextInstallment:{dueDate:'2026-10-22',principalAmount:8,interestAmount:2,totalAmount:10,status:'PENDING'}},productDetails={}){
 const slots=[];let cursor=0,serial=0;
 const hooks={useState(initial){let i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},useRef(initial){let i=cursor++;return slots[i]||(slots[i]={current:initial});}};
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/ProductOperations.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,crypto:{randomUUID:()=>`request-${++serial}`},require:n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n==='../../services/auth'?{authenticatedRequest:api}:n==='../../services/banking-content'?loadSource('services/banking-content.ts'):n==='./ui'?{Panel:'panel',useLoad:load=>({data:load.toString().includes('repayment-options')?repayment:null,reload(){}})}: {bankApi:{}}});
 let tree;const render=()=>{cursor=0;tree=exports.ProductOperations({token:'owner',kind,product:{id:'product-1',status,...productDetails},reload(){}});};render();
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

test('loans awaiting approval or rejected cannot be disbursed by the customer',()=>{
 for(const state of ['PENDING_APPROVAL','REJECTED','CREATED']){
  const app=setup(async()=>assert.fail('No request expected'),'loans',state);
  assert.equal(app.button('Accept approved loan and receive funds'),undefined);
 }
 const approved=setup(async()=>({}),'loans','APPROVED');assert.ok(approved.button('Accept approved loan and receive funds'));
});

test('loan review enforces server payment limits and shows interest and principal split',()=>{
 const app=setup(async()=>assert.fail('Review must not post'),'loans');
 for(const amount of ['9.99','1000.01','10.001','0']){
  app.amount(amount);assert.equal(app.button('Review repayment').props.disabled,true);
 }
 app.amount('25');assert.equal(app.button('Review repayment').props.disabled,false);
 app.button('Review repayment').props.onClick();app.render();
 const text=JSON.stringify(app.nodes());assert.match(text,/₹23/);assert.match(text,/₹2/);
 assert.match(text,/EMIs left/);assert.match(text,/22 Mar 2027/);
 app.amount('1000');assert.equal(app.button('Review repayment').props.disabled,false);
});

test('covered EMI allows small principal top-ups and unavailable limits block review',()=>{
 const app=setup(async()=>({}),'loans','ACTIVE',{minimumAmount:0.01,maximumAmount:500,interestAmount:0,principalOnly:true,regularEmi:100,remainingInstallments:6,finalDueDate:'2027-03-22'});
 app.amount('0.01');assert.equal(app.button('Review repayment').props.disabled,false);
 assert.match(JSON.stringify(app.nodes()),/Extra payments go to principal/);
 const unavailable=setup(async()=>assert.fail('No limits'),'loans','ACTIVE',null);
 unavailable.amount('25');assert.equal(unavailable.button('Review repayment').props.disabled,true);
});

test('final EMI shows scheduled interest separately from principal-only payoff',()=>{
 const calls=[];
 const app=setup(async(...args)=>calls.push(args),'loans','ACTIVE',{
  minimumAmount:0.01,maximumAmount:1706.62,interestAmount:0,principalOnly:true,
  regularEmi:3414.21,remainingInstallments:1,finalDueDate:'2026-11-22',
  nextInstallment:{dueDate:'2026-11-22',principalAmount:1706.62,interestAmount:20.62,totalAmount:1727.24,status:'PENDING'}
 },{outstanding:'1706.62',nextEmi:'1727.24',interestRate:'14.5'});
 const text=JSON.stringify(app.nodes());
 assert.match(text,/Close loan today/);assert.match(text,/Final EMI/);
 assert.match(text,/₹1,706.62/);assert.match(text,/₹1,727.24/);assert.match(text,/₹20.62/);
 assert.match(text,/Outstanding principal/);assert.match(text,/% ÷ 12 =/);
 const disclosure=app.nodes().find(n=>n.type==='details');assert.ok(disclosure);assert.equal(disclosure.props.open,undefined);
 app.button('Use payoff amount').props.onClick();app.render();
 assert.equal(app.nodes().find(n=>n.type==='input').props.value,'1706.62');
 assert.equal(calls.length,0);assert.equal(app.button('Confirm payment'),undefined);
 app.button('Review repayment').props.onClick();app.render();
 assert.ok(app.button('Confirm payment'));assert.equal(calls.length,0);
});
