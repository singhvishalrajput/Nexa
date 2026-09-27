import { useRef, useState } from "preact/hooks";
import { ApiRequestError } from "../../services/auth";
import { CardApplication, cardApplications, localCardNotice } from "../../services/card-applications";
import { formatDate, formatMoney, humanize } from "../../services/banking-content";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { Detail, Modal, PageHeading, Panel, State, Status, useLoad } from "./ui";
import { validAmount } from "./utils";

export function AdminCardQueue({ token }: { token: string }) {
  const [status, setStatus] = useState("PENDING_APPROVAL"), [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<CardApplication>();
  const queue = useLoad(() => cardApplications.queue(token, status), [token, status]);
  const requests = queue.data || [];
  return <><PageHeading title="Card requests" description="Review credit card applications and the limits approved by the bank." action={<button class="bank-button secondary" disabled={queue.loading} onClick={queue.reload}>Refresh requests</button>}/>
    {notice && <p class="bank-notice" role="status">{notice}</p>}
    <Panel title="Card application queue"><div class="admin-filters"><label>Application status<select value={status} onChange={event => setStatus(event.currentTarget.value)}><option value="ALL">All statuses</option>{["PENDING_APPROVAL", "ACTIVE", "BLOCKED", "REJECTED", "CLOSED"].map(value => <option key={value} value={value}>{humanize(value)}</option>)}</select></label></div>
      <State loading={queue.loading} error={queue.error} retry={queue.reload} empty={!queue.loading && !queue.error && !requests.length ? "No card applications matching this status" : undefined}>
        {requests.map(request => <article class="admin-mandate admin-loan-request" key={request.id}><header><div><strong>{request.displayName}</strong><p>{request.applicantName} · {humanize(request.cardType)} card</p></div><Status value={request.status}/></header><div class="admin-request-meta"><span>Requested {formatDate(request.createdAt, true)}</span>{Number(request.creditLimit) > 0 && <span>Approved limit {formatMoney(request.creditLimit, "INR")}</span>}</div><div class="admin-actions"><button class="bank-button" type="button" onClick={() => setSelected(request)}>{request.status === "PENDING_APPROVAL" ? "Review request" : "View application"}</button><a href={`#/admin/accounts/${request.accountId}/overview`}>Open linked account →</a></div></article>)}
      </State>
    </Panel>
    {selected && <AdminCardDecision key={selected.id} token={token} request={selected} close={() => setSelected(undefined)} done={message => { setSelected(undefined); setNotice(message); queue.reload(); }}/>}</>;
}

export function AdminCardDecision({ token, request, close, done }: {
  token: string; request: CardApplication; close: () => void; done: (message: string) => void;
}) {
  const [decision, setDecision] = useState<"approve" | "reject">("approve");
  const [limit, setLimit] = useState(""), [reason, setReason] = useState("");
  const [review, setReview] = useState(false), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [error, setError] = useState("");
  const lock = useRef(false);
  const pending = request.cardType === "CREDIT" && request.status === "PENDING_APPROVAL";
  const valid = !!reason.trim() && reason.trim().length <= 500 && (decision === "reject" || validAmount(limit));
  useNavigationGuard(pending && !!(limit || reason), busy || uncertain);
  function finish(application: CardApplication) {
    if (application.id !== request.id || !["ACTIVE", "BLOCKED", "REJECTED", "CLOSED"].includes(application.status)) return false;
    done(application.status === "ACTIVE" ? `Credit card approved with a limit of ${formatMoney(application.creditLimit, "INR")}.` : application.status === "REJECTED" ? "Card request rejected. The decision has been recorded." : `The card request is now ${humanize(application.status).toLowerCase()}.`);
    return true;
  }
  async function submit(checkOnly = false) {
    if (lock.current || !pending || (!checkOnly && (!review || !valid))) return;
    lock.current = true; setBusy(true); setError("");
    try {
      const application = await (checkOnly ? cardApplications.application(token, request.id) : cardApplications.decide(token, request.id, decision, {reason: reason.trim(), ...(decision === "approve" ? {creditLimit: limit} : {})}));
      if (!finish(application)) { setUncertain(true); setError("The request is still awaiting review. Retry the same decision to confirm its result."); }
    } catch (cause) {
      setUncertain(!(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500);
      setError(cause instanceof Error ? cause.message : "The decision could not be confirmed. Check the application status.");
    } finally { lock.current = false; setBusy(false); }
  }
  return <Modal title={pending ? "Review credit card application" : "Card application"} className="admin-dialog" locked={busy || uncertain} onClose={close}><div class="bank-form admin-review-form">
    <section class="admin-dialog-section"><h3>Application details</h3><dl class="admin-dialog-summary"><Detail label="Applicant">{request.applicantName}</Detail><Detail label="Card">{request.displayName}</Detail><Detail label="Card type">{humanize(request.cardType)}</Detail><Detail label="Status"><Status value={request.status}/></Detail><Detail label="Requested">{formatDate(request.createdAt, true)}</Detail><Detail label="Linked account"><a href={`#/admin/accounts/${request.accountId}/overview`}>Open account →</a></Detail>{Number(request.creditLimit) > 0 && <Detail label="Approved limit">{formatMoney(request.creditLimit, "INR")}</Detail>}{request.reviewedAt && <Detail label="Reviewed">{formatDate(request.reviewedAt, true)}</Detail>}{request.reviewReason && <Detail label="Decision reason">{request.reviewReason}</Detail>}</dl></section>
    <p class="bank-form-note">{localCardNotice}</p>
    {pending && <><fieldset class="admin-dialog-fields" disabled={busy || uncertain || review}><legend>Record your decision</legend><label>Decision<select value={decision} onChange={event => setDecision(event.currentTarget.value as "approve" | "reject")}><option value="approve">Approve</option><option value="reject">Reject</option></select></label>{decision === "approve" && <label>Approved credit limit (INR)<input required inputMode="decimal" value={limit} maxLength={16} onInput={event => setLimit(event.currentTarget.value)}/><small>Enter a positive amount with at most two decimal places.</small></label>}<label class="admin-field-wide">Reason for decision<textarea required maxLength={500} value={reason} onInput={event => setReason(event.currentTarget.value)}/></label></fieldset>
      {!review ? <button class="bank-button" disabled={!valid || busy} onClick={() => { if (valid) setReview(true); }}>Review decision</button> : <div class="bank-confirm-summary"><p>{decision === "approve" ? `Approve a ${formatMoney(limit, "INR")} credit limit for ${request.applicantName}?` : `Reject ${request.applicantName}’s card application?`}</p><p>{reason}</p><div class="bank-form-actions"><button class="bank-button" disabled={busy || !valid} onClick={() => void submit()}>{busy ? "Saving…" : uncertain ? "Retry same decision" : decision === "approve" ? "Confirm approval" : "Confirm rejection"}</button>{uncertain ? <button class="bank-button secondary" disabled={busy} onClick={() => void submit(true)}>Check application status</button> : <button class="bank-button secondary" disabled={busy} onClick={() => setReview(false)}>Edit decision</button>}</div></div>}
      {error && <p role="alert" class="bank-error">{error}</p>}
    </>}
  </div></Modal>;
}
