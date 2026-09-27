const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node)
  ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];

function mount(route) {
  const calls = [], exports = {}, jsx = (type, props) => ({type, props});
  const source = ts.transpileModule(fs.readFileSync('src/features/banking/AdminApp.tsx', 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021,
      jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}
  }).outputText;
  vm.runInNewContext(source, {exports, require: name => {
    if (name === 'preact/jsx-runtime') return {jsx, jsxs: jsx};
    if (name === 'preact/hooks') return {useEffect() {}, useState: value => [value, () => {}], useRef: value => ({current: value})};
    if (name.endsWith('/auth')) return {authenticatedRequest: async path => {calls.push(path); return [];}};
    if (name === './ui') return {PageHeading: 'heading', Panel: 'panel', State: 'state', useLoad: load => {void load(); return {data: [], loading: false, reload() {}};}};
    if (name === './AdminAnalytics') return {AdminAnalytics: 'analytics'};
    if (name === './AdminLoanQueue') return {AdminLoanQueue: 'loans'};
    if (name === './AdminCardQueue') return {AdminCardQueue: 'cards'};
    if (name === './AdminBankFunding') return {AdminBankFunding: 'funding'};
    if (name === './AdminAccountApplications') return {AdminAccountApplications: 'applications'};
    if (name.endsWith('/WorkspaceRail')) return {WorkspaceRail: 'rail'};
    return {};
  }});
  return {calls, exports, tree: exports.AdminApp({route, session: {accessToken: 'fixture', user: {id: 'admin-fixture', email: 'fixture@example.test'}, profile: {fullName: 'Fixture Admin'}}, signOut: async () => {}})};
}

test('admin home opens analytics without loading full account or loan directories', () => {
  for (const route of [{page: 'admin'}, {page: 'admin', section: 'analytics'}, {page: 'login'}]) {
    const result = mount(route);
    assert.ok(nodes(result.tree).some(node => node.type === 'analytics'));
    assert.deepEqual(result.calls, []);
    assert.equal(nodes(result.tree).find(node => node.type === 'rail').props.page, 'admin');
  }
});

test('admin directory and queues load only their own datasets and preserve navigation', () => {
  const directory = mount({page: 'admin', section: 'accounts'});
  assert.deepEqual(directory.calls, ['/admin/accounts']);
  assert.ok(!nodes(directory.tree).some(node => node.type === 'analytics'));
  assert.equal(nodes(directory.tree).find(node => node.type === 'rail').props.page, 'admin/accounts');
  const loans = mount({page: 'admin', section: 'loans'});
  assert.deepEqual(loans.calls, ['/admin/loans']);
  assert.ok(nodes(loans.tree).some(node => node.type === 'loans'));
  const applications = mount({page: 'admin', section: 'applications'});
  assert.deepEqual(applications.calls, []);
  assert.ok(nodes(applications.tree).some(node => node.type === 'applications'));
  const cards = mount({page: 'admin', section: 'cards'});
  assert.deepEqual(cards.calls, []);
  assert.ok(nodes(cards.tree).some(node => node.type === 'cards'));
  assert.equal(nodes(cards.tree).find(node => node.type === 'rail').props.page, 'admin/cards');
  const account = mount({page: 'admin', id: '42', section: 'transactions'});
  assert.deepEqual(account.calls, []);
  assert.equal(nodes(account.tree).find(node => node.type === 'rail').props.page, 'admin/accounts');
  assert.ok(nodes(account.tree).some(node => typeof node.type === 'function' && node.type.name === 'AccountWorkspace' && node.props.id === '42'));
});

test('bank funding navigation uses account-scoped recovery and does not load unrelated directories', () => {
  const result = mount({page: 'admin', section: 'bank-funding'});
  assert.deepEqual(result.calls, []);
  const funding = nodes(result.tree).find(node => node.type === 'funding');
  assert.equal(funding.props.userId, 'admin-fixture');
  assert.equal(nodes(result.tree).find(node => node.type === 'rail').props.page, 'admin/bank-funding');
});

test('workflow-owned system account management is hidden without blocking ordinary customer accounts', () => {
  const {isWorkflowManagedAccount} = mount({page: 'admin'}).exports;
  for (const number of ['NEXA-BANK-FUNDING', ' nexa-bank-funding ', 'NEXA-LOAN-CONTROL', 'NEXA-OPENING-HOLD', 'SYSTEM-CASH']) {
    assert.equal(isWorkflowManagedAccount({number, type: 'CLEARING'}), true);
  }
  assert.equal(isWorkflowManagedAccount({number: 'CASH-OTHER', type: 'CASH'}), true);
  assert.equal(isWorkflowManagedAccount({number: '1234567890', type: 'SAVINGS'}), false);
});
