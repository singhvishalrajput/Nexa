const {test}=require('node:test'), assert=require('node:assert/strict');
const fs=require('node:fs'), vm=require('node:vm'), ts=require('typescript');
const nodes=n=>!n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.action),...nodes(n.props?.children)];
const months=['2026-06','2026-07','2026-08'];
class FormDataFixture {
  constructor(form){this.entries=form?.entries ? [...form.entries] : [];}
  append(key,value){this.entries.push([key,value]);}
  get(key){return this.entries.find(([k])=>k===key)?.[1] ?? null;}
  getAll(key){return this.entries.filter(([k])=>k===key).map(([,v])=>v);}
}
function setup(api=async()=>({}), download=async()=>new Blob(['%PDF-data'])){
  let cursor=0; const slots=[], clicks=[];
  const hooks={
    useState(v){const i=cursor++;if(!(i in slots))slots[i]=typeof v==='function'?v():v;return[slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},
    useRef(v){const i=cursor++;return slots[i]||(slots[i]={current:v});}
  };
  const jsx=(type,props)=>({type,props}), slips={}, products={};
  const dependencies=n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:
    n==='../../services/auth'?{authenticatedRequest:api,authenticatedBlobRequest:download}:
    n==='./LoanSalarySlips'?slips:n==='./api'?{bankApi:{accounts:async()=>[]}}:
    n==='./ui'?{Panel:'panel',State:'state',useLoad:fn=>({data:fn.toString().includes('salary-slip-requirements')?months:[],reload(){}})}:{};
  for(const [file,exports] of [['LoanSalarySlips',slips],['ProductOperations',products]]){
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/'+file+'.tsx','utf8'),{
      compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}
    }).outputText,{
      exports,require:dependencies,FormData:FormDataFixture,Blob,Error,crypto:{randomUUID:()=> 'application-key'},
      URL:{createObjectURL:()=> 'blob:private',revokeObjectURL(){}},window:{setTimeout:fn=>fn()},
      document:{body:{appendChild(){}},createElement:()=>({click(){clicks.push('download');},remove(){}})}
    });
  }
  return{slips,products,clicks,render(component,props){cursor=0;return nodes(component(props));}};
}
const form=()=>new FormDataFixture({entries:[
  ['name','Education'],['account','12'],['amount','100000'],['tenure','12'],
  ...months.map(month=>['slip-'+month,{name:month+'.pdf',type:'application/pdf',size:100}])
]});
test('salary-slip fields require a separate upload for each of the three months',()=>{
  const app=setup(), tree=app.render(app.slips.SalarySlipFields,{months});
  const inputs=tree.filter(n=>n.type==='input');
  assert.equal(inputs.length,3);
  for(const input of inputs){assert.equal(input.props.type,'file');assert.equal(input.props.required,true);assert.match(input.props.accept,/application\/pdf/);}
  assert.match(JSON.stringify(tree),/June 2026/);assert.match(JSON.stringify(tree),/August 2026/);
});
test('salary-slip validation rejects missing, oversized and unsupported files',()=>{
  const {slips}=setup();
  for(const replacement of [null,{size:0,type:'application/pdf'},{size:5242881,type:'application/pdf'},{size:100,type:'text/html'}]){
    const data=form();data.entries=data.entries.filter(([k])=>k!=='slip-2026-07');
    if(replacement)data.append('slip-2026-07',replacement);
    assert.throws(()=>slips.appendSalarySlips(new FormDataFixture(),data,months));
  }
  const target=new FormDataFixture();slips.appendSalarySlips(target,form(),months);
  assert.deepEqual(target.getAll('months'),months);
  assert.equal(target.getAll('files').length,3);
});
test('loan form submits application JSON and all three salary files in one multipart request',async()=>{
  const calls=[],app=setup(async(path,token,request)=>{calls.push({path,body:request.body});return{};});
  const props={token:'owner',kind:'loans',reload(){}};
  let tree=app.render(app.products.ProductCreate,props);
  tree.find(n=>n.type==='button'&&n.props.children==='Request a loan').props.onClick();
  tree=app.render(app.products.ProductCreate,props);
  await tree.find(n=>n.type==='form').props.onSubmit({preventDefault(){},currentTarget:form()});
  assert.equal(calls.length,1);assert.equal(calls[0].path,'/loans');
  assert.deepEqual(calls[0].body.getAll('months'),months);
  assert.equal(calls[0].body.getAll('files').length,3);
  const application=JSON.parse(await calls[0].body.get('application').text());
  assert.equal(application.accountId,12);assert.equal(application.applicationKey,'application-key');
});
test('admin must download a slip before its verification checkbox is enabled',async()=>{
  const verified=[],app=setup(),props={token:'admin',loanId:'L-1',bundle:{requiredMonths:months,documents:[{id:'D-1',month:months[0],fileName:'June.pdf',size:100}]},verified:[],onVerify:ids=>verified.push(...ids)};
  let tree=app.render(app.slips.SalarySlipDocuments,props);
  assert.equal(tree.find(n=>n.type==='input').props.disabled,true);
  await tree.find(n=>n.type==='button').props.onClick();
  tree=app.render(app.slips.SalarySlipDocuments,props);
  assert.equal(tree.find(n=>n.type==='input').props.disabled,false);
  tree.find(n=>n.type==='input').props.onChange({currentTarget:{checked:true}});
  assert.deepEqual(verified,['D-1']);assert.equal(app.clicks.length,1);
});
test('failed private download does not allow verification',async()=>{
  const app=setup(undefined,async()=>{throw new Error('Forbidden');});
  const props={token:'admin',loanId:'L-1',bundle:{requiredMonths:months,documents:[{id:'D-1',month:months[0],fileName:'June.pdf',size:100}]},onVerify(){}};
  let tree=app.render(app.slips.SalarySlipDocuments,props);
  await tree.find(n=>n.type==='button').props.onClick();
  tree=app.render(app.slips.SalarySlipDocuments,props);
  assert.equal(tree.find(n=>n.type==='input').props.disabled,true);
  assert.ok(tree.some(n=>n.props?.role==='alert'));
});
