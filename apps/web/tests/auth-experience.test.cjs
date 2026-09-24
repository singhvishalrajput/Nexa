const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'), vm=require('node:vm'), ts=require('typescript');
const nodes=n=>!n||typeof n!=='object'?[]:Array.isArray(n)?n.flatMap(nodes):[n,...nodes(n.props?.children)];
function harness(mode, api) {
  const slots=[];let cursor=0,tree;const authenticated=[];
  const hooks={useEffect(){},useState(initial){const i=cursor++;if(!(i in slots))slots[i]=initial;return [slots[i],v=>slots[i]=typeof v==='function'?v(slots[i]):v];},useRef(value){const i=cursor++;return slots[i]||(slots[i]={current:value});}};
  class ApiRequestError extends Error {}
  const exports={},jsx=(type,props)=>({type,props});
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/components/auth/AuthPage.tsx','utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2021,jsx:ts.JsxEmit.ReactJSX,jsxImportSource:'preact'}}).outputText,{exports,require:name=>name==='preact/hooks'?hooks:name==='preact/jsx-runtime'?{jsx,jsxs:jsx}:name.endsWith('/auth')?{...api,ApiRequestError}:name.endsWith('/locale')?{t:x=>x}:name.endsWith('/Action')?{Action:'Action'}:{}});
  const render=()=>{cursor=0;tree=exports.AuthPage({mode,onBack(){},onSwitch(){},onAuthenticated:s=>authenticated.push(s)});};render();
  return {render,authenticated,nodes:()=>nodes(tree),set(label,value){nodes(tree).find(n=>(n.type==='oj-input-text'||n.type==='oj-input-password')&&(n.props.labelHint===label||n.props['aria-label']===label)).props.onrawValueChanged({detail:{value}});render();},submit:()=>nodes(tree).find(n=>n.type==='form').props.onSubmit({preventDefault(){}})};
}
test('JET raw-value events retain login values and suppress duplicate authentication',async()=>{
 let release,calls=0;const a=harness('login',{login:async(email,password)=>{calls++;assert.equal(email,'demo@example.com');assert.equal(password,'Password1!');await new Promise(r=>release=r);return {user:{id:'demo'}};}});
 a.set('Email address',' demo@example.com ');a.set('Password','Password1!');const request=a.submit();await a.submit();a.render();assert.equal(calls,1);assert.ok(a.nodes().filter(n=>n.type==='oj-input-text'||n.type==='oj-input-password').every(n=>n.props.disabled));release();await request;assert.equal(a.authenticated.length,1);
});
test('registration retains full name, optional phone and password complexity validation',async()=>{
 const calls=[];const a=harness('register',{register:async(...args)=>{calls.push(args);return {};}});
 a.set('Email address','demo@example.com');a.set('Full name',' Demo Customer ');a.set('Phone number (optional)','+91 9000000000');a.set('Password','abcdefgh');await a.submit();a.render();assert.equal(calls.length,0);assert.ok(a.nodes().some(n=>n.props.role==='alert'));
 a.set('Password','Password1!');await a.submit();assert.deepEqual(calls[0],['Demo Customer','demo@example.com','Password1!','+91 9000000000']);
});
test('failed sign-in preserves entries and allows a deliberate retry',async()=>{
 let calls=0;const a=harness('login',{login:async()=>{calls++;throw Error('offline');}});a.set('Email address','demo@example.com');a.set('Password','Password1!');await a.submit();a.render();assert.equal(a.authenticated.length,0);assert.ok(a.nodes().some(n=>n.props.role==='alert'));assert.equal(a.nodes().find(n=>n.props.labelHint==='Email address').props.value,'demo@example.com');await a.submit();assert.equal(calls,2);
});

test('password visibility keeps the accessible label and entered password',()=>{
 const a=harness('login',{});
 a.set('Password','Password1!');
 a.nodes().find(n=>n.type==='button'&&n.props['aria-label']==='Show password').props.onClick();a.render();
 const visible=a.nodes().find(n=>n.type==='oj-input-text'&&n.props['aria-label']==='Password');
 assert.equal(visible.props.value,'Password1!');
 a.nodes().find(n=>n.type==='button'&&n.props['aria-label']==='Hide password').props.onClick();a.render();
 assert.equal(a.nodes().find(n=>n.type==='oj-input-password').props.value,'Password1!');
});
