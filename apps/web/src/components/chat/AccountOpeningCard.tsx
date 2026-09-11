import { h } from "preact";
import { useState } from "preact/hooks";

type AccountOpeningCardProps = {
  opening: boolean;
  error: string;
  onCancel: () => void;
  onOpen: (request: { displayName: string; accountType: "SAVINGS" | "CURRENT"; dateOfBirth: string; address: string }) => void;
};

export function AccountOpeningCard({ opening, error, onCancel, onOpen }: AccountOpeningCardProps) {
  const [displayName, setDisplayName] = useState("Primary account");
  const [accountType, setAccountType] = useState<"SAVINGS" | "CURRENT">("SAVINGS");

  const [dateOfBirth, setDateOfBirth] = useState("");
  const [address, setAddress] = useState("");

  return <section class="nexa-account-opening" aria-labelledby="nexa-open-account-title">
    <header><div><span>Open your first account</span><h2 id="nexa-open-account-title">Start banking with Nexa.</h2></div><button type="button" onClick={onCancel} aria-label="Close account opening">×</button></header>
    <p>Your account starts with a zero balance. Add your customer details to open it.</p>
    <div class="nexa-opening-fields">
      <label>Account name<input value={displayName} maxLength={100} onInput={(event) => setDisplayName((event.currentTarget as HTMLInputElement).value)} /></label>
      <label>Account type<select value={accountType} onChange={(event) => setAccountType((event.currentTarget as HTMLSelectElement).value as "SAVINGS" | "CURRENT")}><option value="SAVINGS">Savings account</option><option value="CURRENT">Current account</option></select></label>
      <label>Date of birth<input type="date" value={dateOfBirth} max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)} onInput={(e) => setDateOfBirth(e.currentTarget.value)} /></label>
      <label>Address<input value={address} maxLength={255} onInput={(e) => setAddress(e.currentTarget.value)} /></label>
      <label>Currency<input value="INR" disabled /></label>
    </div>
    <div class="nexa-opening-review"><span>Opening balance</span><strong>₹0</strong></div>
    {error && <p class="nexa-opening-error" role="alert">{error}</p>}
    <footer><button type="button" onClick={onCancel}>Not now</button><button class="is-primary" type="button" disabled={opening || !displayName.trim() || !dateOfBirth || !address.trim()} onClick={() => onOpen({ displayName: displayName.trim(), accountType, dateOfBirth, address: address.trim() })}>{opening ? "Opening account…" : "Confirm and open account"}</button></footer>
  </section>;
}
