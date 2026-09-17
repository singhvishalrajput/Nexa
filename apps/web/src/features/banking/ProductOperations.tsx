import { useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { bankApi, Product } from "./api";
import { Panel, useLoad } from "./ui";

type Kind = "mandates" | "loans";
const write = (token: string, path: string, value: unknown = {}) => authenticatedRequest<Record<string, unknown>>(path, token, {method:"POST", body:JSON.stringify(value)});

export function ProductCreate({token,kind,reload}:{token:string;kind:Kind;reload:()=>void}) {
 const inFlight=useRef(false);
 const accounts=useLoad(()=>bankApi.accounts(token),[token]);
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 async function submit(event: Event) {
  event.preventDefault();if(inFlight.current)return;inFlight.current=true;const form=new FormData(event.currentTarget as HTMLFormElement);setBusy(true);setError("");
  try {
   const common={displayName:form.get("name"),accountId:Number(form.get("account")),principal:form.get("amount"),interestRate:form.get("rate")};
   await write(token,"/"+kind,kind==="loans"?common:{sourceAccountId:Number(form.get("account")),beneficiaryAccountNumber:form.get("beneficiary"),payee:form.get("name"),limit:form.get("amount"),startDate:form.get("start"),endDate:form.get("end")||null});
   setOpen(false);reload();
  } catch(e) {setError(e instanceof Error?e.message:"Unable to create this request.");}finally{inFlight.current=false;setBusy(false);}
 }
 return <Panel title={kind==="loans"?"New loan":"New direct debit"}><button onClick={()=>setOpen(!open)}>{open?"Close":kind==="loans"?"Create loan":"Create mandate"}</button>{open&&<form class="bank-form" onSubmit={submit}>
 <label>{kind==="loans"?"Loan name":"Payee name"}<input name="name" required maxLength={120}/></label>
 <label>{kind==="loans"?"Disbursement and repayment account":"Pay from"}<select name="account" required><option value="">Choose an account</option>{accounts.data?.filter(a=>["SAVINGS","CURRENT"].includes(a.accountType)&&a.status==="ACTIVE").map(a=><option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label>
 {kind==="mandates"&&<label>Beneficiary Nexa account number<input name="beneficiary" required inputMode="numeric"/></label>}
 <label>{kind==="loans"?"Principal (INR)":"Maximum per payment (INR)"}<input name="amount" type="number" min="0.01" step="0.01" required/></label>
 {kind==="loans"?<label>Annual interest rate (%)<input name="rate" type="number" min="0" max="100" step="0.000001" required/><small>Repayments reduce principal. Interest accrual is not automated.</small></label>:<><label>Effective date<input name="start" type="date" required/></label><label>End date (optional)<input name="end" type="date"/></label></>}
 <p>{kind==="loans"?"Creation records the loan terms. Disburse it from its details page to credit your account.":"Creation records a pending authorization. Activate it from its details page before making payments."}</p>
 {error&&<p role="alert">{error}</p>}<button type="submit" disabled={busy}>{busy?"Saving…":"Create"}</button></form>}</Panel>;
}

export function ProductOperations({token,kind,product,reload}:{token:string;kind:Kind;product:Product;reload:()=>void}) {
 const inFlight=useRef(false);
 const terms=useLoad(()=>authenticatedRequest<Record<string,string | number>>("/"+kind+"/"+encodeURIComponent(product.id)+(kind==="loans"?"/account":"/authorization"),token),[token,kind,product.id,product.status]);
 const [busy,setBusy]=useState(false),[error,setError]=useState(""),[receipt,setReceipt]=useState("");
 const [amount,setAmount]=useState("");const [key,setKey]=useState(()=>crypto.randomUUID());
 const [review,setReview]=useState(false);
 async function act(operation:string,money=false) {if(inFlight.current)return;inFlight.current=true;setBusy(true);setError("");try {const result=await write(token,"/"+kind+"/"+encodeURIComponent(product.id)+"/"+operation,money?{amount,requestId:key}:{});setReceipt(result.transactionId?"Posted: "+result.transactionId:"Status updated");setReview(false);setKey(crypto.randomUUID());reload();}catch(e){setError(e instanceof Error?e.message:"The operation could not be completed.");}finally{inFlight.current=false;setBusy(false);}}
 return <Panel title={kind==="loans"?"Loan payments":"Mandate controls"}>
 {terms.data&&<p>{kind==="loans"?"Principal: INR "+(terms.data.PRINCIPAL_AMOUNT??"not recorded")+" · Annual rate: "+terms.data.INTEREST_RATE+"%":"Effective: "+terms.data.EFFECTIVE_DATE+(terms.data.END_DATE?" to "+terms.data.END_DATE:"")}</p>}
 {kind==="mandates"&&terms.data&&!terms.data.DESTINATION_ACCOUNT_ID&&<p>This imported mandate has no verified beneficiary account. Create a new mandate before executing payments.</p>}
 {kind==="loans"&&product.status==="CREATED"&&<button disabled={busy} onClick={()=>act("disburse")}>Disburse loan to linked account</button>}
 {kind==="mandates"&&["PENDING","PAUSED","ACTION_REQUIRED"].includes(product.status)&&<button disabled={busy} onClick={()=>act("activate")}>Activate mandate</button>}
 {product.status==="ACTIVE"&&<><label>Amount (INR)<input type="number" min="0.01" step="0.01" value={amount} onInput={e=>{setAmount(e.currentTarget.value);setKey(crypto.randomUUID());setReview(false);}}/></label><button disabled={busy||!amount} onClick={()=>setReview(true)}>{kind==="loans"?"Review repayment":"Review mandate payment"}</button>{review&&<div role="region" aria-label="Payment review"><p>Pay INR {amount} from the linked account {kind==="loans"?"towards this loan":"to this mandate’s beneficiary"}.</p><button disabled={busy} onClick={()=>act(kind==="loans"?"repay":"execute",true)}>Confirm payment</button><button disabled={busy} onClick={()=>setReview(false)}>Back</button></div>}</>}
 {kind==="mandates"&&!["CANCELLED","REVOKED"].includes(product.status)&&<button disabled={busy} onClick={()=>act("revoke")}>Revoke mandate</button>}
 {error&&<p role="alert">{error}</p>}{receipt&&<p role="status">{receipt}</p>}</Panel>;
}
