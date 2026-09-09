import { h } from "preact";
import { useState } from "preact/hooks";

export type GoalDraft = { name: string; target: number };

type Goal = {
  id: string;
  name: string;
  target: number;
  saved: number;
  targetDate: string;
  monthly: number;
  account: string;
  automation: boolean;
  status: "active" | "paused" | "complete";
  symbol: string;
};

type GoalForm = Omit<Goal, "id" | "status" | "symbol">;
type GoalsWorkspaceProps = { draft: GoalDraft; onBack: () => void; onAsk: (prompt: string) => void };

const startingGoals: Goal[] = [
  { id: "goal-trip", name: "Goa in December", target: 85000, saved: 32000, targetDate: "2026-12", monthly: 18000, account: "Primary · •••• 4291", automation: false, status: "active", symbol: "G" },
  { id: "goal-studio", name: "Home studio", target: 140000, saved: 91000, targetDate: "2027-03", monthly: 8500, account: "Savings · •••• 1840", automation: true, status: "active", symbol: "H" }
];

const currency = (value: number) => `₹${Math.max(0, Math.round(value)).toLocaleString("en-IN")}`;
const monthLabel = (value: string) => value ? new Date(`${value}-01T00:00:00`).toLocaleDateString("en-IN", { month: "short", year: "numeric" }) : "Not set";
const monthsUntil = (value: string) => {
  if (!value) return 12;
  const today = new Date();
  const target = new Date(`${value}-01T00:00:00`);
  return Math.max(1, (target.getFullYear() - today.getFullYear()) * 12 + target.getMonth() - today.getMonth());
};
const roundedContribution = (amount: number, months: number) => Math.max(500, Math.ceil((amount / Math.max(1, months)) / 500) * 500);

export function GoalsWorkspace({ draft, onBack, onAsk }: GoalsWorkspaceProps) {
  const initialTarget = draft.target || 200000;
  const initialDate = "2027-12";
  const [goals, setGoals] = useState<Goal[]>(startingGoals);
  const [view, setView] = useState<"overview" | "editor">(draft.target > 0 ? "editor" : "overview");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [reviewOpen, setReviewOpen] = useState(false);
  const [form, setForm] = useState<GoalForm>({ name: draft.name || "Emergency fund", target: initialTarget, saved: 0, targetDate: initialDate, monthly: roundedContribution(initialTarget, monthsUntil(initialDate)), account: "Primary · •••• 4291", automation: false });

  const activeGoals = goals.filter((goal) => goal.status !== "complete");
  const totalSaved = activeGoals.reduce((sum, goal) => sum + goal.saved, 0);
  const totalTarget = activeGoals.reduce((sum, goal) => sum + goal.target, 0);
  const monthlyCommitted = activeGoals.filter((goal) => goal.status === "active").reduce((sum, goal) => sum + goal.monthly, 0);
  const remaining = Math.max(0, form.target - form.saved);
  const months = monthsUntil(form.targetDate);
  const recommended = roundedContribution(remaining, months);
  const plans = [
    { id: "steady", label: "Steady", amount: roundedContribution(remaining, months + 6), detail: `${months + 6} months · More flexibility` },
    { id: "recommended", label: "Recommended", amount: recommended, detail: `${months} months · On target` },
    { id: "accelerated", label: "Accelerated", amount: roundedContribution(remaining, Math.max(1, months - 4)), detail: `${Math.max(1, months - 4)} months · Finish sooner` }
  ];
  const safeToSave = 26900;
  const budgetAfter = safeToSave - form.monthly;
  const budgetState = budgetAfter >= 8000 ? "Comfortable" : budgetAfter >= 2500 ? "Manageable" : "Tight";

  const beginNewGoal = () => {
    const targetDate = "2027-12";
    setEditingId(null);
    setForm({ name: "", target: 100000, saved: 0, targetDate, monthly: roundedContribution(100000, monthsUntil(targetDate)), account: "Primary · •••• 4291", automation: false });
    setNotice("");
    setView("editor");
  };

  const editGoal = (goal: Goal) => {
    setEditingId(goal.id);
    setForm({ name: goal.name, target: goal.target, saved: goal.saved, targetDate: goal.targetDate, monthly: goal.monthly, account: goal.account, automation: goal.automation });
    setNotice("");
    setView("editor");
  };

  const saveGoal = (automationApproved: boolean) => {
    if (editingId) {
      setGoals((current) => current.map((goal) => goal.id === editingId ? { ...goal, ...form, automation: automationApproved } : goal));
      setNotice(`${form.name} was updated.`);
    } else {
      const goal: Goal = { ...form, id: `goal-${Date.now()}`, automation: automationApproved, status: "active", symbol: (form.name.trim()[0] || "S").toUpperCase() };
      setGoals((current) => [goal, ...current]);
      setNotice(`${form.name} was created${automationApproved ? " with a monthly contribution" : ""}.`);
    }
    setReviewOpen(false);
    setView("overview");
  };

  const toggleGoal = (id: string) => setGoals((current) => current.map((goal) => goal.id === id ? { ...goal, status: goal.status === "paused" ? "active" : "paused" } : goal));

  return <section class="nexa-experience-view nexa-goals-view" aria-label="Savings goals and smart budgeting">
    <header class="nexa-experience-heading"><div><p>Savings goals</p><h1>{view === "editor" ? (editingId ? "Adjust the plan." : "Make it achievable.") : "Save with a reason."}</h1><span>{view === "editor" ? "Choose a pace that protects your monthly budget." : "Track each goal and know what your budget can support."}</span></div><button type="button" onClick={view === "editor" ? () => setView("overview") : onBack}>{view === "editor" ? "← Goals overview" : "← Conversation"}</button></header>
    {notice && <div class="nexa-goal-notice" role="status"><span>✓</span><p>{notice}</p><button type="button" onClick={() => setNotice("")} aria-label="Dismiss notification">×</button></div>}

    {view === "overview" ? <>
      <div class="nexa-goal-summary"><div><span>Saved toward goals</span><strong>{currency(totalSaved)}</strong><small>of {currency(totalTarget)} combined</small></div><div><span>Monthly commitments</span><strong>{currency(monthlyCommitted)}</strong><small>Across {activeGoals.length} active goals</small></div><div><span>Available to allocate</span><strong>{currency(Math.max(0, safeToSave - monthlyCommitted))}</strong><small>After bills and current goals</small></div></div>
      <article class="nexa-budget-guidance"><div><span>Smart budget signal</span><h2>You can safely direct another ₹8,400 each month toward savings.</h2><p>Reducing dining and low-use subscriptions by ₹3,200 would create more breathing room without affecting essential spending.</p></div><button type="button" onClick={() => onAsk("Show me how to free up more money for my savings goals")}>Explore the opportunity <b>↗</b></button></article>
      <section class="nexa-goals-list"><header><div><span>Active plans</span><h2>Your goals</h2></div><button type="button" onClick={beginNewGoal}>＋ Create a goal</button></header><div>{goals.map((goal) => { const progress = Math.min(100, Math.round((goal.saved / goal.target) * 100)); return <article class={`is-${goal.status}`} key={goal.id}><header><span>{goal.symbol}</span><em>{goal.status}</em></header><h3>{goal.name}</h3><p><strong>{currency(goal.saved)}</strong><span>of {currency(goal.target)}</span></p><i><b style={{ width: `${progress}%` }}/></i><dl><div><dt>Progress</dt><dd>{progress}%</dd></div><div><dt>Monthly</dt><dd>{currency(goal.monthly)}</dd></div><div><dt>Target</dt><dd>{monthLabel(goal.targetDate)}</dd></div></dl><footer><button type="button" onClick={() => editGoal(goal)}>Manage goal</button><button type="button" onClick={() => toggleGoal(goal.id)}>{goal.status === "paused" ? "Resume" : "Pause"}</button></footer></article>; })}<button class="nexa-new-goal-card" type="button" onClick={beginNewGoal}><span>＋</span><strong>Create another goal</strong><small>Build a plan from your budget</small></button></div></section>
      <section class="nexa-budget-breakdown"><header><span>Monthly capacity</span><h2>What your income can support</h2></header><div><p><span>Income</span><strong>₹82,400</strong></p><i><b style={{ width: "100%" }}/></i></div><div><p><span>Essentials and regular spending</span><strong>₹55,500</strong></p><i><b style={{ width: "67%" }}/></i></div><div><p><span>Safe savings capacity</span><strong>₹26,900</strong></p><i class="is-accent"><b style={{ width: "33%" }}/></i></div></section>
    </> : <form class="nexa-goal-editor" onSubmit={(event) => { event.preventDefault(); if (form.name.trim() && form.target > 0 && form.monthly > 0) setReviewOpen(true); }}><div class="nexa-goal-form"><section><span>01 · Goal</span><h2>What are you saving for?</h2><div class="nexa-goal-fields"><label>Goal name<input aria-label="Goal name" value={form.name} onInput={(event) => setForm({ ...form, name: (event.currentTarget as HTMLInputElement).value })} placeholder="Emergency fund" required/></label><label>Target amount<div><b>₹</b><input aria-label="Target amount" inputMode="numeric" value={form.target} onInput={(event) => setForm({ ...form, target: Number((event.currentTarget as HTMLInputElement).value) || 0 })} required/></div></label><label>Already saved<div><b>₹</b><input aria-label="Already saved" inputMode="numeric" value={form.saved} onInput={(event) => setForm({ ...form, saved: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></div></label><label>Target month<input aria-label="Target month" type="month" value={form.targetDate} onInput={(event) => setForm({ ...form, targetDate: (event.currentTarget as HTMLInputElement).value })} required/></label></div></section><section><span>02 · Contribution plan</span><h2>Choose your pace.</h2><p>Nexa calculated these plans from the amount remaining and your target date.</p><div class="nexa-contribution-plans">{plans.map((plan) => <button class={form.monthly === plan.amount ? "is-selected" : ""} type="button" onClick={() => setForm({ ...form, monthly: plan.amount })} key={plan.id}><span>{plan.label}</span><strong>{currency(plan.amount)}<small>/month</small></strong><em>{plan.detail}</em></button>)}</div><label class="nexa-custom-contribution">Custom monthly contribution<div><b>₹</b><input aria-label="Monthly contribution" inputMode="numeric" value={form.monthly} onInput={(event) => setForm({ ...form, monthly: Number((event.currentTarget as HTMLInputElement).value) || 0 })}/></div></label></section><section><span>03 · Funding</span><h2>Decide how contributions happen.</h2><label class="nexa-goal-account">Funding account<select aria-label="Funding account" value={form.account} onChange={(event) => setForm({ ...form, account: (event.currentTarget as HTMLSelectElement).value })}><option>Primary · •••• 4291</option><option>Savings · •••• 1840</option></select></label><button class="nexa-auto-contribution" type="button" role="switch" aria-checked={form.automation} onClick={() => setForm({ ...form, automation: !form.automation })}><span><strong>Automatic monthly contribution</strong><small>Requires separate approval when the goal is created</small></span><i/></button></section></div><aside class={`is-${budgetState.toLowerCase()}`}><span>Budget impact</span><h2>{budgetState}</h2><p>This plan uses {currency(form.monthly)} of your estimated {currency(safeToSave)} monthly savings capacity.</p><dl><div><dt>Goal target</dt><dd>{currency(form.target)}</dd></div><div><dt>Target date</dt><dd>{monthLabel(form.targetDate)}</dd></div><div><dt>Monthly contribution</dt><dd>{currency(form.monthly)}</dd></div><div><dt>Flexible money left</dt><dd>{currency(Math.max(0, budgetAfter))}</dd></div></dl>{budgetAfter < 2500 && <small>Consider a later target date or a smaller contribution to preserve your monthly buffer.</small>}<button type="submit">Review goal plan</button></aside></form>}

    {reviewOpen && <div class="nexa-goal-dialog-backdrop" onClick={() => setReviewOpen(false)}><section role="dialog" aria-modal="true" aria-labelledby="nexa-goal-review-title" onClick={(event) => event.stopPropagation()}><span>Final review</span><h2 id="nexa-goal-review-title">Create {form.name}?</h2><p>The goal target is {currency(form.target)} by {monthLabel(form.targetDate)}, with a planned contribution of {currency(form.monthly)} per month.</p><div class="nexa-goal-review-details"><p><span>Funding account</span><strong>{form.account}</strong></p><p><span>Automatic contribution</span><strong>{form.automation ? "Awaiting approval" : "Off"}</strong></p></div>{form.automation && <small>Approving automation represents a separate instruction to schedule the monthly contribution in this prototype.</small>}<footer><button type="button" onClick={() => setReviewOpen(false)}>Keep editing</button>{form.automation && <button type="button" onClick={() => saveGoal(false)}>Create without automation</button>}<button class="is-primary" type="button" onClick={() => saveGoal(form.automation)}>{form.automation ? "Approve goal & automation" : editingId ? "Apply changes" : "Create goal"}</button></footer></section></div>}
  </section>;
}
