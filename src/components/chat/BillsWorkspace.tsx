import { h } from "preact";
import { useState } from "preact/hooks";

export type BillDraft = { billId: string; intent: "overview" | "pay" | "schedule" | "analyze" | "autopay" | "add" };

type BillStatus = "due" | "overdue" | "scheduled" | "paid";
type Bill = {
  id: string;
  provider: string;
  category: string;
  amount: number;
  previousAmount: number;
  dueDate: string;
  dueLabel: string;
  accountRef: string;
  status: BillStatus;
  autopay: boolean;
  reminder: boolean;
  frequency?: string;
  highAlert?: boolean;
  symbol: string;
};

type BillsWorkspaceProps = { draft: BillDraft; onBack: () => void; onAsk: (prompt: string) => void };
type PaymentMode = "now" | "schedule";
type NewBillForm = { category: string; provider: string; accountRef: string; amount: number; dueDate: string; frequency: string; reminder: boolean; highAlert: boolean; autopay: boolean; autopayLimit: number };

const startingBills: Bill[] = [
  { id: "electricity", provider: "BESCOM", category: "Electricity", amount: 1840, previousAmount: 1690, dueDate: "2026-09-04", dueLabel: "04 Sep", accountRef: "Consumer · •••• 7204", status: "due", autopay: false, reminder: true, symbol: "E" },
  { id: "rent", provider: "Maple Residency", category: "Rent", amount: 22000, previousAmount: 22000, dueDate: "2026-09-05", dueLabel: "05 Sep", accountRef: "Apartment B-704", status: "scheduled", autopay: false, reminder: true, symbol: "R" },
  { id: "mobile", provider: "Airtel Postpaid", category: "Mobile", amount: 999, previousAmount: 799, dueDate: "2026-09-08", dueLabel: "08 Sep", accountRef: "Mobile · •••• 8247", status: "due", autopay: true, reminder: true, symbol: "M" },
  { id: "broadband", provider: "ACT Fibernet", category: "Broadband", amount: 1299, previousAmount: 1299, dueDate: "2026-09-11", dueLabel: "11 Sep", accountRef: "Account · •••• 1862", status: "due", autopay: false, reminder: false, symbol: "B" },
  { id: "credit-card", provider: "HDFC Credit Card", category: "Credit card", amount: 18750, previousAmount: 16340, dueDate: "2026-09-15", dueLabel: "15 Sep", accountRef: "Card · •••• 7310", status: "due", autopay: false, reminder: true, symbol: "C" }
];

const money = (value: number) => `₹${value.toLocaleString("en-IN")}`;

export function BillsWorkspace({ draft, onBack, onAsk }: BillsWorkspaceProps) {
  const initialBill = startingBills.find((bill) => bill.id === draft.billId) || startingBills[0];
  const opensDetail = !!draft.billId && draft.intent !== "overview";
  const [bills, setBills] = useState<Bill[]>(startingBills);
  const [view, setView] = useState<"overview" | "detail" | "receipt">(opensDetail ? "detail" : "overview");
  const [selectedId, setSelectedId] = useState(initialBill.id);
  const [filter, setFilter] = useState<"all" | "due" | "scheduled" | "autopay">("all");
  const [paymentMode, setPaymentMode] = useState<PaymentMode>(draft.intent === "schedule" ? "schedule" : "now");
  const [scheduleDate, setScheduleDate] = useState(initialBill.dueDate);
  const [paymentReview, setPaymentReview] = useState(false);
  const [autopayReview, setAutopayReview] = useState(false);
  const [addBillOpen, setAddBillOpen] = useState(draft.intent === "add");
  const [addBillReview, setAddBillReview] = useState(false);
  const [billerVerified, setBillerVerified] = useState(false);
  const [newBill, setNewBill] = useState<NewBillForm>({ category: "Water", provider: "", accountRef: "", amount: 0, dueDate: "2026-09-20", frequency: "Monthly", reminder: true, highAlert: true, autopay: false, autopayLimit: 2500 });
  const [notice, setNotice] = useState("");
  const [receipt, setReceipt] = useState<{ bill: Bill; mode: PaymentMode; date: string; reference: string } | null>(null);
  const selected = bills.find((bill) => bill.id === selectedId) || bills[0];
  const visibleBills = bills.filter((bill) => filter === "all" || filter === "autopay" ? filter === "all" || bill.autopay : bill.status === filter);
  const outstanding = bills.filter((bill) => bill.status === "due" || bill.status === "overdue").reduce((sum, bill) => sum + bill.amount, 0);
  const scheduled = bills.filter((bill) => bill.status === "scheduled").reduce((sum, bill) => sum + bill.amount, 0);
  const dueCount = bills.filter((bill) => bill.status === "due" || bill.status === "overdue").length;
  const autopayCount = bills.filter((bill) => bill.autopay).length;
  const increase = Math.round(((selected.amount - selected.previousAmount) / selected.previousAmount) * 100);

  const openBill = (bill: Bill) => {
    setSelectedId(bill.id);
    setScheduleDate(bill.dueDate);
    setPaymentMode("now");
    setNotice("");
    setView("detail");
  };

  const confirmPayment = () => {
    const nextStatus: BillStatus = paymentMode === "now" ? "paid" : "scheduled";
    setBills((current) => current.map((bill) => bill.id === selected.id ? { ...bill, status: nextStatus } : bill));
    const completed = { ...selected, status: nextStatus };
    setReceipt({ bill: completed, mode: paymentMode, date: paymentMode === "now" ? "01 Sep 2026" : new Date(`${scheduleDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }), reference: `NXB2609${String(Date.now()).slice(-6)}` });
    setPaymentReview(false);
    setView("receipt");
  };

  const confirmAutopay = () => {
    const enabled = !selected.autopay;
    setBills((current) => current.map((bill) => bill.id === selected.id ? { ...bill, autopay: enabled } : bill));
    setAutopayReview(false);
    setNotice(`AutoPay ${enabled ? "enabled" : "paused"} for ${selected.provider}.`);
  };

  const openAddBill = () => {
    setNewBill({ category: "Water", provider: "", accountRef: "", amount: 0, dueDate: "2026-09-20", frequency: "Monthly", reminder: true, highAlert: true, autopay: false, autopayLimit: 2500 });
    setBillerVerified(false);
    setAddBillReview(false);
    setAddBillOpen(true);
  };

  const confirmAddBill = (autopayApproved: boolean) => {
    const date = new Date(`${newBill.dueDate}T00:00:00`);
    const bill: Bill = { id: `bill-${Date.now()}`, provider: newBill.provider.trim(), category: newBill.category, amount: newBill.amount, previousAmount: newBill.amount, dueDate: newBill.dueDate, dueLabel: date.toLocaleDateString("en-IN", { day: "2-digit", month: "short" }), accountRef: newBill.accountRef.trim(), status: "due", autopay: autopayApproved, reminder: newBill.reminder, frequency: newBill.frequency, highAlert: newBill.highAlert, symbol: (newBill.category[0] || "B").toUpperCase() };
    setBills((current) => [...current, bill]);
    setSelectedId(bill.id);
    setAddBillReview(false);
    setAddBillOpen(false);
    setFilter("all");
    setNotice(`${bill.provider} was added${autopayApproved ? " with AutoPay" : ""}.`);
  };

  if (view === "receipt" && receipt) return <section class="nexa-experience-view nexa-bill-receipt" aria-live="polite"><div><span>{receipt.mode === "now" ? "Payment complete" : "Payment scheduled"}</span><b>✓</b><h1>{money(receipt.bill.amount)} {receipt.mode === "now" ? "paid to" : "scheduled for"} {receipt.bill.provider}.</h1><p>{receipt.mode === "now" ? "Paid" : "Scheduled"} from Primary account · •••• 4291</p><dl><div><dt>Payment date</dt><dd>{receipt.date}</dd></div><div><dt>Bill account</dt><dd>{receipt.bill.accountRef}</dd></div><div><dt>Reference</dt><dd>{receipt.reference}</dd></div><div><dt>Fee</dt><dd>₹0</dd></div></dl><div><button type="button" onClick={() => { setView("overview"); setNotice(receipt.mode === "now" ? "Bill paid successfully." : "Bill payment scheduled successfully."); }}>Return to bills</button><button type="button" onClick={onBack}>Conversation</button></div></div></section>;

  return <section class="nexa-experience-view nexa-bills-view" aria-label="Bills and upcoming obligations">
    <header class="nexa-experience-heading"><div><p>Bills and payments</p><h1>{view === "detail" ? "Review before paying." : "Nothing due gets missed."}</h1><span>{view === "detail" ? `${selected.provider} · ${selected.accountRef}` : "Upcoming obligations, unusual changes and payment controls in one place."}</span></div><button type="button" onClick={view === "detail" ? () => setView("overview") : onBack}>{view === "detail" ? "← All bills" : "← Conversation"}</button></header>
    {notice && <div class="nexa-bill-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" onClick={() => setNotice("")} aria-label="Dismiss notification">×</button></div>}

    {view === "overview" ? <>
      <div class="nexa-bill-summary"><div><span>Outstanding</span><strong>{money(outstanding)}</strong><small>{dueCount} bills awaiting action</small></div><div><span>Already scheduled</span><strong>{money(scheduled)}</strong><small>Rent · 05 September</small></div><div><span>Protected by AutoPay</span><strong>{autopayCount}</strong><small>{autopayCount} recurring {autopayCount === 1 ? "bill" : "bills"}</small></div></div>
      <article class="nexa-bill-insight"><div><span>Needs attention</span><h2>Your Airtel bill is 25% higher than last month.</h2><p>Most of the increase comes from an international roaming add-on. Review it before the AutoPay date on 08 September.</p></div><button type="button" onClick={() => openBill(bills.find((bill) => bill.id === "mobile") || bills[0])}>Review mobile bill <b>↗</b></button></article>
      <div class="nexa-bills-layout"><section class="nexa-bills-list"><header><div><span>September obligations</span><h2>Upcoming bills</h2></div><div class="nexa-bills-list-actions"><button class="nexa-add-bill-action" type="button" onClick={openAddBill}><b>＋</b><span>Add another bill</span></button><div aria-label="Filter bills">{(["all", "due", "scheduled", "autopay"] as const).map((item) => <button class={filter === item ? "is-active" : ""} type="button" onClick={() => setFilter(item)} key={item}>{item === "all" ? "All" : item === "autopay" ? "AutoPay" : item.charAt(0).toUpperCase() + item.slice(1)}</button>)}</div></div></header><div>{visibleBills.map((bill) => <article key={bill.id}><span>{bill.symbol}</span><div><strong>{bill.provider}</strong><small>{bill.category} · {bill.accountRef}</small></div><p><strong>{money(bill.amount)}</strong><small>Due {bill.dueLabel}</small></p><em class={`is-${bill.status}`}>{bill.status}</em><button type="button" onClick={() => openBill(bill)}>{bill.status === "paid" ? "View" : "Manage"}</button></article>)}</div></section><aside class="nexa-obligation-calendar"><span>Payment outlook</span><h2>September</h2><div>{bills.slice(0,4).map((bill) => <p key={`calendar-${bill.id}`}><b>{bill.dueLabel.split(" ")[0]}</b><span>{bill.provider}<small>{bill.status}</small></span><strong>{money(bill.amount)}</strong></p>)}</div><footer><span>Balance after scheduled bills</span><strong>₹74,280</strong><small>Your emergency-fund plan remains affordable.</small></footer></aside></div>
    </> : <>
      <div class="nexa-bill-detail-lead"><div><span>{selected.symbol}</span><div><p>{selected.category}</p><h2>{selected.provider}</h2><small>{selected.accountRef}</small></div></div><div><span>Amount due</span><strong>{money(selected.amount)}</strong><small>Due {selected.dueLabel}</small></div></div>
      {increase > 10 && <article class="nexa-bill-change"><span>Bill analysis</span><h2>{increase}% higher than the previous bill</h2><p>Previous bill: {money(selected.previousAmount)}. Nexa detected a meaningful increase worth reviewing before payment.</p><button type="button" onClick={() => onAsk(`Explain why my ${selected.provider} bill increased from ${money(selected.previousAmount)} to ${money(selected.amount)}`)}>Ask Nexa to explain <b>↗</b></button></article>}
      <form class="nexa-bill-payment" onSubmit={(event) => { event.preventDefault(); setPaymentReview(true); }}><div><section><span>01 · Payment timing</span><h2>When should this be paid?</h2><div class="nexa-payment-mode"><button class={paymentMode === "now" ? "is-selected" : ""} type="button" onClick={() => setPaymentMode("now")}><strong>Pay now</strong><small>Process immediately</small></button><button class={paymentMode === "schedule" ? "is-selected" : ""} type="button" onClick={() => setPaymentMode("schedule")}><strong>Schedule</strong><small>Choose a future date</small></button></div>{paymentMode === "schedule" && <label>Payment date<input aria-label="Payment date" type="date" value={scheduleDate} min="2026-09-01" max={selected.dueDate} onInput={(event) => setScheduleDate((event.currentTarget as HTMLInputElement).value)} required/></label>}</section><section><span>02 · Pay from</span><h2>Choose the funding account.</h2><button class="nexa-bill-source" type="button"><i>NB</i><span><strong>Primary account</strong><small>Nexa Bank · •••• 4291</small></span><b>₹96,280</b></button></section><section><span>03 · Payment controls</span><h2>Keep future bills organized.</h2><button class="nexa-bill-setting" type="button" role="switch" aria-checked={selected.autopay} onClick={() => setAutopayReview(true)}><span><strong>AutoPay</strong><small>{selected.autopay ? "Enabled · pays on the due date" : "Off · requires approval for every bill"}</small></span><i/></button><button class="nexa-bill-setting" type="button" role="switch" aria-checked={selected.reminder} onClick={() => { setBills((current) => current.map((bill) => bill.id === selected.id ? { ...bill, reminder: !bill.reminder } : bill)); setNotice(`Payment reminder ${selected.reminder ? "disabled" : "enabled"}.`); }}><span><strong>Payment reminder</strong><small>Notify three days before the due date</small></span><i/></button></section></div><aside><span>Final amount</span><h2>{money(selected.amount)}</h2><p>{selected.provider} · Due {selected.dueLabel}</p><dl><div><dt>Payment method</dt><dd>{paymentMode === "now" ? "Pay now" : "Scheduled"}</dd></div><div><dt>Payment date</dt><dd>{paymentMode === "now" ? "Today" : new Date(`${scheduleDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short" })}</dd></div><div><dt>Fee</dt><dd>₹0</dd></div><div><dt>Balance after</dt><dd>{money(96280 - selected.amount)}</dd></div></dl><small>This payment does not affect your existing savings-goal contribution.</small><button type="submit">Review {paymentMode === "now" ? "payment" : "schedule"}</button></aside></form>
    </>}

    {paymentReview && <div class="nexa-bill-dialog-backdrop" onClick={() => setPaymentReview(false)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-bill-payment-title" onClick={(event) => event.stopPropagation()}><span>{paymentMode === "now" ? "Confirm payment" : "Confirm schedule"}</span><h2 id="nexa-bill-payment-title">{paymentMode === "now" ? "Pay" : "Schedule"} {money(selected.amount)} to {selected.provider}?</h2><p>{paymentMode === "now" ? "The payment will be processed from Primary account immediately." : `The payment will be scheduled for ${new Date(`${scheduleDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "long", year: "numeric" })}.`}</p><footer><button type="button" onClick={() => setPaymentReview(false)}>Go back</button><button class="is-primary" type="button" onClick={confirmPayment}>{paymentMode === "now" ? "Confirm payment" : "Confirm schedule"}</button></footer></section></div>}
    {autopayReview && <div class="nexa-bill-dialog-backdrop" onClick={() => setAutopayReview(false)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-autopay-title" onClick={(event) => event.stopPropagation()}><span>AutoPay authorization</span><h2 id="nexa-autopay-title">{selected.autopay ? "Pause" : "Enable"} AutoPay for {selected.provider}?</h2><p>{selected.autopay ? "Future bills will require manual approval after AutoPay is paused." : `Future bills will be paid from Primary account on their due date, up to ${money(Math.ceil(selected.amount * 1.2))}.`}</p><footer><button type="button" onClick={() => setAutopayReview(false)}>Keep current setting</button><button class="is-primary" type="button" onClick={confirmAutopay}>{selected.autopay ? "Pause AutoPay" : "Approve AutoPay"}</button></footer></section></div>}
    {addBillOpen && <div class="nexa-bill-sheet-backdrop" onClick={() => { setAddBillOpen(false); setAddBillReview(false); }}><section class="nexa-bill-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-add-bill-title" onClick={(event) => event.stopPropagation()}><header><div><span>Add a bill</span><h2 id="nexa-add-bill-title">Connect an obligation.</h2><p>Nexa will organize its due date, reminders and future payment controls.</p></div><button type="button" onClick={() => setAddBillOpen(false)} aria-label="Close add bill">×</button></header>{!addBillReview ? <form class="nexa-add-bill-form" onSubmit={(event) => { event.preventDefault(); if (billerVerified && newBill.provider.trim() && newBill.accountRef.trim() && newBill.amount > 0) setAddBillReview(true); }}><section><span>01 · Biller</span><h3>Who should Nexa track?</h3><div class="nexa-add-bill-fields"><label>Bill category<select aria-label="Bill category" value={newBill.category} onChange={(event) => { setNewBill({ ...newBill, category: (event.currentTarget as HTMLSelectElement).value }); setBillerVerified(false); }}><option>Water</option><option>Electricity</option><option>Gas</option><option>Mobile</option><option>Broadband</option><option>Rent</option><option>EMI</option><option>Insurance</option><option>Custom</option></select></label><label>Provider name<input aria-label="Provider name" value={newBill.provider} onInput={(event) => { setNewBill({ ...newBill, provider: (event.currentTarget as HTMLInputElement).value }); setBillerVerified(false); }} placeholder="e.g. BWSSB" required/></label><label>Consumer or account reference<input aria-label="Bill account reference" value={newBill.accountRef} onInput={(event) => { setNewBill({ ...newBill, accountRef: (event.currentTarget as HTMLInputElement).value }); setBillerVerified(false); }} placeholder="e.g. Consumer · 458921" required/></label></div><button class={`nexa-verify-biller ${billerVerified ? "is-verified" : ""}`} type="button" disabled={!newBill.provider.trim() || !newBill.accountRef.trim()} onClick={() => setBillerVerified(true)}>{billerVerified ? "✓ Biller account verified" : "Verify biller account"}</button></section><section><span>02 · Schedule</span><h3>When does it become due?</h3><div class="nexa-add-bill-fields is-three"><label>Expected amount<div><b>₹</b><input aria-label="Expected bill amount" inputMode="numeric" value={newBill.amount || ""} onInput={(event) => setNewBill({ ...newBill, amount: Number((event.currentTarget as HTMLInputElement).value) || 0 })} required/></div></label><label>Next due date<input aria-label="Next bill due date" type="date" value={newBill.dueDate} min="2026-09-01" onInput={(event) => setNewBill({ ...newBill, dueDate: (event.currentTarget as HTMLInputElement).value })} required/></label><label>Frequency<select aria-label="Bill frequency" value={newBill.frequency} onChange={(event) => setNewBill({ ...newBill, frequency: (event.currentTarget as HTMLSelectElement).value })}><option>Monthly</option><option>Quarterly</option><option>Annual</option><option>One-time</option></select></label></div></section><section><span>03 · Controls</span><h3>How should Nexa look after it?</h3><div class="nexa-new-bill-controls"><button type="button" role="switch" aria-checked={newBill.reminder} onClick={() => setNewBill({ ...newBill, reminder: !newBill.reminder })}><span><strong>Payment reminder</strong><small>Notify three days before it is due</small></span><i/></button><button type="button" role="switch" aria-checked={newBill.highAlert} onClick={() => setNewBill({ ...newBill, highAlert: !newBill.highAlert })}><span><strong>Unusual amount alert</strong><small>Flag meaningful increases before payment</small></span><i/></button><button type="button" role="switch" aria-checked={newBill.autopay} onClick={() => setNewBill({ ...newBill, autopay: !newBill.autopay })}><span><strong>AutoPay</strong><small>Requires separate approval during review</small></span><i/></button>{newBill.autopay && <label>Maximum automatic payment<div><b>₹</b><input aria-label="AutoPay maximum" inputMode="numeric" value={newBill.autopayLimit} onInput={(event) => setNewBill({ ...newBill, autopayLimit: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></div></label>}</div></section><button class="nexa-add-bill-submit" type="submit" disabled={!billerVerified || newBill.amount <= 0}>Review bill connection</button></form> : <div class="nexa-add-bill-review"><span>Final review</span><h3>Add {newBill.provider}?</h3><p>This will add the bill to your payment outlook. No payment will be made while connecting it.</p><dl><div><dt>Category</dt><dd>{newBill.category}</dd></div><div><dt>Account</dt><dd>{newBill.accountRef}</dd></div><div><dt>Expected amount</dt><dd>{money(newBill.amount)}</dd></div><div><dt>Next due date</dt><dd>{new Date(`${newBill.dueDate}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" })}</dd></div><div><dt>Frequency</dt><dd>{newBill.frequency}</dd></div><div><dt>AutoPay</dt><dd>{newBill.autopay ? `Awaiting approval · limit ${money(newBill.autopayLimit)}` : "Off"}</dd></div></dl>{newBill.autopay && <small>Approving AutoPay is a separate authorization for future bills up to the selected limit.</small>}<footer><button type="button" onClick={() => setAddBillReview(false)}>Keep editing</button>{newBill.autopay && <button type="button" onClick={() => confirmAddBill(false)}>Add without AutoPay</button>}<button class="is-primary" type="button" onClick={() => confirmAddBill(newBill.autopay)}>{newBill.autopay ? "Add & approve AutoPay" : "Add bill"}</button></footer></div>}</section></div>}
  </section>;
}
