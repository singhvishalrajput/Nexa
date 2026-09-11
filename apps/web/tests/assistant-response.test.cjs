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

test('clarification and error envelopes remain readable chat messages', () => {
  for (const type of ['TEXT', 'ERROR', 'ACTION_REQUIRED']) {
    const result = AssistantResponse({ turn: { ...turn, banking: { version: 1, type } }, accessToken: 'test' });
    assert.equal(result.type, MessageBubble);
    assert.equal(result.props.text, turn.assistantText);
    assert.equal(result.props.children, false);
  }
});

const { collectionLayout } = require('../src/components/chat/BankingCollection.tsx');
test('single objects stay direct and multiple substantial objects browse horizontally', () => {
  for (const type of ['ACCOUNTS', 'CARDS', 'LOANS']) {
    assert.equal(collectionLayout(type, 1), 'direct');
    assert.equal(collectionLayout(type, 2), 'horizontal');
    assert.equal(collectionLayout(type, 20), 'horizontal');
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
