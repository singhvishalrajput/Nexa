import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { formatMoney } from "../../services/banking-content";
import { Status } from "../../features/banking/ui";
import { useEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError, AuthSession, CustomerProfile, updateProfile } from "../../services/auth";
import { BankAccount } from "../../services/banking";

type AccountSettingsProps = {
  session: AuthSession;
  account?: BankAccount;
  onBack: () => void;
  onLogout: () => void | Promise<void>;
  onProfileUpdated: (profile: CustomerProfile) => void;
};



export function AccountSettings({ session, account, onBack, onLogout, onProfileUpdated }: AccountSettingsProps) {
  const [fullName, setFullName] = useState(session.profile.fullName);
  const [phoneNumber, setPhoneNumber] = useState(session.profile.phoneNumber || "");
  const [address, setAddress] = useState(session.profile.address || "");
  const submissionLock = useRef(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");

  useNavigationGuard(fullName !== session.profile.fullName || phoneNumber !== (session.profile.phoneNumber || "") || address !== (session.profile.address || ""), saving);

  useEffect(() => {
    setFullName(session.profile.fullName);
    setPhoneNumber(session.profile.phoneNumber || "");
    setAddress(session.profile.address || "");
  }, [session.profile.fullName, session.profile.phoneNumber, session.profile.address]);

  const save = async (event: Event) => {
    event.preventDefault();
    if (submissionLock.current) return;
    submissionLock.current = true;
    setSaving(true);
    setSaved(false);
    setError("");
    try {
      const updated = await updateProfile(session.accessToken, fullName.trim(), phoneNumber, address);
      setFullName(updated.fullName);
      setPhoneNumber(updated.phoneNumber || "");
      setAddress(updated.address || "");
      setSaved(true);
      onProfileUpdated(updated);
    } catch (cause) {
      setError(cause instanceof ApiRequestError ? cause.message : "Could not update your profile.");
    } finally {
      submissionLock.current = false;
      setSaving(false);
    }
  };

  return <section class="nexa-account-view">
    <div class="nexa-account-heading"><p>Your Nexa profile</p><h1>Account settings</h1><span>Manage the personal details linked to your secure Nexa session.</span></div>
    <form class="nexa-account-form" onSubmit={save}><fieldset disabled={saving}>
      <div class="nexa-settings-group">
        <div><strong>Personal information</strong><span>Your name, address and phone number are stored with your customer profile.</span></div>
        <div class="nexa-settings-fields">
          <label>Full name<input required maxLength={160} value={fullName} onInput={(event) => { setFullName(event.currentTarget.value); setSaved(false); }} /></label>
          <label>Login email<input class="is-readonly" type="email" value={session.profile.email} readOnly aria-readonly="true" /></label>
          <label>Address<input maxLength={255} value={address} required={!!account} onInput={(event) => { setAddress(event.currentTarget.value); setSaved(false); }} /></label>
          <label>Phone number<input type="tel" autocomplete="tel" maxLength={32} value={phoneNumber} placeholder="Optional" onInput={(event) => { setPhoneNumber(event.currentTarget.value); setSaved(false); }} /></label>
        </div>
      </div>
      <div class="nexa-settings-group">
        <div><strong>Nexa bank account</strong><span>Live account information from your Nexa banking record.</span></div>
        {account ? <div class="nexa-account-record">
          <header><div><span>{account.accountType} ACCOUNT</span><strong>{account.displayName}</strong></div><Status value={account.status} /></header>
          <dl><div><dt>Account number</dt><dd>{account.accountNumberMasked}</dd></div><div><dt>Available balance</dt><dd>{formatMoney(account.availableBalance, account.currencyCode)}</dd></div><div><dt>Ledger balance</dt><dd>{formatMoney(account.ledgerBalance, account.currencyCode)}</dd></div><div><dt>Currency</dt><dd>{account.currencyCode}</dd></div></dl>
        </div> : <div class="nexa-account-record is-empty"><strong>No Nexa account yet</strong><span>Open an account from Accounts to see its balance and details here.</span><button type="button" onClick={onBack}>View accounts</button></div>}
      </div>
      <div class="nexa-settings-group nexa-session-settings">
        <div><strong>Session</strong><span>Sign out securely when you have finished using Nexa.</span></div>
        <div class="nexa-session-action"><span>Signed in as <b>{session.profile.email}</b></span><button type="button" onClick={onLogout}>Sign out</button></div>
      </div>
      {error && <p class="nexa-settings-error" role="alert">{error}</p>}
      <div class="nexa-account-actions"><span role="status">{saved ? "Profile updated in Nexa" : "Your name, address and phone number can be changed here."}</span><button type="submit" disabled={saving || !fullName.trim()}>{saving ? "Saving…" : saved ? "Saved" : "Save changes"}</button></div>
    </fieldset></form>
  </section>;
}
