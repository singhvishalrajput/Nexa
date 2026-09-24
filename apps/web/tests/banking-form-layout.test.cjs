const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const nodes = n => !n || typeof n !== 'object' ? [] : Array.isArray(n) ? n.flatMap(nodes) : [n, ...nodes(n.props?.children)];

test('shared page headings omit the generic eyebrow and preserve explicit context', () => {
  const exports = {};
  const jsx = (type, props) => ({ type, props });
  const source = ts.transpileModule(fs.readFileSync('src/features/banking/ui.tsx', 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact' }
  }).outputText;
  vm.runInNewContext(source, { exports, require: name => name === 'preact/jsx-runtime' ? { jsx, jsxs: jsx } : name.endsWith('/locale') ? { t: value => value } : {} });
  const ordinary = nodes(exports.PageHeading({ title: 'Bills', description: 'Manage your bills.' }));
  assert.equal(ordinary.filter(n => n.props?.class === 'bank-eyebrow').length, 0);
  assert.equal(ordinary.find(n => n.type === 'h1').props.children, 'Bills');
  const specific = nodes(exports.PageHeading({ title: 'Estimate', eyebrow: 'Plan your borrowing' }));
  assert.equal(specific.find(n => n.props?.class === 'bank-eyebrow').props.children, 'Plan your borrowing');
});

test('compact service styles load after the legacy experience styles and use existing tokens', () => {
  const html = fs.readFileSync('src/index.html', 'utf8');
  assert.ok(html.indexOf('styles/banking-forms.css') > html.indexOf('styles/experience.css'));
  const source = fs.readFileSync('src/styles/banking-forms.css', 'utf8');
  const tokens = new Set([...fs.readFileSync('src/styles/tokens.css', 'utf8').matchAll(/--nexa-([\w-]+):/g)].map(match => match[1]));
  for (const match of source.matchAll(/var\(--nexa-([\w-]+)/g)) assert.ok(tokens.has(match[1]), 'Missing design token: ' + match[1]);
  assert.doesNotMatch(source, /#[\da-f]{3,8}\b|\brgba?\(|\bhsla?\(/i);
});
