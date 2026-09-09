import { h } from "preact";

type StatusCardProps = { activeIndex: number };

export function StatusCard({ activeIndex }: StatusCardProps) {
  const titles = ["Account position", "Card control", "Monthly review"];

  return <article class="nexa-intelligence-canvas" aria-live="polite">
    <header class="nexa-intelligence-head">
      <strong>{titles[activeIndex]}</strong>
    </header>

    <div class="nexa-intelligence-scene" key={activeIndex}>
      {activeIndex === 0 && <section class="nexa-balance-scene">
        <div class="nexa-balance-figure"><span>Available now</span><strong>₹4,25,890<small>.50</small></strong><p>Primary account <b>•• 4291</b></p></div>
        <div class="nexa-balance-path"><header><span>30-day position</span><b>+8.4%</b></header><svg viewBox="0 0 360 116" role="img" aria-label="Balance increased over the last thirty days"><path class="is-grid" d="M0 92H360M0 52H360M0 12H360" /><path class="is-line" d="M0 88 C42 84 58 70 92 74 S140 57 176 62 S225 36 257 43 S311 18 360 20" /><circle cx="360" cy="20" r="4" /></svg><footer><span>01 Aug</span><span>Today</span></footer></div>
        <div class="nexa-balance-ledger"><div><span>Scheduled</span><strong>₹22,000</strong></div><div><span>Safe to spend</span><strong>₹3,78,890</strong></div><p>All known obligations are covered.</p></div>
      </section>}

      {activeIndex === 1 && <section class="nexa-freeze-scene">
        <div class="nexa-card-object"><header><strong>Nexa</strong><span>Debit</span></header><b>•••• 4832</b><footer><span>Vishal Singh</span><small>09 / 29</small></footer><em>Frozen</em></div>
        <div class="nexa-freeze-copy"><span>Card secured</span><h3>Locked in<br />one move.</h3><p>New payments and cash withdrawals are blocked. Existing recurring payments stay active.</p></div>
        <div class="nexa-freeze-controls"><div><span><i /> Card payments</span><b>Blocked</b></div><div><span><i /> ATM access</span><b>Blocked</b></div><div><span><i class="is-safe" /> Recurring payments</span><b class="is-safe">Active</b></div></div>
      </section>}

      {activeIndex === 2 && <section class="nexa-spending-scene">
        <div class="nexa-spending-lead"><span>Spent last month</span><strong>₹38,420</strong><p>↓ ₹4,860 versus the previous month</p></div>
        <div class="nexa-spending-composition" aria-label="Monthly spending composition"><i style={{ flex: 36.9 }}><span>Home & bills</span><b>₹14,180</b></i><i style={{ flex: 25.1 }}><span>Food</span><b>₹9,640</b></i><i style={{ flex: 16.2 }}><span>Travel</span><b>₹6,240</b></i><i class="is-rest" style={{ flex: 21.8 }}><span>Other</span><b>₹8,360</b></i></div>
        <div class="nexa-spending-note"><span>Monthly context</span><p>Dining and travel both declined while essential spending remained stable.</p><b>11% lower overall</b></div>
      </section>}
    </div>
  </article>;
}
