import { h } from "preact";
import { useState } from "preact/hooks";

type AccountOpeningCardProps = {
  opening: boolean;
  error: string;
  onCancel: () => void;
  onOpen: (request: { displayName: string; accountType: "SAVINGS" | "CURRENT" }) => void;
};

export function AccountOpeningCard({ opening, error, onCancel, onOpen }: AccountOpeningCardProps) {
  const [displayName, setDisplayName] = useState("Primary account");
  const [accountType, setAccountType] = useState<"SAVINGS" | "CURRENT">("SAVINGS");

  return <section class="nexa-account-opening" aria-labelledby="nexa-open-account-title">
    <header><div><span>Open your first account</span><h2 id="nexa-open-account-title">Start banking with Nexa.</h2></div><button type="button" onClick={onCancel} aria-label="Close account opening">×</button></header>
    <p>This simulated account includes a ₹1,00,000 demo opening credit, recorded in your transaction history.</p>
    <div class="nexa-opening-fields">
      <label>Account name<input value={displayName} maxLength={120} onInput={(event) => setDisplayName((event.currentTarget as HTMLInputElement).value)} /></label>
      <label>Account type<select value={accountType} onChange={(event) => setAccountType((event.currentTarget as HTMLSelectElement).value as "SAVINGS" | "CURRENT")}><option value="SAVINGS">Savings account</option><option value="CURRENT">Current account</option></select></label>
      <label>Currency<input value="INR" disabled /></label>
    </div>
    <div class="nexa-opening-review"><span>Demo opening credit</span><strong>₹1,00,000</strong><small>One-time credit · no real money</small></div>
    {error && <p class="nexa-opening-error" role="alert">{error}</p>}
    <footer><button type="button" onClick={onCancel}>Not now</button><button class="is-primary" type="button" disabled={opening || !displayName.trim()} onClick={() => onOpen({ displayName: displayName.trim(), accountType })}>{opening ? "Opening account…" : "Confirm and open account"}</button></footer>
  </section>;
}
