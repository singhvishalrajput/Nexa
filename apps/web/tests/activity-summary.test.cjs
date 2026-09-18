const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
const {loadSource}=require('./source-loader.cjs');
const {summarizeActivity,minorUnitsDecimal}=loadSource('services/activity-summary.ts');
const content=loadSource('services/banking-content.ts');
const transaction=(extra={})=>({id:'test',accountId:'account-1',reference:'REF',type:'PAYMENT',merchantName:'Coffee shop',category:'Food',amount:'-12.50',currencyCode:'INR',status:'POSTED',occurredAt:'2026-09-18T10:00:00Z',...extra});
test('activity totals and category shares use exact completed amounts only',()=>{
 const result=summarizeActivity([transaction({amount:'-0.10'}),transaction({amount:'-0.20',category:'FOOD'}),transaction({amount:'100',category:'Salary'}),transaction({amount:'-99',status:'PENDING'}),transaction({amount:'-999',status:'FAILED'}),transaction({amount:'-1',status:'CANCELLED'})],'INR');
 assert.equal(result.incoming,10000n);assert.equal(result.outgoing,30n);assert.equal(result.categories.length,1);assert.equal(result.categories[0].units,30n);assert.equal(result.categories[0].percent,100);assert.equal(result.unsettled,3);assert.equal(minorUnitsDecimal(result.outgoing),'0.30');
});
test('compact category breakdown preserves every amount and handles missing categories',()=>{
 const rows=Array.from({length:6},(_,i)=>transaction({category:i===0?null:'Category '+i,amount:String(-(i+1))}));
 const summary=summarizeActivity(rows,'INR');assert.equal(summary.categories.length,4);assert.equal(summary.categories[3].category,'Other categories');assert.equal(summary.categories.reduce((sum,r)=>sum+r.units,0n),summary.outgoing);
 assert.equal(summarizeActivity([transaction({category:'  '})],'INR').categories[0].category,'Uncategorized');
});
test('summary does not mix currencies or turn invalid data into plausible totals',()=>{
 assert.equal(summarizeActivity([transaction({currencyCode:'USD'})],'INR'),null);
 for(const amount of ['bad',NaN,Infinity])assert.equal(summarizeActivity([transaction({amount})],'INR'),null);
 assert.equal(summarizeActivity([transaction({amount:'-999999999999999.99'})],'INR').outgoing,99999999999999999n);
 assert.equal(summarizeActivity([],'INR').categories.length,0);
 assert.equal(summarizeActivity([transaction({amount:0}),transaction({amount:10})],'INR').outgoing,0n);
});
function activityComponents(){
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/Activity.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,require:name=>name==='preact/jsx-runtime'?{jsx,jsxs:jsx}:name.endsWith('/banking-content')?content:name.endsWith('/activity-summary')?{summarizeActivity,minorUnitsDecimal}:name.endsWith('/locale')?{t:x=>x}:name.endsWith('/ui')?{Status:({value})=>jsx('span',{children:content.statusPresentation(value).label})}:{}});return exports;
}
const flatten=node=>!node||typeof node!=='object'?[]:Array.isArray(node)?node.flatMap(flatten):typeof node.type==='function'?flatten(node.type(node.props)):[node,...flatten(node.props?.children)];
const allText=node=>node==null?'':typeof node==='string'||typeof node==='number'?String(node):Array.isArray(node)?node.map(allText).join(' '):typeof node.type==='function'?allText(node.type(node.props)):allText(node.props?.children);
test('transaction rows distinguish received, spent, pending and failed amounts while retaining detail links',()=>{
 const {TransactionRow}=activityComponents();
 const received=TransactionRow({item:transaction({amount:42.50})});assert.match(allText(received),/\+ ₹42.50/);assert.match(allText(received),/Received/);
 const spent=TransactionRow({item:transaction()});assert.match(allText(spent),/− ₹12.50/);assert.match(allText(spent),/Spent/);
 for(const status of ['PENDING','FAILED']){const row=TransactionRow({item:transaction({status,id:'txn/1'})});assert.equal(row.props.href,'#/transactions/txn%2F1');assert.match(allText(row),/₹12.50/);assert.doesNotMatch(allText(row),/Spent|Received|−/);assert.match(allText(row),new RegExp(status==='PENDING'?'Pending':'Failed'));assert.ok(!flatten(row).some(n=>n.props.class==='activity-credit'));}
});
test('activity summary labels its sample and provides an honest empty spending state',()=>{
 const {ActivitySummary}=activityComponents();
 const tree=ActivitySummary({items:[transaction({amount:100})],currency:'INR',accountName:'Savings'});const text=allText(tree).replace(/\s+/g," ");assert.match(text,/Savings · Latest 1 transaction/);assert.match(text,/No completed spending/);assert.match(text,/Pending and failed payments are excluded/);assert.ok(!flatten(tree).some(n=>n.type==='oj-c-meter-bar'));
});
