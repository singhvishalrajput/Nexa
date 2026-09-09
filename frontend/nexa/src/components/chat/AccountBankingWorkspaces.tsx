import { h } from "preact";
import { useState } from "preact/hooks";

type WorkspaceProps = { onBack: () => void; onAsk: (prompt: string) => void };

type ConnectedAccount = {
  id: string;
  bank: string;
  label: string;
  type: string;
  last4: string;
  balance: number;
  primary: boolean;
  status: "connected" | "attention";
  updated: string;
};

const initialAccounts: ConnectedAccount[] = [
  { id: "nexa-4291", bank: "Nexa Bank", label: "Primary account", type: "Savings", last4: "4291", balance: 96280, primary: true, status: "connected", updated: "Updated just now" },
  { id: "horizon-1186", bank: "Horizon Bank", label: "Savings reserve", type: "Savings", last4: "1186", balance: 32140, primary: false, status: "connected", updated: "Updated 4 min ago" },
  { id: "hdfc-7310", bank: "HDFC Bank", label: "Travel credit", type: "Credit", last4: "7310", balance: -25220, primary: false, status: "attention", updated: "Statement due 15 Sep" }
];

const money = (value: number) => `${value < 0 ? "−" : ""}₹${Math.abs(value).toLocaleString("en-IN")}`;
const emptyAccount = { bank: "", label: "", type: "Savings", last4: "" };

export function ConnectedAccounts({ onBack, onAsk }: WorkspaceProps) {
  const [accounts, setAccounts] = useState<ConnectedAccount[]>(initialAccounts);
  const [selectedId, setSelectedId] = useState(initialAccounts[0].id);
  const [addOpen, setAddOpen] = useState(false);
  const [verified, setVerified] = useState(false);
  const [form, setForm] = useState(emptyAccount);
  const [removeId, setRemoveId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const selected = accounts.find((account) => account.id === selectedId) || accounts[0];
  const total = accounts.reduce((sum, account) => sum + account.balance, 0);
  const removable = accounts.find((account) => account.id === removeId);

  const openAdd = () => { setForm(emptyAccount); setVerified(false); setAddOpen(true); };
  const addAccount = (event: Event) => {
    event.preventDefault();
    if (!verified || !form.bank.trim() || !form.label.trim() || form.last4.length !== 4) return;
    const account: ConnectedAccount = { id: `account-${Date.now()}`, bank: form.bank.trim(), label: form.label.trim(), type: form.type, last4: form.last4, balance: 0, primary: false, status: "connected", updated: "Connected just now" };
    setAccounts((current) => [...current, account]);
    setSelectedId(account.id);
    setAddOpen(false);
    setNotice(`${account.label} was connected successfully.`);
  };
  const removeAccount = () => {
    if (!removable || removable.primary) return;
    const remaining = accounts.filter((account) => account.id !== removable.id);
    setAccounts(remaining);
    if (selectedId === removable.id) setSelectedId(remaining[0]?.id || "");
    setNotice(`${removable.label} was removed from Nexa.`);
    setRemoveId(null);
  };

  return <section class="nexa-experience-view nexa-connected-view" aria-label="Connected bank accounts">
    <header class="nexa-experience-heading"><div><p>Connected accounts</p><h1>Every account. One view.</h1><span>Review balances, account details and connection health across your banks.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-banking-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <div class="nexa-connected-summary"><div><span>Across connected accounts</span><strong>{money(total)}</strong><small>Net position from {accounts.length} accounts</small></div><div><span>Available cash</span><strong>{money(accounts.filter((item) => item.balance > 0).reduce((sum, item) => sum + item.balance, 0))}</strong><small>Savings and current accounts</small></div><div><span>Connection health</span><strong>{accounts.filter((item) => item.status === "connected").length}/{accounts.length}</strong><small>Accounts reporting normally</small></div></div>
    <div class="nexa-connected-layout">
      <section class="nexa-account-directory"><header><div><span>Your banks</span><h2>Connected accounts</h2></div><button type="button" onClick={openAdd}>＋ Add account</button></header><div>{accounts.map((account) => <button class={account.id === selected.id ? "is-selected" : ""} type="button" onClick={() => setSelectedId(account.id)} key={account.id}><i>{account.bank.split(" ").map((word) => word[0]).slice(0,2).join("")}</i><span><strong>{account.label}</strong><small>{account.bank} · •••• {account.last4}</small></span><b>{money(account.balance)}</b><em class={`is-${account.status}`}>{account.status}</em></button>)}</div></section>
      {selected && <article class="nexa-account-detail"><header><div><span>{selected.bank}</span><h2>{selected.label}</h2><small>{selected.type} account · •••• {selected.last4}</small></div><em class={`is-${selected.status}`}>{selected.status}</em></header><div class="nexa-account-detail-balance"><span>Current balance</span><strong>{money(selected.balance)}</strong><small>{selected.updated}</small></div><dl><div><dt>Account type</dt><dd>{selected.type}</dd></div><div><dt>Account role</dt><dd>{selected.primary ? "Primary" : "Secondary"}</dd></div><div><dt>Data access</dt><dd>Balances & transactions</dd></div><div><dt>Last four digits</dt><dd>•••• {selected.last4}</dd></div></dl><section><span>Recent activity</span><p><b>{selected.type === "Credit" ? "Statement generated" : "Account synchronized"}</b><small>{selected.updated}</small></p><p><b>{selected.type === "Credit" ? "Payment received" : "Balance refreshed"}</b><small>{selected.type === "Credit" ? "28 Aug · ₹18,000" : "No connection issues"}</small></p></section><footer><button type="button" onClick={() => onAsk(`Show recent transactions for my ${selected.label}`)}>View transactions</button><button class="is-remove" type="button" disabled={selected.primary} onClick={() => setRemoveId(selected.id)}>{selected.primary ? "Primary account" : "Remove account"}</button></footer></article>}
    </div>
    {addOpen && <div class="nexa-banking-dialog-backdrop" onClick={() => setAddOpen(false)}><section class="nexa-banking-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-connect-account-title" onClick={(event) => event.stopPropagation()}><header><div><span>Connect an account</span><h2 id="nexa-connect-account-title">Add another bank.</h2><p>Choose the account and review the access Nexa will request.</p></div><button type="button" aria-label="Close account connection" onClick={() => setAddOpen(false)}>×</button></header><form onSubmit={addAccount}><label>Bank name<input aria-label="Bank name" value={form.bank} onInput={(event) => { setForm({ ...form, bank: (event.currentTarget as HTMLInputElement).value }); setVerified(false); }} placeholder="e.g. ICICI Bank" required /></label><div><label>Account label<input aria-label="Account label" value={form.label} onInput={(event) => { setForm({ ...form, label: (event.currentTarget as HTMLInputElement).value }); setVerified(false); }} placeholder="Salary account" required /></label><label>Account type<select aria-label="Account type" value={form.type} onChange={(event) => { setForm({ ...form, type: (event.currentTarget as HTMLSelectElement).value }); setVerified(false); }}><option>Savings</option><option>Current</option><option>Credit</option></select></label></div><label>Last four account digits<input aria-label="Last four account digits" inputMode="numeric" maxLength={4} value={form.last4} onInput={(event) => { setForm({ ...form, last4: (event.currentTarget as HTMLInputElement).value.replace(/\D/g, "").slice(0,4) }); setVerified(false); }} placeholder="0000" required /></label><div class="nexa-connection-consent"><span>Access requested</span><p>Account identity, balances and transaction history. Nexa cannot move money without a separate approval.</p></div><button class={`nexa-verify-connection ${verified ? "is-verified" : ""}`} type="button" disabled={!form.bank.trim() || !form.label.trim() || form.last4.length !== 4} onClick={() => setVerified(true)}>{verified ? "✓ Bank connection verified" : "Verify connection"}</button><button class="nexa-banking-submit" type="submit" disabled={!verified}>Connect account</button></form></section></div>}
    {removeId && removable && <div class="nexa-banking-dialog-backdrop" onClick={() => setRemoveId(null)}><section class="nexa-banking-confirm" role="dialog" aria-modal="true" aria-labelledby="nexa-remove-account-title" onClick={(event) => event.stopPropagation()}><span>Remove connection</span><h2 id="nexa-remove-account-title">Remove {removable.label}?</h2><p>Nexa will stop refreshing this account. Its existing chat history will remain available.</p><footer><button type="button" onClick={() => setRemoveId(null)}>Keep connected</button><button class="is-destructive" type="button" onClick={removeAccount}>Remove account</button></footer></section></div>}
  </section>;
}

type Beneficiary = { id: string; name: string; method: "Bank account" | "UPI"; detail: string; bank: string; verified: boolean; lastPaid: string };
const initialBeneficiaries: Beneficiary[] = [
  { id: "rahul", name: "Rahul Mehta", method: "UPI", detail: "rahul@upi", bank: "HDFC Bank", verified: true, lastPaid: "₹5,000 · 24 Aug" },
  { id: "ananya", name: "Ananya Rao", method: "Bank account", detail: "•••• 2841", bank: "ICICI Bank", verified: true, lastPaid: "₹12,500 · 10 Aug" },
  { id: "rent", name: "Maple Residency", method: "Bank account", detail: "•••• 7042", bank: "Axis Bank", verified: true, lastPaid: "₹22,000 · 05 Aug" }
];
const emptyBeneficiary = { name: "", method: "UPI" as "Bank account" | "UPI", detail: "", bank: "" };

export function BeneficiaryManager({ onBack, onAsk }: WorkspaceProps) {
  const [beneficiaries, setBeneficiaries] = useState<Beneficiary[]>(initialBeneficiaries);
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState(initialBeneficiaries[0].id);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(emptyBeneficiary);
  const [verified, setVerified] = useState(false);
  const [removeId, setRemoveId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const selected = beneficiaries.find((item) => item.id === selectedId) || beneficiaries[0];
  const visible = beneficiaries.filter((item) => item.name.toLowerCase().includes(query.toLowerCase()) || item.detail.toLowerCase().includes(query.toLowerCase()));
  const removing = beneficiaries.find((item) => item.id === removeId);

  const beginAdd = () => { setEditingId(null); setForm(emptyBeneficiary); setVerified(false); setEditorOpen(true); };
  const beginEdit = (item: Beneficiary) => { setEditingId(item.id); setForm({ name: item.name, method: item.method, detail: item.detail.replace(/[•\s]/g, ""), bank: item.bank }); setVerified(true); setEditorOpen(true); };
  const saveBeneficiary = (event: Event) => {
    event.preventDefault();
    if (!verified || !form.name.trim() || !form.detail.trim()) return;
    const masked = form.method === "Bank account" ? `•••• ${form.detail.replace(/\D/g, "").slice(-4)}` : form.detail.trim();
    if (editingId) {
      setBeneficiaries((current) => current.map((item) => item.id === editingId ? { ...item, name: form.name.trim(), method: form.method, detail: masked, bank: form.bank.trim() || item.bank, verified: true } : item));
      setNotice(`${form.name.trim()} was updated.`);
    } else {
      const item: Beneficiary = { id: `beneficiary-${Date.now()}`, name: form.name.trim(), method: form.method, detail: masked, bank: form.bank.trim() || "UPI", verified: true, lastPaid: "No payments yet" };
      setBeneficiaries((current) => [...current, item]);
      setSelectedId(item.id);
      setNotice(`${item.name} was added as a verified beneficiary.`);
    }
    setEditorOpen(false);
  };
  const removeBeneficiary = () => {
    if (!removing) return;
    const remaining = beneficiaries.filter((item) => item.id !== removing.id);
    setBeneficiaries(remaining);
    if (selectedId === removing.id) setSelectedId(remaining[0]?.id || "");
    setNotice(`${removing.name} was removed.`);
    setRemoveId(null);
  };

  return <section class="nexa-experience-view nexa-beneficiary-view" aria-label="Beneficiary management">
    <header class="nexa-experience-heading"><div><p>Beneficiaries</p><h1>People you pay, organized.</h1><span>Add and verify recipients before preparing a secure transfer.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-banking-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <div class="nexa-beneficiary-toolbar"><label><span class="sr-only">Search beneficiaries</span><input value={query} onInput={(event) => setQuery((event.currentTarget as HTMLInputElement).value)} placeholder="Search name, UPI ID or account" /></label><button type="button" onClick={beginAdd}>＋ Add beneficiary</button></div>
    <div class="nexa-beneficiary-layout"><section class="nexa-beneficiary-list"><header><span>{beneficiaries.length} verified recipients</span><strong>Saved beneficiaries</strong></header><div>{visible.map((item) => <button class={item.id === selected?.id ? "is-selected" : ""} type="button" onClick={() => setSelectedId(item.id)} key={item.id}><i>{item.name.split(" ").map((word) => word[0]).slice(0,2).join("")}</i><span><strong>{item.name}</strong><small>{item.method} · {item.detail}</small></span><em>{item.verified ? "Verified" : "Review"}</em></button>)}{!visible.length && <p>No beneficiaries match your search.</p>}</div></section>{selected && <article class="nexa-beneficiary-detail"><header><i>{selected.name.split(" ").map((word) => word[0]).slice(0,2).join("")}</i><div><span>Verified beneficiary</span><h2>{selected.name}</h2><small>{selected.method} · {selected.detail}</small></div></header><dl><div><dt>Payment method</dt><dd>{selected.method}</dd></div><div><dt>Bank or network</dt><dd>{selected.bank}</dd></div><div><dt>Verification</dt><dd class="is-verified">Verified</dd></div><div><dt>Last payment</dt><dd>{selected.lastPaid}</dd></div></dl><div class="nexa-beneficiary-safety"><span>Recipient check</span><p>Name and destination details have been reviewed. Always confirm the recipient before sending money.</p></div><footer><button type="button" onClick={() => onAsk(`Send ₹5,000 to ${selected.name}`)}>Prepare transfer</button><button type="button" onClick={() => beginEdit(selected)}>Edit</button><button class="is-remove" type="button" onClick={() => setRemoveId(selected.id)}>Remove</button></footer></article>}</div>
    {editorOpen && <div class="nexa-banking-dialog-backdrop" onClick={() => setEditorOpen(false)}><section class="nexa-banking-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-beneficiary-editor-title" onClick={(event) => event.stopPropagation()}><header><div><span>{editingId ? "Edit beneficiary" : "Add beneficiary"}</span><h2 id="nexa-beneficiary-editor-title">{editingId ? "Update recipient details." : "Who would you like to pay?"}</h2><p>Recipient details must be verified before they can be saved.</p></div><button type="button" aria-label="Close beneficiary editor" onClick={() => setEditorOpen(false)}>×</button></header><form onSubmit={saveBeneficiary}><label>Recipient name<input aria-label="Recipient name" value={form.name} onInput={(event) => { setForm({ ...form, name: (event.currentTarget as HTMLInputElement).value }); setVerified(false); }} placeholder="Full name or business" required /></label><div><label>Payment method<select aria-label="Payment method" value={form.method} onChange={(event) => { setForm({ ...form, method: (event.currentTarget as HTMLSelectElement).value as "Bank account" | "UPI", detail: "" }); setVerified(false); }}><option>UPI</option><option>Bank account</option></select></label><label>{form.method === "UPI" ? "UPI ID" : "Account number"}<input aria-label={form.method === "UPI" ? "UPI ID" : "Account number"} value={form.detail} onInput={(event) => { setForm({ ...form, detail: (event.currentTarget as HTMLInputElement).value }); setVerified(false); }} placeholder={form.method === "UPI" ? "name@bank" : "Account number"} required /></label></div><label>Bank name <small>Optional for UPI</small><input aria-label="Beneficiary bank name" value={form.bank} onInput={(event) => { setForm({ ...form, bank: (event.currentTarget as HTMLInputElement).value }); setVerified(false); }} placeholder="e.g. HDFC Bank" /></label><div class="nexa-connection-consent"><span>Verification check</span><p>Nexa will validate the recipient name and destination before saving it.</p></div><button class={`nexa-verify-connection ${verified ? "is-verified" : ""}`} type="button" disabled={!form.name.trim() || !form.detail.trim()} onClick={() => setVerified(true)}>{verified ? "✓ Recipient verified" : "Verify recipient"}</button><button class="nexa-banking-submit" type="submit" disabled={!verified}>{editingId ? "Save changes" : "Add beneficiary"}</button></form></section></div>}
    {removeId && removing && <div class="nexa-banking-dialog-backdrop" onClick={() => setRemoveId(null)}><section class="nexa-banking-confirm" role="dialog" aria-modal="true" aria-labelledby="nexa-remove-beneficiary-title" onClick={(event) => event.stopPropagation()}><span>Remove beneficiary</span><h2 id="nexa-remove-beneficiary-title">Remove {removing.name}?</h2><p>You will need to add and verify this recipient again before preparing another transfer.</p><footer><button type="button" onClick={() => setRemoveId(null)}>Keep beneficiary</button><button class="is-destructive" type="button" onClick={removeBeneficiary}>Remove beneficiary</button></footer></section></div>}
  </section>;
}
