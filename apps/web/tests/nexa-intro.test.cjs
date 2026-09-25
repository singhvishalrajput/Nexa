const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),ts=require('typescript');
function harness(){
 const slots=[],effects=[],timers=new Map();let cursor=0,now=0,id=0,tree;
 const hooks={
  useState(initial){const i=cursor++;if(!(i in slots))slots[i]=initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},
  useEffect(fn,deps){const i=cursor++;if(!slots[i]||deps.some((v,j)=>v!==slots[i].deps[j])){slots[i]?.cleanup?.();slots[i]={deps};effects.push(()=>slots[i].cleanup=fn());}}
 };
 const exports={},jsx=(type,props)=>({type,props});
 vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/NexaIntro.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{
  exports,require:name=>name==='preact/hooks'?hooks:name==='preact/jsx-runtime'?{jsx,jsxs:jsx}:name.includes('locale')?{t:s=>s}:{Modal:'Modal'},
  window:{setTimeout(fn,delay){timers.set(++id,{fn,at:now+delay});return id;},clearTimeout:id=>timers.delete(id)}
 });
 const render=()=>{cursor=0;tree=exports.NexaIntro({onClose(){}});while(effects.length)effects.shift()();};
 const root=()=>tree.props.children;
 const nodes=n=>!n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];
 render();
 return {
  get step(){return root().props.children[1].props.children.findIndex(n=>n.props['aria-current']==='step');},
  click(index){if(index!==undefined)root().props.children[1].props.children[index].props.onClick();root().props.onClick();render();},
  practice(){nodes(tree).find(n=>n.type==='button'&&n.props.class?.startsWith('nexa-intro-try')).props.onClick();root().props.onClick();render();},
  get text(){return JSON.stringify(tree);},
  key(){root().props.onKeyDown();render();},
  tick(ms){const end=now+ms;while(true){const pending=[...timers].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!pending)break;now=pending[1].at;timers.delete(pending[0]);pending[1].fn();render();}now=end;},
  unmount(){for(const slot of slots)slot?.cleanup?.();},get pending(){return timers.size;}
 };
}

test('chat tour demonstrates each exchange automatically and loops without awarding practice points',()=>{
 const tour=harness();assert.equal(tour.step,0);tour.tick(1800);assert.match(tour.text,/Of course/);tour.tick(2800);assert.match(tour.text,/You started a conversation/);assert.match(tour.text,/0 \/ 6 tried/);tour.tick(3400);assert.equal(tour.step,1);tour.tick(8000*5);assert.equal(tour.step,0);
});
test('manual practice earns one tick per chapter and resumes only after five idle seconds',()=>{
 const tour=harness();tour.click(3);tour.practice();assert.match(tour.text,/Confirm ₹1 to Asha/);tour.practice();assert.match(tour.text,/No money moved/);assert.match(tour.text,/1 \/ 6 tried/);tour.tick(4999);assert.equal(tour.step,3);tour.click();tour.tick(4999);assert.equal(tour.step,3);tour.tick(1);assert.equal(tour.step,3);tour.tick(3400);assert.equal(tour.step,4);
 tour.click(3);tour.practice();tour.practice();assert.match(tour.text,/1 \/ 6 tried/);
});
test('navigation resets the exchange, keyboard activity delays playback, and closing cancels it',()=>{
 const tour=harness();tour.practice();tour.click(2);assert.match(tour.text,/Send message/);tour.key();tour.tick(4000);tour.key();tour.tick(4999);assert.match(tour.text,/Send message/);tour.tick(1801);assert.match(tour.text,/Review payment/);tour.unmount();assert.equal(tour.pending,0);
});
