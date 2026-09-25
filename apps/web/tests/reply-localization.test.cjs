const {test} = require('node:test');
const assert = require('node:assert/strict');
const {loadSource} = require('./source-loader.cjs');
const {localizeReply,replyForSpeech,accountDisplayName} = loadSource('services/reply-localization.ts');

test('standard banking reply introductions have Hindi translations, not just a Hindi voice', () => {
  const replies=[
    'Here are your available balances.','Here are your latest transactions.',
    'Here are the transaction details.','Here are your mandates.','Here are the mandate details.',
    'Here are your bills and their payment statuses.','Here are the bill details.',
    'Here are your cards.','Here are your credit cards.','Here are your beneficiaries.',
    'Here are your scheduled payments.','Here are your loans and EMIs.','Here are the loan details.',
    'Here is the recorded transfer status.'
  ];
  for(const reply of replies) {
    const translated=localizeReply(reply,'hi-IN');
    assert.match(translated,/[\u0900-\u097f]/);
    assert.doesNotMatch(translated,/[a-z]/i);
    assert.equal(replyForSpeech(reply,'hi-IN'),translated);
    assert.equal(localizeReply(reply,'en-IN'),reply);
  }
});

test('translations preserve empty results, restrictions and privacy warnings', () => {
  assert.match(localizeReply('You do not have a matching Nexa account yet.','hi-IN'),/मेल नहीं खाता/);
  assert.match(localizeReply('Historical balances are not available. Please ask for your current balance.','hi-IN'),/पुराने बैलेंस की जानकारी उपलब्ध नहीं/);
  assert.match(localizeReply('Here are recorded bills due in the requested period (from the first 100 bills).','hi-IN'),/पहले 100 दर्ज बिलों/);
  const privacy='Please do not send passwords, PINs, verification codes or full card numbers. This message was not retained. Describe what you need without those details.';
  const translated=replyForSpeech(privacy,'hi-IN');
  assert.match(translated,/पासवर्ड, पिन, सत्यापन कोड या पूरा कार्ड नंबर न भेजें/);
  assert.match(translated,/सहेजा नहीं गया/);
});

test('only default account names get display aliases; custom names and identifiers stay unchanged', () => {
  assert.equal(accountDisplayName('Primary account','hi-IN'),'मुख्य खाता');
  assert.equal(accountDisplayName('Demo everyday account','hi-IN'),'डेमो रोज़मर्रा का खाता');
  for(const name of ['Vishal’s travel account','Primary account - family','Asha Sharma','constructor','__proto__']) {
    assert.equal(accountDisplayName(name,'hi-IN'),name);
  }
  assert.equal(accountDisplayName('Primary account','en-IN'),'Primary account');
  assert.equal(localizeReply('Here are transactions for ACME Platinum · 1234.','hi-IN'),'ACME Platinum · 1234 के लेन-देन ये हैं।');
});

test('unrecognized server messages remain visible and speech explains missing Hindi translation', () => {
  const message='Payment failed: reference TX-123. Do not send again.';
  assert.equal(localizeReply(message,'hi-IN'),message);
  assert.match(replyForSpeech(message,'hi-IN'),/हिन्दी अनुवाद उपलब्ध नहीं/);
  assert.doesNotMatch(replyForSpeech(message,'hi-IN'),/[a-z]/i);
  assert.equal(replyForSpeech(message,'en-IN'),message);
  const hindi='Asha Sharma को भुगतान नहीं हुआ।';
  assert.equal(replyForSpeech(hindi,'hi-IN'),hindi);
  assert.equal(replyForSpeech('','hi-IN'),'');
});
