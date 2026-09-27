const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

function nodes(node) {
  return !node || typeof node !== 'object' ? [] : Array.isArray(node)
    ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
}

function setup(createBill, payeeState = {}) {
  const slots = [];
  let cursor = 0, tree, reloads = 0;
  const hooks = {
    useState(initial) {
      const index = cursor++;
      if (!(index in slots)) slots[index] = initial;
      return [slots[index], value => { slots[index] = value; }];
    },
    useRef(initial) {
      const index = cursor++;
      return slots[index] || (slots[index] = { current: initial });
    },
    useEffect() {}
  };
  const exports = {};
  const jsx = (type, props) => ({ type, props });
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/features/banking/ProductOperations.tsx', 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021,
      jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact' }
  }).outputText, {
    exports, Error,
    FormData: class { constructor(values) { this.values = values; } get(key) { return this.values[key] ?? null; } },
    require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? { jsx, jsxs: jsx }
      : name === './api' ? { bankApi: { createBill } } : name === './ui' ? { Panel: 'panel', useLoad: () => ({data: [], loading: false, error: '', reload() {}, ...payeeState}) } : {}
  });
  const render = () => {
    cursor = 0;
    tree = exports.BillCreate({ token: 'bill-owner', initiallyOpen: true, reload() { reloads++; } });
  };
  render();
  return {
    render, nodes: () => nodes(tree), reloads: () => reloads,
    submit: values => nodes(tree).find(node => node.type === 'form').props.onSubmit({
      preventDefault() {}, currentTarget: values
    })
  };
}

const validBill = { billerName: ' City Electricity ', customerNumber: ' 123456789 ', category: 'Electricity',
  amount: '1000.50', dueAt: '2026-10-05' };

test('bill creation sends entered values without a customer-set minimum and refreshes after success', async () => {
  const calls = [];
  let release;
  const app = setup(async (token, request) => {
    calls.push({ token, request });
    await new Promise(resolve => { release = resolve; });
    return { id: 'B-created' };
  });
  const pending = app.submit({ ...validBill, minimumAmount: '500' });
  await app.submit(validBill);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].token, 'bill-owner');
  assert.deepEqual(JSON.parse(JSON.stringify(calls[0].request)), {
    billerName: 'City Electricity', customerNumber: '123456789', category: 'Electricity', amount: '1000.50', dueAt: '2026-10-05'
  });
  release();
  await pending;
  app.render();
  assert.equal(app.reloads(), 1);
  assert.equal(app.nodes().find(node => node.type === 'form'), undefined);
});

test('bill validation errors remain visible in the open form and allow correction', async () => {
  const app = setup(async () => { throw new Error('Bill amount must be positive with at most two decimal places.'); });
  await app.submit({ ...validBill, amount: '-2000' });
  app.render();
  assert.match(app.nodes().find(node => node.props?.role === 'alert').props.children, /Bill amount/);
  assert.ok(app.nodes().find(node => node.type === 'form'));
  assert.equal(app.nodes().find(node => node.type === 'button' && node.props.type === 'submit').props.disabled, false);
  assert.equal(app.reloads(), 0);
});

test('bill form bounds agree with supported payments and database text fields', () => {
  const app = setup(async () => assert.fail('No request expected'));
  const field = name => app.nodes().find(node => node.type === 'input' && node.props.name === name).props;
  assert.equal(field('customerNumber').maxLength, 40);
  assert.equal(field('billerName').maxLength, 160);
  assert.ok(app.nodes().find(node => node.type === 'select' && node.props.name === 'category').props.required);
  assert.equal(field('amount').step, '0.01');
  assert.equal(field('amount').max, '9999999999999.99');
  assert.equal(app.nodes().find(node => node.props?.name === 'minimumAmount'), undefined);
  assert.equal(field('dueAt').max, '9999-12-31');
});

test('bill recipient selection links a saved Nexa payee while keeping the consumer reference separate', async () => {
  const calls = [];
  const app = setup(async (_token, request) => { calls.push(request); }, {data: [
    {id: 'payee-electric', status: 'ACTIVE', bankName: 'Nexa', displayName: 'City Electricity', accountNumberMasked: '•••• 8888'},
    {id: 'EP-other', status: 'ACTIVE', bankName: 'Nexa', transferType: 'EXTERNAL_BANK', displayName: 'External'},
    {id: 'inactive', status: 'BLOCKED', bankName: 'Nexa', displayName: 'Blocked'},
    {id: 'legacy-other', status: 'ACTIVE', bankName: 'Another bank', displayName: 'Imported'}
  ]});
  const select = app.nodes().find(node => node.type === 'select' && node.props.name === 'payeeId');
  assert.deepEqual(nodes(select).filter(node => node.type === 'option').map(node => node.props.value), ['', 'payee-electric']);
  select.props.onChange({currentTarget: {value: 'payee-electric'}});
  app.render();
  assert.equal(app.nodes().find(node => node.type === 'input' && node.props.name === 'billerName').props.value, 'City Electricity');
  await app.submit(validBill);
  assert.equal(calls[0].payeeId, 'payee-electric');
  assert.equal(calls[0].customerNumber, '123456789');
});

test('bill tracking remains available when saved recipients cannot load', async () => {
  const calls = [];
  const app = setup(async (_token, request) => { calls.push(request); }, {error: 'Unavailable'});
  assert.equal(app.nodes().find(node => node.type === 'select' && node.props.name === 'payeeId').props.disabled, true);
  assert.ok(app.nodes().some(node => node.type === 'p' && node.props.role === 'alert'));
  await app.submit(validBill);
  assert.equal(calls.length, 1);
  assert.equal('payeeId' in calls[0], false);
});
