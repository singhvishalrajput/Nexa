import { useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { authenticatedRequest } from "../../services/auth";
import { Panel } from "./ui";
// Convenience choices, not an account-verification or provider-support list.
const bankNames = ["State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank", "Kotak Mahindra Bank",
    "Punjab National Bank", "Bank of Baroda", "Canara Bank", "Union Bank of India", "Bank of India",
    "Indian Bank", "Bank of Maharashtra", "IDFC FIRST Bank", "Federal Bank", "IndusInd Bank", "YES Bank"];
export function PayeeForm({token, id, name = "", reload}: {token: string; id?: string; name?: string; reload: () => void}) {
    const [open, setOpen] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const [external, setExternal] = useState(false);
    const [selectedBank, setSelectedBank] = useState("");
    const [dirty, setDirty] = useState(false);
    const lock = useRef(false);
    useNavigationGuard(open && dirty, busy);
    async function save(event: Event) {
        event.preventDefault();
        if (lock.current) return;
        const form = new FormData(event.currentTarget as HTMLFormElement);
        const value = (field: string) => String(form.get(field) || "").trim();
        const accountNumber = external ? value("number").toUpperCase() : value("number");
        const confirmation = value("confirmation").toUpperCase();
        const ifsc = value("ifsc").toUpperCase();
        const bankName = value("bank") === "OTHER" ? value("customBank") : value("bank");
        setError("");
        if (external && (!/^[A-Z0-9]{9,18}$/.test(accountNumber) || accountNumber !== confirmation)) {
            setError("Enter the same 9–18 character bank account number in both fields, using only letters and digits."); return;
        }
        if (external && !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(ifsc)) { setError("Enter a valid 11-character IFSC, for example HDFC0001234."); return; }
        if (external && (Array.from(value("recipient")).length > 100 || !new RegExp("^[\\p{L}\\p{M} ]+$", "u").test(value("recipient")))) {
            setError("Recipient name must contain at most 100 letters and spaces."); return;
        }
        if (!external && !/^[0-9]{6,30}$/.test(accountNumber)) { setError("Enter the full Nexa account number using 6–30 digits."); return; }
        if (!value("name") || (external && (!value("recipient") || !bankName))) { setError("Complete the payee and bank details."); return; }
        lock.current = true; setBusy(true);
        try {
            await authenticatedRequest(external ? "/external-payees" : "/beneficiaries" + (id ? "/" + encodeURIComponent(id) : ""), token, {
                method: id ? "PUT" : "POST", body: JSON.stringify(external ? {
                    displayName: value("name"), recipientName: value("recipient"), bankName,
                    accountNumber, accountNumberConfirmation: confirmation, ifsc
                } : {displayName: value("name"), accountNumber})
            });
            setOpen(false); setDirty(false); setSelectedBank(""); reload();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save payee."); }
        finally { lock.current = false; setBusy(false); }
    }
    return <Panel className="bank-create-panel" title={id ? "Link verified Nexa account" : "Save a payee"} action={<button class="bank-button secondary" type="button" aria-expanded={open} aria-controls="payee-form" onClick={() => { setOpen(!open); setDirty(false); setError(""); }} disabled={busy}>{open ? "Close" : id ? "Link account" : "Add payee"}</button>}>
        <p class="bank-create-description">{id ? "Link this payee to their full Nexa account number." : "Save the recipient’s account details, then select them in Payments."}</p>
        {open && <form id="payee-form" class="bank-form bank-editor-form" onSubmit={save} onInput={() => setDirty(true)}>
            <fieldset class="bank-fields-grid" disabled={busy}>
                {!id && <label>Payee bank<select value={external ? "external" : "nexa"} onChange={event => { setExternal(event.currentTarget.value === "external"); setSelectedBank(""); setError(""); setDirty(true); }}><option value="nexa">Nexa</option><option value="external">Other bank</option></select></label>}
                <label>Payee name<input name="name" defaultValue={name} required maxLength={160}/></label>
                {external && <><label>Recipient account holder name<input name="recipient" required maxLength={100}/><small>Use letters and spaces, as shown on the bank account.</small></label><label>Bank name<select name="bank" required value={selectedBank} onChange={event => { setSelectedBank(event.currentTarget.value); setError(""); setDirty(true); }}><option value="" disabled>Select a bank</option>{bankNames.map(bank => <option key={bank} value={bank}>{bank}</option>)}<option value="OTHER">Another bank</option></select></label>{selectedBank === "OTHER" && <label>Other bank name<input name="customBank" required maxLength={100}/></label>}</>}
                <label>{external ? "Bank account number" : "Full Nexa account number"}<input key={external ? "external" : "nexa"} name="number" required inputMode={external ? "text" : "numeric"} pattern={external ? "[A-Za-z0-9]{9,18}" : "[0-9]{6,30}"} maxLength={external ? 18 : 30} autoComplete="off"/></label>
                {external && <><label>Re-enter bank account number<input name="confirmation" required pattern="[A-Za-z0-9]{9,18}" maxLength={18} autoComplete="off"/></label><label>IFSC<input name="ifsc" required pattern="[A-Za-z]{4}0[A-Za-z0-9]{6}" maxLength={11} placeholder="HDFC0001234" autoComplete="off"/></label></>}
            </fieldset>
            <p class="bank-form-note">{external ? "Check the account holder name, account number and IFSC with the recipient. Other-bank transfers currently use Cashfree sandbox mode, where no real money moves. Saving a payee does not send money." : "Check this number with the recipient before saving."}</p>
            {error && <p role="alert">{error}</p>}
            <div class="bank-editor-actions"><button class="bank-button" disabled={busy}>{busy ? "Saving…" : "Save payee"}</button></div>
        </form>}
    </Panel>;
}
