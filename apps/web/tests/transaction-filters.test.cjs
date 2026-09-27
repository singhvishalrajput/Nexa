const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);

// Run the real page and useLoad hook with synthetic responses and no browser/server.
function harness(options = {}) {
  const slots = [], effects = [], calls = [];
  let cursor = 0, dirty = true, tree;
  const hooks = {
    useState(initial) { const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial};
      return [slots[at].value, value => { const next = typeof value === 'function' ? value(slots[at].value) : value;
        if (!Object.is(next, slots[at].value)) { slots[at].value = next; dirty = true; } }]; },
    useEffect(effect, deps) { const at = cursor++, old = slots[at];
      if (!old || deps.some((value, i) => value !== old.deps[i])) { slots[at] = {deps}; effects.push(() => { old?.cleanup?.(); slots[at].cleanup = effect(); }); } }
  };
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  const api = {
    async transaction(token, id) { calls.push({method: 'transaction', id}); return options.transaction; },
    async transactions(token, accountId, filters) { calls.push({method: 'transactions', accountId, filters}); return {content: [], page: filters.page, totalPages: 0, totalElements: 0}; },
    async transactionCategories(token, accountId) { calls.push({method: 'categories', accountId});
      return options.categories ? options.categories(accountId) : accountId === '1' ? ['account_opening', 'food'] : ['utilities']; }
  };
  let ui;
  function load(file) {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/' + file, 'utf8'), {compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
      {exports, Error, require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
        : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content
        : name === './api' ? {bankApi: api} : name === './ui' ? ui : name === './Activity' ? {TransactionList: () => null} : {}});
    return exports;
  }
  ui = load('ui.tsx');
  const {TransactionsPage} = load('Accounts.tsx');
  const props = {token: 'synthetic', id: options.transaction?.id, accounts: [{id: '1', displayName: 'Savings', accountNumberMasked: '1234'}, {id: '2', displayName: 'Current', accountNumberMasked: '•••• 0221'}]};
  function render() { let attempts = 0; do { dirty = false; cursor = 0; tree = TransactionsPage(props); effects.splice(0).forEach(run => run());
    if (++attempts > 25) throw new Error('Page did not settle'); } while (dirty); return tree; }
  async function ready() { for (let i = 0; i < 4; i++) { render(); await new Promise(setImmediate); } return render(); }
  const select = label => nodes(render()).find(n => n.type === 'label' && text(n).startsWith(label)).props.children.find(n => n?.type === 'select');
  const change = (label, value) => { select(label).props.onChange({currentTarget: {value}}); render(); };
  return {calls, ready, render, select, change,
    submit() { nodes(render()).find(n => n.type === 'form').props.onSubmit({preventDefault() {}}); return ready(); },
    dispose() { slots.forEach(slot => slot.cleanup?.()); }};
}

test('categories come from the account history endpoint and submit their exact stored values', async () => {
  const app = harness(); await app.ready();
  const choices = nodes(app.select('Category')).filter(n => n.type === 'option');
  assert.deepEqual(choices.map(n => n.props.value), ['', 'account_opening', 'food']);
  assert.equal(text(choices[1]), 'Account opening');
  assert.match(text(app.render()), /Enter a shop or biller's name, or paste the reference shown in transaction details/);
  app.change('Category', 'account_opening'); await app.submit();
  assert.equal(app.calls.filter(c => c.method === 'transactions').at(-1).filters.category, 'account_opening');
  assert.equal(app.calls.filter(c => c.method === 'categories').length, 1, 'filtering must not restrict the available categories');
  app.dispose();
});

test('switching accounts clears the previous category and loads the new account categories', async () => {
  const app = harness(); await app.ready(); app.change('Category', 'food'); await app.submit();
  app.change('Account', '2');
  assert.equal(app.select('Category').props.disabled, true);
  await app.ready();
  assert.equal(app.select('Category').props.value, '');
  assert.deepEqual(nodes(app.select('Category')).filter(n => n.type === 'option').map(n => n.props.value), ['', 'utilities']);
  const request = app.calls.filter(c => c.method === 'transactions').at(-1);
  assert.equal(request.accountId, '2'); assert.equal(request.filters.category, ''); assert.equal(request.filters.page, 0);
  app.dispose();
});

test('utility category labels are readable while filters retain the stored category key', async () => {
  const app = harness({categories: async () => ['water', 'TV_DTH', 'other']}); await app.ready();
  const choices = nodes(app.select('Category')).filter(node => node.type === 'option');
  assert.deepEqual(choices.map(node => node.props.value), ['', 'water', 'TV_DTH', 'other']);
  assert.equal(text(choices[1]), 'Water bill');
  assert.equal(text(choices[2]), 'TV / DTH bill');
  assert.equal(text(choices[3]), 'Other');
  app.change('Category', 'water'); await app.submit();
  assert.equal(app.calls.filter(call => call.method === 'transactions').at(-1).filters.category, 'water');
  app.dispose();
});

test('transaction receipts clarify the owned account and category without renaming the merchant', async () => {
  for (const status of ['SUCCESS', 'PENDING', 'FAILED']) {
    const app = harness({transaction: {id: 'TXN-WATER', accountId: '2', reference: 'REF-WATER',
      type: 'TRANSFER', merchantName: 'Water', category: 'Water', amount: -75.25, currencyCode: 'INR',
      status, occurredAt: '2026-09-27T10:00:00Z'}});
    await app.ready();
    const tree = app.render();
    assert.equal(text(nodes(tree).find(node => node.type === 'h2')), 'Water');
    assert.match(text(tree), /Category Water bill/);
    assert.match(text(tree), /Your Nexa account •••• 0221/);
    assert.match(text(tree), /REF-WATER/);
    assert.equal(app.calls.filter(call => call.method === 'transaction').at(-1).id, 'TXN-WATER');
    assert.equal(app.calls.some(call => call.method === 'transactions'), false);
    app.dispose();
  }
});

test('category lookup failure can be retried without blocking transaction history', async () => {
  let fail = true;
  const app = harness({categories: async () => { if (fail) throw new Error('Temporarily unavailable'); return ['salary']; }});
  await app.ready();
  assert.match(text(app.render()), /Categories could not be loaded/);
  assert.match(text(app.render()), /No transactions match your selection/);
  fail = false;
  nodes(app.render()).find(n => n.type === 'button' && text(n) === 'Retry categories').props.onClick();
  await app.ready();
  assert.doesNotMatch(text(app.render()), /Categories could not be loaded/);
  assert.deepEqual(nodes(app.select('Category')).filter(n => n.type === 'option').map(n => n.props.value), ['', 'salary']);
  app.dispose();
});
