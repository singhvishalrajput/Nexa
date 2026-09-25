const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('chat entry and exit use the Nexa page transition', () => {
  const workspace = fs.readFileSync('src/features/banking/BankingApp.tsx', 'utf8');
  const transition = fs.readFileSync('src/components/NexaPageTransition.tsx', 'utf8');
  const styles = fs.readFileSync('src/styles/brand-transition.css', 'utf8');
  assert.match(workspace, /previous !== "assistant" && page !== "assistant"/);
  assert.match(workspace, /showPageTransition && <NexaPageTransition\/>/);
  assert.match(transition, /class="nexa-transition nexa-route-transition"/);
  assert.match(styles, /\.nexa-route-transition\s*\{[\s\S]*animation:\s*nexa-transition-reveal 1450ms/);
});
