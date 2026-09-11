const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const source = fs.readFileSync(path.join(__dirname, '../src/services/banking-content.ts'), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
const exported = {};
new Function('exports', compiled)(exported);
const { formatMoney, safeMask, transactionDirection, dayLabel, dueLabel } = exported;

test('Indian grouping preserves paise, signs and large decimal amounts', () => {
  assert.equal(formatMoney('42350.75', 'INR'), '₹42,350.75');
  assert.equal(formatMoney('-485.00', 'INR', true), '− ₹485');
  assert.equal(formatMoney('85000', 'INR', true), '+ ₹85,000');
  assert.equal(formatMoney('999999999999999.99', 'INR'), '₹99,99,99,99,99,99,999.99');
  assert.equal(formatMoney('1.995', 'INR'), '₹2');
  assert.equal(formatMoney('-0.001', 'INR', true), '₹0');
  assert.equal(formatMoney('invalid', 'INR'), 'Amount unavailable');
});
test('only the final four digits can be displayed', () => {
  assert.equal(safeMask('1234567812345678'), '•••• 5678');
  assert.equal(safeMask('•••• 1234'), '•••• 1234');
  assert.equal(safeMask(''), 'Number unavailable');
});
test('pending and failed transactions never claim completed spending', () => {
  assert.equal(transactionDirection({amount:'-500', status:'POSTED'}), 'Spent');
  assert.equal(transactionDirection({amount:'500', status:'POSTED'}), 'Received');
  assert.equal(transactionDirection({amount:'-500', status:'PENDING'}), 'Outgoing');
  assert.equal(transactionDirection({amount:'-500', status:'FAILED'}), 'Not completed');
});
test('date groups and near-due labels use local calendar days', () => {
  const now = new Date(2026, 8, 10, 12);
  assert.equal(dayLabel(new Date(2026, 8, 10, 9).toISOString(), now), 'Today');
  assert.equal(dayLabel(new Date(2026, 8, 9, 18).toISOString(), now), 'Yesterday');
  assert.equal(dueLabel(new Date(2026, 8, 11, 12).toISOString(), now), 'Due tomorrow');
});
