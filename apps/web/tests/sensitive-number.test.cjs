const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

function harness(response) {
  const slots = []; let cursor = 0, effect, cleanup;
  const calls = [];
  const exports = {};
  const jsx = (type, props) => ({type, props});
  const dependencies = {
    'preact': {}, 'preact/jsx-runtime': {jsx, jsxs:jsx},
    'preact/hooks': {
      useState(initial) { const i = cursor++; if (!(i in slots)) slots[i] = initial; return [slots[i], v => {slots[i] = v;}]; },
      useRef(initial) { const i = cursor++; return slots[i] || (slots[i] = {current:initial}); },
      useEffect(fn) { if (!effect) effect = fn; }
    },
    '../../services/auth': {authenticatedRequest: async (...args) => { calls.push(args); return response; }},
    '../../services/banking-content': {safeMask: value => '•••• ' + value.slice(-4)}
  };
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/SensitiveNumber.tsx','utf8'), {
    compilerOptions: {module:ts.ModuleKind.CommonJS, target:ts.ScriptTarget.ES2021, jsx:ts.JsxEmit.ReactJSX, jsxImportSource:'preact'}
  }).outputText, {exports, encodeURIComponent, require:name => dependencies[name]});
  const render = () => { cursor = 0; return exports.SensitiveNumber({id:'owned-account',masked:'•••• 1234'}); };
  let tree = render(); cleanup = effect(); tree = render();
  return {calls, render, unmount:cleanup, button:() => render().props.children[0], click:async () => {
    let prevented=false, stopped=false;
    render().props.children[0].props.onClick({preventDefault(){prevented=true;},stopPropagation(){stopped=true;}});
    await new Promise(resolve => setImmediate(resolve));
    assert.ok(prevented && stopped);
  }};
}

test('masked numbers fetch only on click, advertise reveal, and hide again without persisting the number', async () => {
  const h = harness({number:'987654321234'});
  assert.equal(h.calls.length, 0);
  assert.equal(h.button().props.children, '•••• 1234');
  assert.match(h.button().props.title, /reveal the full number/);
  await h.click();
  assert.equal(h.calls[0][0], '/sensitive-numbers/accounts/owned-account');
  assert.equal(h.calls[0][2].cache, 'no-store');
  assert.equal(h.button().props.children, '987654321234');
  assert.equal(h.button().props['aria-pressed'], true);
  await h.click();
  assert.equal(h.button().props.children, '•••• 1234');
  assert.equal(h.calls.length, 1);
});

test('masked-only records stay masked and explain why the full number is unavailable', async () => {
  const h = harness({message:'The bank has not provided the full number.'});
  await h.click();
  assert.equal(h.button().props.children, '•••• 1234');
  assert.equal(h.render().props.children[1].props.role, 'status');
  assert.match(h.render().props.children[1].props.children, /not provided/);
});
