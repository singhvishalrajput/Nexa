const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const content = loadSource('services/banking-content.ts');
const nodes = node => !node || typeof node !== 'object' ? [] : Array.isArray(node) ? node.flatMap(nodes) : [node, ...nodes(node.props?.children)];
const text = node => node == null || typeof node === 'boolean' ? '' : typeof node !== 'object' ? String(node) : Array.isArray(node) ? node.map(text).join(' ') : text(node.props?.children);

function fixture(days = 1, empty = false) {
  const daily = Array.from({length: days === 1 ? 0 : days}, (_, index) => {
    const day = new Date('2026-09-26T00:00:00Z'); day.setUTCDate(day.getUTCDate() - days + 1 + index);
    const last = index === days - 1;
    return {date: day.toISOString().slice(0, 10), postedPayments: empty ? 0 : last ? 2 : index === 0 ? 1 : 0,
      paymentAmounts: empty ? [] : last ? [{currencyCode: 'INR', amount: '15.00'}, {currencyCode: 'USD', amount: '5.00'}] : index === 0 ? [{currencyCode: 'INR', amount: '10.00'}] : [],
      newCustomers: !empty && last ? 1 : 0, newAccounts: !empty && last ? 2 : 0};
  });
  const hourly = [];
  const endAt = '2026-09-26T11:47:30Z', end = Date.parse(endAt), start = end - 24 * 3600000;
  if (days === 1) {
    for (let cursor = start; cursor < end;) {
      const next = Math.min(end, (Math.floor((cursor + 19800000) / 3600000) + 1) * 3600000 - 19800000);
      const first = cursor === start, last = next === end;
      hourly.push({startAt: new Date(cursor).toISOString(), endAt: new Date(next).toISOString(),
        postedPayments: empty ? 0 : last ? 2 : first ? 1 : 0,
        paymentAmounts: empty ? [] : last ? [{currencyCode: 'INR', amount: '15.00'}, {currencyCode: 'USD', amount: '5.00'}] : first ? [{currencyCode: 'INR', amount: '10.00'}] : [],
        newCustomers: !empty && last ? 1 : 0, newAccounts: !empty && last ? 2 : 0});
      cursor = next;
    }
  }
  return {generatedAt: endAt, timezone: 'Asia/Kolkata',
    period: {days, granularity: days === 1 ? 'HOUR' : 'DAY', startDate: days === 1 ? '2026-09-25' : daily[0].date,
      endDate: '2026-09-26', startAt: days === 1 ? new Date(start).toISOString() : new Date(Date.parse(daily[0].date + 'T00:00:00Z') - 19800000).toISOString(), endAt},
    snapshot: {customers: empty ? 0 : 5, activeCustomers: empty ? 0 : 4, customerAccounts: empty ? 0 : 6, activeCustomerAccounts: empty ? 0 : 5,
      activeDepositBalances: empty ? [] : [{currencyCode: 'INR', amount: '1000.25'}, {currencyCode: 'USD', amount: '20.00'}],
      accountTypes: empty ? [] : [{key: 'SAVINGS', count: 4}, {key: 'CURRENT', count: 1}, {key: 'LOAN', count: 1}],
      accountStatuses: empty ? [] : [{key: 'ACTIVE', count: 5}, {key: 'BLOCKED', count: 1}],
      applicationStatuses: [{key: 'PENDING_REVIEW', count: empty ? 0 : 1}, {key: 'APPROVED_AWAITING_CASH', count: 0}, {key: 'CASH_RECEIVED', count: empty ? 0 : 2}, {key: 'REFUND_PENDING', count: 0}, {key: 'CHANGES_REQUESTED', count: empty ? 0 : 1}],
      applicationBacklog: empty ? 0 : 3, pendingLoans: empty ? 0 : 2},
    activity: {newCustomers: empty ? 0 : 1, newAccounts: empty ? 0 : 2, postedPayments: empty ? 0 : 3,
      paymentAmounts: empty ? [] : [{currencyCode: 'INR', amount: '25.00'}, {currencyCode: 'USD', amount: '5.00'}], daily, hourly}};
}

function harness(handler = async days => fixture(days)) {
  const slots = [], effects = [], calls = [];
  let cursor = 0, dirty = true, tree, ui;
  const hooks = {
    useState(initial) { const at = cursor++; if (!(at in slots)) slots[at] = {value: typeof initial === 'function' ? initial() : initial};
      return [slots[at].value, value => { const next = typeof value === 'function' ? value(slots[at].value) : value; if (!Object.is(next, slots[at].value)) { slots[at].value = next; dirty = true; } }]; },
    useRef(initial) { const at = cursor++; return slots[at] || (slots[at] = {current: initial}); },
    useEffect(effect, deps) { const at = cursor++, old = slots[at]; if (!old || deps.some((value, i) => value !== old.deps[i])) { slots[at] = {deps}; effects.push(() => { old?.cleanup?.(); slots[at].cleanup = effect(); }); } }
  };
  const jsx = (type, props) => typeof type === 'function' ? type(props || {}) : {type, props: props || {}};
  const request = async (path, token, options) => { calls.push({path, token, options}); return handler(Number(new URLSearchParams(path.split('?')[1]).get('days'))); };
  let api;
  const load = relative => {
    const exports = {};
    vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/' + relative, 'utf8'), {compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'}}).outputText,
    {exports, Error, Intl, Date, require: name => name === 'preact/hooks' ? hooks : name === 'preact/jsx-runtime' ? {jsx, jsxs: jsx, Fragment: 'fragment'}
      : name.endsWith('/locale') ? {t: value => value, getLocale: () => 'en-IN'} : name.endsWith('/banking-content') ? content
      : name.endsWith('/auth') ? {authenticatedRequest: request} : name.endsWith('/admin-analytics') ? api : name === './ui' ? ui : {}});
    return exports;
  };
  api = load('services/admin-analytics.ts'); ui = load('features/banking/ui.tsx');
  const Component = load('features/banking/AdminAnalytics.tsx').AdminAnalytics;
  function render() { let attempts = 0; do { dirty = false; cursor = 0; tree = Component({token: 'admin-test-token'}); effects.splice(0).forEach(run => run()); if (++attempts > 30) throw Error('Render did not settle'); } while (dirty); return tree; }
  async function ready() { for (let i = 0; i < 4; i++) { render(); await new Promise(setImmediate); } return render(); }
  const button = label => nodes(render()).find(node => node.type === 'button' && text(node).replace(/\s+/g, ' ').trim() === label);
  const select = label => nodes(nodes(render()).find(node => node.type === 'label' && text(node).startsWith(label))).find(node => node.type === 'select');
  return {calls, ready, render, button, select,
    async click(label) { const target = button(label); assert.ok(target, 'Missing button: ' + label); target.props.onClick(); return ready(); },
    change(label, value) { select(label).props.onChange({currentTarget: {value}}); return render(); },
    metric(label) { return nodes(render()).find(node => node.type === 'article' && nodes(node).some(child => child.type === 'h3' && text(child) === label)); },
    dispose() { slots.forEach(slot => slot.cleanup?.()); }};
}

test('analytics loads authenticated aggregates without placeholder values', async () => {
  const app = harness();
  assert.match(text(app.render()), /Loading the latest figures/);
  assert.equal(app.metric('Customers'), undefined);
  assert.equal(app.button('Refreshing…').props.disabled, true);
  await app.ready();
  assert.deepEqual(app.calls, [{path: '/admin/analytics?days=1', token: 'admin-test-token', options: undefined}]);
  assert.match(text(app.metric('Customers')), /Customers 5 4 active customers/);
  assert.match(text(app.metric('Customer accounts')), /Customer accounts 6 5 active accounts/);
  assert.match(text(app.render()), /Current totals, independent of the activity period/);
  assert.match(text(app.render()), /Activity in the last 24 hours/);
  assert.match(text(app.render()), /Ends at this server check/);
  assert.match(text(app.render()), /Asia\/Kolkata/);
  assert.equal(app.button('Last 24 hours').props['aria-pressed'], true);
  assert.ok(nodes(app.render()).some(node => node.type === 'a' && node.props.href === '#/admin/accounts'));
  assert.ok(nodes(app.render()).some(node => node.type === 'a' && node.props.href === '#/admin/applications'));
  assert.ok(nodes(app.render()).some(node => node.type === 'a' && node.props.href === '#/admin/loans'));
  app.dispose();
});

test('24 hour, 7, 30 and 90 day controls and refresh request real server aggregates', async () => {
  const app = harness(); await app.ready();
  await app.click('Last 7 days'); assert.equal(app.calls.at(-1).path, '/admin/analytics?days=7');
  assert.match(text(app.render()), /Activity over\s+7\s+days/);
  assert.match(text(app.render()), /Today is still in progress/);
  assert.equal(nodes(app.render()).filter(node => node.type === 'circle').length, 0);
  assert.equal(nodes(app.render()).filter(node => node.type === 'rect').length, 7);
  await app.click('Last 30 days'); assert.equal(app.calls.at(-1).path, '/admin/analytics?days=30');
  await app.click('Last 90 days'); assert.equal(app.calls.at(-1).path, '/admin/analytics?days=90');
  assert.match(text(app.render()), /Activity over\s+90\s+days/);
  await app.click('Refresh analytics'); assert.equal(app.calls.at(-1).path, '/admin/analytics?days=90');
  assert.equal(app.calls.length, 5); assert.equal(app.button('Last 90 days').props['aria-pressed'], true);
  await app.click('Last 24 hours'); assert.equal(app.calls.at(-1).path, '/admin/analytics?days=1');
  assert.match(text(app.render()), /Hourly posted payments/);
  app.dispose();
});

test('a slow previous-period response never overwrites the latest selected period', async () => {
  let finishOld;
  const app = harness(days => days === 1 ? new Promise(resolve => { finishOld = resolve; }) : Promise.resolve(fixture(days)));
  await app.ready(); await app.click('Last 7 days');
  assert.match(text(app.render()), /Activity over\s+7\s+days/);
  finishOld(fixture(1)); await app.ready();
  assert.match(text(app.render()), /Activity over\s+7\s+days/); assert.doesNotMatch(text(app.render()), /Activity in the last 24 hours/);
  app.dispose();
});

test('failure hides aggregates and retry reloads the current selected period', async () => {
  let fail = true;
  const app = harness(async days => { if (fail) throw Error('Analytics service unavailable'); return fixture(days); });
  await app.ready(); assert.match(text(app.render()), /Analytics service unavailable/); assert.equal(app.metric('Customers'), undefined);
  fail = false; await app.click('Try again'); assert.ok(app.metric('Customers')); assert.equal(app.calls.length, 2);
  fail = true; await app.click('Refresh analytics'); assert.equal(app.metric('Customers'), undefined);
  assert.match(text(app.render()), /Analytics have not loaded/); app.dispose();
});

test('empty bank shows zero counts and helpful states without fabricated chart or currency', async () => {
  const app = harness(async days => fixture(days, true)); await app.ready();
  assert.match(text(app.metric('Customers')), /Customers 0 0 active customers/);
  assert.match(text(app.render()), /No active customer deposit accounts yet/);
  assert.match(text(app.render()), /No posted payments in this period/);
  assert.match(text(app.render()), /No applications are waiting for staff action/);
  assert.match(text(app.render()), /The loan approval queue is clear/);
  assert.equal(nodes(app.render()).filter(node => node.type === 'svg').length, 0);
  app.change('Show', 'amount'); assert.equal(app.select('Currency'), undefined);
  await app.click('View hourly data');
  const body = nodes(app.render()).find(node => node.type === 'tbody');
  assert.equal(nodes(body).filter(node => node.type === 'tr').length, 25);
  assert.doesNotMatch(text(app.render()), /₹|\$/); app.dispose();
});

test('payment charts and exact daily data keep currencies separate and include zero days', async () => {
  const app = harness(); await app.ready(); await app.click('Last 30 days');
  assert.match(nodes(app.render()).find(node => node.type === 'svg').props['aria-label'], /Number of posted payments/);
  app.change('Show', 'amount'); assert.match(nodes(app.render()).find(node => node.type === 'figcaption').props.children, /INR/);
  app.change('Currency', 'USD'); assert.match(nodes(app.render()).find(node => node.type === 'figcaption').props.children, /USD/);
  const titles = nodes(app.render()).filter(node => node.type === 'title').map(text);
  assert.match(titles.at(-1), /USD 5\b/); assert.match(titles[0], /USD 0\b/);
  await app.click('View daily data'); assert.equal(app.button('Hide daily data').props['aria-expanded'], true);
  const table = nodes(app.render()).find(node => node.type === 'table');
  assert.match(text(table).replace(/\s+/g, ' '), /Payment value \( INR \).*Payment value \( USD \)/);
  assert.equal(nodes(table).filter(node => node.type === 'tbody').flatMap(node => nodes(node)).filter(node => node.type === 'tr').length, 30);
  assert.match(text(table), /₹15\b/); assert.match(text(table), /USD 5\b/);
  assert.match(text(app.render()), /Sandbox transfers and unposted attempts are excluded/);
  assert.match(text(app.render()), /activity volume, not bank revenue/);
  await app.click('Hide daily data'); assert.equal(nodes(app.render()).some(node => node.type === 'table'), false);
  app.dispose();
});

test('hourly chart plots all real interval totals with visible markers, zero hours and date context', async () => {
  const app = harness(); await app.ready();
  const tree = app.render(), circles = nodes(tree).filter(node => node.type === 'circle');
  assert.equal(circles.length, 25);
  assert.ok(circles.every(node => node.props.r > 0 && Number.isFinite(node.props.cx) && Number.isFinite(node.props.cy)));
  assert.ok(circles.slice(1).every((node, index) => node.props.cx > circles[index].props.cx));
  assert.ok(circles[0].props.cy > circles.at(-1).props.cy);
  assert.ok(circles[1].props.cy > circles[0].props.cy);
  assert.ok(circles.slice(1, -1).every(node => node.props.cy === circles[1].props.cy));
  assert.equal(nodes(tree).filter(node => node.type === 'polyline').length, 1);
  assert.equal(nodes(tree).filter(node => node.type === 'path').length, 0);
  assert.equal(nodes(tree).filter(node => node.type === 'polygon').length, 1);
  const labels = nodes(tree).filter(node => node.type === 'tspan').map(text).join(' ');
  assert.match(labels, /25 Sept/); assert.match(labels, /26 Sept/); assert.match(labels, /17:17/);
  assert.match(text(circles[0]), /25 Sept 2026, 17:17:30.*25 Sept 2026, 18:00:00.*Partial hour.*1 posted payments/);
  assert.match(text(circles.at(-1)), /26 Sept 2026, 17:00:00.*26 Sept 2026, 17:17:30.*Partial hour.*2 posted payments/);
  assert.match(text(circles[1]), /0 posted payments/); assert.doesNotMatch(text(circles[1]), /Partial hour/);
  assert.match(text(tree), /Lines connect recorded totals/); app.dispose();
});

test('hourly values and accessible table preserve currencies and exact clipped interval bounds', async () => {
  const app = harness(); await app.ready();
  app.change('Show', 'amount'); app.change('Currency', 'USD');
  const titles = nodes(app.render()).filter(node => node.type === 'title').map(text);
  assert.match(titles[0], /USD 0\b/); assert.match(titles.at(-1), /USD 5\b/);
  await app.click('View hourly data');
  assert.equal(app.button('Hide hourly data').props['aria-expanded'], true);
  const table = nodes(app.render()).find(node => node.type === 'table');
  assert.match(text(table), /includes its start and excludes its end/);
  const rows = nodes(table).filter(node => node.type === 'tbody').flatMap(nodes).filter(node => node.type === 'tr');
  assert.equal(rows.length, 25);
  const times = nodes(table).filter(node => node.type === 'time');
  assert.equal(times[0].props.dateTime, fixture().period.startAt);
  assert.equal(Date.parse(times.at(-1).props.dateTime), Date.parse(fixture().period.endAt));
  assert.ok(times.every(node => node.props.title === node.props.dateTime));
  assert.equal(nodes(table).filter(node => node.props?.class === 'analytics-partial-hour').length, 2);
  assert.match(text(table), /₹15\b/); assert.match(text(table), /USD 5\b/);
  await app.click('Hide hourly data'); assert.equal(nodes(app.render()).some(node => node.type === 'table'), false);
  app.dispose();
});

test('missing hourly details do not create chart observations from aggregate totals', async () => {
  const app = harness(async days => { const data = fixture(days); data.activity.hourly = []; return data; });
  await app.ready(); assert.match(text(app.render()), /Hourly payment details are unavailable/);
  assert.equal(nodes(app.render()).some(node => node.type === 'svg'), false);
  assert.match(text(app.metric('Posted payments')), /Posted payments 3/); app.dispose();
});

test('hourly table is still available when only customer registrations occurred', async () => {
  const app = harness(async days => { const data = fixture(days, true); data.activity.newCustomers = 1; data.activity.hourly.at(-1).newCustomers = 1; return data; });
  await app.ready(); assert.equal(nodes(app.render()).some(node => node.type === 'svg'), false);
  await app.click('View hourly data');
  const rows = nodes(app.render()).filter(node => node.type === 'tbody').flatMap(nodes).filter(node => node.type === 'tr');
  assert.equal(rows.length, 25); assert.match(text(app.metric('New customers')), /New customers 1/);
  const cells = nodes(rows.at(-1)).filter(node => node.type === 'td'); assert.equal(text(cells[1]), '1'); app.dispose();
});
