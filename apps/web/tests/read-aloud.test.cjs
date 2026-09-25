const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), ts = require('typescript');
const {loadSource} = require('./source-loader.cjs');

function load(file, dependencies = {}, globals = {}) {
  const exports = {};
  const source = ts.transpileModule(fs.readFileSync('src/' + file, 'utf8'), {
    compilerOptions: {module:ts.ModuleKind.CommonJS, target:ts.ScriptTarget.ES2021}
  }).outputText;
  vm.runInNewContext(source, {exports, ...globals, require:name => {
    if (!(name in dependencies)) throw new Error('Unexpected import: ' + name);
    return dependencies[name];
  }});
  return exports;
}
const english = {name:'English default',lang:'en-US',default:true,localService:true};
const hindi = {name:'Hindi',lang:'hi-IN',default:false,localService:true};
function browser(initial = []) {
  let voices = initial, sequence = 0;
  const spoken = [], errors = [], listeners = new Set(), timers = new Map();
  const window = {
    setTimeout(fn) { const id = ++sequence; timers.set(id, fn); return id; },
    clearTimeout(id) { timers.delete(id); }
  };
  const synthesis = {
    getVoices:() => voices,
    speak:utterance => spoken.push(utterance),
    cancel() {},
    addEventListener(name, handler) { assert.equal(name, 'voiceschanged'); listeners.add(handler); },
    removeEventListener(name, handler) { listeners.delete(handler); }
  };
  const exports = load('services/read-aloud.ts', {}, {window});
  const reader = exports.createSpeechReader(message=>errors.push(message), synthesis, text=>({text}));
  return {exports, reader, spoken, errors, synthesis, listeners, timers,
    voices(list, notify=true) { voices=list; if(notify) [...listeners].forEach(fn=>fn()); },
    timeout() { const pending=[...timers.values()]; timers.clear(); pending.forEach(fn=>fn()); }
  };
}

test('Hindi speech explicitly selects a Hindi voice instead of the default English voice', () => {
  const app=browser([english,hindi]);
  app.reader.speak('आपका उपलब्ध बैलेंस 500 रुपये है।','hi-IN');
  assert.equal(app.spoken.length,1);
  assert.equal(app.spoken[0].voice,hindi);
  assert.equal(app.spoken[0].lang,'hi-IN');
  assert.equal(app.spoken[0].text,'आपका उपलब्ध बैलेंस 500 रुपये है।');
  assert.equal(app.listeners.size,0);
});

test('selected Hindi and Devanagari replies both request Hindi, while English stays English', () => {
  const {speechLocale,selectSpeechVoice}=browser().exports;
  assert.equal(speechLocale('Your balance is 500.','hi-IN'),'hi-IN');
  assert.equal(speechLocale('आपका बैलेंस','en-IN'),'hi-IN');
  assert.equal(speechLocale('Your balance','en-IN'),'en-IN');
  const indianEnglish={...english,lang:'en-IN'};
  assert.equal(selectSpeechVoice([english,indianEnglish,hindi],'en-IN'),indianEnglish);
  assert.equal(selectSpeechVoice([english],'en-IN'),english);
  assert.equal(selectSpeechVoice([english],'hi-IN'),undefined);
  assert.equal(selectSpeechVoice([{...hindi,lang:'HI_in'}],'hi-IN').lang,'HI_in');
  assert.equal(selectSpeechVoice([{...hindi,lang:'hi'}],'hi-IN').lang,'hi');
});

test('read aloud waits for delayed Hindi voices even when English is already loaded', () => {
  for(const initial of [[],[english]]) {
    const app=browser(initial);
    app.reader.speak('नमस्ते','hi-IN');
    assert.equal(app.spoken.length,0);
    app.voices([english]);
    assert.equal(app.spoken.length,0);
    app.voices([english,hindi]);
    assert.equal(app.spoken.length,1);
    assert.equal(app.spoken[0].voice,hindi);
    assert.equal(app.listeners.size,0);
    assert.equal(app.timers.size,0);
    app.voices([english,hindi]);
    assert.equal(app.spoken.length,1,'voice changes cannot replay the same reply');
  }
});

test('missing Hindi voice displays guidance and never falls back to English', () => {
  const app=browser([english]);
  app.reader.speak('नमस्ते','hi-IN');
  app.timeout();
  assert.equal(app.spoken.length,0);
  assert.match(app.errors.at(-1),/हिन्दी आवाज़ उपलब्ध नहीं/);
  assert.equal(app.listeners.size,0);
  app.voices([english,hindi]);
  assert.equal(app.spoken.length,0,'late availability needs an explicit retry');
  app.reader.speak('नमस्ते','hi-IN');
  assert.equal(app.spoken.length,1);
  assert.equal(app.errors.at(-1),'');
});

test('timeout makes a final voice check for browsers that miss voiceschanged', () => {
  const app=browser();
  app.reader.speak('नमस्ते','hi-IN');
  app.voices([hindi],false);
  app.timeout();
  assert.equal(app.spoken[0].voice,hindi);
  assert.equal(app.errors.at(-1),'');
});

test('stop and replacement cancel pending voice loads and ignore old playback errors', () => {
  const app=browser();
  app.reader.speak('पुराना जवाब','hi-IN');
  app.reader.cancel();
  app.voices([english,hindi]); app.timeout();
  assert.equal(app.spoken.length,0);
  assert.equal(app.listeners.size,0);
  app.reader.speak('पहला जवाब','hi-IN');
  const old=app.spoken[0];
  app.reader.speak('New reply','en-IN');
  old.onerror({error:'synthesis-failed'});
  assert.equal(app.errors.at(-1),'');
  assert.equal(app.spoken[1].voice,english);
  app.spoken[1].onerror({error:'synthesis-failed'});
  assert.match(app.errors.at(-1),/could not be read aloud/);
});

test('unsupported speech shows a recoverable message rather than throwing', () => {
  const app=browser(), errors=[];
  const reader=app.exports.createSpeechReader(message=>errors.push(message),null);
  reader.speak('नमस्ते','hi-IN');
  assert.match(errors.at(-1),/हिन्दी आवाज़ उपलब्ध नहीं/);
});

function hookHarness() {
  let cursor=0;
  const slots=[], effects=[], calls=[];
  const hooks={
    useState(initial) { const i=cursor++; if(!(i in slots)) slots[i]=initial; return [slots[i],value=>slots[i]=value]; },
    useRef(initial) { const i=cursor++; return slots[i]||(slots[i]={current:initial}); },
    useEffect(effect,deps) {
      const i=cursor++,old=slots[i];
      if(!old||deps.some((value,index)=>value!==old.deps[index])) effects.push(()=>{
        old?.cleanup?.(); slots[i]={deps,cleanup:effect()};
      });
    }
  };
  const controller={speak:(text,locale)=>calls.push({text,locale}),cancel:()=>calls.push({cancel:true})};
  const speech=browser().exports;
  const narration=loadSource('services/spoken-response.ts');
  const {useReadAloud}=load('hooks/useReadAloud.ts',{
    'preact/hooks':hooks,'../services/read-aloud':{...speech,createSpeechReader:()=>controller},
    '../services/spoken-response':narration,
    '../services/reply-localization':loadSource('services/reply-localization.ts')
  });
  return {calls,render(enabled,language,paused=false){
    cursor=0;const result=useReadAloud(enabled,language,paused); effects.splice(0).forEach(fn=>fn()); return result;
  },unmount(){slots.forEach(slot=>slot?.cleanup?.());}};
}

test('pending replies use the current language and respect the latest read-aloud toggle', () => {
  const app=hookHarness();
  const oldRender=app.render(true,'en-IN');
  app.render(true,'hi-IN');
  oldRender.speak({assistantText:'आपका बैलेंस',banking:null});
  assert.equal(app.calls.at(-1).locale,'hi-IN');
  app.render(false,'hi-IN');
  const count=app.calls.length;
  oldRender.speak('नया जवाब');
  assert.equal(app.calls.length,count);
  oldRender.speak('पिछला जवाब',true);
  assert.equal(app.calls.at(-1).locale,'hi-IN');
  app.render(true,'en-IN');
  oldRender.speak('Your balance');
  assert.equal(app.calls.at(-1).locale,'en-IN');
});

test('language changes, microphone use and leaving chat cancel reading', () => {
  const app=hookHarness(),old=app.render(true,'en-IN');
  app.render(true,'hi-IN');
  assert.equal(app.calls.at(-1).cancel,true);
  app.render(true,'hi-IN',true);
  const count=app.calls.length;
  old.speak('नमस्ते',true);
  assert.equal(app.calls.length,count);
  app.unmount();
  assert.equal(app.calls.at(-1).cancel,true);
  const afterUnmount=app.calls.length;
  old.speak('नमस्ते',true);
  assert.equal(app.calls.length,afterUnmount);
});

test('Hindi account narration keeps only the last four digits and exact paise', () => {
  const {spokenResponse}=loadSource('services/spoken-response.ts');
  const reply={assistantText:'ये आपके खातों के बैलेंस हैं।',banking:{type:'ACCOUNTS',accounts:[
    {displayName:'मुख्य खाता',accountNumberMasked:'90000014291',availableBalance:'95280.05',currencyCode:'INR'}
  ]}};
  const hindiText=spokenResponse(reply,'hi-IN');
  assert.match(hindiText,/उपलब्ध बैलेंस 95,280\.05 रुपये/);
  assert.match(hindiText,/अंतिम अंक 4 2 9 1/);
  assert.doesNotMatch(hindiText,/Available balance|account ending|90000014291/);
  assert.match(spokenResponse(reply,'en-IN'),/Available balance ₹95,280\.05/);
  assert.equal(reply.assistantText,'ये आपके खातों के बैलेंस हैं।');
});

test('Hindi product narration localizes labels and statuses without inventing debit-card balances', () => {
  const {spokenResponse}=loadSource('services/spoken-response.ts');
  const cases=[
    {type:'TRANSACTIONS',transactions:[{merchantName:null,type:'TRANSFER',amount:'-10.50',currencyCode:'INR',status:'FAILED'}]},
    {type:'MANDATES',mandates:[{payee:'सेवा',limit:'299',currencyCode:'INR',status:'ACTIVE'}]},
    {type:'BILLS',bills:[{billerName:'बिजली',amount:'100',currencyCode:'INR',status:'DUE'}]},
    {type:'CARDS',cards:[{displayName:'डेबिट कार्ड',cardType:'DEBIT',currencyCode:'INR',status:'ACTIVE'}]},
    {type:'LOANS',loans:[{displayName:'ऋण',nextEmi:'500',currencyCode:'INR',status:'ACTIVE'}]},
    {type:'BENEFICIARIES',beneficiaries:[{displayName:'नाम',status:'ACTIVE'}]},
    {type:'SCHEDULED_PAYMENTS',payments:[{payee:'सेवा',amount:'100',currencyCode:'INR',status:'SCHEDULED',dueAt:'2026-09-25'}]}
  ];
  for(const banking of cases) {
    const result=spokenResponse({assistantText:'आपकी जानकारी।',banking},'hi-IN');
    assert.doesNotMatch(result,/ACTIVE|FAILED|DUE|SCHEDULED|TRANSFER|next EMI|up to|outstanding|undefined|NaN/);
    if(banking.type==='CARDS') assert.doesNotMatch(result,/बकाया|रुपये/);
    if(banking.type==='TRANSACTIONS') assert.match(result,/− 10\.50 रुपये, असफल/);
  }
});

test('read-aloud confirmation is Hindi after choosing Hindi', () => {
  const locale=loadSource('services/locale.ts');
  locale.setLocale('hi-IN');
  try {
    assert.match(locale.t('Spoken replies are on. Say ‘stop reading’ to turn them off.'),/हिन्दी में सुनाए/);
    assert.match(locale.t('Spoken replies are off.'),/बंद/);
  } finally {locale.setLocale('en-IN');}
});

test('English balance replies are translated before reaching Hindi speech, including Read last reply', () => {
  const app=hookHarness();
  const oldRender=app.render(true,'en-IN');
  app.render(true,'hi-IN');
  const reply={assistantText:'Here are your available balances.',banking:{type:'ACCOUNTS',accounts:[
    {displayName:'Primary account',accountNumberMasked:'•••• 4291',availableBalance:'95280',currencyCode:'INR'},
    {displayName:'Demo everyday account',accountNumberMasked:'•••• 1837',availableBalance:'1000',currencyCode:'INR'}
  ]}};
  const snapshot=JSON.stringify(reply);
  oldRender.speak(reply);
  assert.equal(app.calls.at(-1).locale,'hi-IN');
  const narration=app.calls.at(-1).text;
  assert.match(narration,/ये आपके खातों में उपलब्ध बैलेंस हैं/);
  assert.match(narration,/मुख्य खाता.*4 2 9 1.*95,280 रुपये/);
  assert.match(narration,/डेमो रोज़मर्रा का खाता.*1 8 3 7.*1,000 रुपये/);
  assert.doesNotMatch(narration,/[a-z]/i);
  app.render(false,'hi-IN');
  oldRender.speak(reply,true);
  assert.equal(app.calls.at(-1).text,narration);
  oldRender.speak('Here are your latest transactions.',true);
  assert.equal(app.calls.at(-1).text,'ये आपके हाल के लेन-देन हैं।');
  app.render(true,'en-IN');
  oldRender.speak(reply);
  assert.match(app.calls.at(-1).text,/Here are your available balances.*Primary account/);
  assert.equal(JSON.stringify(reply),snapshot);
});
