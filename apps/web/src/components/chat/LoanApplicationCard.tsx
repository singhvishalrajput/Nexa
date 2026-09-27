import { useEffect, useState } from "preact/hooks";
import { ProductCreate } from "../../features/banking/ProductOperations";
import { Status, useLoad } from "../../features/banking/ui";
import { chatLoanApplicationKey, findLoanApplication, LoanApplication } from "../../services/loan-applications";

export function LoanApplicationCard({ clientId, accessToken, active, busy = false, onSubmissionLocked }: {
  clientId: string; accessToken: string; active: boolean; busy?: boolean; onSubmissionLocked?: (locked: boolean) => void;
}) {
  const applicationKey = chatLoanApplicationKey(clientId);
  const identity = accessToken + ":" + applicationKey;
  const lookup = useLoad(() => applicationKey ? findLoanApplication(accessToken, applicationKey) : Promise.resolve(null), [accessToken, applicationKey]);
  const [checked, setChecked] = useState("");
  const [recovery, setRecovery] = useState("");
  const [submitted, setSubmitted] = useState<{ identity: string; loan: LoanApplication } | null>(null);
  useEffect(() => { if (lookup.data === null && !lookup.loading && !lookup.error) setChecked(identity); }, [identity, lookup.data, lookup.loading, lookup.error]);
  const loan = submitted?.identity === identity ? submitted.loan : lookup.data;
  useEffect(() => { if (loan) onSubmissionLocked?.(false); }, [loan]);
  if (!applicationKey) return <section class="conversation-proposal"><h3>Loan application</h3><p>This older message cannot open an application form. Start a new loan application request in chat.</p><a href="#/loans">View your loans</a></section>;
  if (loan) return <section class="conversation-proposal" aria-label="Loan application">
    <h3>Loan application received</h3><Status value={loan.status}/>
    <p>Your application is saved. Track the bank’s review and any terms that need your acceptance.</p>
    <a href={"#/loans/" + encodeURIComponent(loan.id)}>Track application</a>
  </section>;
  const showForm = active && checked === identity;
  return <section class="messenger-banking-response conversation-loan-application" aria-label="Loan application">
    {lookup.loading && <p role="status">Checking whether this application was already submitted…</p>}
    {lookup.error && <div role="alert"><p>{lookup.error}</p><button class="bank-button secondary" type="button" onClick={lookup.reload}>Check application status</button></div>}
    {!active && !lookup.loading && !lookup.error && <div class="conversation-proposal"><h3>Earlier loan application request</h3><p>Use the latest loan application form in this conversation, or check your existing applications.</p><a href="#/loans">View your loans</a></div>}
    {showForm && <ProductCreate key={identity} token={accessToken} kind="loans" initiallyOpen applicationKey={applicationKey}
      disabled={busy || lookup.loading || !!lookup.error} reload={() => {}}
      onCreated={(_id, result) => setSubmitted({ identity, loan: result })} onSubmissionUncertain={unknown => { setRecovery(unknown ? identity : ""); lookup.reload(); }} onSubmissionLocked={onSubmissionLocked}/>}
    {showForm && recovery === identity && !lookup.error && <button type="button" class="bank-button secondary" disabled={lookup.loading || busy} onClick={lookup.reload}>Check application status</button>}
  </section>;
}
