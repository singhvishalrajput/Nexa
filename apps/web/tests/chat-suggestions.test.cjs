const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {loadSource} = require('./source-loader.cjs');
const {getFollowUpSuggestions} = loadSource('services/chat-suggestions.ts');
const {t} = loadSource('services/locale.ts');

test('chat follow-ups only advertise implemented balance, history and transfer operations', () => {
  assert.deepEqual([...getFollowUpSuggestions('ACCOUNTS')], ['Show my latest transactions', 'Move money between my accounts']);
  for (const type of [undefined, 'TRANSACTIONS', 'INSIGHTS', 'BILLS', 'CARDS', 'LOANS', 'MANDATES', 'UPCOMING', 'SCHEDULED_PAYMENTS', 'BENEFICIARIES', 'TRANSFER_STATUS', 'TEXT', 'ERROR']) {
    assert.deepEqual([...getFollowUpSuggestions(type)], ['What’s my balance?']);
  }
});

test('English and Hindi suggestions do not promise an unsupported amount filter or result count', () => {
  for (const locale of ['en-IN', 'hi-IN']) {
    const labels = [...getFollowUpSuggestions('ACCOUNTS'), ...getFollowUpSuggestions('TRANSACTIONS')].map(label => t(label, locale));
    for (const label of labels) assert.doesNotMatch(label, /[₹0-9०-९]/);
  }
  // The Hindi query must use the transaction alias the backend recognizes.
  assert.equal(t('Show my latest transactions', 'hi-IN'), 'मेरे हाल के लेनदेन दिखाओ।');
});

test('all visible recent-transaction entry points use the same supported localized prompt', () => {
  const source = fs.readFileSync('src/components/chat/ConversationWorkspace.tsx', 'utf8');
  assert.match(source, /getFollowUpSuggestions\(turns\[turns\.length - 1\]\.banking\?\.type\)/);
  assert.equal(source.split('submit(t("Show my latest transactions"))').length - 1, 2);
  assert.doesNotMatch(source, /Show transactions above|₹5,000|पिछले 10/);
});
