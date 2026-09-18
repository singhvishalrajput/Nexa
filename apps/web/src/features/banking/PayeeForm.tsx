import { useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { Panel } from "./ui";
export function PayeeForm({token,id,name="",reload}:{token:string;id?:string;name?:string;reload:()=>void}) {
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState("");
 const lock=useRef(false);
 async function save(event:Event){event.preventDefault();if(lock.current)return;lock.current=true;setBusy(true);setError("");const form=new FormData(event.currentTarget as HTMLFormElement);try{await authenticatedRequest("/beneficiaries"+(id?"/"+encodeURIComponent(id):""),token,{method:id?"PUT":"POST",body:JSON.stringify({displayName:form.get("name"),accountNumber:form.get("number")})});setOpen(false);reload();}catch(e){setError(e instanceof Error?e.message:"Unable to save payee.");}finally{lock.current=false;setBusy(false);}}
 return <Panel title={id?"Link verified Nexa account":"Add Nexa payee"}><p>Payments need the recipient’s full Nexa account number. Imported external payees cannot receive money here.</p><button onClick={()=>setOpen(!open)} disabled={busy}>{open?"Close":id?"Link account":"Add payee"}</button>{open&&<form class="bank-form" onSubmit={save}><label>Payee name<input name="name" defaultValue={name} required maxLength={160}/></label><label>Full Nexa account number<input name="number" required inputMode="numeric" pattern="[0-9]{6,30}" maxLength={30}/></label><p>Check this number with the recipient before saving.</p>{error&&<p role="alert">{error}</p>}<button class="bank-button" disabled={busy}>{busy?"Saving…":"Save payee"}</button></form>}</Panel>;
}
