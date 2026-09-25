const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');
const jsx = (type, props) => ({type, props});

function component(file, dependencies) {
  const exports = {};
  const source = ts.transpileModule(fs.readFileSync('src/' + file, 'utf8'), {compilerOptions:{
    module:ts.ModuleKind.CommonJS, target:ts.ScriptTarget.ES2021, jsx:ts.JsxEmit.ReactJSX, jsxImportSource:'preact'
  }}).outputText;
  vm.runInNewContext(source, {exports, require:name => {
    if(name==='preact/jsx-runtime') return {jsx,jsxs:jsx};
    if(name==='preact/hooks') return {useState:value=>[value,()=>{}],useRef:value=>({current:value}),useEffect(){}};
    if(name in dependencies) return dependencies[name];
    throw new Error('Unmapped dependency: ' + name);
  }});
  return exports;
}

test('chat transaction history uses the padded response surface, not an unpadded nested card', () => {
  const {BankingResponse} = component('components/chat/BankingResponse.tsx', {
    '../BankingIcon':{},
    '../../features/banking/SensitiveNumber':{},
    '../../features/banking/CardActions':{},
    '../../services/locale':loadSource('services/locale.ts'),
    '../../services/reply-localization':loadSource('services/reply-localization.ts'),
    '../../services/banking-content':loadSource('services/banking-content.ts'),
    '../../features/banking/ui':{Status:'status',Detail:'detail'},
    '../../services/banking':{}, './SpendingSummary':{},
    './BankingCollection':{BankingCollection:'collection',collectionNotice:()=>''}
  });
  const content={version:1,type:'TRANSACTIONS',account:{id:'a',displayName:'Savings',accountNumberMasked:'•••• 1234'},transactions:[],totalElements:0};
  const response=BankingResponse({content,accessToken:'test',capturedAt:'2026-09-25'});
  const renderer=response.props.children[0];
  const panelNode=renderer.type(renderer.props);
  const panel=panelNode.type(panelNode.props);
  assert.equal(panel.props.class,'bank-transaction-panel');
  assert.ok(panel.props.children.some(child=>child?.props?.title==='Recent transactions'));
});

test('transaction rows and date headings have real side gutters even on small screens', () => {
  const css=fs.readFileSync('src/styles/conversation-results.css','utf8');
  assert.match(css,/\.nexa-messenger \.bank-transaction-row\s*\{[^}]*box-sizing:\s*border-box;[^}]*padding:\s*14px;/);
  assert.match(css,/\.bank-date-heading\s*\{[^}]*padding:\s*8px 14px;/);
  assert.match(css,/@media \(max-width: 420px\)[\s\S]*\.bank-transaction-row\s*\{[^}]*padding:\s*12px;/);
  assert.match(css,/\.bank-pagination\s*\{[^}]*flex-wrap:\s*wrap/);
});

test('request history puts refresh inside the padded header and retains its action', () => {
  let refreshes=0;
  const reload=()=>refreshes++;
  const {DemoHistory}=component('features/banking/Showcase.tsx', {
    './demo-api':{},
    './ui':{Panel:'panel',State:'state',useLoad:()=>({data:[],loading:false,reload})},
    '../../services/banking-content':loadSource('services/banking-content.ts')
  });
  const panel=DemoHistory({token:'test'});
  assert.equal(panel.props.className,'bank-request-history');
  assert.equal(panel.props.action.props.children,'Refresh');
  assert.equal(panel.props.action.props.class,'bank-button secondary');
  panel.props.action.props.onClick();
  assert.equal(refreshes,1);
  assert.equal(panel.props.children.some(child=>child?.type==='button'),false);
});

test('filter labels keep their first-row position and history cards allow long references to wrap', () => {
  const css=fs.readFileSync('src/styles/experience.css','utf8');
  assert.match(css,/\.experience-workspace \.bank-filters > label\s*\{[^}]*grid-row:1/);
  assert.match(css,/\.bank-request-history\s*\{\s*margin-top:24px/);
  assert.match(css,/\.bank-request-history \.bank-record > div\s*\{[^}]*min-width:0;[^}]*overflow-wrap:anywhere/);
});
