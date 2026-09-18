// Run after the offline cleanup and V21 migration. Credentials live only in ignored local files.
const fs=require('node:fs'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const base=process.env.NEXA_DEMO_API||'http://localhost:8088/api/v1';
const credentialsPath='.tools/demo-access.json';
const admin=JSON.parse(fs.readFileSync('.tools/admin-access.json','utf8').replace(/^\uFEFF/,''));
async function api(path,token,body,method='POST'){
 const r=await fetch(base+path,{method:body===undefined?'GET':method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 const data=await r.json();if(!r.ok)throw Error(`${path}: ${r.status} ${JSON.stringify(data)}`);return data;
}
async function main(){
 let people;
 if(fs.existsSync(credentialsPath))people=JSON.parse(fs.readFileSync(credentialsPath,'utf8'));
 else{
  if(!process.env.NEXA_DEMO_VISHAL_PASSWORD)throw Error('Set NEXA_DEMO_VISHAL_PASSWORD to the existing Vishal password');
  people=[{email:'vishal@example.com',fullName:'Vishal Singh',password:process.env.NEXA_DEMO_VISHAL_PASSWORD},{email:'asha@example.com',fullName:'Asha Rao',password:'Nexa!'+crypto.randomBytes(15).toString('base64url')},{email:'rahul@example.com',fullName:'Rahul Sharma',password:'Nexa!'+crypto.randomBytes(15).toString('base64url')}];
  fs.writeFileSync(credentialsPath,JSON.stringify(people,null,2));
 }
 const root=process.env.NEXA_DEMO_ADMIN_TOKEN||(await api('/auth/login',null,{email:admin.email,password:admin.password})).accessToken;
 const directory=await api('/admin/accounts',root);
 for(const p of people){
  let login;
  // Only register the two named demo users. Existing credentials are never reset.
  try{login=await api('/auth/login',null,{email:p.email,password:p.password});}
  catch(e){if(p.email==='vishal@example.com'||directory.some(a=>a.customerEmail===p.email))throw e;login=await api('/auth/register',null,{email:p.email,fullName:p.fullName,password:p.password});}
  p.token=login.accessToken;
  let accounts=await api('/accounts',p.token);p.account=accounts.find(a=>a.accountType==='SAVINGS');
  if(!p.account)p.account=await api('/accounts',p.token,{displayName:p.fullName.split(' ')[0]+' Demo Savings',accountType:'SAVINGS',currencyCode:'INR',dateOfBirth:'1990-01-01',address:'Mumbai, India'});
  p.number=(await api(`/accounts/${p.account.id}/number`,p.token)).accountNumber;
  const audit=await api(`/admin/accounts/${p.account.id}/audit`,root);
  if(!audit.some(a=>a.AUDIT_REASON==='Demo opening funds')){
   const current=(await api(`/admin/accounts/${p.account.id}`,root)).account;
   const difference=250000-Number(current.balance);
   if(difference>0)await api(`/admin/accounts/${p.account.id}/adjustments`,root,{direction:'CREDIT',amount:difference.toFixed(2),reason:'Demo opening funds',requestId:crypto.randomUUID()});
  }
 }
 for(let i=0;i<people.length;i++){
  const p=people[i],other=people[(i+1)%people.length];
  const payees=await api('/beneficiaries',p.token);
  for(const recipient of people.filter(person=>person.email!==p.email)){
   const payee=payees.find(b=>b.displayName===recipient.fullName);
   if(payee)await api(`/beneficiaries/${payee.id}`,p.token,{displayName:recipient.fullName,accountNumber:recipient.number},'PUT');
   else await api('/beneficiaries',p.token,{displayName:recipient.fullName,accountNumber:recipient.number});
  }
  const mandates=await api('/mandates',p.token);
  if(!mandates.some(m=>m.payee==='Demo household payment')){
   const m=await api('/mandates',p.token,{sourceAccountId:Number(p.account.id),beneficiaryAccountNumber:other.number,payee:'Demo household payment',limit:'5000',startDate:new Date().toISOString().slice(0,10)});
   await api(`/mandates/${m.ID}/activate`,p.token,{});
  }
  if(i>0){
   const loan=await api('/loans',p.token,{accountId:Number(p.account.id),applicationKey:'demo-starter-loan',purpose:i===1?'Education loan':'Home improvement loan',amount:i===1?'60000':'40000',tenureMonths:12});
   let current=await api(`/loans/${loan.id}`,p.token);
   if(current.status==='PENDING_APPROVAL')await api(`/admin/loans/${loan.id}/approve`,root,{reason:'Demo borrower approved with a funded repayment account'});
   if(['PENDING_APPROVAL','APPROVED'].includes(current.status))await api(`/loans/${loan.id}/accept`,p.token,{});
   const schedule=await api(`/loans/${loan.id}/schedule`,p.token);
   if(schedule.length&&!schedule.some(s=>s.status==='PAID'))await api(`/loans/${loan.id}/installments/${schedule[0].id}/pay`,p.token,{});
  }
 }
 const v=people[0];
 await api('/loans',v.token,{accountId:Number(v.account.id),applicationKey:'demo-admin-review',purpose:'Home office upgrade',amount:'25000',tenureMonths:12});
 const final=await api('/admin/accounts',root);
 assert.equal(new Set(final.filter(a=>a.category==='CUSTOMER').map(a=>a.customerEmail)).size,3);
 assert.ok((await api('/admin/loans',root)).some(l=>l.EMAIL===v.email));
 const result=people.map(p=>({name:p.fullName,email:p.email,accountNumber:p.number,balance:final.find(a=>String(a.id)===String(p.account.id)).balance}));
 fs.writeFileSync('.tools/demo-seed-result.json',JSON.stringify(result,null,2));
 console.log(JSON.stringify(result,null,2));
 console.log('Demo credentials: '+credentialsPath+'; one pending application is ready for admin review.');
 // End script sessions; never persist bearer tokens alongside the demonstration credentials.
 for(const p of people)delete p.token;
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
