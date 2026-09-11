const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');

// Run the real hook against a controllable browser speech service.
function setup(supported = true) {
  const slots = [];
  const errors = [];
  let cursor = 0;
  let recognizer;
  const hooks = {
    useState(initial) {
      const index = cursor++;
      if (!(index in slots)) slots[index] = initial;
      return [slots[index], value => { slots[index] = value; }];
    },
    useRef(initial) {
      const index = cursor++;
      if (!(index in slots)) slots[index] = {current: initial};
      return slots[index];
    },
    useEffect() {}
  };
  class Recognition {
    constructor() { recognizer = this; }
    start() {}
    stop() { this.onend(); }
    abort() {}
  }
  const exports = {};
  vm.runInNewContext(ts.transpileModule(fs.readFileSync('src/hooks/useVoiceInput.ts', 'utf8'), {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021}
  }).outputText, {
    exports, require: () => hooks,
    window: {SpeechRecognition: supported ? Recognition : undefined, speechSynthesis: {cancel() {}}}
  });
  const render = () => { cursor = 0; return exports.useVoiceInput(message => errors.push(message)); };
  return {render, errors, recognition: () => recognizer};
}

test('Hindi speech is reviewed and editable before any message is sent', () => {
  const app = setup();
  app.render().start('hi-IN');
  const recognition = app.recognition();
  assert.equal(recognition.lang, 'hi-IN');
  assert.equal(app.render().phase, 'listening');
  const result = [{transcript: 'मेरा बैलेंस'}]; result.isFinal = true;
  recognition.onresult({results: [result]});
  app.render().stop();
  assert.equal(app.render().phase, 'review');
  assert.equal(app.render().transcript, 'मेरा बैलेंस');
  app.render().editTranscript('show my balance');
  assert.equal(app.render().transcript, 'show my balance');
  app.render().cancel();
  assert.equal(app.render().transcript, '');
  recognition.onresult({results: [result]});
  recognition.onend();
  assert.equal(app.render().phase, 'idle');
  assert.equal(app.render().transcript, '');
});

test('unsupported speech and denied microphone give a typing recovery path', () => {
  const unavailable = setup(false);
  unavailable.render().start('en-IN');
  assert.match(unavailable.errors[0], /type your message/);
  assert.equal(unavailable.render().phase, 'idle');
  const denied = setup();
  denied.render().start('en-IN');
  denied.recognition().onerror({error: 'not-allowed'});
  denied.recognition().onend();
  assert.match(denied.errors[0], /type your message/);
  assert.equal(denied.render().phase, 'idle');
  assert.equal(denied.render().transcript, '');
});
