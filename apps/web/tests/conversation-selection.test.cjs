const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const nodes = n => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap(nodes) : [n, ...nodes(n.props?.children)];
async function harness({confirm=true, fail=()=>false}={}) {
  let cursor=0, tree, warning;
  const slots=[], effects=[], calls=[], pages=[];
  let records=Array.from({length:65}, (_,i)=>({id:String(i),title:`Chat ${i}`,createdAt:'2026-09-25'}));
  const jsx=(type,props)=>({type,props}), row=()=>{}, exports={};
  const hooks={
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=typeof initial==='function'?initial():initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},
    useRef(initial){const i=cursor++;return slots[i]||(slots[i]={current:initial});},
    useEffect(fn,deps){if(deps?.[0]==='fixture')effects.push(fn);},useLayoutEffect(){}
  };
  const api={listConversations:async(_,p=0)=>{pages.push(p);return records.slice(p*30,(p+1)*30);},loadTurns:async()=>[],deleteConversation:async(_,id)=>{calls.push(id);if(fail(id))throw Error('offline');records=records.filter(r=>r.id!==id);}};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/components/chat/ConversationWorkspace.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{
    exports,window:{matchMedia:()=>({matches:true}),confirm:message=>{warning=message;return confirm;}},
    require:n=>n==='preact/hooks'?hooks:n==='preact/jsx-runtime'?{jsx,jsxs:jsx}:n.endsWith('/conversations')?api:n.endsWith('/locale')?{t:x=>x,getLocale:()=> 'en-IN'}:n.endsWith('/auth')?{ApiRequestError:class extends Error{}}:n.endsWith('/useVoiceInput')?{useVoiceInput:()=>({phase:'idle'})}:n.endsWith('/useReadAloud')?{useReadAloud:()=>({speak(){},cancel(){}})}:n.endsWith('/useNavigationGuard')?{useNavigationGuard(){}}:n.endsWith('/ConversationHistoryItem')?{ConversationHistoryItem:row}:{}
  });
  const render=()=>{cursor=0;tree=exports.ConversationWorkspace({session:{accessToken:'fixture',user:{role:'CUSTOMER'}}});};
  render();effects[0]();await new Promise(resolve=>setImmediate(resolve));render();
  return {render,calls,pages,warning:()=>warning,rows:()=>nodes(tree).filter(n=>n.type===row),button:label=>nodes(tree).find(n=>n.type==='button'&&(n.props.children===label||(Array.isArray(n.props.children)&&n.props.children.includes(label))||n.props['aria-label']===label)),nodes:()=>nodes(tree)};
}
test('select-all fetches older pages and selects every conversation; individual items can be deselected',async()=>{
  const app=await harness();assert.equal(app.button('Select all conversations'),undefined);app.button('Select conversations').props.onClick();app.render();await app.button('Select all conversations').props.onClick();app.render();
  assert.deepEqual(app.pages,[0,0,1,2]);assert.equal(app.rows().filter(r=>r.props.selected).length,65);
  app.rows()[0].props.onToggleSelection();app.render();assert.equal(app.rows().filter(r=>r.props.selected).length,64);
  app.button('Cancel').props.onClick();app.render();assert.equal(app.rows().filter(r=>r.props.selected).length,0);
});
test('cancelled confirmation never deletes selected conversations',async()=>{
  const app=await harness({confirm:false});assert.equal(app.button('Select all conversations'),undefined);app.button('Select conversations').props.onClick();app.render();await app.button('Select all conversations').props.onClick();app.render();
  await app.button('Delete selected').props.onClick();assert.match(app.warning(),/\(65\).*cannot be undone/);assert.equal(app.calls.length,0);
});
test('delete-all keeps failed records selected and retries only those records',async()=>{
  let failing=true;const app=await harness({fail:id=>failing&&id==='2'});
  assert.equal(app.button('Select all conversations'),undefined);app.button('Select conversations').props.onClick();app.render();await app.button('Select all conversations').props.onClick();app.render();await app.button('Delete selected').props.onClick();app.render();
  assert.equal(app.calls.length,65);assert.deepEqual(app.rows().filter(r=>r.props.selected).map(r=>r.props.conversation.id),['2']);
  assert.ok(app.nodes().some(n=>n.props?.role==='alert'));failing=false;
  await app.button('Delete selected').props.onClick();app.render();assert.equal(app.calls.length,66);assert.equal(app.rows().length,0);
});
