const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const ts = require("typescript");

function setup(seed = "") {
  const timers = new Map();
  let timerId = 0;
  const sandbox = {
    exports: {},
    setTimeout: (fn) => {
      timers.set(++timerId, fn);
      return timerId;
    },
    clearTimeout: (id) => timers.delete(id),
  };
  vm.runInNewContext(
    ts.transpileModule(
      fs.readFileSync("src/components/voice-session.ts", "utf8"),
      {
        compilerOptions: {
          module: ts.ModuleKind.CommonJS,
          target: ts.ScriptTarget.ES2021,
        },
      },
    ).outputText,
    sandbox,
  );
  const instances = [],
    transcripts = [],
    states = [],
    errors = [];
  class FakeRecognition {
    constructor() {
      instances.push(this);
    }
    start() {
      this.started = true;
    }
    stop() {
      this.stopped = true;
    }
    abort() {
      this.aborted = true;
    }
    result(...texts) {
      this.onresult?.({
        results: texts.map((text) => ({
          0: { transcript: text },
          isFinal: true,
        })),
      });
    }
  }
  const session = sandbox.exports.createVoiceSession(FakeRecognition, seed, {
    transcript: (text) => transcripts.push(text),
    listening: (active) => states.push(active),
    error: (code) => errors.push(code),
  });
  const flush = () => {
    const pending = [...timers.values()];
    timers.clear();
    pending.forEach((fn) => fn());
  };
  return { session, instances, transcripts, states, errors, timers, flush };
}

test("silence restarts recognition without ending the listening session", () => {
  const x = setup();
  x.session.start();
  const first = x.instances[0];
  assert.equal(first.continuous, true);
  assert.equal(first.interimResults, true);
  first.onerror({ error: "no-speech" });
  first.onend();
  x.flush();
  assert.equal(x.instances.length, 2);
  assert.deepEqual(x.states, [true]);
  assert.deepEqual(x.errors, []);
});

test("live results replace interim text and append across silent restarts", () => {
  const x = setup("Please");
  x.session.start();
  x.instances[0].result("show");
  x.instances[0].result("show my balance");
  x.instances[0].onend();
  x.flush();
  x.instances[1].result("and my savings");
  assert.deepEqual(x.transcripts, [
    "Please show",
    "Please show my balance",
    "Please show my balance and my savings",
  ]);
});

test("Stop retains the last result and never restarts, including during retry", () => {
  const x = setup();
  x.session.start();
  const r = x.instances[0];
  x.session.stop();
  assert.equal(r.stopped, true);
  r.result("final words");
  r.onend();
  x.flush();
  assert.equal(x.instances.length, 1);
  assert.deepEqual(x.transcripts, ["final words"]);
  assert.deepEqual(x.states, [true, false]);
  x.session.start();
  x.instances[1].onend();
  x.session.stop();
  x.flush();
  assert.equal(x.instances.length, 2);
});

test("permission failures stop retries; cleanup ignores late results", () => {
  const x = setup();
  x.session.start();
  const r = x.instances[0],
    lateResult = r.onresult,
    lateEnd = r.onend;
  r.onerror({ error: "not-allowed" });
  lateResult({ results: [{ 0: { transcript: "ignored" }, isFinal: true }] });
  lateEnd();
  x.flush();
  assert.deepEqual(x.errors, ["not-allowed"]);
  assert.deepEqual(x.transcripts, []);
  assert.equal(x.instances.length, 1);
  assert.equal(r.aborted, true);
  const y = setup();
  y.session.start();
  y.instances[0].onend();
  y.session.cancel();
  y.flush();
  assert.equal(y.instances.length, 1);
});
