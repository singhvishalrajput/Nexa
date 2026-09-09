import { h } from "preact";
import { useState } from "preact/hooks";

type WorkspaceProps = { onBack: () => void; onAsk: (prompt: string) => void };

export type ScheduledTransferDraft = {
  recipient?: string;
  amount?: number;
  cadence?: "once" | "weekly" | "monthly";
};

type ScheduledTransfer = {
  id: string;
  recipient: string;
  destination: string;
  amount: number;
  cadence: "Once" | "Weekly" | "Monthly";
  nextDate: string;
  account: string;
  status: "active" | "paused";
};

const startingTransfers: ScheduledTransfer[] = [
  { id: "rent", recipient: "Maple Residency", destination: "Bank account · •••• 7042", amount: 22000, cadence: "Monthly", nextDate: "05 Sep 2026", account: "Primary · •••• 4291", status: "active" },
  { id: "parents", recipient: "Family support", destination: "UPI · family@upi", amount: 10000, cadence: "Monthly", nextDate: "10 Sep 2026", account: "Primary · •••• 4291", status: "active" },
  { id: "trip", recipient: "Rahul Mehta", destination: "UPI · rahul@upi", amount: 4500, cadence: "Once", nextDate: "18 Sep 2026", account: "Primary · •••• 4291", status: "active" }
];

const money = (value: number) => `₹${Math.max(0, Math.round(value)).toLocaleString("en-IN")}`;
const initials = (value: string) => value.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "—";
const prettyDate = (value: string) => value ? new Date(`${value}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }) : "Choose a date";

export function ScheduledTransfers({ draft, onBack, onAsk }: WorkspaceProps & { draft: ScheduledTransferDraft }) {
  const [items, setItems] = useState<ScheduledTransfer[]>(startingTransfers);
  const [selectedId, setSelectedId] = useState(startingTransfers[0].id);
  const [editorOpen, setEditorOpen] = useState(Boolean(draft.recipient || draft.amount));
  const [reviewOpen, setReviewOpen] = useState(false);
  const [pendingCancel, setPendingCancel] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState({ recipient: draft.recipient || "", amount: draft.amount || 0, cadence: draft.cadence || "monthly", date: "2026-09-15", account: "Primary · •••• 4291", note: "" });
  const selected = items.find((item) => item.id === selectedId) || items[0];
  const active = items.filter((item) => item.status === "active");
  const monthlyCommitted = active.reduce((sum, item) => sum + (item.cadence === "Monthly" ? item.amount : item.cadence === "Weekly" ? item.amount * 4 : 0), 0);
  const nextTransfer = [...active].sort((a, b) => new Date(a.nextDate).getTime() - new Date(b.nextDate).getTime())[0];

  const beginNew = () => {
    setForm({ recipient: "", amount: 0, cadence: "monthly", date: "2026-09-15", account: "Primary · •••• 4291", note: "" });
    setEditorOpen(true);
  };
  const saveTransfer = () => {
    const item: ScheduledTransfer = { id: `scheduled-${Date.now()}`, recipient: form.recipient.trim(), destination: "Verified beneficiary", amount: form.amount, cadence: form.cadence === "once" ? "Once" : form.cadence === "weekly" ? "Weekly" : "Monthly", nextDate: prettyDate(form.date), account: form.account, status: "active" };
    setItems((current) => [item, ...current]);
    setSelectedId(item.id);
    setReviewOpen(false);
    setEditorOpen(false);
    setNotice(`${item.cadence} transfer to ${item.recipient} was scheduled.`);
  };
  const toggleStatus = (id: string) => setItems((current) => current.map((item) => item.id === id ? { ...item, status: item.status === "active" ? "paused" : "active" } : item));
  const cancelTransfer = () => {
    if (!pendingCancel) return;
    const remaining = items.filter((item) => item.id !== pendingCancel);
    setItems(remaining);
    setSelectedId(remaining[0]?.id || "");
    setPendingCancel(null);
    setNotice("The transfer instruction was cancelled.");
  };

  return <section class="nexa-experience-view nexa-planning-view" aria-label="Scheduled and recurring transfers">
    <header class="nexa-experience-heading"><div><p>Transfer schedule</p><h1>Payments that stay on time.</h1><span>Review future transfers and recurring instructions before money moves.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-planning-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <div class="nexa-schedule-summary"><div><span>Monthly committed</span><strong>{money(monthlyCommitted)}</strong><small>Across recurring instructions</small></div><div><span>Active instructions</span><strong>{active.length}</strong><small>{items.length - active.length} currently paused</small></div><div><span>Next transfer</span><strong>{nextTransfer ? money(nextTransfer.amount) : "—"}</strong><small>{nextTransfer ? `${nextTransfer.recipient} · ${nextTransfer.nextDate}` : "Nothing scheduled"}</small></div></div>
    <section class="nexa-schedule-workspace"><header><div><span>Upcoming and recurring</span><h2>Your transfer instructions</h2></div><button type="button" onClick={beginNew}>＋ Schedule transfer</button></header>
      {items.length ? <div class="nexa-schedule-cards">{items.map((item) => <button class={item.id === selectedId ? "is-selected" : ""} type="button" onClick={() => setSelectedId(item.id)} key={item.id}><span>{initials(item.recipient)}</span><div><strong>{item.recipient}</strong><small>{item.cadence} · {item.nextDate}</small><b>{money(item.amount)}</b></div><em class={`is-${item.status}`}>{item.status}</em></button>)}</div> : <div class="nexa-planning-empty"><strong>No transfer instructions</strong><p>Schedule a future payment or create a recurring transfer.</p><button type="button" onClick={beginNew}>Schedule a transfer</button></div>}
      {selected && <article class="nexa-schedule-detail"><header><div><span>{selected.cadence} transfer</span><h2>{selected.recipient}</h2><p>{selected.destination}</p></div><em class={`is-${selected.status}`}>{selected.status}</em></header><dl><div><dt>Amount</dt><dd>{money(selected.amount)}</dd></div><div><dt>Next payment</dt><dd>{selected.nextDate}</dd></div><div><dt>Frequency</dt><dd>{selected.cadence}</dd></div><div><dt>Pay from</dt><dd>{selected.account}</dd></div></dl><div class="nexa-schedule-safety"><span>Approval control</span><p>Nexa will show a reminder before the next payment. Changes and cancellations take effect before the processing date.</p></div><footer><button type="button" onClick={() => toggleStatus(selected.id)}>{selected.status === "active" ? "Pause instruction" : "Resume instruction"}</button><button type="button" onClick={() => onAsk(`Show me the payment history for ${selected.recipient}`)}>View payment history</button><button class="is-remove" type="button" onClick={() => setPendingCancel(selected.id)}>Cancel instruction</button></footer></article>}
    </section>
    {editorOpen && <div class="nexa-planning-backdrop" onClick={() => setEditorOpen(false)}><section class="nexa-planning-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-schedule-editor-title" onClick={(event) => event.stopPropagation()}><header><div><span>New instruction</span><h2 id="nexa-schedule-editor-title">Schedule a transfer.</h2><p>Set the recipient, timing and recurrence. Nothing is saved until final approval.</p></div><button type="button" aria-label="Close schedule editor" onClick={() => setEditorOpen(false)}>×</button></header><form onSubmit={(event) => { event.preventDefault(); setReviewOpen(true); }}><label>Recipient<input aria-label="Scheduled transfer recipient" value={form.recipient} onInput={(event) => setForm({ ...form, recipient: (event.currentTarget as HTMLInputElement).value })} placeholder="Verified beneficiary" required /></label><div><label>Amount<span><b>₹</b><input aria-label="Scheduled transfer amount" inputMode="numeric" value={form.amount || ""} onInput={(event) => setForm({ ...form, amount: Number((event.currentTarget as HTMLInputElement).value.replace(/\D/g, "")) || 0 })} required /></span></label><label>Frequency<select aria-label="Transfer frequency" value={form.cadence} onChange={(event) => setForm({ ...form, cadence: (event.currentTarget as HTMLSelectElement).value as "once" | "weekly" | "monthly" })}><option value="once">One time</option><option value="weekly">Every week</option><option value="monthly">Every month</option></select></label></div><div><label>{form.cadence === "once" ? "Transfer date" : "First transfer"}<input aria-label="First transfer date" type="date" value={form.date} onInput={(event) => setForm({ ...form, date: (event.currentTarget as HTMLInputElement).value })} required /></label><label>Pay from<select aria-label="Scheduled transfer account" value={form.account} onChange={(event) => setForm({ ...form, account: (event.currentTarget as HTMLSelectElement).value })}><option>Primary · •••• 4291</option><option>Savings · •••• 1186</option></select></label></div><label>Note <small>Optional</small><input aria-label="Scheduled transfer note" value={form.note} onInput={(event) => setForm({ ...form, note: (event.currentTarget as HTMLInputElement).value })} placeholder="What is this payment for?" /></label><button class="nexa-planning-submit" type="submit" disabled={!form.recipient.trim() || form.amount <= 0 || !form.date}>Review instruction</button></form></section></div>}
    {reviewOpen && <div class="nexa-planning-backdrop" onClick={() => setReviewOpen(false)}><section class="nexa-planning-confirm" role="dialog" aria-modal="true" aria-labelledby="nexa-schedule-review-title" onClick={(event) => event.stopPropagation()}><span>Final review</span><h2 id="nexa-schedule-review-title">Approve this transfer instruction?</h2><p>{money(form.amount)} will be sent to {form.recipient} {form.cadence === "once" ? "once" : form.cadence === "weekly" ? "every week" : "every month"}, starting {prettyDate(form.date)}.</p><dl><div><dt>Funding account</dt><dd>{form.account}</dd></div><div><dt>Processing fee</dt><dd>₹0</dd></div><div><dt>Instruction status</dt><dd>Active after approval</dd></div></dl><small>This approval creates a future payment instruction. You can pause or cancel it before its next processing date.</small><footer><button type="button" onClick={() => setReviewOpen(false)}>Keep editing</button><button class="is-primary" type="button" onClick={saveTransfer}>Approve and schedule</button></footer></section></div>}
    {pendingCancel && <div class="nexa-planning-backdrop" onClick={() => setPendingCancel(null)}><section class="nexa-planning-confirm" role="dialog" aria-modal="true" aria-labelledby="nexa-schedule-cancel-title" onClick={(event) => event.stopPropagation()}><span>Cancel instruction</span><h2 id="nexa-schedule-cancel-title">Stop this future transfer?</h2><p>No further payments will be processed from this instruction.</p><footer><button type="button" onClick={() => setPendingCancel(null)}>Keep instruction</button><button class="is-destructive" type="button" onClick={cancelTransfer}>Cancel transfer</button></footer></section></div>}
  </section>;
}

type BudgetCategory = { id: string; name: string; spent: number; limit: number; tone: string; transactions: number };
const startingBudgets: BudgetCategory[] = [
  { id: "food", name: "Food & dining", spent: 9640, limit: 12000, tone: "#e54e2b", transactions: 18 },
  { id: "travel", name: "Travel", spent: 6240, limit: 8000, tone: "#355f58", transactions: 11 },
  { id: "shopping", name: "Shopping", spent: 7100, limit: 6500, tone: "#a05a48", transactions: 7 },
  { id: "entertainment", name: "Entertainment", spent: 2280, limit: 4000, tone: "#726150", transactions: 6 }
];

export function BudgetManager({ onBack, onAsk }: WorkspaceProps) {
  const [budgets, setBudgets] = useState<BudgetCategory[]>(startingBudgets);
  const [selectedId, setSelectedId] = useState(startingBudgets[0].id);
  const [editing, setEditing] = useState<BudgetCategory | null>(null);
  const [notice, setNotice] = useState("");
  const selected = budgets.find((item) => item.id === selectedId) || budgets[0];
  const totalLimit = budgets.reduce((sum, item) => sum + item.limit, 0);
  const totalSpent = budgets.reduce((sum, item) => sum + item.spent, 0);
  const overspent = budgets.filter((item) => item.spent > item.limit);
  const daysLeft = 28;

  const saveBudget = (event: Event) => {
    event.preventDefault();
    if (!editing || !editing.name.trim() || editing.limit <= 0) return;
    const exists = budgets.some((item) => item.id === editing.id);
    const next = exists ? budgets.map((item) => item.id === editing.id ? editing : item) : [...budgets, editing];
    setBudgets(next);
    setSelectedId(editing.id);
    setNotice(`${editing.name} budget was ${exists ? "updated" : "created"}.`);
    setEditing(null);
  };
  const removeBudget = (id: string) => {
    const next = budgets.filter((item) => item.id !== id);
    setBudgets(next);
    setSelectedId(next[0]?.id || "");
    setNotice("The category budget was removed. Transactions remain available in analysis.");
  };

  return <section class="nexa-experience-view nexa-planning-view" aria-label="Monthly budget manager">
    <header class="nexa-experience-heading"><div><p>Budget manager</p><h1>Give every rupee a boundary.</h1><span>September 2026 · Category limits across connected accounts</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-planning-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <div class="nexa-budget-summary"><div><span>Monthly budget</span><strong>{money(totalLimit)}</strong><small>Across {budgets.length} categories</small></div><div><span>Used so far</span><strong>{money(totalSpent)}</strong><small>{totalLimit ? Math.round((totalSpent / totalLimit) * 100) : 0}% of planned spending</small></div><div><span>Still available</span><strong>{money(Math.max(0, totalLimit - totalSpent))}</strong><small>{daysLeft} days remaining this month</small></div></div>
    {overspent.length > 0 && <article class="nexa-budget-alert"><div><span>Needs attention</span><h2>{overspent[0].name} is {money(overspent[0].spent - overspent[0].limit)} over its monthly limit.</h2><p>Adjust the limit if the higher spend was planned, or ask Nexa to identify transactions that can be reduced.</p></div><button type="button" onClick={() => { setSelectedId(overspent[0].id); onAsk(`Help me reduce my ${overspent[0].name.toLowerCase()} spending`); }}>Ask Nexa to review <b>↗</b></button></article>}
    <section class="nexa-budget-workspace"><header><div><span>Category plan</span><h2>Your monthly limits</h2></div><button type="button" onClick={() => setEditing({ id: `budget-${Date.now()}`, name: "", spent: 0, limit: 5000, tone: "#e54e2b", transactions: 0 })}>＋ Add category</button></header>
      {budgets.length ? <div class="nexa-budget-cards">{budgets.map((item) => { const used = item.limit ? Math.round((item.spent / item.limit) * 100) : 0; return <button class={`${item.id === selectedId ? "is-selected" : ""} ${used > 100 ? "is-over" : ""}`} type="button" onClick={() => setSelectedId(item.id)} key={item.id}><span style={{ background: item.tone }}>{item.name.slice(0, 1)}</span><div><strong>{item.name}</strong><small>{money(item.spent)} of {money(item.limit)}</small><i><b style={{ width: `${Math.min(100, used)}%`, background: item.tone }}/></i></div><em>{used}%</em></button>; })}</div> : <div class="nexa-planning-empty"><strong>No category budgets yet</strong><p>Create a limit to start tracking monthly usage.</p></div>}
      {selected && <article class="nexa-budget-detail"><header><div><span>Category budget</span><h2>{selected.name}</h2><p>{selected.transactions} transactions this month</p></div><strong class={selected.spent > selected.limit ? "is-over" : ""}>{Math.round((selected.spent / selected.limit) * 100)}% used</strong></header><div class="nexa-budget-meter"><i><b style={{ width: `${Math.min(100, Math.round((selected.spent / selected.limit) * 100))}%`, background: selected.tone }}/></i><div><span>{money(selected.spent)} spent</span><span>{money(selected.limit)} limit</span></div></div><dl><div><dt>Remaining</dt><dd class={selected.limit - selected.spent < 0 ? "is-over" : ""}>{selected.limit - selected.spent < 0 ? `−${money(selected.spent - selected.limit)}` : money(selected.limit - selected.spent)}</dd></div><div><dt>Daily allowance</dt><dd>{money(Math.max(0, (selected.limit - selected.spent) / daysLeft))}</dd></div><div><dt>Projected month end</dt><dd>{money(Math.round(selected.spent * 1.36))}</dd></div></dl><div class="nexa-budget-guidance-card"><span>Nexa guidance</span><p>{selected.spent > selected.limit ? "This category has crossed its limit. Review recent activity before increasing the budget." : `You can spend about ${money(Math.max(0, (selected.limit - selected.spent) / daysLeft))} per day and remain within this limit.`}</p></div><footer><button type="button" onClick={() => setEditing({ ...selected })}>Adjust limit</button><button type="button" onClick={() => onAsk(`Show my ${selected.name.toLowerCase()} transactions this month`)}>View transactions</button><button class="is-remove" type="button" onClick={() => removeBudget(selected.id)}>Remove budget</button></footer></article>}
    </section>
    {editing && <div class="nexa-planning-backdrop" onClick={() => setEditing(null)}><section class="nexa-planning-sheet nexa-budget-editor" role="dialog" aria-modal="true" aria-labelledby="nexa-budget-editor-title" onClick={(event) => event.stopPropagation()}><header><div><span>{budgets.some((item) => item.id === editing.id) ? "Adjust category" : "New category"}</span><h2 id="nexa-budget-editor-title">Set a useful limit.</h2><p>Choose an amount you can follow without hiding essential spending.</p></div><button type="button" aria-label="Close budget editor" onClick={() => setEditing(null)}>×</button></header><form onSubmit={saveBudget}><label>Category name<input aria-label="Budget category name" value={editing.name} onInput={(event) => setEditing({ ...editing, name: (event.currentTarget as HTMLInputElement).value })} placeholder="e.g. Health & fitness" required /></label><label>Monthly limit<span><b>₹</b><input aria-label="Monthly budget limit" inputMode="numeric" value={editing.limit || ""} onInput={(event) => setEditing({ ...editing, limit: Number((event.currentTarget as HTMLInputElement).value.replace(/\D/g, "")) || 0 })} required /></span></label><div class="nexa-budget-limit-options"><span>Suggested limits</span><div>{[4000, 6000, 8000, 12000].map((amount) => <button class={editing.limit === amount ? "is-selected" : ""} type="button" onClick={() => setEditing({ ...editing, limit: amount })} key={amount}>{money(amount)}</button>)}</div></div><div class="nexa-budget-impact"><span>Monthly plan after this change</span><strong>{money(totalLimit - (budgets.find((item) => item.id === editing.id)?.limit || 0) + editing.limit)}</strong><small>Estimated flexible income: ₹55,500</small></div><button class="nexa-planning-submit" type="submit" disabled={!editing.name.trim() || editing.limit <= 0}>Save monthly limit</button></form></section></div>}
  </section>;
}
