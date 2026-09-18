const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'), vm=require('node:vm'), ts=require('typescript');
function harness(reduced=false) {
  const slots=[],effects=[],timers=new Map(); let cursor=0,now=0,nextTimer=0,removed=false,fading=false,tree;
  const boot={remove(){removed=true;},classList:{add(name){if(name==='nexa-loading-ready') fading=true;}}};
  const hooks={
    useState(initial){const i=cursor++;if(!(i in slots))slots[i]=initial;return[slots[i],value=>slots[i]=value];},
    useCallback(fn){cursor++;return fn;},
    useEffect(fn,deps){const i=cursor++;if(!slots[i]||deps.some((v,j)=>v!==slots[i].deps[j])){slots[i]?.cleanup?.();slots[i]={deps};effects.push(()=>slots[i].cleanup=fn());}}
  };
  class Component {}
  const exports={},jsx=(type,props)=>({type,props});
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/components/app.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{
    exports,document:{getElementById:()=>removed?null:boot},performance:{now:()=>now},
    window:{matchMedia:()=>({matches:reduced}),setTimeout(fn,delay){const id=++nextTimer;timers.set(id,{fn,at:now+delay});return id;},clearTimeout:id=>timers.delete(id)},
    require:name=>name==='preact/hooks'?hooks:name==='preact'?{Component}:name==='preact/jsx-runtime'?{jsx,jsxs:jsx}:name==='ojs/ojvcomponent'?{registerCustomElement:(_,fn)=>fn}:name==='ojs/ojcontext'?{getPageContext:()=>({getBusyContext:()=>({applicationBootstrapComplete(){}})})}:{BankingApp:'BankingApp'}
  });
  const render=()=>{cursor=0;tree=exports.App();while(effects.length)effects.shift()();};
  const content=()=>tree.props.children;
  render();
  return {render,ready(){content().props.children.props.onReady();render();},get inert(){return content().props.inert;},get removed(){return removed;},get fading(){return fading;},
    tick(ms){const end=now+ms;while(true){const pending=[...timers].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!pending)break;now=pending[1].at;timers.delete(pending[0]);pending[1].fn();render();}now=end;},
    recover(){tree.type.prototype.componentDidCatch();},
    unmount(){for(const slot of slots)slot?.cleanup?.();},get pending(){return timers.size;}
  };
}
test('startup keeps the application inert until the session and logo sequence are ready',()=>{
 const app=harness();app.tick(1000);assert.equal(app.removed,false);assert.equal(app.inert,true);app.ready();app.tick(699);assert.equal(app.fading,false);app.tick(1);assert.equal(app.fading,true);assert.equal(app.inert,true);app.tick(240);assert.equal(app.removed,true);assert.equal(app.inert,false);
});
test('a slow session cannot disappear behind a timer and reduced motion skips the delay',()=>{
 const slow=harness();slow.tick(10000);assert.equal(slow.removed,false);slow.ready();slow.tick(240);assert.equal(slow.removed,true);
 const reduced=harness(true);reduced.ready();reduced.tick(0);assert.equal(reduced.removed,true);assert.equal(reduced.inert,false);
});
test('startup cancellation clears pending timers and render recovery removes the cover',()=>{
 const app=harness();app.ready();app.tick(1700);assert.ok(app.pending);app.unmount();assert.equal(app.pending,0);
 const failed=harness();failed.recover();assert.equal(failed.removed,true);
});
