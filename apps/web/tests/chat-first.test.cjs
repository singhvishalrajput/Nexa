const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const ts=require('typescript');
global.window={NEXA_API_BASE_URL:'http://localhost/api/v1'};
for(const extension of ['.ts','.tsx']) require.extensions[extension]=(module,filename)=>module._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,filename);
const {WorkflowCard}=require('../src/components/chat/WorkflowCard.tsx');
const {AssistantResponse}=require('../src/components/chat/AssistantResponse.tsx');
const {parseRoute}=require('../src/features/banking/utils.ts');
const proposal={version:1,id:'proposal',operation:'OWN_TRANSFER',status:'REVIEW',accountLabel:'Everyday',targetLabel:'Reserve',amount:'5000.00',currency:'INR',message:'Review this transfer',choices:[],expiresAt:new Date(Date.now()+60000).toISOString(),confirmationRequired:true,executionAvailable:true};
function nodes(node){if(!node || typeof node!=='object')return [];if(Array.isArray(node))return node.flatMap(nodes);return [node,...nodes(node.props?.children)];}
function buttons(node){return nodes(node).filter(n=>n.type==='button');}
const render=(extra={})=>WorkflowCard({workflow:proposal,active:true,busy:false,onAction:()=>{},...extra});
test('chat is the default landing route while supporting detail routes remain',()=>{assert.equal(parseRoute('').page,'assistant');assert.equal(parseRoute('#/accounts/2').id,'2');});
test('only an explicit confirmation emits the action-bound command',()=>{let sent;const card=render({onAction:c=>sent=c});assert.equal(sent,undefined);buttons(card).find(b=>b.props.children==='Confirm transfer').props.onClick();assert.deepEqual(sent,{actionId:'proposal',type:'CONFIRM',value:undefined});});
test('historical, unavailable, expired and processing proposals cannot be confirmed',()=>{for(const extra of [{active:false},{busy:true},{workflow:{...proposal,status:'UNAVAILABLE',executionAvailable:false}},{workflow:{...proposal,expiresAt:'2000-01-01T00:00:00Z'}}]){assert.ok(buttons(render(extra)).every(b=>b.props.disabled));}});
test('completion renders the actual transaction reference and has no action buttons',()=>{const card=render({workflow:{...proposal,status:'COMPLETED',reference:'TX-123'}});assert.equal(buttons(card).length,0);assert.ok(nodes(card).some(n=>n.type==='a'&&n.props.href==='#/transactions/TX-123'));});
test('structured choices emit IDs rather than labels or text parsing',()=>{let sent;const card=render({workflow:{...proposal,status:'COLLECTING',choices:[{id:'12',label:'Savings'}]},onAction:c=>sent=c});buttons(card)[0].props.onClick();assert.equal(sent.value,'12');assert.equal(sent.type,'SELECT');});
test('workflow response dispatch is based on the versioned contract',()=>{assert.equal(AssistantResponse({turn:{workflow:proposal},accessToken:'test'}).type,WorkflowCard);assert.equal(render({workflow:{...proposal,version:2}}).type,'p');});
