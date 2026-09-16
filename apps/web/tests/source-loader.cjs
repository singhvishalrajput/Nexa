// Load real TypeScript utilities for the isolated component harnesses.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');
const cache = new Map();
function loadSource(relative) {
  const file = path.resolve(__dirname, '../src', relative);
  if (cache.has(file)) return cache.get(file);
  const exports = {};
  cache.set(file, exports);
  const compiled = ts.transpileModule(fs.readFileSync(file, 'utf8'), {compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2021,
    jsx: ts.JsxEmit.ReactJSX, jsxImportSource: 'preact'
  }}).outputText;
  vm.runInNewContext(compiled, {exports, Event, require: name => name.startsWith('.')
    ? loadSource(path.relative(path.resolve(__dirname, '../src'), path.resolve(path.dirname(file), name + '.ts')))
    : require(name)});
  return exports;
}
module.exports = {loadSource};
