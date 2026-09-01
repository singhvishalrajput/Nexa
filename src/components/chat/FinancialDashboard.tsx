import { h } from "preact";
import { useState } from "preact/hooks";

type FinancialDashboardProps = {
  userName: string;
  onBack: () => void;
  onAsk: (prompt: string) => void;
};

const cashFlow = [
  { label: "Jan", income: 54, spent: 39 },
  { label: "Feb", income: 67, spent: 45 },
  { label: "Mar", income: 58, spent: 52 },
  { label: "Apr", income: 76, spent: 48 },
  { label: "May", income: 70, spent: 56 },
  { label: "Jun", income: 86, spent: 53 }
];

const transactions = [
  { merchant: "Amazon India", category: "Shopping", date: "Today, 11:42", amount: "− ₹2,499" },
  { merchant: "Salary credit", category: "Income", date: "28 Jun", amount: "+ ₹82,400", positive: true },
  { merchant: "BESCOM", category: "Utilities", date: "27 Jun", amount: "− ₹1,840" },
  { merchant: "Swiggy", category: "Food", date: "26 Jun", amount: "− ₹684" }
];

export function FinancialDashboard({ userName, onBack, onAsk }: FinancialDashboardProps) {
  const [period, setPeriod] = useState<"month" | "quarter">("month");
  const firstName = userName.trim().split(" ")[0] || "there";
  const totals = period === "month"
    ? { balance: "₹1,28,420", income: "₹82,400", spent: "₹51,280", saved: "₹31,120" }
    : { balance: "₹1,28,420", income: "₹2,41,900", spent: "₹1,49,620", saved: "₹92,280" };

  return <section class="nexa-dashboard" aria-label="Financial overview">
    <div class="nexa-dashboard-lead">
      <div><p>Financial overview</p><h1>Good evening, {firstName}.</h1><span>Your money, clearly organized. Updated two minutes ago.</span></div>
      <div class="nexa-dashboard-lead-actions"><div class="nexa-period-switch" aria-label="Dashboard period"><button class={period === "month" ? "is-active" : ""} type="button" onClick={() => setPeriod("month")}>Month</button><button class={period === "quarter" ? "is-active" : ""} type="button" onClick={() => setPeriod("quarter")}>Quarter</button></div><button class="nexa-dashboard-back" type="button" onClick={onBack}>← Conversation</button></div>
    </div>

    <div class="nexa-dashboard-summary">
      <div class="nexa-balance-block"><span>Available across accounts</span><strong>{totals.balance}</strong><small>₹12,480 more than last month</small></div>
      <div class="nexa-summary-metrics"><div><span>Income</span><strong>{totals.income}</strong><small>Received</small></div><div><span>Spent</span><strong>{totals.spent}</strong><small>63% of income</small></div><div><span>Saved</span><strong>{totals.saved}</strong><small>On track</small></div></div>
    </div>

    <div class="nexa-dashboard-grid">
      <article class="nexa-dashboard-panel nexa-cashflow-panel">
        <header><div><span>Cash flow</span><h2>Income versus spending</h2></div><div class="nexa-chart-key"><span><i class="is-income" />Income</span><span><i />Spent</span></div></header>
        <div class="nexa-cashflow-chart" role="img" aria-label="Six month cash flow chart showing income above spending in most months">{cashFlow.map((item) => <div class="nexa-cash-month" key={item.label}><div><i class="is-income" style={{ height: `${item.income}%` }} /><i style={{ height: `${item.spent}%` }} /></div><span>{item.label}</span></div>)}</div>
      </article>

      <article class="nexa-dashboard-panel nexa-insight-panel">
        <header><div><span>Nexa insight</span><h2>You can save ₹4,200 this month</h2></div><b>N</b></header>
        <p>Dining and quick-commerce spending is 18% above your usual range. Reducing three repeat orders keeps your savings goal on track.</p>
        <button type="button" onClick={() => onAsk("Explain how I can save ₹4,200 this month")}>Explain this insight <span>↗</span></button>
      </article>

      <article class="nexa-dashboard-panel nexa-accounts-panel">
        <header><div><span>Connected accounts</span><h2>Where your money sits</h2></div><button type="button" onClick={() => onAsk("Show details for all my connected accounts")}>View details</button></header>
        <div class="nexa-account-row"><span>NB</span><div><strong>Primary account</strong><small>Nexa Bank · •••• 4291</small></div><b>₹96,280</b></div>
        <div class="nexa-account-row"><span>HS</span><div><strong>Savings account</strong><small>Horizon Bank · •••• 1186</small></div><b>₹32,140</b></div>
      </article>

      <article class="nexa-dashboard-panel nexa-spending-panel">
        <header><div><span>Spending</span><h2>Top categories</h2></div><button type="button" onClick={() => onAsk("Analyze my spending categories this month")}>Analyze</button></header>
        <div class="nexa-spend-row"><div><strong>Food & dining</strong><span>₹12,840</span></div><i><b style={{ width: "78%" }} /></i></div>
        <div class="nexa-spend-row"><div><strong>Shopping</strong><span>₹9,620</span></div><i><b style={{ width: "59%" }} /></i></div>
        <div class="nexa-spend-row"><div><strong>Bills</strong><span>₹7,480</span></div><i><b style={{ width: "46%" }} /></i></div>
      </article>

      <article class="nexa-dashboard-panel nexa-progress-panel">
        <header><div><span>Plans</span><h2>Budgets and goals</h2></div><button type="button" onClick={() => onAsk("Help me update my budgets and savings goals")}>Manage</button></header>
        <div class="nexa-plan-row"><div><strong>Monthly spending</strong><small>₹51,280 of ₹70,000</small></div><span>73%</span><i><b style={{ width: "73%" }} /></i></div>
        <div class="nexa-plan-row"><div><strong>Emergency fund</strong><small>₹1,84,000 of ₹3,00,000</small></div><span>61%</span><i><b style={{ width: "61%" }} /></i></div>
      </article>

      <article class="nexa-dashboard-panel nexa-upcoming-panel">
        <header><div><span>Coming up</span><h2>Bills and subscriptions</h2></div><small>Next 7 days</small></header>
        <div><span><b>02</b><small>Jul</small></span><p><strong>Home loan EMI</strong><small>Auto-pay scheduled</small></p><b>₹18,500</b></div>
        <div><span><b>05</b><small>Jul</small></span><p><strong>Netflix</strong><small>Subscription</small></p><b>₹649</b></div>
      </article>

      <article class="nexa-dashboard-panel nexa-transactions-panel">
        <header><div><span>Recent activity</span><h2>Latest transactions</h2></div><button type="button" onClick={() => onAsk("Show and analyze all my recent transactions")}>View all</button></header>
        <div class="nexa-transaction-table">{transactions.map((item) => <div key={`${item.merchant}-${item.date}`}><span>{item.merchant.slice(0, 1)}</span><p><strong>{item.merchant}</strong><small>{item.category} · {item.date}</small></p><b class={item.positive ? "is-positive" : ""}>{item.amount}</b></div>)}</div>
      </article>

      <article class="nexa-dashboard-panel nexa-alert-panel">
        <header><div><span>Attention</span><h2>One useful alert</h2></div><b>!</b></header>
        <p>Your electricity bill is 22% higher than its three-month average. The payment is due on 8 July.</p>
        <button type="button" onClick={() => onAsk("Explain why my electricity bill is higher than usual")}>Review bill <span>↗</span></button>
      </article>
    </div>

    <div class="nexa-dashboard-quick"><span>Quick actions</span><div><button type="button" onClick={() => onAsk("I want to transfer money")}>Transfer money <b>↗</b></button><button type="button" onClick={() => onAsk("Help me pay a bill")}>Pay a bill <b>↗</b></button><button type="button" onClick={() => onAsk("I want to freeze my debit card")}>Freeze card <b>↗</b></button><button type="button" onClick={() => onAsk("Help me create a new budget")}>Create budget <b>↗</b></button></div></div>
  </section>;
}
