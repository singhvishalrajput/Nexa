/* UI-only fixture server. Never used by the application or a production build.
 * Run after npm run build: node tests/messenger-fixture.cjs
 * Visit http://localhost:8123. Authentication and balances here are test data.
 */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const root = process.env.NEXA_UI_FIXTURE_ROOT ? path.resolve(process.env.NEXA_UI_FIXTURE_ROOT) : path.resolve(__dirname, '../web');
const now = () => new Date().toISOString();
const user = { id: 'fixture-user', email: 'fixture@example.com', role: 'CUSTOMER' };
const profile = { ...user, userId: user.id, fullName: 'UI Test Customer', phoneNumber: null, status: 'ACTIVE' };
const conversations = [];
const history = new Map();
const failed = new Set();
const transferReviews = new Map();
const demoReviews = new Map();
const products = {cards:[{id:"card-demo",displayName:"Showcase credit card",cardType:"CREDIT",status:"ACTIVE",accountId:"account-1",numberMasked:"•••• 4242",outstanding:"500",currencyCode:"INR"}],bills:[{id:"bill-demo",billerName:"Demo Electricity",status:"DUE",accountId:"account-1",amount:"100",currencyCode:"INR"}],mandates:[{id:"mandate-demo",payee:"Demo Music",status:"ACTIVE",accountId:"account-1",limit:"299",currencyCode:"INR"}]};
let sequence = 0;
const account = { id: 'account-1', displayName: 'Savings', accountNumberMasked: '•••• 1234', accountType: 'SAVINGS', currencyCode: 'INR', availableBalance: '42350.75', status: 'ACTIVE', updatedAt: now() };
const transactionFixtures = Array.from({length: 24}, (_, i) => ({ id: 'transaction-' + i, accountId: account.id, reference: 'NEXA-TEST-' + String(i).padStart(8, '0'), type: 'PAYMENT', merchantName: ['Swiggy', 'ACME Ltd · Salary', 'Uber', 'Rahul Sharma', 'बिजली बिल'][i % 5], category: ['Food & dining', 'Income', 'Transport', 'Transfer', 'Utilities'][i % 5], amount: ['-485.00', '85000.00', '-320.00', '-2500.00', '-2840.00'][i % 5], currencyCode: 'INR', status: i === 3 ? 'PENDING' : i === 4 ? 'FAILED' : 'POSTED', occurredAt: new Date(Date.now() - i * 16 * 60 * 60 * 1000).toISOString() }));
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.woff': 'font/woff', '.png': 'image/png' };
const bootstrap = `<script>
window.NEXA_API_BASE_URL = '/api/v1';
sessionStorage.setItem('nexa-auth-session', JSON.stringify({accessToken:'fixture',refreshToken:'fixture',tokenType:'Bearer',expiresIn:900,user:${JSON.stringify(user)}}));
// Simulated speech exercises the UI lifecycle without microphone permission.
window.SpeechRecognition = class {
  start() { this.timer = setTimeout(() => {
    const result = [{ transcript: this.lang === 'hi-IN' ? 'मेरे खाते का बैलेंस बताइए' : 'Please tell me my balance' }];
    result.isFinal = true; this.onresult?.({results:[result]});
  }, 500); }
  stop() { clearTimeout(this.timer); setTimeout(() => this.onend?.(), 100); }
  abort() { clearTimeout(this.timer); this.onend?.(); }
};
</script>`;
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost:8123');
  const reply = (value, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
  if (url.pathname.startsWith('/api/')) {
    let body = '';
    for await (const chunk of req) body += chunk;
    const input = body ? JSON.parse(body) : {};
    if (url.pathname.endsWith('/me')) { if (req.method === 'PATCH' || req.method === 'PUT') Object.assign(profile,input); return reply(profile); }
    if (url.pathname === '/api/v1/accounts') return reply([{...account, ledgerBalance: account.availableBalance}, {...account,id:'account-2',displayName:'Reserve',accountNumberMasked:'•••• 5678',availableBalance:'5000.00',ledgerBalance:'5000.00'}]);
    if (url.pathname === '/api/v1/accounts/account-1') return reply({...account, ledgerBalance: account.availableBalance});
    if (url.pathname === '/api/v1/transactions') {
      const query = url.searchParams;
      const rows = transactionFixtures.filter(item =>
        (!query.get('accountId') || item.accountId === query.get('accountId')) &&
        (!query.get('search') || (item.merchantName + ' ' + item.reference).toLowerCase().includes(query.get('search').toLowerCase())) &&
        (!query.get('category') || item.category.toLowerCase() === query.get('category').toLowerCase()) &&
        (!query.get('direction') || (query.get('direction') === 'CREDIT' ? Number(item.amount) > 0 : Number(item.amount) < 0)) &&
        (!query.get('from') || item.occurredAt.slice(0, 10) >= query.get('from')) &&
        (!query.get('to') || item.occurredAt.slice(0, 10) <= query.get('to')));
      const page = Math.max(0, Number(query.get('page') || 0)), size = Math.max(1, Number(query.get('size') || 15));
      return reply({content: rows.slice(page * size, (page + 1) * size), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size)});
    }
    if (/^\/api\/v1\/transactions\/transaction-/.test(url.pathname)) return reply(transactionFixtures.find(t=>url.pathname.endsWith('/'+t.id)) || {},200);
    if (url.pathname === '/api/v1/beneficiaries') return reply([{id:'payee-1',displayName:'Test payee',name:'Test payee',accountNumberMasked:'•••• 5678',status:'ACTIVE'}]);
    if (url.pathname === '/api/v1/demo/actions/prepare') {
      const review = {...input,id:randomUUID(),status:'REVIEW',currencyCode:'INR',simulated:true,expiresAt:new Date(Date.now()+600000).toISOString()}; demoReviews.set(review.id,review); return reply(review);
    }
    if (url.pathname === '/api/v1/demo/actions') return reply([...demoReviews.values()].reverse());
    const demoMatch = url.pathname.match(/^\/api\/v1\/demo\/actions\/([^/]+)(?:\/(confirm|cancel))?$/);
    if (demoMatch) {
      const review=demoReviews.get(demoMatch[1]); if(!review)return reply({},404);
      if(review.status==='REVIEW' && demoMatch[2]) {review.status=demoMatch[2]==='confirm'?'SIMULATED':'CANCELLED';if(review.status==='SIMULATED'){review.reference='DEMO-'+review.id;review.completedAt=now();}}
      return reply(review);
    }
    const productMatch=url.pathname.match(/^\/api\/v1\/(cards|bills|mandates|loans|scheduled-payments)(?:\/([^/]+))?$/);
    if(productMatch) {const rows=products[productMatch[1]]||[];return reply(productMatch[2]?rows.find(p=>p.id===productMatch[2])||{}:rows);}
    if (url.pathname === '/api/v1/money-transfers/prepare') {
      const id = randomUUID();
      const review = {id, sourceAccountId:input.sourceAccountId,sourceName:'Savings',sourceMasked:'•••• 1234',recipientName:'Test recipient',destinationMasked:'•••• 5678',amount:input.amount,currencyCode:'INR',status:'READY',reference:null,expiresAt:new Date(Date.now()+300000).toISOString(),completedAt:null};
      transferReviews.set(id,review);return reply(review);
    }
    const transferMatch = url.pathname.match(/^\/api\/v1\/money-transfers\/([^/]+)(\/confirm)?$/);
    if (transferMatch) {
      const review=transferReviews.get(transferMatch[1]);if(!review)return reply({},404);
      if(transferMatch[2] && review.status!=='COMPLETED') {review.status='COMPLETED';review.reference='TX-FIXTURE-'+review.id;review.completedAt=now();account.availableBalance=String(Number(account.availableBalance)-Number(review.amount));}
      return reply(review);
    }
    if (url.pathname === '/api/v1/actions/prepare') return reply({...input,status:'PREPARED',executionAvailable:false,confirmationRequired:true,currencyCode:'INR'});
    if (url.pathname === '/api/v1/auth/login') return reply({},401);
    if (url.pathname.endsWith('/auth/logout')) return reply({}, 204);
    if (/\/accounts\/[^/]+\/transactions$/.test(url.pathname)) {
      const page = Number(url.searchParams.get('page') || 0);
      return reply({content:transactionFixtures.slice(page * 10, (page + 1) * 10), page, size:10, totalElements:24, totalPages:3});
    }
    if (url.pathname === '/api/v1/conversations') {
      if (req.method === 'POST') {
        const item = { id: randomUUID(), title: 'New conversation', createdAt: now() };
        conversations.unshift(item); history.set(item.id, []); return reply(item);
      }
      return reply(conversations);
    }
    const match = url.pathname.match(/conversations\/([^/]+)(\/turns|\/actions\/([^/]+))?$/);
    if (match) {
      if (match[3]) { input.action = {actionId:match[3],type:input.type,value:input.value}; input.source = "TEXT"; input.text = input.type === "CONFIRM" ? "Confirm transfer" : "Cancel"; }
      const turns = history.get(match[1]) || [];
      if (req.method === 'DELETE') { history.delete(match[1]); const i = conversations.findIndex(c => c.id === match[1]); if (i >= 0) conversations.splice(i, 1); return reply({}, 204); }
      if (req.method === 'GET') return reply(turns.filter(t => +t.id < +(url.searchParams.get('before') || Number.MAX_SAFE_INTEGER)).slice(-30).reverse());
      const existing = turns.find(t => t.clientId === input.clientId);
      if (existing) return reply(existing);
      if (input.text === 'test failure' && !failed.has(input.clientId)) { failed.add(input.clientId); return reply({ detail: 'Internal fixture failure: should not appear in UI' }, 503); }
      const assistantText = input.text === 'test long'
        ? ('आपके खाते की जानकारी सुरक्षित है। Your recent transactions are listed below.\n• Grocery payment: INR 520\n• Salary credit: INR 40,000\n\n').repeat(18)
        : input.source === 'VOICE' ? 'This is a UI test reply. No audio was saved.'
        : 'Your savings account balance is INR 42,350.\n\nThis is a UI test fixture, not a live bank balance.';
      let banking = null;
      if (/balance|बैलेंस/i.test(input.text)) banking = {version:1, type:'ACCOUNTS', accounts:[account, {...account, id:'account-2', displayName:'Everyday', accountType:'CURRENT', accountNumberMasked:'•••• 6789', availableBalance:'150000.20'}]};
      if (/transactions|ट्रांज़ैक्शन|लेन-देन/i.test(input.text)) banking = {version:1, type:'TRANSACTIONS', account, transactions:transactionFixtures.slice(0, 5), totalElements:24};
      if (input.text === 'test mandates') banking = {version:1, type:'MANDATES', mandates:['ACTIVE','PAUSED','EXPIRED','CANCELLED','ACTION_REQUIRED'].map((status,i)=>({id:String(i), payee:['Netflix','Internet','Insurance','Music','Electricity'][i], status, limit:'649', currencyCode:'INR', frequency:'MONTHLY', nextDebit: i === 0 ? now() : undefined, accountName:'Savings', accountNumberMasked:'1234567890121234'}))};
      if (/spending|खर्च/i.test(input.text)) banking = {version:1,type:'INSIGHTS',meta:{from:'2026-09-01',to:'2026-09-16',categories:[{category:'Food & dining',currency:'INR',amount:'5840.50'},{category:'Transport',currency:'INR',amount:'2320.00'},{category:'Shopping',currency:'INR',amount:'3800.00'}]}};
      let workflow = null;
      const previous = [...turns].reverse().find(t=>t.workflow)?.workflow;
      if (input.action && previous && previous.id === input.action.actionId) workflow = {...previous,status:input.action.type==='CONFIRM'?'COMPLETED':'CANCELLED',message:input.action.type==='CONFIRM'?'UI fixture transfer completed. No real money moved.':'UI fixture proposal cancelled.',reference:input.action.type==='CONFIRM'?'TX-FIXTURE-REVIEW':null};
      else if (/between my accounts|खातों के बीच/i.test(input.text)) workflow={version:1,id:randomUUID(),operation:'OWN_TRANSFER',status:'REVIEW',accountLabel:'Savings •••• 1234',targetLabel:'Reserve •••• 5678',amount:'2000.00',currency:'INR',message:'Review this UI fixture transfer. No real money will move.',choices:[],expiresAt:new Date(Date.now()+300000).toISOString(),confirmationRequired:true,executionAvailable:true};
      const turn = { workflow, id: String(++sequence), clientId: input.clientId, source: input.source, intent: 'BALANCE', userText: input.source === 'VOICE' ? 'Check available account balances.' : input.text, assistantText:banking ? 'Here is your banking summary. (UI test data)' : assistantText, banking, createdAt: now() };
      turns.push(turn); history.set(match[1], turns);
      const item = conversations.find(c => c.id === match[1]); if (item) item.title = 'UI test conversation';
      setTimeout(() => reply(turn), input.text === 'test slow' ? 12000 : 700);
      return;
    }
    return reply({}, 404);
  }
  const target = path.resolve(root, '.' + decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname));
  if (!target.startsWith(root + path.sep)) { res.writeHead(403); return res.end(); }
  try {
    let data = fs.readFileSync(target);
    if (target.endsWith('index.html')) data = Buffer.from(data.toString().replace('</head>', bootstrap + '</head>'));
    res.writeHead(200, { 'Content-Type': (mime[path.extname(target)] || 'application/octet-stream') + (['.html', '.js', '.css'].includes(path.extname(target)) ? '; charset=utf-8' : ''), 'Cache-Control': 'no-store' });
    res.end(data);
  } catch (_) { res.writeHead(404); res.end(); }
});
server.listen(Number(process.env.NEXA_UI_FIXTURE_PORT || 8123), '127.0.0.1', () => console.log('UI fixtures only: http://localhost:' + (process.env.NEXA_UI_FIXTURE_PORT || 8123)));
