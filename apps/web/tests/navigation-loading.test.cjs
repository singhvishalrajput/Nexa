const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

function harness(file, browser = {}) {
  const slots = [];
  let cursor = 0;
  let effects = [];
  const hooks = {
    useState(initial) {
      const index = cursor++;
      if (!(index in slots)) slots[index] = typeof initial === 'function' ? initial() : initial;
      return [slots[index], value => { slots[index] = typeof value === 'function' ? value(slots[index]) : value; }];
    },
    useRef(initial) {
      const index = cursor++;
      return slots[index] || (slots[index] = {current: initial});
    },
    useEffect(effect, deps) {
      const index = cursor++;
      const old = slots[index];
      if (!old || deps.some((value, i) => value !== old.deps[i])) {
        effects.push(() => { old?.cleanup?.(); slots[index] = {deps, cleanup: effect()}; });
      }
    }
  };
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText, {exports, window: browser, require: name => name === 'preact/hooks' ? hooks : {}});
  return {
    api: exports,
    render(fn) { cursor = 0; return fn(exports); },
    flush() { const pending = effects; effects = []; pending.forEach(fn => fn()); },
    dispose() { slots.forEach(slot => slot?.cleanup?.()); }
  };
}

test('navigation preserves dirty drafts when leaving is declined and blocks pending requests', () => {
  let confirms = 0, alerts = 0;
  const app = harness('src/hooks/useNavigationGuard.ts', {confirm: () => { confirms++; return false; }, alert: () => { alerts++; }});
  app.render(api => api.useNavigationGuard(true)); app.flush();
  assert.equal(app.api.confirmNavigation(), false);
  assert.equal(confirms, 1);
  app.render(api => api.useNavigationGuard(true, true)); app.flush();
  assert.equal(app.api.confirmNavigation(), false);
  assert.equal(alerts, 1);
  assert.equal(confirms, 1);
  app.dispose();
  assert.equal(app.api.hasUnsavedWork(), false);
  assert.equal(app.api.confirmNavigation(), true);
});

test('navigation permits explicit discard and clean forms do not prompt', () => {
  let prompts = 0;
  const app = harness('src/hooks/useNavigationGuard.ts', {confirm: () => { prompts++; return true; }});
  app.render(api => api.useNavigationGuard(false)); app.flush();
  assert.equal(app.api.confirmNavigation(), true);
  assert.equal(prompts, 0);
  app.render(api => api.useNavigationGuard(true)); app.flush();
  assert.equal(app.api.confirmNavigation(), true);
  assert.equal(prompts, 1);
});

test('changed resource keys hide old data immediately and discard late responses', async () => {
  const app = harness('src/features/banking/ui.tsx');
  let resolveFirst, resolveSecond;
  const first = new Promise(resolve => { resolveFirst = resolve; });
  const second = new Promise(resolve => { resolveSecond = resolve; });
  app.render(api => api.useLoad(() => first, ['first'])); app.flush();
  await Promise.resolve();
  const changed = app.render(api => api.useLoad(() => second, ['second']));
  assert.equal(changed.loading, true);
  assert.equal(changed.data, undefined);
  app.flush();
  resolveSecond('second account');
  await new Promise(setImmediate);
  resolveFirst('stale account');
  await new Promise(setImmediate);
  assert.equal(app.render(api => api.useLoad(() => second, ['second'])).data, 'second account');
  const next = app.render(api => api.useLoad(() => Promise.resolve('third'), ['third']));
  assert.equal(next.data, undefined);
  assert.equal(next.loading, true);
});
