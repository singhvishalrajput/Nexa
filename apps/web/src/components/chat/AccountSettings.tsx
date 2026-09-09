import { h } from "preact";
import { useEffect, useState } from "preact/hooks";
import { ApiRequestError, AuthSession, CustomerProfile, updateProfile } from "../../services/auth";
import { BankAccount } from "../../services/banking";

type AccountSettingsProps = {
  session: AuthSession;
  account?: BankAccount;
  onBack: () => void;
  onLogout: () => void | Promise<void>;
  onProfileUpdated: (profile: CustomerProfile) => void;
};

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });

export function AccountSettings({ session, account, onBack, onLogout, onProfileUpdated }: AccountSettingsProps) {
  const [fullName, setFullName] = useState(session.profile.fullName);
  const [phoneNumber, setPhoneNumber] = useState(session.profile.phoneNumber || "");
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setFullName(session.profile.fullName);
    setPhoneNumber(session.profile.phoneNumber || "");
  }, [session.profile.fullName, session.profile.phoneNumber]);

  const save = async (event: Event) => {
    event.preventDefault();
    setSaving(true);
    setSaved(false);
    setError("");
    try {
      const updated = await updateProfile(session.accessToken, fullName.trim(), phoneNumber);
      setFullName(updated.fullName);
      setPhoneNumber(updated.phoneNumber || "");
      setSaved(true);
      onProfileUpdated(updated);
    } catch (cause) {
      setError(cause instanceof ApiRequestError ? cause.message : "Could not update your profile.");
    } finally {
      setSaving(false);
    }
  };

  return <section class="nexa-account-view">
    <div class="nexa-account-heading"><p>Your Nexa profile</p><h1>Account settings</h1><span>Manage the personal details linked to your secure Nexa session.</span></div>
    <form class="nexa-account-form" onSubmit={save}>
      <div class="nexa-settings-group">
        <div><strong>Personal information</strong><span>Your name and phone number are stored with your customer profile.</span></div>
        <div class="nexa-settings-fields">
          <label>Full name<input required maxLength={160} value={fullName} onInput={(event) => { setFullName(event.currentTarget.value); setSaved(false); }} /></label>
          <label>Login email<input class="is-readonly" type="email" value={session.profile.email} readOnly aria-readonly="true" /></label>
          <label>Phone number<input maxLength={32} value={phoneNumber} placeholder="Optional" onInput={(event) => { setPhoneNumber(event.currentTarget.value); setSaved(false); }} /></label>
        </div>
      </div>
      <div class="nexa-settings-group">
        <div><strong>Nexa bank account</strong><span>Live account information from your Nexa banking record.</span></div>
        {account ? <div class="nexa-account-record">
          <header><div><span>{account.accountType} ACCOUNT</span><strong>{account.displayName}</strong></div><b class={`is-${account.status.toLowerCase()}`}>{account.status}</b></header>
          <dl><div><dt>Account number</dt><dd>{account.accountNumberMasked}</dd></div><div><dt>Available balance</dt><dd>{inr.format(account.availableBalance)}</dd></div><div><dt>Ledger balance</dt><dd>{inr.format(account.ledgerBalance)}</dd></div><div><dt>Currency</dt><dd>{account.currencyCode}</dd></div></dl>
        </div> : <div class="nexa-account-record is-empty"><strong>No Nexa account yet</strong><span>Open an account from the conversation to see its balance and details here.</span><button type="button" onClick={onBack}>Return to conversation</button></div>}
      </div>
      <div class="nexa-settings-group nexa-session-settings">
        <div><strong>Session</strong><span>Sign out securely when you have finished using Nexa.</span></div>
        <div class="nexa-session-action"><span>Signed in as <b>{session.profile.email}</b></span><button type="button" onClick={onLogout}>Sign out</button></div>
      </div>
      {error && <p class="nexa-settings-error" role="alert">{error}</p>}
      <div class="nexa-account-actions"><span>{saved ? "Profile updated in Nexa" : "Only your name and phone number can be changed here."}</span><button type="submit" disabled={saving || !fullName.trim()}>{saving ? "Saving…" : saved ? "Saved" : "Save changes"}</button></div>
    </form>
  </section>;
}
