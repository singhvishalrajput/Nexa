import { useEffect, useState } from "preact/hooks";
import { CustomerAccountApplications } from "../../features/banking/AccountApplications";
import { ProductCreate } from "../../features/banking/ProductOperations";
import { State, Status, useLoad } from "../../features/banking/ui";
import { CustomerProfile } from "../../services/auth";
import { getAccounts } from "../../services/banking";
import { chatMandateApplicationKey, findMandateApplication, MandateApplication } from "../../services/mandate-applications";

type ApplicationProps = { accessToken: string; active: boolean; busy?: boolean; onSubmissionLocked?: (locked: boolean) => void };

export function AccountApplicationCard({ accessToken, profile, active, busy = false, onAccountsChanged, onSubmissionLocked }: ApplicationProps & {
  profile?: CustomerProfile; onAccountsChanged?: () => void;
}) {
  const accounts = useLoad(() => active && profile ? getAccounts(accessToken) : Promise.resolve(null), [accessToken, profile?.id, active]);
  if (!active) return <section class="conversation-proposal"><h3>Earlier account opening request</h3><p>Use the latest application form in this conversation, or continue your saved application in Accounts.</p><a href="#/accounts">View accounts and applications</a></section>;
  if (!profile) return <section class="conversation-proposal"><h3>Account opening</h3><p>Your signed-in profile is unavailable. Open Accounts to continue securely.</p><a href="#/accounts">View accounts and applications</a></section>;
  return <section class="messenger-banking-response conversation-loan-application" aria-label="Account opening application">
    <State loading={accounts.loading} error={accounts.error} retry={accounts.reload}>
      {accounts.data && <CustomerAccountApplications key={profile.id} token={accessToken} profile={profile} accounts={accounts.data}
        embedded initiallyOpen disabled={busy} onSubmissionLocked={onSubmissionLocked}
        onAccountsChanged={() => { accounts.reload(); onAccountsChanged?.(); }}/>} 
    </State>
  </section>;
}

export function MandateApplicationCard({ clientId, accessToken, active, busy = false, onSubmissionLocked }: ApplicationProps & { clientId: string }) {
  const applicationKey = chatMandateApplicationKey(clientId);
  const identity = accessToken + ":" + applicationKey;
  const lookup = useLoad(() => applicationKey ? findMandateApplication(accessToken, applicationKey) : Promise.resolve(null), [accessToken, applicationKey]);
  const [checked, setChecked] = useState("");
  const [recovery, setRecovery] = useState("");
  const [submitted, setSubmitted] = useState<{ identity: string; mandate: MandateApplication } | null>(null);
  useEffect(() => { if (lookup.data === null && !lookup.loading && !lookup.error) setChecked(identity); }, [identity, lookup.data, lookup.loading, lookup.error]);
  const mandate = submitted?.identity === identity ? submitted.mandate : lookup.data;
  useEffect(() => { if (mandate) onSubmissionLocked?.(false); }, [mandate]);
  if (!applicationKey) return <section class="conversation-proposal"><h3>Direct debit mandate</h3><p>This older message cannot open a mandate form. Start a new mandate request in chat.</p><a href="#/mandates">View your mandates</a></section>;
  if (mandate) return <section class="conversation-proposal" aria-label="Mandate application">
    <h3>Mandate saved</h3><Status value={mandate.STATUS}/>
    <p>{mandate.STATUS === "PENDING" ? "Your authorization is pending. Review its details and activate it before making mandate payments." : "The bank’s current mandate status is shown above. Open its details to review the authorization."} Creating a mandate does not send money.</p>
    <a href={"#/mandates/" + encodeURIComponent(mandate.ID)}>View mandate</a>
  </section>;
  const showForm = active && checked === identity;
  return <section class="messenger-banking-response conversation-loan-application" aria-label="Mandate application">
    {lookup.loading && <p role="status">Checking whether this mandate was already saved…</p>}
    {lookup.error && <div role="alert"><p>{lookup.error}</p><button class="bank-button secondary" type="button" onClick={lookup.reload}>Check mandate status</button></div>}
    {!active && !lookup.loading && !lookup.error && <div class="conversation-proposal"><h3>Earlier mandate request</h3><p>Use the latest application form in this conversation, or check your saved mandates.</p><a href="#/mandates">View your mandates</a></div>}
    {showForm && <ProductCreate key={identity} token={accessToken} kind="mandates" initiallyOpen applicationKey={applicationKey}
      disabled={busy || lookup.loading || !!lookup.error} reload={() => {}}
      onMandateCreated={result => setSubmitted({ identity, mandate: result })}
      onSubmissionUncertain={unknown => { setRecovery(unknown ? identity : ""); lookup.reload(); }} onSubmissionLocked={onSubmissionLocked}/>}
    {showForm && recovery === identity && !lookup.error && <button type="button" class="bank-button secondary" disabled={lookup.loading || busy} onClick={lookup.reload}>Check mandate status</button>}
  </section>;
}
