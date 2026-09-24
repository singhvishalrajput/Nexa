const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

function modalHarness() {
  const exports = {}, refs = [], effects = [], calls = [];
  let index = 0, ready;
  const pending = new Promise(resolve => { ready = resolve; });
  const jsx = (type, props) => ({type, props});
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/ui.tsx', 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText, {exports, document: {activeElement: {focus: () => calls.push('focus')}}, require: name => {
    if (name === 'preact/jsx-runtime') return {jsx, jsxs: jsx};
    if (name === 'preact/hooks') return {
      useRef: value => refs[index++] ||= {current: value},
      useEffect: effect => effects.push(effect)
    };
    if (name === 'ojs/ojcontext') return {getContext: () => ({getBusyContext: () => ({whenReady: () => pending})})};
    if (name.endsWith('/locale')) return {t: value => value};
    return {};
  }});
  return {calls, ready, render(props = {}) {
    index = 0;
    return exports.Modal({title: 'Review loan request', children: 'body', onClose: () => calls.push('close callback'), ...props});
  }, mount(tree) {
    tree.props.ref.current = {open: () => calls.push('open'), close: () => calls.push('close')};
    return effects.shift()();
  }};
}

test('admin overlays carry their own style scope and keep the accessible modal contract', async () => {
  const app = modalHarness(), tree = app.render({className: 'admin-dialog'});
  assert.equal(tree.type, 'oj-dialog');
  assert.equal(tree.props.class.trim(), 'bank-app bank-dialog admin-dialog');
  assert.equal(tree.props.dialogTitle, 'Review loan request');
  assert.equal(tree.props.modality, 'modal');
  assert.equal(tree.props.cancelBehavior, 'icon');
  assert.equal(tree.props.children.props.slot, 'body');
  const cleanup = app.mount(tree);
  app.ready(); await Promise.resolve();
  assert.deepEqual(app.calls, ['open']);
  tree.props.onojClose();
  assert.equal(app.calls.at(-1), 'close callback');
  cleanup();
  tree.props.onojClose();
  assert.deepEqual(app.calls, ['open', 'close callback', 'close', 'focus']);
});

test('busy dialogs cannot close and a pending dialog cannot open after unmount', async () => {
  const app = modalHarness(), tree = app.render({locked: true});
  assert.equal(tree.props.cancelBehavior, 'none');
  assert.ok(!tree.props.class.includes('admin-dialog'), 'Customer modals keep their existing scope');
  let prevented = false;
  tree.props.onojBeforeClose({preventDefault() { prevented = true; }});
  assert.equal(prevented, true);
  const cleanup = app.mount(tree);
  cleanup(); app.ready(); await Promise.resolve();
  assert.deepEqual(app.calls, ['close', 'focus']);
});

test('both admin editors opt into the overlay style scope', () => {
  for (const file of ['AdminApp', 'AdminLoanQueue']) {
    const source = fs.readFileSync(`src/features/banking/${file}.tsx`, 'utf8');
    assert.match(source, /<Modal[^>]*className="admin-dialog"/);
    assert.match(source, /class="admin-dialog-summary"/);
    assert.match(source, /class="admin-dialog-fields/);
  }
});

test('admin styles load last, bound dialog scrolling, and reuse the application palette', () => {
  const html = fs.readFileSync('src/index.html', 'utf8');
  assert.ok(html.indexOf('styles/admin.css') > html.indexOf('styles/experience.css'));
  assert.ok(html.indexOf('styles/admin.css') > html.indexOf('styles/banking-forms.css'));
  const css = fs.readFileSync('src/styles/admin.css', 'utf8');
  const tokens = new Set([...fs.readFileSync('src/styles/tokens.css', 'utf8').matchAll(/(--nexa-[\w-]+):/g)].map(m => m[1]));
  for (const [, token] of css.matchAll(/var\((--nexa-[\w-]+)/g)) assert.ok(tokens.has(token), token);
  assert.doesNotMatch(css, /#[\da-f]{3,8}\b|\brgba?\(|\bhsla?\(/i);
  assert.match(css, /\.bank-app\.bank-dialog\.admin-dialog\s*\{[^}]*max-height: calc\(100dvh - 32px\)/);
  assert.match(css, /\.admin-dialog \.oj-dialog-body\s*\{[^}]*overflow: auto/);
  assert.match(css, /\.loan-slip-check input\[type="checkbox"\]\s*\{[^}]*min-width: 18px/);
  assert.match(css, /\.admin-sections a\s*\{[^}]*flex: 0 0 auto/);
  assert.match(css, /\.bank-app\.experience-admin > \.experience-rail\s*\{[^}]*display: flex/);
});

test('visual administration fixture never accepts a write', () => {
  const handle = require('./admin-ui-fixtures.cjs');
  for (const method of ['POST', 'PUT', 'DELETE']) {
    let result;
    assert.equal(handle({method}, new URL('http://localhost/api/v1/admin/loans/fixture-loan/approve'), (body, status) => {result = {body, status};}), true);
    assert.equal(result.status, 405);
  }
});
