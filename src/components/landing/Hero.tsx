import { h } from "preact";
import { useEffect, useState } from "preact/hooks";

const prompts = ["Show my recent transactions", "Mera balance batao", "माझे transactions दाखवा", "Transfer ₹5,000 to Rahul"];

export function Hero() {
  const [prompt, setPrompt] = useState("");
  const [phraseIndex, setPhraseIndex] = useState(0);
  const [deleting, setDeleting] = useState(false);
  useEffect(() => {
    const phrase = prompts[phraseIndex];
    const complete = prompt === phrase;
    const empty = prompt.length === 0;
    const timer = window.setTimeout(() => {
      if (complete) setDeleting(true);
      else if (deleting && empty) { setDeleting(false); setPhraseIndex((phraseIndex + 1) % prompts.length); }
      else setPrompt(deleting ? prompt.slice(0, -1) : phrase.slice(0, prompt.length + 1));
    }, complete ? 1700 : deleting ? 45 : 80);
    return () => window.clearTimeout(timer);
  }, [prompt, phraseIndex, deleting]);
  return (
    <section class="nexa-hero" id="platform">
      <div class="nexa-hero__intro">
        <p class="nexa-kicker">Nexa / personal banking interface</p>
        <h1>
          Less banking.
          <br />
          More doing.
        </h1>

        <p>
          Ask in your own words. Check a balance, review a payment, or move
          money without working through a maze of menus.
        </p>
      </div>

      <div class="nexa-command-panel">
        <div class="nexa-command-panel__prompt">
          <span class="nexa-microphone" aria-hidden="true">↳</span>

          <span class="nexa-typewriter">{prompt}</span>
        </div>

        <div class="nexa-transactions">
          <p class="nexa-eyebrow">A quick look at your ledger</p>

          <div class="nexa-transaction">
            <div class="nexa-transaction__details">
              <span class="nexa-transaction__icon" aria-hidden="true">▰</span><div><strong>Amazon</strong><span>Today, 2:30 PM</span></div>
            </div>

            <strong>-₹2,450.00</strong>
          </div>

          <div class="nexa-transaction">
            <div class="nexa-transaction__details">
              <span class="nexa-transaction__icon nexa-transaction__icon--primary" aria-hidden="true">▣</span><div><strong>Salary Credited</strong><span>Yesterday</span></div>
            </div>

            <strong class="nexa-positive">+₹1,20,000.00</strong>
          </div>
        </div>
      </div>
    </section>
  );
}
