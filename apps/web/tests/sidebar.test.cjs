const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
for (const extension of ['.ts', '.tsx']) {
  require.extensions[extension] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText, filename);
}
const {SidebarNavigation, SidebarFooter} = require('../src/components/SidebarNavigation.tsx');
const {routes} = require('../src/features/banking/utils.ts');
const {setLocale, t} = require('../src/services/locale.ts');
function nodes(vnode) {
  if (Array.isArray(vnode)) return vnode.flatMap(nodes);
  if (!vnode || typeof vnode !== 'object') return [];
  if (typeof vnode.type === 'function') return nodes(vnode.type(vnode.props));
  return [vnode, ...nodes(vnode.props.children)];
}
const render = (page, admin = false) => nodes([SidebarNavigation({page, admin}), SidebarFooter({page, onLogout(){}})]);

test('shared navigation preserves all routes once and gates administrator navigation', () => {
  for (const admin of [false, true]) {
    const links = render('assistant', admin).filter(n => n.type === 'a');
    const destinations = links.map(n => n.props.href.slice(2));
    assert.equal(new Set(destinations).size, destinations.length);
    assert.deepEqual(destinations.sort(), routes.filter(r => admin || r !== 'operations').slice().sort());
  }
});

test('a secondary destination is disclosed and exactly one current page is announced', () => {
  for (const page of routes) {
    const tree = render(page, true);
    const current = tree.filter(n => n.props['aria-current'] === 'page');
    assert.equal(current.length, 1, page);
    assert.equal(current[0].props.href, '#/' + page);
    if (page === 'mandates' || page === 'operations') assert.equal(tree.find(n => n.type === 'details').props.open, true);
  }
});

test('Hindi navigation keeps route identifiers and localizes the disclosure and labels', () => {
  setLocale('hi-IN');
  try {
    const tree = render('accounts');
    assert.ok(tree.some(n => n.type === 'span' && n.props.children === t('More banking')));
    assert.notEqual(t('More banking'), 'More banking');
    const account = tree.find(n => n.type === 'a' && n.props.href === '#/accounts');
    assert.equal(account.props.children[1].props.children, t('Accounts'));
  } finally { setLocale('en-IN'); }
});
