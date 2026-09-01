import { h } from "preact";
import { useState } from "preact/hooks";

export type TransferDraft = {
  recipient: string;
  amount: number;
  note?: string;
};

type ExperienceProps = {
  onBack: () => void;
  onAsk: (prompt: string) => void;
};

const transactions = [
  { id: "t1", merchant: "Amazon India", category: "Shopping", date: "30 Jun, 11:42", account: "Primary · 4291", amount: -2499 },
  { id: "t2", merchant: "Salary credit", category: "Income", date: "28 Jun, 09:10", account: "Primary · 4291", amount: 82400 },
  { id: "t3", merchant: "BESCOM", category: "Bills", date: "27 Jun, 18:20", account: "Primary · 4291", amount: -1840 },
  { id: "t4", merchant: "Swiggy", category: "Food", date: "26 Jun, 20:14", account: "Primary · 4291", amount: -684 },
  { id: "t5", merchant: "Uber", category: "Travel", date: "25 Jun, 08:32", account: "Primary · 4291", amount: -428 },
  { id: "t6", merchant: "Netflix", category: "Subscriptions", date: "23 Jun, 07:00", account: "Primary · 4291", amount: -649 },
  { id: "t7", merchant: "Apollo Pharmacy", category: "Health", date: "21 Jun, 15:48", account: "Primary · 4291", amount: -1260 },
  { id: "t8", merchant: "Café Coffee Day", category: "Food", date: "19 Jun, 17:25", account: "Primary · 4291", amount: -385 }
];

const formatMoney = (amount: number) => `${amount < 0 ? "− " : amount > 0 ? "+ " : ""}₹${Math.abs(amount).toLocaleString("en-IN")}`;

export function TransactionAnalysis({ onBack, onAsk }: ExperienceProps) {
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("All");
  const categories = ["All", "Food", "Shopping", "Bills", "Travel", "Subscriptions"];
  const filtered = transactions.filter((item) => (category === "All" || item.category === category) && item.merchant.toLowerCase().includes(query.toLowerCase()));

  return <section class="nexa-experience-view nexa-transactions-view" aria-label="Transaction analysis">
    <header class="nexa-experience-heading"><div><p>Transaction intelligence</p><h1>Your money, explained.</h1><span>June 2026 · All connected accounts</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    <div class="nexa-transaction-hero"><div><span>Spent last month</span><strong>₹38,420</strong><small>₹4,860 less than May</small></div><div><span>Money in</span><strong>₹86,200</strong><small>Salary and refunds</small></div><div><span>Transactions</span><strong>42</strong><small>Across two accounts</small></div></div>
    <div class="nexa-analysis-layout">
      <article class="nexa-analysis-chart"><header><div><span>Daily spending</span><h2>Where the month moved</h2></div><small>Average ₹1,280/day</small></header><div class="nexa-spend-line" role="img" aria-label="Daily spending trend for June"><svg viewBox="0 0 620 190" preserveAspectRatio="none"><path class="grid" d="M0 35H620M0 80H620M0 125H620M0 170H620"/><path class="area" d="M0 158L45 141L90 150L135 97L180 118L225 58L270 132L315 110L360 123L405 48L450 88L495 70L540 116L580 94L620 102V190H0Z"/><path class="line" d="M0 158L45 141L90 150L135 97L180 118L225 58L270 132L315 110L360 123L405 48L450 88L495 70L540 116L580 94L620 102"/></svg><div><span>01 Jun</span><span>15 Jun</span><span>30 Jun</span></div></div></article>
      <article class="nexa-category-analysis"><header><div><span>Category analysis</span><h2>Top spending areas</h2></div></header><div><p><strong>Home & bills</strong><span>₹14,180 · 37%</span></p><i><b style={{ width: "82%" }}/></i></div><div><p><strong>Food & dining</strong><span>₹9,640 · 25%</span></p><i><b style={{ width: "61%" }}/></i></div><div><p><strong>Travel</strong><span>₹6,240 · 16%</span></p><i><b style={{ width: "43%" }}/></i></div><button type="button" onClick={() => onAsk("Explain why my home and bills spending was highest last month")}>Ask Nexa about this <span>↗</span></button></article>
    </div>
    <article class="nexa-transaction-browser"><header><div><span>Activity</span><h2>All transactions</h2></div><label><span class="sr-only">Search transactions</span><input value={query} onInput={(event) => setQuery((event.currentTarget as HTMLInputElement).value)} placeholder="Search merchant" /></label></header><div class="nexa-transaction-filters" aria-label="Filter transactions by category">{categories.map((item) => <button class={category === item ? "is-active" : ""} type="button" onClick={() => setCategory(item)} key={item}>{item}</button>)}</div><div class="nexa-transaction-list">{filtered.map((item) => <div key={item.id}><span>{item.merchant.slice(0, 1)}</span><p><strong>{item.merchant}</strong><small>{item.category} · {item.account}</small></p><time>{item.date}</time><b class={item.amount > 0 ? "is-positive" : ""}>{formatMoney(item.amount)}</b></div>)}{!filtered.length && <p class="nexa-empty-state">No transactions match these filters.</p>}</div></article>
  </section>;
}

export function MoneyTransfer({ draft, onBack, onAsk }: ExperienceProps & { draft: TransferDraft }) {
  const [recipient, setRecipient] = useState(draft.recipient);
  const [amount, setAmount] = useState(String(draft.amount || ""));
  const [note, setNote] = useState(draft.note || "");
  const [sent, setSent] = useState(false);
  const numericAmount = Number(amount.replace(/,/g, "")) || 0;

  if (sent) return <section class="nexa-experience-view nexa-transfer-success" aria-live="polite"><div><span>Transfer complete</span><b>✓</b><h1>₹{numericAmount.toLocaleString("en-IN")} sent to {recipient}.</h1><p>Paid from Primary account · •••• 4291</p><dl><div><dt>Reference</dt><dd>NXA2609018427</dd></div><div><dt>Completed</dt><dd>Just now</dd></div><div><dt>Fee</dt><dd>₹0</dd></div></dl><button type="button" onClick={onBack}>Return to conversation</button></div></section>;

  return <section class="nexa-experience-view nexa-transfer-view" aria-label="Review money transfer">
    <header class="nexa-experience-heading"><div><p>Secure transfer</p><h1>Review before sending.</h1><span>Nexa has prepared the details from your request.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    <form class="nexa-transfer-layout" onSubmit={(event) => { event.preventDefault(); if (recipient.trim() && numericAmount > 0) setSent(true); }}>
      <div class="nexa-transfer-form"><section><span>01 · Recipient</span><label>Send to<input value={recipient} onInput={(event) => setRecipient((event.currentTarget as HTMLInputElement).value)} required /></label><div class="nexa-recipient-verified"><b>{recipient.slice(0, 2).toUpperCase() || "--"}</b><p><strong>{recipient || "Recipient required"}</strong><small>{recipient ? "Verified UPI contact · rahul@upi" : "Enter a recipient"}</small></p><span>{recipient ? "Verified" : "Required"}</span></div></section><section><span>02 · Amount</span><label>Amount<div class="nexa-amount-input"><b>₹</b><input inputMode="decimal" value={amount} onInput={(event) => setAmount((event.currentTarget as HTMLInputElement).value.replace(/[^0-9.,]/g, ""))} required /></div></label><label>Note <small>Optional</small><input value={note} onInput={(event) => setNote((event.currentTarget as HTMLInputElement).value)} placeholder="What is this for?" /></label></section><section><span>03 · Pay from</span><button class="nexa-source-account" type="button"><i>NB</i><p><strong>Primary account</strong><small>Nexa Bank · •••• 4291</small></p><b>₹96,280</b></button></section></div>
      <aside class="nexa-transfer-review"><span>Final review</span><h2>Everything looks ready.</h2><div><p><span>Recipient</span><strong>{recipient || "—"}</strong></p><p><span>Transfer amount</span><strong>₹{numericAmount.toLocaleString("en-IN")}</strong></p><p><span>Transfer fee</span><strong>₹0</strong></p><p><span>Balance after</span><strong>₹{Math.max(0, 96280 - numericAmount).toLocaleString("en-IN")}</strong></p></div><p class="nexa-transfer-safety">Your bank will securely verify this payment. Nexa never stores your UPI PIN.</p><button type="submit" disabled={!recipient.trim() || numericAmount <= 0}>Send ₹{numericAmount.toLocaleString("en-IN")}</button><button class="nexa-review-question" type="button" onClick={() => onAsk(`Is it safe to send ₹${numericAmount.toLocaleString("en-IN")} to ${recipient}?`)}>Ask Nexa before sending</button></aside>
    </form>
  </section>;
}

type Subscription = { id: string; name: string; plan: string; amount: number; usage: string; lastUsed: string; status: "active" | "cancelled"; attention?: boolean };
const startingSubscriptions: Subscription[] = [
  { id: "s1", name: "Netflix", plan: "Standard", amount: 649, usage: "Frequent", lastUsed: "Yesterday", status: "active" },
  { id: "s2", name: "Spotify", plan: "Individual", amount: 119, usage: "Frequent", lastUsed: "Today", status: "active" },
  { id: "s3", name: "Adobe Creative Cloud", plan: "Photography", amount: 797, usage: "Low usage", lastUsed: "24 days ago", status: "active", attention: true },
  { id: "s4", name: "Amazon Prime", plan: "Annual", amount: 125, usage: "Occasional", lastUsed: "8 days ago", status: "active" },
  { id: "s5", name: "Cult.fit", plan: "Elite", amount: 1499, usage: "Not used", lastUsed: "41 days ago", status: "active", attention: true }
];

export function SubscriptionManager({ onBack, onAsk }: ExperienceProps) {
  const [items, setItems] = useState(startingSubscriptions);
  const [filter, setFilter] = useState<"all" | "attention">("all");
  const [pendingCancel, setPendingCancel] = useState<string | null>(null);
  const active = items.filter((item) => item.status === "active");
  const visible = active.filter((item) => filter === "all" || item.attention);
  const monthly = active.reduce((total, item) => total + item.amount, 0);
  const selected = items.find((item) => item.id === pendingCancel);

  return <section class="nexa-experience-view nexa-subscriptions-view" aria-label="Subscription manager">
    <header class="nexa-experience-heading"><div><p>Subscription manager</p><h1>Keep what earns its place.</h1><span>Recurring payments detected across your connected accounts.</span></div><button type="button" onClick={onBack}>← Conversation</button></header>
    <div class="nexa-subscription-summary"><div><span>Monthly cost</span><strong>₹{monthly.toLocaleString("en-IN")}</strong><small>₹{(monthly * 12).toLocaleString("en-IN")} per year</small></div><div><span>Active subscriptions</span><strong>{active.length}</strong><small>Across entertainment and lifestyle</small></div><div><span>Potential savings</span><strong>₹2,296</strong><small>Two subscriptions need attention</small></div></div>
    <article class="nexa-subscription-insight"><div><span>Nexa insight</span><h2>Two subscriptions cost ₹27,552 yearly but are rarely used.</h2><p>Review Adobe Creative Cloud and Cult.fit. Cancelling both would improve your monthly savings without affecting frequently used services.</p></div><button type="button" onClick={() => setFilter("attention")}>Review low usage <span>↗</span></button></article>
    <div class="nexa-subscription-toolbar"><div><button class={filter === "all" ? "is-active" : ""} type="button" onClick={() => setFilter("all")}>All active</button><button class={filter === "attention" ? "is-active" : ""} type="button" onClick={() => setFilter("attention")}>Needs attention</button></div><button type="button" onClick={() => onAsk("Analyze whether my current subscriptions are worth their cost")}>Ask Nexa to analyze</button></div>
    <div class="nexa-subscription-list">{visible.map((item) => <article class={item.attention ? "needs-attention" : ""} key={item.id}><span>{item.name.slice(0, 1)}</span><div><strong>{item.name}</strong><small>{item.plan} · {item.usage}</small></div><p><strong>₹{item.amount.toLocaleString("en-IN")}</strong><small>per month</small></p><p><strong>{item.lastUsed}</strong><small>last used</small></p><button type="button" onClick={() => setPendingCancel(item.id)}>Manage</button></article>)}</div>
    {pendingCancel && selected && <div class="nexa-subscription-dialog-backdrop" onClick={() => setPendingCancel(null)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-cancel-subscription" onClick={(event) => event.stopPropagation()}><span>Manage subscription</span><h2 id="nexa-cancel-subscription">Cancel {selected.name}?</h2><p>Nexa will mark this subscription as cancelled in this prototype. A real cancellation would require confirmation from the provider.</p><div><button type="button" onClick={() => setPendingCancel(null)}>Keep subscription</button><button class="is-destructive" type="button" onClick={() => { setItems((current) => current.map((item) => item.id === selected.id ? { ...item, status: "cancelled" } : item)); setPendingCancel(null); }}>Cancel subscription</button></div></section></div>}
  </section>;
}

type CardAction = "freeze" | "unfreeze" | "report" | "fraud";
type CardStatus = "active" | "frozen" | "blocked";
type CardSettings = { monthlyLimit: number; dailyLimit: number; atmLimit: number; international: boolean; online: boolean; contactless: boolean };
type CardActivity = { merchant: string; detail: string; amount: string; state: "Review" | "Cleared" };
type ManagedCard = { id: string; bank: string; brand: string; type: string; last4: string; expiry: string; account: string; status: CardStatus; tone: string; settings: CardSettings; alertStatus: "review" | "verified" | "blocked"; activity: CardActivity[] };

const startingCards: ManagedCard[] = [
  { id: "primary-4832", bank: "Nexa", brand: "VISA", type: "Debit", last4: "4832", expiry: "08/29", account: "Primary · •••• 4291", status: "active", tone: "graphite", settings: { monthlyLimit: 200000, dailyLimit: 75000, atmLimit: 25000, international: false, online: true, contactless: true }, alertStatus: "review", activity: [
    { merchant: "Amazon Marketplace", detail: "Online · Singapore · Today, 03:14", amount: "− ₹12,999", state: "Review" },
    { merchant: "Swiggy", detail: "Online · Bengaluru · Yesterday", amount: "− ₹684", state: "Cleared" },
    { merchant: "Fabindia", detail: "Contactless · Bengaluru · 29 Jun", amount: "− ₹3,240", state: "Cleared" }
  ] },
  { id: "travel-7310", bank: "HDFC", brand: "Mastercard", type: "Credit", last4: "7310", expiry: "11/28", account: "Travel credit line", status: "active", tone: "sand", settings: { monthlyLimit: 150000, dailyLimit: 50000, atmLimit: 10000, international: true, online: true, contactless: false }, alertStatus: "verified", activity: [
    { merchant: "IndiGo", detail: "Online · Bengaluru · 27 Jun", amount: "− ₹8,420", state: "Cleared" },
    { merchant: "The Oberoi", detail: "Chip & PIN · Mumbai · 24 Jun", amount: "− ₹16,800", state: "Cleared" }
  ] }
];

const emptyCardForm = { bank: "", cardNumber: "", expiry: "", type: "Debit", brand: "VISA" };

export function CardControls({ onBack, onAsk }: ExperienceProps) {
  const [cards, setCards] = useState<ManagedCard[]>(startingCards);
  const [selectedId, setSelectedId] = useState(startingCards[0].id);
  const [view, setView] = useState<"overview" | "settings">("overview");
  const [pendingAction, setPendingAction] = useState<CardAction | null>(null);
  const [pendingSettings, setPendingSettings] = useState(false);
  const [addCardOpen, setAddCardOpen] = useState(false);
  const [newCard, setNewCard] = useState(emptyCardForm);
  const [notice, setNotice] = useState("");
  const selected = cards.find((card) => card.id === selectedId) || cards[0];
  const [settingsDraft, setSettingsDraft] = useState<CardSettings>({ ...startingCards[0].settings });
  const isFrozen = selected.status === "frozen";
  const isBlocked = selected.status === "blocked";

  const updateSelected = (change: (card: ManagedCard) => ManagedCard) => setCards((current) => current.map((card) => card.id === selected.id ? change(card) : card));
  const chooseCard = (card: ManagedCard) => { setSelectedId(card.id); setSettingsDraft({ ...card.settings }); setView("overview"); setNotice(""); };
  const openSettings = () => { setSettingsDraft({ ...selected.settings }); setView("settings"); setNotice(""); };

  const confirmAction = () => {
    if (pendingAction === "freeze") { updateSelected((card) => ({ ...card, status: "frozen" })); setNotice(`${selected.bank} card ending ${selected.last4} is frozen.`); }
    if (pendingAction === "unfreeze") { updateSelected((card) => ({ ...card, status: "active" })); setNotice(`${selected.bank} card ending ${selected.last4} is active again.`); }
    if (pendingAction === "report") { updateSelected((card) => ({ ...card, status: "blocked" })); setNotice(`Card ending ${selected.last4} was permanently blocked in this prototype.`); }
    if (pendingAction === "fraud") { updateSelected((card) => ({ ...card, status: "frozen", alertStatus: "blocked" })); setNotice(`Payment disputed and card ending ${selected.last4} frozen for protection.`); }
    setPendingAction(null);
  };

  const saveSettings = () => {
    updateSelected((card) => ({ ...card, settings: { ...settingsDraft } }));
    setPendingSettings(false);
    setView("overview");
    setNotice(`Settings updated for ${selected.bank} card ending ${selected.last4}.`);
  };

  const addCard = (event: Event) => {
    event.preventDefault();
    const digits = newCard.cardNumber.replace(/\D/g, "");
    if (digits.length < 12 || !newCard.bank.trim() || !/^\d{2}\/\d{2}$/.test(newCard.expiry)) return;
    const card: ManagedCard = { id: `card-${Date.now()}`, bank: newCard.bank.trim(), brand: newCard.brand, type: newCard.type, last4: digits.slice(-4), expiry: newCard.expiry, account: `${newCard.type} card`, status: "active", tone: cards.length % 2 ? "clay" : "graphite", settings: { monthlyLimit: 100000, dailyLimit: 50000, atmLimit: 20000, international: false, online: true, contactless: true }, alertStatus: "verified", activity: [] };
    setCards((current) => [...current, card]);
    setSelectedId(card.id);
    setSettingsDraft({ ...card.settings });
    setNewCard(emptyCardForm);
    setAddCardOpen(false);
    setView("overview");
    setNotice(`${card.bank} ${card.type.toLowerCase()} card ending ${card.last4} was added.`);
  };

  const actionCopy = pendingAction === "freeze" ? { eyebrow: "Freeze card", title: `Pause card •••• ${selected.last4}?`, body: "New purchases, ATM withdrawals and contactless payments will be blocked immediately. You can unfreeze it later." } : pendingAction === "unfreeze" ? { eyebrow: "Unfreeze card", title: `Reactivate card •••• ${selected.last4}?`, body: "Purchases, ATM withdrawals and contactless payments will be available again immediately." } : pendingAction === "report" ? { eyebrow: "Lost or stolen", title: `Permanently block card •••• ${selected.last4}?`, body: "This cannot be undone. The card will be blocked and a replacement process would begin with your bank." } : { eyebrow: "Fraud protection", title: `Protect card •••• ${selected.last4}?`, body: "Nexa will freeze this card and mark the highlighted transaction as disputed in this prototype." };

  return <section class="nexa-experience-view nexa-card-view" aria-label="Manage cards">
    <header class="nexa-experience-heading"><div><p>Manage cards</p><h1>{view === "settings" ? "Set the rules." : "Every card. One clear view."}</h1><span>{view === "settings" ? `Editing ${selected.bank} ${selected.type.toLowerCase()} card · •••• ${selected.last4}` : "Select a card to manage its security, settings and recent activity."}</span></div><button type="button" onClick={view === "settings" ? () => setView("overview") : onBack}>{view === "settings" ? "← Card overview" : "← Conversation"}</button></header>
    {notice && <div class="nexa-card-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" onClick={() => setNotice("")} aria-label="Dismiss notification">×</button></div>}

    <section class="nexa-card-portfolio" aria-label="Your cards"><header><div><span>Your cards</span><strong>{cards.length} connected</strong></div></header><div role="tablist" aria-label="Choose a card">{cards.map((card) => <button class={card.id === selected.id ? "is-selected" : ""} type="button" role="tab" aria-selected={card.id === selected.id} onClick={() => chooseCard(card)} key={card.id}><i class={`is-${card.tone}`} aria-hidden="true"/><span><strong>{card.bank} {card.type}</strong><small>{card.brand} · •••• {card.last4}</small></span><em class={`is-${card.status}`}>{card.status}</em></button>)}<button class="nexa-add-card-tile" type="button" onClick={() => setAddCardOpen(true)}><i aria-hidden="true">＋</i><span><strong>Add another card</strong><small>Connect securely</small></span></button></div></section>

    {view === "overview" ? <>
      <div class="nexa-card-command-center"><div class="nexa-card-identity"><div class={`nexa-payment-card is-${selected.tone} ${selected.status !== "active" ? "is-frozen" : ""}`}><header><span>{selected.bank}</span><b>{selected.brand}</b></header><i aria-hidden="true"/><strong>••••&nbsp; {selected.last4}</strong><footer><p><small>{selected.type}</small><b>VISHAL SINGH</b></p><p><small>Expires</small><b>{selected.expiry}</b></p></footer>{selected.status !== "active" && <div><span>{selected.status}</span></div>}</div><dl><div><dt>Linked account</dt><dd>{selected.account}</dd></div><div><dt>Monthly limit</dt><dd>₹{selected.settings.monthlyLimit.toLocaleString("en-IN")}</dd></div><div><dt>International usage</dt><dd>{selected.settings.international ? "On" : "Off"}</dd></div></dl></div><div class="nexa-protection-actions"><span>Card status</span><div><i class={selected.status !== "active" ? "is-frozen" : ""}/><strong>{selected.status.charAt(0).toUpperCase() + selected.status.slice(1)}</strong></div><h2>{isBlocked ? "This card is permanently blocked." : isFrozen ? "This card is paused." : "Your card is ready to use."}</h2><p>{isBlocked ? "Contact your bank to follow the replacement process." : isFrozen ? "Purchases and withdrawals are blocked until you reactivate it." : "Freeze it instantly if the card is missing or you notice something unusual."}</p>{!isBlocked && <button class={isFrozen ? "is-unfreeze" : ""} type="button" onClick={() => setPendingAction(isFrozen ? "unfreeze" : "freeze")}>{isFrozen ? "Unfreeze card" : "Freeze card"}</button>}<button class="nexa-settings-card" type="button" onClick={openSettings}>Card settings</button>{!isBlocked && <button class="nexa-report-card" type="button" onClick={() => setPendingAction("report")}>Report lost or stolen</button>}</div></div>

      {selected.activity.some((item) => item.state === "Review") && <article class={`nexa-security-review is-${selected.alertStatus}`}><div class="nexa-security-symbol"><span>!</span></div><div><span>Security review</span><h2>{selected.alertStatus === "review" ? "Do you recognize this payment?" : selected.alertStatus === "verified" ? "Payment confirmed as yours" : "Payment disputed and card protected"}</h2><p>{selected.activity[0].merchant} · {selected.activity[0].detail} · {selected.activity[0].amount}</p></div>{selected.alertStatus === "review" ? <div class="nexa-security-actions"><button type="button" onClick={() => { updateSelected((card) => ({ ...card, alertStatus: "verified" })); setNotice("Transaction verified as yours."); }}>Yes, it’s mine</button><button type="button" onClick={() => setPendingAction("fraud")}>No, protect my card</button></div> : <button class="nexa-security-reset" type="button" onClick={() => updateSelected((card) => ({ ...card, alertStatus: "review" }))}>Review again</button>}</article>}

      <div class="nexa-card-detail-layout"><article class="nexa-card-activity"><header><div><span>Recent card activity</span><h2>Latest payments</h2></div></header>{selected.activity.length ? selected.activity.map((item) => <div key={`${selected.id}-${item.merchant}`}><span>{item.merchant.slice(0, 1)}</span><p><strong>{item.merchant}</strong><small>{item.detail}</small></p><b>{item.amount}</b><em class={item.state === "Cleared" ? "is-cleared" : ""}>{item.state}</em></div>) : <p class="nexa-card-empty">No payments yet on this card.</p>}</article><aside class="nexa-card-assistance"><span>Nexa protection</span><h2>Unsure about recent activity?</h2><p>Nexa can compare merchant location, timing and your usual spending pattern before you decide what to do.</p><button type="button" onClick={() => onAsk(`Analyze activity on my ${selected.bank} card ending ${selected.last4}`)}>Analyze recent activity <b>↗</b></button></aside></div>
    </> : <form class="nexa-card-settings-workspace" onSubmit={(event) => { event.preventDefault(); setPendingSettings(true); }}><div><section><span>Spending limits</span><h2>Choose comfortable boundaries.</h2><p>These limits apply only to the selected card.</p><div class="nexa-limit-fields"><label>Monthly limit <span>₹</span><input aria-label="Monthly limit" inputMode="numeric" value={settingsDraft.monthlyLimit} onInput={(event) => setSettingsDraft({ ...settingsDraft, monthlyLimit: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></label><label>Daily limit <span>₹</span><input aria-label="Daily limit" inputMode="numeric" value={settingsDraft.dailyLimit} onInput={(event) => setSettingsDraft({ ...settingsDraft, dailyLimit: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></label><label>ATM withdrawal limit <span>₹</span><input aria-label="ATM withdrawal limit" inputMode="numeric" value={settingsDraft.atmLimit} onInput={(event) => setSettingsDraft({ ...settingsDraft, atmLimit: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></label></div></section><section><span>Payment access</span><h2>Decide where the card works.</h2><div class="nexa-setting-switches"><button type="button" role="switch" aria-checked={settingsDraft.international} onClick={() => setSettingsDraft({ ...settingsDraft, international: !settingsDraft.international })}><span><strong>International usage</strong><small>Allow payments outside India</small></span><i/></button><button type="button" role="switch" aria-checked={settingsDraft.online} onClick={() => setSettingsDraft({ ...settingsDraft, online: !settingsDraft.online })}><span><strong>Online payments</strong><small>Allow ecommerce and in-app purchases</small></span><i/></button><button type="button" role="switch" aria-checked={settingsDraft.contactless} onClick={() => setSettingsDraft({ ...settingsDraft, contactless: !settingsDraft.contactless })}><span><strong>Contactless payments</strong><small>Tap to pay at supported terminals</small></span><i/></button></div></section></div><aside><span>Selected card</span><strong>{selected.bank} {selected.type}</strong><small>•••• {selected.last4}</small><dl><div><dt>Monthly</dt><dd>₹{settingsDraft.monthlyLimit.toLocaleString("en-IN")}</dd></div><div><dt>Daily</dt><dd>₹{settingsDraft.dailyLimit.toLocaleString("en-IN")}</dd></div><div><dt>International</dt><dd>{settingsDraft.international ? "On" : "Off"}</dd></div></dl><p>Changes are reviewed before they are applied. Your bank may require additional verification in production.</p><button type="submit">Review changes</button></aside></form>}

    {addCardOpen && <div class="nexa-card-sheet-backdrop" onClick={() => setAddCardOpen(false)}><section class="nexa-card-sheet" role="dialog" aria-modal="true" aria-labelledby="nexa-add-card-title" onClick={(event) => event.stopPropagation()}><header><div><span>Add a card</span><h2 id="nexa-add-card-title">Connect another card.</h2><p>Enter the card details to create a secure connection in this prototype.</p></div><button type="button" onClick={() => setAddCardOpen(false)} aria-label="Close add card">×</button></header><form class="nexa-add-card-form" onSubmit={addCard}><label>Bank name<input aria-label="Bank name" value={newCard.bank} onInput={(event) => setNewCard({ ...newCard, bank: (event.currentTarget as HTMLInputElement).value })} placeholder="e.g. ICICI Bank" required/></label><label>Card number<input aria-label="Card number" inputMode="numeric" maxLength={19} value={newCard.cardNumber} onInput={(event) => setNewCard({ ...newCard, cardNumber: (event.currentTarget as HTMLInputElement).value.replace(/[^0-9 ]/g, "") })} placeholder="0000 0000 0000 0000" required/></label><div><label>Card type<select aria-label="Card type" value={newCard.type} onChange={(event) => setNewCard({ ...newCard, type: (event.currentTarget as HTMLSelectElement).value })}><option>Debit</option><option>Credit</option></select></label><label>Network<select aria-label="Card network" value={newCard.brand} onChange={(event) => setNewCard({ ...newCard, brand: (event.currentTarget as HTMLSelectElement).value })}><option>VISA</option><option>Mastercard</option><option>RuPay</option></select></label><label>Expiry<input aria-label="Card expiry" value={newCard.expiry} onInput={(event) => setNewCard({ ...newCard, expiry: (event.currentTarget as HTMLInputElement).value })} placeholder="MM/YY" maxLength={5} required/></label></div><p>For your safety, Nexa stores only the last four digits in this demonstration.</p><button type="submit">Verify and add card</button></form></section></div>}

    {pendingSettings && <div class="nexa-card-dialog-backdrop" onClick={() => setPendingSettings(false)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-settings-review" onClick={(event) => event.stopPropagation()}><span>Review settings</span><h2 id="nexa-settings-review">Apply these card rules?</h2><p>Monthly limit ₹{settingsDraft.monthlyLimit.toLocaleString("en-IN")} · Daily limit ₹{settingsDraft.dailyLimit.toLocaleString("en-IN")} · International usage {settingsDraft.international ? "on" : "off"}.</p><div><button type="button" onClick={() => setPendingSettings(false)}>Keep editing</button><button class="is-primary" type="button" onClick={saveSettings}>Apply changes</button></div></section></div>}
    {pendingAction && <div class="nexa-card-dialog-backdrop" onClick={() => setPendingAction(null)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-card-action-title" onClick={(event) => event.stopPropagation()}><span>{actionCopy.eyebrow}</span><h2 id="nexa-card-action-title">{actionCopy.title}</h2><p>{actionCopy.body}</p><div><button type="button" onClick={() => setPendingAction(null)}>Go back</button><button class="is-primary" type="button" onClick={confirmAction}>{pendingAction === "freeze" ? "Freeze card" : pendingAction === "unfreeze" ? "Unfreeze card" : pendingAction === "report" ? "Block permanently" : "Block and dispute"}</button></div></section></div>}
  </section>;
}
