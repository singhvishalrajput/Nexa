const Module = require('node:module');
const original = Module._load;
Module._load = function(name, ...args) {
  if (name === 'ojs/ojcontext') return {getContext: () => ({getBusyContext: () => ({whenReady: () => Promise.resolve()})})};
  if (['ojs/ojdialog', 'ojs/ojprogress-circle', 'ojs/ojbutton', 'ojs/ojinputtext'].includes(name)) return {};
  return original.call(this, name, ...args);
};
