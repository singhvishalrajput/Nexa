import { h } from "preact";
type StatusCardProps = { activeIndex: number };

export function StatusCard({ activeIndex }: StatusCardProps) {
  return <article class="nexa-status-card" key={activeIndex} aria-live="polite">
    <div class="nexa-status-card__header"><span>{activeIndex === 2 ? "Spending insight" : "Current status"}</span><span class="nexa-active"><i /> {activeIndex === 1 ? "Secured" : "Updated"}</span></div>

    {activeIndex === 0 && <div class="nexa-answer-view"><p>Available Balance</p><strong>₹4,25,890.50</strong><small>Primary account · 4291</small><div class="nexa-chart" aria-label="Balance trend"><i /><i /><i /><i /><i /><i /></div></div>}

    {activeIndex === 1 && <div class="nexa-answer-view nexa-card-answer"><div class="nexa-debit-card"><span>Nexa debit</span><b>•••• 4832</b><small>VISHAL SINGH</small></div><div class="nexa-card-state"><span class="nexa-lock-mark" aria-hidden="true">×</span><div><p>Card frozen</p><strong>New payments are blocked</strong></div></div><small>You can unfreeze it whenever you’re ready.</small></div>}

    {activeIndex === 2 && <div class="nexa-answer-view nexa-spend-answer"><p>Spent last month</p><strong>₹38,420</strong><small>₹4,860 less than the previous month</small><div class="nexa-spend-breakdown"><div><span><b>Home & bills</b><small>₹14,180</small></span><i><b style={{ width: "78%" }} /></i></div><div><span><b>Food & dining</b><small>₹9,640</small></span><i><b style={{ width: "53%" }} /></i></div><div><span><b>Travel</b><small>₹6,240</small></span><i><b style={{ width: "34%" }} /></i></div></div></div>}
  </article>;
}
