import { h } from "preact";
import { useState } from "preact/hooks";

type WorkspaceProps = { onBack: () => void; onAsk: (prompt: string) => void };
const money = (value: number) => `${value < 0 ? "−" : ""}₹${Math.abs(Math.round(value)).toLocaleString("en-IN")}`;

type FinancialAlert = {
  id: string;
  kind: "bill" | "spending" | "balance" | "transfer" | "goal";
  title: string;
  detail: string;
  time: string;
  priority: "high" | "normal" | "positive";
  unread: boolean;
  action: string;
};

const startingAlerts: FinancialAlert[] = [
  { id: "unusual", kind: "spending", title: "Unusual card payment", detail: "₹8,940 at Urban Gadgets is larger than your typical electronics purchases.", time: "12 min ago", priority: "high", unread: true, action: "Review payment" },
  { id: "bill", kind: "bill", title: "Electricity bill due in 3 days", detail: "BESCOM · ₹1,840 from Primary account. AutoPay is currently off.", time: "Today", priority: "normal", unread: true, action: "Review bill" },
  { id: "balance", kind: "balance", title: "Balance may fall below ₹20,000", detail: "Scheduled bills and transfers could reduce your primary balance on 10 September.", time: "Today", priority: "high", unread: true, action: "View forecast" },
  { id: "transfer", kind: "transfer", title: "Transfer completed", detail: "₹5,000 was sent to Rahul Mehta. Reference NXA2609018427.", time: "Yesterday", priority: "positive", unread: false, action: "View transfer" },
  { id: "goal", kind: "goal", title: "Emergency fund is ahead", detail: "You are ₹4,200 ahead of this month’s contribution plan.", time: "01 Sep", priority: "positive", unread: false, action: "View goal" }
];
const alertLabels: Record<FinancialAlert["kind"], string> = { bill: "Bill reminder", spending: "Spending alert", balance: "Balance warning", transfer: "Transfer update", goal: "Goal progress" };

export function FinancialNotifications({ onBack, onAsk }: WorkspaceProps) {
  const [alerts, setAlerts] = useState(startingAlerts);
  const [filter, setFilter] = useState<"all" | "attention" | "unread">("all");
  const [preferencesOpen, setPreferencesOpen] = useState(false);
  const [settings, setSettings] = useState({ bill: true, spending: true, balance: true, transfer: true, goal: true, push: true, email: false });
  const visible = alerts.filter((item) => filter === "all" || (filter === "attention" ? item.priority === "high" : item.unread));
  const unread = alerts.filter((item) => item.unread).length;
  const urgent = alerts.filter((item) => item.priority === "high").length;
  const dismiss = (id: string) => setAlerts((current) => current.filter((item) => item.id !== id));
  const openAlert = (item: FinancialAlert) => {
    setAlerts((current) => current.map((alert) => alert.id === item.id ? { ...alert, unread: false } : alert));
    const prompts: Record<FinancialAlert["kind"], string> = { bill: "Show my electricity bill", spending: "Review the suspicious Urban Gadgets payment", balance: "Show my cash-flow forecast", transfer: "Show the status of my transfer to Rahul", goal: "Show my emergency fund goal" };
    onAsk(prompts[item.kind]);
  };
  return <section class="nexa-experience-view nexa-insight-view" aria-label="Financial notifications">
    <header class="nexa-experience-heading"><div><p>Financial notifications</p><h1>Only what needs your attention.</h1><span>Bills, unusual activity, balances, transfers and goals in one ordered feed.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    <div class="nexa-notification-summary"><div><span>Needs attention</span><strong>{urgent}</strong><small>Review recommended today</small></div><div><span>Unread updates</span><strong>{unread}</strong><small>Across all connected accounts</small></div><div><span>Protection status</span><strong>Active</strong><small>Monitoring five financial signals</small></div></div>
    <div class="nexa-notification-toolbar"><div><button class={filter === "all" ? "is-active" : ""} type="button" onClick={() => setFilter("all")}>All updates</button><button class={filter === "attention" ? "is-active" : ""} type="button" onClick={() => setFilter("attention")}>Needs attention</button><button class={filter === "unread" ? "is-active" : ""} type="button" onClick={() => setFilter("unread")}>Unread</button></div><span><button type="button" onClick={() => setAlerts((current) => current.map((item) => ({ ...item, unread: false })))}>Mark all read</button><button type="button" onClick={() => setPreferencesOpen(true)}>Notification settings</button></span></div>
    <section class="nexa-notification-feed"><header><span>Latest activity</span><h2>{filter === "attention" ? "Priority notifications" : filter === "unread" ? "Unread notifications" : "Your financial feed"}</h2></header>{visible.length ? visible.map((item) => <article class={`is-${item.priority} ${item.unread ? "is-unread" : ""}`} key={item.id}><span>{item.kind === "bill" ? "B" : item.kind === "spending" ? "!" : item.kind === "balance" ? "₹" : item.kind === "transfer" ? "↗" : "G"}</span><div><small>{alertLabels[item.kind]} · {item.time}</small><h3>{item.title}</h3><p>{item.detail}</p></div><footer><button type="button" onClick={() => openAlert(item)}>{item.action}</button><button type="button" onClick={() => dismiss(item.id)} aria-label={`Dismiss ${item.title}`}>Dismiss</button></footer></article>) : <div class="nexa-insight-empty"><strong>You’re all caught up.</strong><p>No notifications match this view.</p></div>}</section>
    {preferencesOpen && <div class="nexa-insight-backdrop" onClick={() => setPreferencesOpen(false)}><section class="nexa-insight-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-notification-settings" onClick={(event) => event.stopPropagation()}><header><div><span>Notification settings</span><h2 id="nexa-notification-settings">Choose what reaches you.</h2><p>Critical security alerts remain enabled regardless of these preferences.</p></div><button type="button" aria-label="Close notification settings" onClick={() => setPreferencesOpen(false)}>×</button></header><div class="nexa-notification-settings">{(["bill","spending","balance","transfer","goal"] as const).map((key) => <button type="button" role="switch" aria-checked={settings[key]} onClick={() => setSettings({ ...settings, [key]: !settings[key] })} key={key}><span><strong>{alertLabels[key]}</strong><small>{key === "spending" ? "Unusual purchases and category spikes" : key === "bill" ? "Upcoming and overdue bills" : key === "balance" ? "Low and projected balance warnings" : key === "transfer" ? "Completed, failed and scheduled transfers" : "Milestones and contribution reminders"}</small></span><i /></button>)}</div><div class="nexa-notification-channels"><span>Delivery</span><button type="button" class={settings.push ? "is-active" : ""} onClick={() => setSettings({ ...settings, push: !settings.push })}>In-app & push</button><button type="button" class={settings.email ? "is-active" : ""} onClick={() => setSettings({ ...settings, email: !settings.email })}>Email summary</button></div><button class="nexa-insight-primary" type="button" onClick={() => setPreferencesOpen(false)}>Save preferences</button></section></div>}
  </section>;
}

type SuspiciousTransaction = { id: string; merchant: string; detail: string; amount: number; date: string; reason: string; state: "review" | "recognized" | "disputed" };
type DisputeCase = { id: string; merchant: string; amount: number; opened: string; status: "Submitted" | "Under review" | "Resolved"; update: string };
const suspiciousTransactions: SuspiciousTransaction[] = [
  { id: "urban", merchant: "Urban Gadgets", detail: "Nexa debit · •••• 4832", amount: 8940, date: "02 Sep · 18:42", reason: "First payment to this merchant and 4× your usual electronics spend", state: "review" },
  { id: "cloud", merchant: "CloudHost EU", detail: "Nexa debit · •••• 4832", amount: 3299, date: "31 Aug · 03:18", reason: "International online payment made at an unusual time", state: "review" },
  { id: "taxi", merchant: "Metro Taxi", detail: "Nexa debit · •••• 4832", amount: 1180, date: "28 Aug · 23:06", reason: "Location differs from your recent card activity", state: "review" }
];
const startingCases: DisputeCase[] = [{ id: "NXD-2608-1142", merchant: "QuickCart", amount: 2290, opened: "24 Aug 2026", status: "Under review", update: "Merchant evidence requested · next update by 06 Sep" }];

export function FraudDisputeCentre({ onBack }: WorkspaceProps) {
  const [transactions, setTransactions] = useState(suspiciousTransactions);
  const [cases, setCases] = useState(startingCases);
  const [selectedId, setSelectedId] = useState(suspiciousTransactions[0].id);
  const [disputeOpen, setDisputeOpen] = useState(false);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [notice, setNotice] = useState("");
  const [form, setForm] = useState({ reason: "I do not recognize this transaction", cardSecured: true, detail: "" });
  const selected = transactions.find((item) => item.id === selectedId) || transactions[0];
  const pending = transactions.filter((item) => item.state === "review").length;
  const markRecognized = () => {
    if (!selected) return;
    setTransactions((current) => current.map((item) => item.id === selected.id ? { ...item, state: "recognized" } : item));
    setNotice(`${selected.merchant} was marked as recognized.`);
  };
  const submitDispute = () => {
    if (!selected) return;
    const caseItem: DisputeCase = { id: `NXD-2609-${String(cases.length + 1247).padStart(4,"0")}`, merchant: selected.merchant, amount: selected.amount, opened: "02 Sep 2026", status: "Submitted", update: "Transaction secured · evidence review will begin shortly" };
    setCases((current) => [caseItem, ...current]);
    setTransactions((current) => current.map((item) => item.id === selected.id ? { ...item, state: "disputed" } : item));
    setReviewOpen(false); setDisputeOpen(false); setNotice(`Dispute ${caseItem.id} was submitted.`);
  };
  return <section class="nexa-experience-view nexa-insight-view" aria-label="Fraud and dispute centre">
    <header class="nexa-experience-heading"><div><p>Fraud and disputes</p><h1>Resolve what doesn’t look right.</h1><span>Review suspicious activity, protect the card and track every dispute case.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-insight-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <div class="nexa-fraud-summary"><div><span>Awaiting review</span><strong>{pending}</strong><small>Transactions need your confirmation</small></div><div><span>Open disputes</span><strong>{cases.filter((item) => item.status !== "Resolved").length}</strong><small>Case progress is tracked below</small></div><div><span>Card monitoring</span><strong>Active</strong><small>Nexa debit · •••• 4832</small></div></div>
    <section class="nexa-fraud-workspace"><header><div><span>Suspicious activity</span><h2>Review these payments</h2></div><small>Recognizing a payment removes it from priority review.</small></header><div class="nexa-fraud-cards">{transactions.map((item) => <button class={`${item.id === selectedId ? "is-selected" : ""} is-${item.state}`} type="button" onClick={() => setSelectedId(item.id)} key={item.id}><span>{item.merchant.slice(0,1)}</span><div><strong>{item.merchant}</strong><small>{item.date}</small><b>{money(item.amount)}</b></div><em>{item.state}</em></button>)}</div>
      {selected && <article class="nexa-fraud-detail"><header><div><span>Transaction review</span><h2>{selected.merchant}</h2><p>{selected.detail}</p></div><strong>{money(selected.amount)}</strong></header><dl><div><dt>Date and time</dt><dd>{selected.date}</dd></div><div><dt>Payment method</dt><dd>Online card payment</dd></div><div><dt>Location</dt><dd>Bengaluru, IN</dd></div><div><dt>Status</dt><dd>{selected.state}</dd></div></dl><div class="nexa-risk-reason"><span>Why Nexa flagged this</span><p>{selected.reason}.</p></div>{selected.state === "review" ? <footer><button type="button" onClick={markRecognized}>I recognize this</button><button class="is-danger" type="button" onClick={() => setDisputeOpen(true)}>I don’t recognize it</button></footer> : <div class={`nexa-fraud-resolution is-${selected.state}`}><strong>{selected.state === "recognized" ? "Marked as recognized" : "Dispute submitted"}</strong><span>{selected.state === "recognized" ? "No restriction was placed on this card." : "This transaction is now linked to an active case."}</span></div>}</article>}
    </section>
    <section class="nexa-case-list"><header><span>Case tracking</span><h2>Your disputes</h2></header>{cases.map((item) => <article key={item.id}><span>{item.status === "Resolved" ? "✓" : "D"}</span><div><strong>{item.merchant} · {money(item.amount)}</strong><small>{item.id} · Opened {item.opened}</small></div><p><strong>{item.status}</strong><small>{item.update}</small></p><button type="button" onClick={() => setNotice(`${item.id}: ${item.update}.`)}>View case</button></article>)}</section>
    {disputeOpen && selected && <div class="nexa-insight-backdrop" onClick={() => setDisputeOpen(false)}><section class="nexa-insight-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-dispute-title" onClick={(event) => event.stopPropagation()}><header><div><span>Start a dispute</span><h2 id="nexa-dispute-title">Tell us what happened.</h2><p>{selected.merchant} · {money(selected.amount)} · {selected.date}</p></div><button type="button" aria-label="Close dispute form" onClick={() => setDisputeOpen(false)}>×</button></header><form class="nexa-dispute-form" onSubmit={(event) => { event.preventDefault(); setReviewOpen(true); }}><label>Reason<select aria-label="Dispute reason" value={form.reason} onChange={(event) => setForm({ ...form, reason: (event.currentTarget as HTMLSelectElement).value })}><option>I do not recognize this transaction</option><option>I was charged the wrong amount</option><option>I paid but did not receive the service</option><option>I was charged more than once</option></select></label><label>Additional details <small>Optional</small><textarea aria-label="Dispute details" value={form.detail} onInput={(event) => setForm({ ...form, detail: (event.currentTarget as HTMLTextAreaElement).value })} placeholder="Add information that may help the review" /></label><button class="nexa-secure-card" type="button" role="switch" aria-checked={form.cardSecured} onClick={() => setForm({ ...form, cardSecured: !form.cardSecured })}><span><strong>Temporarily secure this card</strong><small>Blocks new card payments while the case begins</small></span><i /></button><button class="nexa-insight-primary" type="submit">Review dispute</button></form></section></div>}
    {reviewOpen && selected && <div class="nexa-insight-backdrop" onClick={() => setReviewOpen(false)}><section class="nexa-insight-confirm" role="dialog" aria-modal="true" aria-labelledby="nexa-dispute-review-title" onClick={(event) => event.stopPropagation()}><span>Final review</span><h2 id="nexa-dispute-review-title">Submit this dispute?</h2><p>Nexa will open a formal case for {money(selected.amount)} at {selected.merchant}.</p><dl><div><dt>Reason</dt><dd>{form.reason}</dd></div><div><dt>Card protection</dt><dd>{form.cardSecured ? "Temporarily secured" : "No change"}</dd></div><div><dt>Expected update</dt><dd>Within 2 business days</dd></div></dl><small>Submitting a dispute does not guarantee reimbursement. The transaction and available evidence will be reviewed.</small><footer><button type="button" onClick={() => setReviewOpen(false)}>Keep editing</button><button class="is-danger" type="button" onClick={submitDispute}>Submit dispute</button></footer></section></div>}
  </section>;
}

type Statement = { id: string; period: string; dates: string; account: string; credits: number; debits: number; closing: number; transactions: number };
const statements: Statement[] = [
  { id: "sep", period: "September 2026", dates: "01 Sep – 30 Sep 2026", account: "Primary · •••• 4291", credits: 82400, debits: 38420, closing: 96280, transactions: 42 },
  { id: "aug", period: "August 2026", dates: "01 Aug – 31 Aug 2026", account: "Primary · •••• 4291", credits: 86120, debits: 43280, closing: 52300, transactions: 51 },
  { id: "jul", period: "July 2026", dates: "01 Jul – 31 Jul 2026", account: "Primary · •••• 4291", credits: 82400, debits: 40210, closing: 9460, transactions: 47 }
];

export function StatementsReports({ onBack }: WorkspaceProps) {
  const [range, setRange] = useState("3-months");
  const [account, setAccount] = useState("all");
  const [report, setReport] = useState("statement");
  const [notice, setNotice] = useState("");
  const [selectedId, setSelectedId] = useState(statements[0].id);
  const selected = statements.find((item) => item.id === selectedId) || statements[0];
  const download = (kind: "csv" | "report", item = selected) => {
    const content = kind === "csv" ? `Period,Account,Money in,Money out,Closing balance,Transactions\n${item.period},${item.account},${item.credits},${item.debits},${item.closing},${item.transactions}\n` : `NEXA FINANCIAL REPORT\n${item.period}\n${item.account}\n\nMoney in: ${money(item.credits)}\nMoney out: ${money(item.debits)}\nClosing balance: ${money(item.closing)}\nTransactions: ${item.transactions}\n`;
    const blob = new Blob([content], { type: kind === "csv" ? "text/csv" : "text/plain" });
    const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = `nexa-${item.id}-${kind === "csv" ? "transactions.csv" : "financial-report.txt"}`; link.click(); URL.revokeObjectURL(url);
    setNotice(`${kind === "csv" ? "Transaction file" : "Financial report"} downloaded for ${item.period}.`);
  };
  return <section class="nexa-experience-view nexa-insight-view" aria-label="Statements and financial reports">
    <header class="nexa-experience-heading"><div><p>Statements and reports</p><h1>Your financial record, ready.</h1><span>Choose a period, review the summary and download a usable report.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    {notice && <div class="nexa-insight-notice" role="status"><span>↓</span><p>{notice}</p><button type="button" aria-label="Dismiss notification" onClick={() => setNotice("")}>×</button></div>}
    <form class="nexa-report-controls" onSubmit={(event) => event.preventDefault()}><label>Period<select aria-label="Statement period" value={range} onChange={(event) => setRange((event.currentTarget as HTMLSelectElement).value)}><option value="month">This month</option><option value="3-months">Last 3 months</option><option value="year">Financial year 2026–27</option><option value="custom">Custom dates</option></select></label><label>Account<select aria-label="Statement account" value={account} onChange={(event) => setAccount((event.currentTarget as HTMLSelectElement).value)}><option value="all">All connected accounts</option><option value="primary">Primary · •••• 4291</option><option value="savings">Savings · •••• 1186</option></select></label><label>Report type<select aria-label="Financial report type" value={report} onChange={(event) => setReport((event.currentTarget as HTMLSelectElement).value)}><option value="statement">Account statement</option><option value="spending">Spending summary</option><option value="cashflow">Cash-flow report</option></select></label><button type="button" onClick={() => setNotice(`Showing ${range === "3-months" ? "the last three months" : range.replace(/-/g," ")} across ${account === "all" ? "all connected accounts" : "the selected account"}.`)}>Apply</button></form>
    <div class="nexa-report-summary"><div><span>Money in</span><strong>{money(250920)}</strong><small>Last three statement periods</small></div><div><span>Money out</span><strong>{money(121910)}</strong><small>Across 140 transactions</small></div><div><span>Net movement</span><strong>+{money(129010)}</strong><small>Income exceeded outgoings</small></div></div>
    <section class="nexa-statement-workspace"><header><div><span>Available statements</span><h2>Monthly records</h2></div><button type="button" onClick={() => download("report")}>Download selected report</button></header><div class="nexa-statement-cards">{statements.map((item) => <button class={item.id === selectedId ? "is-selected" : ""} type="button" onClick={() => setSelectedId(item.id)} key={item.id}><span>{item.period.slice(0,3)}</span><div><strong>{item.period}</strong><small>{item.account}</small><b>{item.transactions} transactions</b></div><em>{money(item.closing)}</em></button>)}</div>
      <article class="nexa-statement-detail"><header><div><span>Statement preview</span><h2>{selected.period}</h2><p>{selected.dates} · {selected.account}</p></div><button type="button" onClick={() => download("csv")}>Download transactions .CSV</button></header><div class="nexa-statement-figures"><div><span>Opening balance</span><strong>{money(selected.closing - selected.credits + selected.debits)}</strong></div><div><span>Money in</span><strong>+{money(selected.credits)}</strong></div><div><span>Money out</span><strong>−{money(selected.debits)}</strong></div><div><span>Closing balance</span><strong>{money(selected.closing)}</strong></div></div><div class="nexa-report-categories"><div><span>Home & bills</span><strong>₹14,180</strong><i><b style={{width:"82%"}} /></i></div><div><span>Food & dining</span><strong>₹9,640</strong><i><b style={{width:"61%"}} /></i></div><div><span>Travel</span><strong>₹6,240</strong><i><b style={{width:"43%"}} /></i></div></div><footer><small>Generated from posted transactions. Pending payments may not appear until they settle.</small><button type="button" onClick={() => download("report")}>Download financial summary</button></footer></article>
    </section>
  </section>;
}

type ForecastFactor = { id: "income" | "bills" | "subscriptions" | "goals"; label: string; value: number; enabled: boolean; detail: string };
const startingFactors: ForecastFactor[] = [
  { id: "income", label: "Expected income", value: 82400, enabled: true, detail: "Salary expected on 28 Sep" },
  { id: "bills", label: "Bills and transfers", value: -36840, enabled: true, detail: "8 scheduled payments" },
  { id: "subscriptions", label: "Subscriptions", value: -3189, enabled: true, detail: "5 recurring services" },
  { id: "goals", label: "Savings contributions", value: -26500, enabled: true, detail: "2 active goal plans" }
];

export function CashFlowForecast({ onBack, onAsk }: WorkspaceProps) {
  const [horizon, setHorizon] = useState<30 | 60 | 90>(30);
  const [factors, setFactors] = useState(startingFactors);
  const current = 96280;
  const enabledTotal = factors.filter((item) => item.enabled).reduce((sum, item) => sum + item.value, 0);
  const multiplier = horizon / 30;
  const projected = Math.round(current + enabledTotal * multiplier);
  const lowest = Math.max(0, Math.round(current - 52500 * Math.min(1.35, multiplier)));
  const warning = lowest < 20000 || projected < 0;
  const values = [current, current - 1840, current - 26500, current - 36500, lowest, lowest + (factors.find((item) => item.id === "income")?.enabled ? 82400 : 0), projected];
  const min = Math.min(...values); const max = Math.max(...values); const points = values.map((value,index) => `${index * 100},${160 - ((value-min)/Math.max(1,max-min))*125}`).join(" ");
  const updateFactor = (id: ForecastFactor["id"], patch: Partial<ForecastFactor>) => setFactors((currentFactors) => currentFactors.map((item) => item.id === id ? { ...item, ...patch } : item));
  return <section class="nexa-experience-view nexa-insight-view" aria-label="Cash-flow forecasting">
    <header class="nexa-experience-heading"><div><p>Cash-flow forecast</p><h1>See the month before it happens.</h1><span>Projected from connected income, bills, subscriptions, transfers and savings goals.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    <div class="nexa-forecast-toolbar"><div><span>Forecast horizon</span>{([30,60,90] as const).map((days) => <button class={horizon === days ? "is-active" : ""} type="button" onClick={() => setHorizon(days)} key={days}>{days} days</button>)}</div><small>Last refreshed just now · Known transactions only</small></div>
    <div class="nexa-forecast-summary"><div><span>Current balance</span><strong>{money(current)}</strong><small>Primary account · •••• 4291</small></div><div><span>Projected balance</span><strong>{money(projected)}</strong><small>At the end of {horizon} days</small></div><div class={lowest < 20000 ? "is-warning" : ""}><span>Lowest expected</span><strong>{money(lowest)}</strong><small>Before the next salary credit</small></div></div>
    <div class="nexa-forecast-layout"><article class="nexa-forecast-chart"><header><div><span>Balance projection</span><h2>{horizon}-day outlook</h2></div><strong>{projected >= current ? "+" : ""}{money(projected-current)}</strong></header><svg viewBox="0 0 600 190" preserveAspectRatio="none" role="img" aria-label={`Projected balance over ${horizon} days`}><path class="grid" d="M0 35H600M0 78H600M0 121H600M0 164H600"/><polyline class="area" points={`0,180 ${points} 600,180`} /><polyline class="line" points={points}/>{values.map((value,index) => <circle cx={index*100} cy={160-((value-min)/Math.max(1,max-min))*125} r="3" key={index}/>)}</svg><div class="nexa-forecast-axis"><span>Today</span><span>{Math.round(horizon/2)} days</span><span>{horizon} days</span></div><div class="nexa-forecast-events"><span><b>05 Sep</b> Rent and bills · −₹23,840</span><span><b>10 Sep</b> Scheduled transfer · −₹10,000</span><span><b>28 Sep</b> Salary · +₹82,400</span></div></article>
      <aside class="nexa-forecast-assumptions"><header><span>Scenario controls</span><h2>What is included</h2><p>Switch a factor off or adjust its expected monthly amount to test a scenario.</p></header>{factors.map((item) => <div class={item.enabled ? "is-enabled" : ""} key={item.id}><button type="button" role="switch" aria-checked={item.enabled} onClick={() => updateFactor(item.id,{enabled:!item.enabled})}><i/><span><strong>{item.label}</strong><small>{item.detail}</small></span></button><label><span class="sr-only">{item.label} amount</span><b>{item.value < 0 ? "−₹" : "+₹"}</b><input aria-label={`${item.label} amount`} inputMode="numeric" value={Math.abs(item.value)} onInput={(event) => { const value=Number((event.currentTarget as HTMLInputElement).value.replace(/\D/g,""))||0; updateFactor(item.id,{value:item.value<0?-value:value}); }} /></label></div>)}</aside></div>
    <article class={`nexa-forecast-insight ${warning ? "is-warning" : ""}`}><div><span>{warning ? "Upcoming pressure" : "Healthy outlook"}</span><h2>{projected < 0 ? "This scenario runs out of available cash within the forecast window." : lowest < 20000 ? `Your balance may fall to ${money(lowest)} before salary arrives.` : "Your planned commitments remain covered."}</h2><p>{warning ? "Restoring expected income or moving the goal contribution would preserve a safer buffer without cancelling the goal." : "Known bills, transfers and savings contributions stay within the available balance."}</p></div><button type="button" onClick={() => onAsk("Help me improve my cash-flow forecast and avoid a low balance")}>Ask Nexa to improve it <b>↗</b></button></article>
  </section>;
}
