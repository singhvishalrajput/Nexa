require('./jet-node-stubs.cjs');
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
global.window = { NEXA_API_BASE_URL: 'http://localhost/api/v1' };

// Compile the actual components for Node without changing the app's AMD build.
for (const extension of ['.ts', '.tsx']) {
  require.extensions[extension] = (module, filename) => module._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021, jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact' }
  }).outputText, filename);
}
const { AssistantResponse } = require('../src/components/chat/AssistantResponse.tsx');
const { MessageBubble } = require('../src/components/chat/MessageBubble.tsx');
const { supportsBankingContent, BillList } = require('../src/components/chat/BankingResponse.tsx');
const turn = { assistantText: 'Here is your summary.', createdAt: '2026-09-10T09:00:00Z' };

test('loan chat summaries render new loans without a masked number alongside imported loans', () => {
  const { LoanSummary } = require('../src/components/chat/BankingResponse.tsx');
  const base = {displayName:'New loan', outstanding:'503', nextEmi:'509.08', currencyCode:'INR', status:'ACTIVE'};
  const result = LoanSummary({content:{version:1, type:'LOANS', loans:[
    {...base, id:'new-loan', numberMasked:null},
    {...base, id:'pending-loan', status:'PENDING_APPROVAL'},
    {...base, id:'imported-loan', numberMasked:'•••• 8842'}
  ]}});
  function text(node) {
    if (node == null || typeof node === 'boolean') return '';
    if (Array.isArray(node)) return node.map(text).join(' ');
    if (typeof node !== 'object') return String(node);
    return text(node.props?.children);
  }
  const rendered = text(result);
  assert.equal((rendered.match(/Number unavailable/g) || []).length, 2);
  assert.match(rendered, /8842/);
  assert.match(rendered, /New loan/);
});

test('plain and historical replies retain standard message bubbles', () => {
  const result = AssistantResponse({ turn, accessToken: 'test' });
  assert.equal(result.type, MessageBubble);
  assert.equal(result.props.text, turn.assistantText);
});
test('structured data renders outside the message bubble', () => {
  const result = AssistantResponse({ turn: { ...turn, banking: { version: 1, type: 'ACCOUNTS', accounts: [] } }, accessToken: 'test' });
  assert.equal(result.type, 'article');
  assert.equal(result.props.class, 'messenger-banking-response');
  assert.equal(result.props.children[1].props.class, 'messenger-banking-surface');
});
test('unknown types and versions preserve the assistant text', () => {
  for (const banking of [{ version: 2, type: 'ACCOUNTS' }, { version: 1, type: 'FUTURE' }, { version: 1, type: 'toString' }]) {
    assert.equal(supportsBankingContent(banking), false);
    assert.equal(AssistantResponse({ turn: { ...turn, banking }, accessToken: 'test' }).props.text, turn.assistantText);
  }
});
test('bills and scheduled payments are registered and empty bills explain their state', () => {
  assert.equal(supportsBankingContent({ version: 1, type: 'BILLS' }), true);
  assert.equal(supportsBankingContent({ version: 1, type: 'SCHEDULED_PAYMENTS' }), true);
  const empty = BillList({ content: { version: 1, type: 'BILLS', bills: [] } });
  assert.equal(empty.props.title, 'Your bills');
});

test('empty banking responses keep the specific translated message', () => {
  const { setLocale, t } = require('../src/services/locale.ts');
  setLocale('hi-IN');
  try {
    const empty = BillList({ content: { version: 1, type: 'BILLS', bills: [] } });
    const rendered = empty.type(empty.props);
    assert.equal(rendered.props.children[1].props.children, t('No bills to show.'));
  } finally { setLocale('en-IN'); }
});

test('clarification and error envelopes remain readable chat messages', () => {
  for (const type of ['TEXT', 'ERROR', 'ACTION_REQUIRED']) {
    const result = AssistantResponse({ turn: { ...turn, banking: { version: 1, type } }, accessToken: 'test' });
    assert.equal(result.type, MessageBubble);
    assert.equal(result.props.text, turn.assistantText);
    assert.equal(result.props.children, false);
  }
});

const { collectionLayout } = require('../src/components/chat/BankingCollection.tsx');
test('single objects stay direct and multiple substantial objects use a responsive comparison grid', () => {
  for (const type of ['ACCOUNTS', 'CARDS', 'LOANS']) {
    assert.equal(collectionLayout(type, 1), 'direct');
    assert.equal(collectionLayout(type, 2), 'comparison');
    assert.equal(collectionLayout(type, 20), 'comparison');
  }
});
test('row records and future types use vertical collections; empty results stay direct', () => {
  for (const type of ['TRANSACTIONS', 'MANDATES', 'BILLS', 'BENEFICIARIES', 'SCHEDULED_PAYMENTS', 'FUTURE_ROWS']) {
    assert.equal(collectionLayout(type, 0), 'direct');
    assert.equal(collectionLayout(type, 1), 'direct');
    assert.equal(collectionLayout(type, 20), 'vertical');
  }
});

test('attention summaries keep failed and pending records discoverable outside scrolling', () => {
  const { collectionNotice } = require('../src/components/chat/BankingCollection.tsx');
  assert.equal(collectionNotice([{status:'POSTED'},{status:'FAILED'},{status:'PENDING'},{status:'FAILED'}]), '2 failed · 1 pending');
  assert.equal(collectionNotice([{status:'ACTIVE'}]), '');
});

test('status badges replace a single localized label when the language changes', () => {
  const {setLocale} = require('../src/services/locale.ts');
  const {Status} = require('../src/features/banking/ui.tsx');
  try {
    for (const [locale, expected] of [['en-IN', 'Completed'], ['hi-IN', 'पूरा हुआ'], ['en-IN', 'Completed'], ['hi-IN', 'पूरा हुआ']]) {
      setLocale(locale);
      const badge = Status({value:'COMPLETED'});
      assert.equal(badge.key, locale);
      assert.equal(badge.props.lang, locale);
      assert.equal(badge.props.translate, false);
      assert.equal(badge.props.children.length, 2);
      assert.equal(badge.props.children[0].type, 'i');
      assert.equal(badge.props.children[1].props.children, expected);
    }
  } finally { setLocale('en-IN'); }
});

test('transaction time and summary date each have one locale-keyed text container', () => {
  const {setLocale} = require('../src/services/locale.ts');
  const {BankingResponse, TransactionList} = require('../src/components/chat/BankingResponse.tsx');
  const timestamp = '2026-09-04T04:45:00Z';
  const item = {id:'salary',accountId:'a',reference:'r',type:'DEPOSIT',merchantName:'Employer',category:'Income',amount:'82400',currencyCode:'INR',status:'COMPLETED',occurredAt:timestamp};
  function nodes(node, type) {
    if (!node || typeof node !== 'object') return [];
    if (Array.isArray(node)) return node.flatMap(child => nodes(child, type));
    return [...(node.type === type ? [node] : []), ...nodes(node.props?.children, type)];
  }
  try {
    for (const locale of ['en-IN', 'hi-IN', 'en-IN']) {
      setLocale(locale);
      const list = TransactionList({items:[item],onSelect:()=>{}});
      const times = nodes(list, 'time');
      assert.equal(times.length, 1);
      assert.equal(times[0].props.dateTime, timestamp);
      const timeContainer = nodes(list, 'small').find(node => nodes(node, 'time').length);
      assert.equal(timeContainer.key, locale);
      assert.equal(timeContainer.props.translate, false);
      assert.equal(timeContainer.props.children[0].props.children, locale === 'hi-IN' ? 'प्राप्त' : 'Received');
      const summary = BankingResponse({content:{version:1,type:'TRANSACTIONS',account:{id:'a'},transactions:[item],totalElements:1},accessToken:'test',capturedAt:timestamp});
      assert.equal(summary.props.translate, false);
      assert.equal(summary.props.children[1].key, locale);
      assert.equal(nodes(summary, 'time').length, 1);
    }
  } finally { setLocale('en-IN'); }
});

test('Hindi chat displays the same translated balance introduction and built-in labels as narration', () => {
  const {setLocale} = require('../src/services/locale.ts');
  const {AccountSummary} = require('../src/components/chat/BankingResponse.tsx');
  const {spokenResponse} = require('../src/services/spoken-response.ts');
  const account={id:'account-1',displayName:'Primary account',accountNumberMasked:'•••• 4291',accountType:'SAVINGS',availableBalance:'95280',currencyCode:'INR',status:'ACTIVE'};
  const reply={...turn,assistantText:'Here are your available balances.',banking:{version:1,type:'ACCOUNTS',accounts:[account]}};
  function text(node) {
    if(node==null||typeof node==='boolean')return '';
    if(Array.isArray(node))return node.map(text).join(' ');
    return typeof node==='object'?text(node.props?.children):String(node);
  }
  setLocale('hi-IN');
  try {
    const intro=AssistantResponse({turn:reply,accessToken:'test'}).props.children[0];
    assert.equal(intro.props.lang,'hi-IN');
    assert.equal(intro.props.children,'ये आपके खातों में उपलब्ध बैलेंस हैं।');
    assert.ok(spokenResponse(reply,'hi-IN').startsWith(intro.props.children));
    const summary=text(AccountSummary({accounts:[account],onTransactions(){}}));
    assert.match(summary,/मुख्य खाता.*4291/);
    assert.match(summary,/बचत\s+खाता/);
    assert.doesNotMatch(summary,/Primary account|Savings|account/);
    assert.equal(reply.assistantText,'Here are your available balances.');
    assert.equal(account.displayName,'Primary account');
  } finally {setLocale('en-IN');}
  assert.equal(AssistantResponse({turn:reply,accessToken:'test'}).props.children[0].props.children,reply.assistantText);
});
