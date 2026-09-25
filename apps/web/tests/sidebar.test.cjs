const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
for (const extension of ['.ts', '.tsx']) {
  require.extensions[extension] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText, filename);
}
const {SidebarNavigation, SidebarFooter, primaryNavigation} = require('../src/components/SidebarNavigation.tsx');
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

test('secondary destinations stay visible and exactly one current page is announced', () => {
  for (const page of routes) {
    const tree = render(page, true);
    const current = tree.filter(n => n.props['aria-current'] === 'page');
    assert.equal(current.length, 1, page);
    assert.equal(current[0].props.href, '#/' + page);
    assert.ok(!tree.some(n => n.type === 'details' || n.type === 'summary'));
  }
});

test('overview sits directly below Chat in the primary sidebar navigation', () => {
  assert.deepEqual(primaryNavigation.map(item => item.page), ['assistant', 'overview', 'accounts', 'payments', 'cards', 'transactions']);
});

test('Hindi navigation keeps route identifiers and localizes labels', () => {
  setLocale('hi-IN');
  try {
    const tree = render('accounts');
    const account = tree.find(n => n.type === 'a' && n.props.href === '#/accounts');
    assert.equal(account.props.children[1].props.children, t('Accounts'));
  } finally { setLocale('en-IN'); }
});

test('customer workspace branding opens overview while banking routes remain available', () => {
  const {SidebarBrand} = require('../src/components/SidebarNavigation.tsx');
  const {WorkspaceRail} = require('../src/components/design/WorkspaceRail.tsx');
  assert.equal(nodes(SidebarBrand({})).find(n => n.type === 'a').props.href, '#/overview');
  for (const admin of [false, true]) {
    const links = nodes(WorkspaceRail({page: admin ? 'admin' : 'assistant', name: 'Test Customer', admin})).filter(n => n.type === 'a');
    assert.ok(links.some(n => n.props.href === (admin ? '#home' : '#/overview') && n.props['aria-label'] === (admin ? 'Nexa home' : 'Overview')));
    assert.ok(links.some(n => n.props.href === (admin ? '#/admin' : '#/assistant')));
  }
});

test('mobile navigation leaves scrolling to the bounded dialog body', () => {
  const sidebar = fs.readFileSync('src/styles/sidebar.css', 'utf8');
  const experience = fs.readFileSync('src/styles/experience.css', 'utf8');
  const menu = sidebar.match(/\.bank-mobile-nav\s*\{([^}]+)\}/)?.[1];
  assert.ok(menu, 'The mobile menu must override the desktop sidebar scroll container');
  assert.match(menu, /overflow:\s*visible/);
  assert.match(menu, /overscroll-behavior:\s*auto/);
  assert.match(menu, /scrollbar-gutter:\s*auto/);
  assert.match(experience, /\.bank-dialog \.oj-dialog-body\s*\{[^}]*max-height:75dvh;[^}]*overflow:auto/);
  const body = experience.match(/\.bank-dialog \.oj-dialog-body:has\(\.bank-mobile-nav\)\s*\{([^}]+)\}/)?.[1];
  assert.match(body, /overscroll-behavior-y:\s*contain/);
  assert.match(body, /touch-action:\s*pan-y pinch-zoom/);
  const desktop = sidebar.match(/\.nexa-sidebar\s*\{([^}]+)\}/)?.[1];
  assert.match(desktop, /overflow-y:\s*auto/);
  assert.match(desktop, /overscroll-behavior:\s*contain/);
});
