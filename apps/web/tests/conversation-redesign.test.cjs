const {test} = require('node:test');
const assert = require('node:assert/strict');
const {loadSource} = require('./source-loader.cjs');
const {spendingGroups} = loadSource('components/chat/SpendingSummary.tsx');
const locale = loadSource('services/locale.ts');
const {formatDate, formatMoney, safeMask} = loadSource('services/banking-content.ts');

test('spending summaries preserve paise and never aggregate different currencies', () => {
  const groups = spendingGroups([
    {category:'Food', amount:'0.10', currency:'INR'},
    {category:'Travel', amount:'0.20', currency:'INR'},
    {category:'Travel', amount:'10.01', currency:'USD'}
  ]);
  assert.equal(groups.length, 2);
  assert.equal(groups[0].total, 30n);
  assert.equal(groups[1].total, 1001n);
  assert.equal(groups[0].rows[0].category, 'Travel');
});
test('invalid or negative spending cannot be rendered as a plausible total', () => {
  for (const amount of ['NaN','broken','-1']) assert.equal(spendingGroups([{category:'Food',currency:'INR',amount}]), null);
  assert.equal(spendingGroups([{category:'Food',currency:'INR',amount:'999999999999999.99'}])[0].total, 99999999999999999n);
  assert.equal(spendingGroups([]).length, 0);
});
test('Hindi locale changes UI and date formatting while keeping amounts and identifiers intact', () => {
  locale.setLocale('hi-IN');
  assert.equal(locale.t('Confirm transfer'), 'ट्रांसफ़र की पुष्टि करें');
  assert.equal(locale.t('Some bank-owned merchant name'), 'Some bank-owned merchant name');
  assert.match(formatDate('2026-09-16'), /सित/);
  assert.equal(formatMoney('999999999999999.99'), '₹99,99,99,99,99,99,999.99');
  assert.equal(safeMask('1234567890123456'), '•••• 3456');
  locale.setLocale('en-IN');
  assert.match(formatDate('2026-09-16'), /Sept?/);
});
