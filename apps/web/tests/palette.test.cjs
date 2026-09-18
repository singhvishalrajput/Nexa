const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const styles = path.join(__dirname, '../src/styles');
const tokens = Object.fromEntries([...fs.readFileSync(path.join(styles, 'tokens.css'), 'utf8').matchAll(/--nexa-([\w-]+):\s*([^;]+);/g)].map(m => [m[1], m[2]]));
function resolve(name) {
  const value = tokens[name];
  assert.ok(value, `Missing token ${name}`);
  return value.startsWith('var(') ? resolve(value.match(/--nexa-([\w-]+)/)[1]) : value;
}
function luminance(name) {
  const hex = resolve(name);
  assert.match(hex, /^#[\da-f]{6}$/i);
  const rgb = [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16) / 255).map(c => c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4);
  return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
}
function check(fg, bg, minimum) {
  const a = luminance(fg), b = luminance(bg);
  const ratio = (Math.max(a,b) + .05) / (Math.min(a,b) + .05);
  assert.ok(ratio >= minimum, `${fg} on ${bg}: ${ratio.toFixed(2)}:1 < ${minimum}:1`);
}
test('light palette meets AA text contrast across surfaces and action states', () => {
  for (const bg of ['surface', 'background', 'soft', 'hover']) {
    for (const fg of ['text', 'text-secondary', 'text-muted', 'action']) check(fg, bg, 4.5);
  }
  for (const bg of ['action', 'action-hover', 'action-active', 'inverse-surface', 'error', 'error-hover']) check('on-action', bg, 4.5);
  check('on-action-soft', 'inverse-surface', 4.5);
  for (const state of ['success', 'warning', 'error']) check(state, `${state}-bg`, 4.5);
  check('selected-text', 'selected', 4.5);
  check('disabled-text', 'disabled-bg', 4.5);
});
test('control boundaries and focus indicators meet non-text contrast', () => {
  for (const bg of ['surface', 'background', 'soft', 'hover']) {
    check('border-control', bg, 3);
    check('focus', bg, 3);
  }
  check('focus-inverse', 'inverse-surface', 3);
  check('inverse-border', 'inverse-surface', 3);
});
test('component styles cannot reintroduce private palettes or missing semantic tokens', () => {
  for (const file of fs.readdirSync(styles).filter(f => f.endsWith('.css') && f !== 'tokens.css')) {
    const source = fs.readFileSync(path.join(styles, file), 'utf8');
    assert.doesNotMatch(source, /#[\da-f]{3,8}\b|\brgba?\(|\bhsla?\(/i, file);
    for (const match of source.matchAll(/var\(--nexa-([\w-]+)/g)) {
      assert.ok(tokens[match[1]] || ['radius', 'selected'].includes(match[1]), `${file}: missing ${match[1]}`);
    }
  }
});

test('reference peach, gray and coral surfaces have readable semantic foregrounds', () => {
  for (const bg of ['reference-ffebe4', 'reference-eeeeec', 'reference-eeede9']) {
    check('text-muted', bg, 4.5);
    check('text', bg, 4.5);
    check('accent-text', bg, 4.5);
  }
  check('text', 'coral', 4.5);
  check('text', 'soft', 4.5);
  check('text', 'pressed', 4.5);
});
