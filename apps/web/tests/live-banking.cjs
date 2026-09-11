const { test } = require('node:test');
const assert = require('node:assert/strict');
const base = process.env.NEXA_TEST_API_URL || 'http://localhost:8088/api/v1';
test('live banking contracts, ownership, filtered history and non-executing payment preparation', async () => {
 assert.ok(process.env.NEXA_TEST_EMAIL && process.env.NEXA_TEST_PASSWORD,'Set NEXA_TEST_EMAIL and NEXA_TEST_PASSWORD for an existing local test login.');
 const anonymous=await fetch(base+'/accounts');assert.equal(anonymous.status,401);
 const response=await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:process.env.NEXA_TEST_EMAIL,password:process.env.NEXA_TEST_PASSWORD})});assert.equal(response.status,200);
 const auth=await response.json();const headers={Authorization:'Bearer '+auth.accessToken,'Content-Type':'application/json'};
 async function read(path){const response=await fetch(base+path,{headers});assert.equal(response.status,200,path);return response.json();}
 try {
  const me=await read('/me');assert.ok(me.id);
  const accounts=await read('/accounts');assert.ok(Array.isArray(accounts));
  for(const kind of ['cards','bills','beneficiaries','mandates','loans','scheduled-payments']) {const rows=await read('/'+kind);assert.ok(Array.isArray(rows),kind);if(rows.length){const detail=await read('/'+kind+'/'+encodeURIComponent(rows[0].id));assert.equal(detail.id,rows[0].id);}console.log(kind+': list and available details verified');}
  if(accounts.length){const a=accounts[0];const detail=await read('/accounts/'+a.id);assert.equal(detail.id,a.id);const balance=await read('/accounts/'+a.id+'/balance');assert.equal(balance.availableBalance,a.availableBalance);const history=await read('/transactions?accountId='+a.id+'&page=0&size=15');assert.ok(Array.isArray(history.content));if(history.content.length){const t=await read('/transactions/'+history.content[0].id);assert.equal(t.id,history.content[0].id);const searched=await read('/transactions?accountId='+a.id+'&search='+encodeURIComponent(t.reference));assert.ok(searched.content.some(row=>row.id===t.id));}
   const beneficiaries=await read('/beneficiaries');const payee=beneficiaries.find(b=>b.status==='ACTIVE');if(payee&&a.status==='ACTIVE'&&Number(a.availableBalance)>=0.01){const review=await fetch(base+'/actions/prepare',{method:'POST',headers,body:JSON.stringify({operation:'START_TRANSFER',accountId:a.id,targetId:payee.id,amount:'0.01'})});assert.equal(review.status,200);const result=await review.json();assert.equal(result.executionAvailable,false);assert.equal(result.status,'PREPARED');const after=await read('/accounts/'+a.id+'/balance');assert.equal(after.availableBalance,balance.availableBalance);}
  }
  if(auth.user.role==='CUSTOMER'){const management=await fetch(base.replace(/\/v1$/,'')+'/accounts',{headers});assert.equal(management.status,403);}
 } finally {const revoked=await fetch(base+'/auth/logout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({refreshToken:auth.refreshToken})});assert.equal(revoked.status,204);}
});
