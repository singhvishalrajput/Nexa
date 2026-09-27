const {test} = require('node:test');
const assert = require('node:assert/strict');
const {categoryLabel, humanize} = require('./source-loader.cjs').loadSource('services/banking-content.ts');

test('utility labels support existing keys, casing and stored underscore aliases without duplicate bill suffixes', () => {
  for (const [raw, label] of [
    ['Water', 'Water bill'], ['  WATER_bill  ', 'Water bill'], ['electricity', 'Electricity bill'],
    ['GAS', 'Gas bill'], ['mobile_bill', 'Mobile bill'], ['Internet', 'Internet / broadband bill'],
    ['broadband bill', 'Internet / broadband bill'], ['INTERNET / BROADBAND BILL', 'Internet / broadband bill'],
    ['TV / DTH', 'TV / DTH bill'], ['TV_DTH', 'TV / DTH bill'], ['dth_bill', 'TV / DTH bill']
  ]) assert.equal(categoryLabel(raw), label, raw);
});

test('general and unknown categories retain their existing display behavior', () => {
  for (const raw of ['Other', 'other_categories', 'account_opening', 'Food', 'Utilities', 'Festival donation', 'Water bottle']) {
    assert.equal(categoryLabel(raw), humanize(raw), raw);
  }
});
