const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('chat entry and exit use the Nexa page transition', () => {
  const workspace = fs.readFileSync('src/features/banking/BankingApp.tsx', 'utf8');
  const transition = fs.readFileSync('src/components/NexaPageTransition.tsx', 'utf8');
  const styles = fs.readFileSync('src/styles/brand-transition.css', 'utf8');
  assert.match(workspace, /previous !== "assistant" && page !== "assistant"/);
  assert.match(workspace, /useLayoutEffect\(\(\) => \{\s*const previous = previousPage.current;[\s\S]*?setShowPageTransition\(true\);[\s\S]*?window.clearTimeout\(timer\);\s*\}, \[page\]\)/);
  assert.equal((workspace.match(/showPageTransition && <NexaPageTransition ai\/>/g) || []).length, 2);
  assert.doesNotMatch(workspace, /overviewAskPending/);
  assert.match(styles, /var\(--nexa-coral\)/);
  assert.doesNotMatch(styles, /--nexa-ai-(pink|blue|violet|mint)/);
  assert.match(transition, /"nexa-transition nexa-route-transition"/);
  assert.match(transition, /ai \? " nexa-transition-ai" : ""/);
  assert.match(styles, /\.nexa-route-transition\s*\{[\s\S]*animation:\s*nexa-transition-reveal 2800ms/);
});
