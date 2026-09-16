const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const vm=require('node:vm');const ts=require('typescript');
function nodes(n){if(!n||typeof n!=='object')return [];if(Array.isArray(n))return n.flatMap(nodes);return [n,...nodes(n.props?.children)];}
function harness(onDelete, extra={}) {
  let cursor=0,tree;const slots=[];const exports={};const jsx=(type,props)=>({type,props});
  const hooks={useState(initial){const i=cursor++;if(!(i in slots))slots[i]=initial;return [slots[i],v=>slots[i]=v];},useRef(initial){const i=cursor++;return slots[i]||(slots[i]={current:initial});}};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/components/chat/ConversationHistoryItem.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,require:n=>n.endsWith('/locale')?require('./source-loader.cjs').loadSource('services/locale.ts'):n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:{}});
  function render(){cursor=0;tree=exports.ConversationHistoryItem({conversation:{id:'chat',title:'balance.',createdAt:'2026-09-11'},current:true,disabled:false,hasDraft:true,onSelect:()=>{},onDelete,...extra});return tree;}
  render();return {render,button:label=>nodes(tree).find(n=>n.type==='button'&&(n.props.children===label || (label==='Delete' && n.props['aria-label']?.startsWith('Delete conversation:')))),nodes:()=>nodes(tree)};
}
test('deleting requires confirmation and keep conversation cancels it',async()=>{
  let deleted=0;const app=harness(async()=>deleted++);
  assert.equal(app.button('Confirm delete'),undefined);
  app.button('Delete').props.onClick();app.render();assert.equal(deleted,0);
  assert.ok(app.nodes().some(n=>n.props?.role==='group'));
  app.button('Keep conversation').props.onClick();app.render();assert.equal(app.button('Confirm delete'),undefined);assert.equal(deleted,0);
  app.button('Delete').props.onClick();app.render();await app.button('Confirm delete').props.onClick();assert.equal(deleted,1);
});
test('duplicate clicks do not send multiple deletion requests',async()=>{
  let release,count=0;const pending=new Promise(r=>release=r);
  const app=harness(async()=>{count++;await pending;});app.button('Delete').props.onClick();app.render();
  const button=app.button('Confirm delete');const first=button.props.onClick();await button.props.onClick();assert.equal(count,1);app.render();assert.equal(app.button('Deleting…').props.disabled,true);release();await first;
});
test('failed deletion remains visible with retry and cancellation controls',async()=>{
  let count=0;const app=harness(async()=>{count++;throw new Error('offline');});app.button('Delete').props.onClick();app.render();await app.button('Confirm delete').props.onClick();app.render();
  assert.ok(app.nodes().some(n=>n.props?.role==='alert'));assert.equal(app.button('Confirm delete').props.disabled,false);assert.ok(app.button('Keep conversation'));assert.equal(count,1);
});
