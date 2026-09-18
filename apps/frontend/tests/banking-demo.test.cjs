const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const output = ts.transpileModule(fs.readFileSync('src/components/banking-demo.ts','utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021 } }).outputText;
const sandbox = { exports: {}, Intl };
vm.runInNewContext(output, sandbox);
const { initialAccount, respond, approve } = sandbox.exports;

test('visitor introductions do not expose account cards or assume an account exists', () => {
  for (const question of ['How does Nexa work?', 'I’m new to Nexa', 'I don’t have an account', 'Open an account', 'Hi']) {
    const reply = respond(question, initialAccount());
    assert.equal(reply.card, undefined);
    assert.equal(reply.proposal, undefined);
    assert.doesNotMatch(reply.text, /42,850|32,000|64%/);
    assert.match(reply.text, /explore|preview/i);
  }
});
test('balance and spending totals reflect sample ledger', () => { const a=initialAccount(); assert.equal(respond('What is my balance?',a).card.amount,42850); assert.equal(respond('Where did my money go?',a).card.amount,12450); assert.equal(respond('Travel fund balance',a).card.amount,32000); });
test('preparing a transfer does not change funds; approval applies exactly once', () => { const a=initialAccount(),p=respond('Send ₹500 to Alex',a).proposal; assert.equal(a.balance,42850); assert.equal(p.recipient,'Alex'); const b=approve(a,p,'payment-1'); assert.equal(b.balance,42350); assert.equal(approve(b,p,'payment-1'),null); assert.equal(respond('Show spending',b).card.amount,12950); });
test('saving transfers conserve balances and are excluded from spending', () => { const a=initialAccount(),p=respond('Move ₹3,000 to my travel fund',a).proposal; const b=approve(a,p,'saving-1'); assert.equal(b.balance,39850); assert.equal(b.travel,35000); assert.equal(a.balance+a.travel,b.balance+b.travel); assert.equal(respond('Show spending',b).card.amount,12450); });
test('invalid and ambiguous requests cannot produce a transfer', () => { for (const q of ['Send -500 to Alex','Send 0 to Alex','Send 0.001 to Alex','Send ₹999999 to Alex','Send ₹500 to Alex and Sam','Send 500 and 600 to Alex','Send $500 to Alex','Send ₹500 to Alex tomorrow','Send ₹500 from my travel fund to Alex','Send ₹500 to Morgan','Don’t send 500 to Alex']) assert.equal(respond(q,initialAccount()).proposal,undefined,q); });
test('confirmation rechecks current funds and proposal status', () => { const a=initialAccount(),p=respond('Send 42000 to Priya',a).proposal; assert.equal(approve({...a,balance:100},p,'x'),null); assert.equal(approve(a,{...p,status:'cancelled'},'x'),null); });
test('decimal and abbreviated amounts remain exact', () => { const a=initialAccount(); assert.equal(respond('Pay ₹12.50 to Sam',a).proposal.amount,12.5); assert.equal(respond('Move 3k to my travel fund',a).proposal.amount,3000); });
