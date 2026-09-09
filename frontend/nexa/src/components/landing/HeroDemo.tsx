import { h } from "preact";
import { useEffect, useState } from "preact/hooks";

const prompts = ["Show my recent transactions", "Mera balance batao", "माझे transactions दाखवा", "Transfer ₹5,000 to Rahul"];

function QueryResult({ index, visible }: { index: number; visible: boolean }) {
  return <div class={`nexa-query-result ${visible ? "is-visible" : ""}`} aria-live="polite">
    {index === 0 && <><div class="nexa-result-heading"><span>Recent activity</span><b>2 entries</b></div><div class="nexa-result-row"><span class="nexa-result-mark">A</span><div><strong>Amazon</strong><small>Today · 2:30 PM</small></div><b>−₹2,450</b></div><div class="nexa-result-row"><span class="nexa-result-mark is-accent">₹</span><div><strong>Salary credited</strong><small>Yesterday</small></div><b class="is-positive">+₹1,20,000</b></div></>}
    {index === 1 && <><div class="nexa-result-heading"><span>Available balance</span><b>Updated now</b></div><div class="nexa-balance-result"><small>Primary account · 4291</small><strong>₹4,25,890.50</strong><span>Upcoming payments are covered</span></div></>}
    {index === 2 && <><div class="nexa-result-heading"><span>अलीकडील व्यवहार</span><b>आज</b></div><div class="nexa-result-row"><span class="nexa-result-mark">C</span><div><strong>Café Mondegar</strong><small>आज · 11:42 AM</small></div><b>−₹680</b></div><div class="nexa-result-row"><span class="nexa-result-mark is-accent">M</span><div><strong>Metro Card</strong><small>काल</small></div><b>−₹500</b></div></>}
    {index === 3 && <><div class="nexa-result-heading"><span>Transfer ready</span><b>Review first</b></div><div class="nexa-transfer-result"><div><small>To</small><strong>Rahul Mehta</strong></div><span>→</span><div><small>Amount</small><strong>₹5,000</strong></div></div><button class="nexa-review-action" type="button">Review transfer <span>↗</span></button></>}
  </div>;
}

export function HeroDemo() {
  const [prompt, setPrompt] = useState("");
  const [phraseIndex, setPhraseIndex] = useState(0);
  const [deleting, setDeleting] = useState(false);
  const [resultVisible, setResultVisible] = useState(false);
  const [displayedIndex, setDisplayedIndex] = useState<number | null>(null);
  const [swapping, setSwapping] = useState(false);

  useEffect(() => {
    const phrase = prompts[phraseIndex];
    const complete = prompt === phrase;
    const empty = prompt.length === 0;
    let delay = deleting ? 34 : 62;
    if (complete && displayedIndex !== phraseIndex && !swapping) delay = 280;
    if (swapping) delay = 220;
    if (complete && displayedIndex === phraseIndex && resultVisible && !deleting) delay = 2400;
    if (deleting && empty) delay = 340;
    const timer = window.setTimeout(() => {
      if (complete && displayedIndex !== phraseIndex && !swapping) { setResultVisible(false); setSwapping(true); }
      else if (swapping) { setDisplayedIndex(phraseIndex); setResultVisible(true); setSwapping(false); }
      else if (complete && displayedIndex === phraseIndex && resultVisible && !deleting) setDeleting(true);
      else if (deleting && empty) { setDeleting(false); setPhraseIndex((phraseIndex + 1) % prompts.length); }
      else setPrompt(deleting ? prompt.slice(0, -1) : phrase.slice(0, prompt.length + 1));
    }, delay);
    return () => window.clearTimeout(timer);
  }, [prompt, phraseIndex, deleting, resultVisible, displayedIndex, swapping]);

  return <section class="nexa-hero" id="platform">
    <div class="nexa-hero__intro">
      <p class="nexa-hero-label">Conversational banking</p>
      <h1>Less banking.<br />More doing.</h1>
      <p>Ask in your own words. Check a balance, review a payment, or move money without working through a maze of menus.</p>
      <div class="nexa-hero-capabilities" aria-label="Nexa capabilities">
        <span><b>01</b> Ask naturally</span>
        <span><b>02</b> Review clearly</span>
        <span><b>03</b> Approve securely</span>
      </div>
    </div>
    <div class="nexa-command-panel">
      <div class="nexa-demo-bar"><span>See Nexa at work</span><span>Live example</span></div>
      <div class="nexa-command-panel__prompt"><span class="nexa-microphone" aria-hidden="true">↳</span><span class="nexa-typewriter">{prompt}</span></div>
      {displayedIndex === null ? <div class="nexa-query-result" /> : <QueryResult index={displayedIndex} visible={resultVisible} />}
      <div class="nexa-demo-progress" aria-hidden="true">{prompts.map((_, index) => <i class={index === phraseIndex ? "is-active" : ""} />)}</div>
    </div>
  </section>;
}
