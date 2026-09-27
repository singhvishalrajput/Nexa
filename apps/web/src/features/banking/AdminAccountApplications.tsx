import { useEffect, useState } from "preact/hooks";
import {
  AccountApplication, AdminApplicationReadiness, ApplicationStatus, APPLICATION_STATUSES, applicationApi,
  applicationStatusLabel, normalizeOpeningAmount,
  validateReviewReason
} from "../../services/account-applications";
import { formatDate, formatMoney } from "../../services/banking-content";
import { useApplicationAction } from "../../hooks/useApplicationAction";
import { confirmNavigation, useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApplicationRecord, ApplicationStatus as ApplicationStatusBadge } from "./AccountApplicationShared";
import { PageHeading, Panel, State, useLoad } from "./ui";

const pageSize = 20;
const applicationHref = (id: string) => `#/admin/applications/${encodeURIComponent(id)}`;

export function AdminAccountApplications({ token, applicationId }: { token: string; applicationId?: string }) {
  return applicationId
    ? <AdminApplicationDetail key={`${token}:${applicationId}`} token={token} id={applicationId}/>
    : <AdminApplicationQueue token={token}/>;
}

function ApplicationAvailability({readiness, loading, error}: {
  readiness?: AdminApplicationReadiness; loading: boolean; error: string;
}) {
  if (loading) return null;
  if (error || !readiness?.applicationsAvailable) return <p class="bank-error" role="status">Account application actions are temporarily unavailable. Refresh to try again.</p>;
  if (!readiness.identityDetailsAvailable || !readiness.reviewsAvailable || !readiness.cashReceiptAvailable || !readiness.accountOpeningAvailable || !readiness.cashRefundAvailable)
    return <p class="bank-notice" role="status">Some account application actions are temporarily unavailable. Refresh before trying again.</p>;
  return null;
}

function AdminApplicationQueue({ token }: { token: string }) {
  const readiness = useLoad(() => applicationApi.adminReadiness(token), [token]);
  const [status, setStatus] = useState<ApplicationStatus | "">("PENDING_REVIEW");
  const [page, setPage] = useState(0);
  const result = useLoad(() => applicationApi.listAdmin(token, { status: status || undefined, page, size: pageSize }), [token, status, page]);
  const items = result.data?.items || [], total = result.data?.total || 0;
  function refresh() { result.reload(); return readiness.reload(); }
  function openRow(event: MouseEvent & {currentTarget: HTMLTableRowElement}) {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    if ((event.target as Element).closest("a, button, input, select, textarea")) return;
    // Use the same native link as keyboard users; the shared router preserves unsaved-work guards.
    event.currentTarget.querySelector<HTMLAnchorElement>("a")?.click();
  }
  return <div class="application-admin">
    <PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title="Account applications" description="Check original identity documents in person, acknowledge actual cash and open eligible savings accounts." action={<button class="bank-button secondary" disabled={result.loading || readiness.loading} onClick={refresh}>Refresh applications</button>}/>
    <ApplicationAvailability readiness={readiness.loading || readiness.error ? undefined : readiness.data} loading={readiness.loading} error={readiness.error}/>
    <Panel title="Application queue">
      <div class="application-filters"><label>Application status<select value={status} onChange={event => { setStatus(event.currentTarget.value as ApplicationStatus | ""); setPage(0); }}>
        <option value="">All statuses</option>{APPLICATION_STATUSES.map(value => <option key={value} value={value}>{applicationStatusLabel(value)}</option>)}
      </select></label></div>
      <State loading={result.loading} error={result.error} retry={result.reload}>
        <p class="application-result-count" role="status">{total} {total === 1 ? "application" : "applications"} found</p>
        {!items.length ? <p class="bank-notice">No applications on this page. Choose another status or return to the previous page.</p> :
          <div class="admin-table-scroll application-table-scroll" tabIndex={0} role="region" aria-label="Scrollable application results"><table class="admin-table"><caption class="sr-only">Account applications matching the selected status</caption><thead><tr><th scope="col">Applicant</th><th scope="col">Opening amount</th><th scope="col">Status</th><th scope="col">Requested</th><th scope="col">Review</th></tr></thead><tbody>
            {items.map(item => <tr key={item.id} class="application-queue-row" onClick={openRow}><td><strong>{item.fullName}</strong><small>{item.email}</small></td><td>{formatMoney(item.openingAmount, item.currencyCode)}</td><td><ApplicationStatusBadge status={item.status}/></td><td>{formatDate(item.createdAt)}</td><td><a class="admin-account-link" href={applicationHref(item.id)}>Inspect application<span class="sr-only"> for {item.fullName}</span></a></td></tr>)}
          </tbody></table></div>}
        <nav class="admin-pagination application-pagination" aria-label="Application pages"><span>Page {page + 1}{total ? ` of ${Math.ceil(total / pageSize)}` : ""}</span><button class="bank-button secondary" disabled={page === 0 || result.loading} onClick={() => setPage(value => Math.max(0, value - 1))}>Previous page</button><button class="bank-button secondary" disabled={result.loading || (page + 1) * pageSize >= total} onClick={() => setPage(value => value + 1)}>Next page</button></nav>
      </State>
    </Panel>
  </div>;
}

function AdminApplicationDetail({ token, id }: { token: string; id: string }) {
  const readiness = useLoad(() => applicationApi.adminReadiness(token), [token]);
  const loaded = useLoad(() => applicationApi.getAdmin(token, id), [token, id]);
  const [updated, setUpdated] = useState<AccountApplication>();
  const [locked, setLocked] = useState(false);
  const [notice, setNotice] = useState("");
  const application = updated || loaded.data;
  function refresh() {
    const checking = readiness.reload();
    if (locked || !confirmNavigation()) return checking;
    setUpdated(undefined); setNotice(""); loaded.reload();
    return checking;
  }
  function saved(result: AccountApplication) {
    setUpdated(result);
    setNotice(`Server confirmed: ${applicationStatusLabel(result.status)}. The application record below is updated.`);
  }
  return <div class="application-admin">
    <nav class="admin-breadcrumb" aria-label="Breadcrumb"><a href="#/admin/applications" aria-disabled={locked || undefined} onClick={event => { if (locked) event.preventDefault(); }}>← Application queue</a></nav>
    <PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title="Review account application" description="A review decision does not create an account or credit money." action={<button class="bank-button secondary" disabled={loaded.loading || readiness.loading} onClick={refresh}>Refresh application</button>}/>
    <ApplicationAvailability readiness={readiness.loading || readiness.error ? undefined : readiness.data} loading={readiness.loading} error={readiness.error}/>
    {notice && <p class="bank-notice" role="status">{notice}</p>}
    <State loading={loaded.loading && !updated} error={updated ? "" : loaded.error} retry={refresh}>
      {application && <>
        <ApplicationRecord application={application} token={token} admin/>
        <AdminApplicationActions key={`${application.id}:${application.version}`} token={token} application={application} onResult={saved} onLockedChange={setLocked}
          readiness={readiness.loading || readiness.error ? undefined : readiness.data}/>
      </>}
    </State>
  </div>;
}

/** Kept separate for isolated interaction tests; never fabricates an updated application. */
export function AdminApplicationActions({ token, application, onResult, onLockedChange, readiness }: {
  token: string; application: AccountApplication; onResult: (result: AccountApplication) => void;
  onLockedChange?: (locked: boolean) => void;
  readiness?: AdminApplicationReadiness;
}) {
  function canPerform(label: string) {
    if (!readiness?.applicationsAvailable) return false;
    switch (label) {
      case "Record application rejection": case "Request application changes":
      case "Request full cash refund": return true;
      case "Record application decision": return readiness.reviewsAvailable && readiness.identityDetailsAvailable;
      case "Record actual cash receipt": return readiness.cashReceiptAvailable;
      case "Create funded savings account": return readiness.accountOpeningAvailable;
      case "Confirm actual full cash return": return readiness.cashRefundAvailable;
      default: return false;
    }
  }
  const action = useApplicationAction(onResult, canPerform);
  const [validation, setValidation] = useState("");
  const [inPersonChecked, setInPersonChecked] = useState(false);
  const [decision, setDecision] = useState<"APPROVED" | "REJECTED" | "CHANGES_REQUESTED">("APPROVED");
  const [reviewReason, setReviewReason] = useState("");
  const [cashConfirmed, setCashConfirmed] = useState(false);
  const [refundReason, setRefundReason] = useState("");
  const [refundConfirmed, setRefundConfirmed] = useState(false);
  const enabled = readiness?.applicationsAvailable === true;
  const locked = !enabled || action.busy || action.uncertain;
  const cashLocked = locked || !readiness?.cashReceiptAvailable;
  const openingLocked = locked || !readiness?.accountOpeningAvailable;
  const refundLocked = locked || !readiness?.cashRefundAvailable;
  const requestedAmount = normalizeOpeningAmount(application.openingAmount);
  const canApprove = inPersonChecked;
  const identityReady = enabled && readiness?.identityDetailsAvailable === true && readiness?.reviewsAvailable === true;
  const heldReceipts = application.receipts.filter(receipt => receipt.status === "RECEIVED" && normalizeOpeningAmount(receipt.amount) === requestedAmount && requestedAmount !== null);
  const pendingRefunds = application.receipts.filter(receipt => receipt.status === "REFUND_PENDING" && normalizeOpeningAmount(receipt.amount) === requestedAmount && requestedAmount !== null);
  const dirty = !!(reviewReason || cashConfirmed || refundReason || refundConfirmed
    || decision !== "APPROVED" || inPersonChecked);
  useNavigationGuard(dirty, action.busy || action.uncertain);
  useEffect(() => {
    onLockedChange?.(action.busy || action.uncertain);
    return () => onLockedChange?.(false);
  }, [action.busy, action.uncertain, onLockedChange]);

  function send(label: string, request: (requestKey: string) => Promise<AccountApplication>) {
    if (locked || !canPerform(label)) return;
    setValidation("");
    action.clearError();
    void action.run(label, request);
  }
  function saveDecision() {
    if (locked || application.status !== "PENDING_REVIEW") return;
    const label = decision === "REJECTED" ? "Record application rejection"
      : decision === "CHANGES_REQUESTED" ? "Request application changes" : "Record application decision";
    if (!canPerform(label)) return;
    const reason = reviewReason.trim(), error = validateReviewReason(reason);
    if (error || (decision === "APPROVED" && !canApprove)) { setValidation(error || "Confirm that you checked the original identity document in person before approving."); return; }
    send(label, requestKey => applicationApi.review(token, application.id,
      { requestKey, expectedVersion: application.version, decision, reason, inPersonChecked: decision === "APPROVED" && inPersonChecked }));
  }
  function recordCash() {
    if (cashLocked || application.status !== "APPROVED_AWAITING_CASH") return;
    if (!cashConfirmed || !requestedAmount) {
      setValidation("Confirm physical receipt of the customer’s exact opening amount. The bank will generate the receipt number."); return;
    }
    send("Record actual cash receipt", requestKey => applicationApi.receiveCash(token, application.id,
      { requestKey, expectedVersion: application.version, cashReceivedConfirmed: true }));
  }
  function openAccount() {
    if (openingLocked || application.status !== "CASH_RECEIVED" || heldReceipts.length !== 1) return;
    send("Create funded savings account", requestKey => applicationApi.open(token, application.id,
      { requestKey, expectedVersion: application.version }));
  }
  function requestRefund() {
    if (locked || application.status !== "CASH_RECEIVED") return;
    const reason = refundReason.trim(), error = validateReviewReason(reason);
    if (error || heldReceipts.length !== 1) { setValidation(error || "A matching, unallocated cash receipt is required."); return; }
    send("Request full cash refund", requestKey => applicationApi.requestRefund(token, application.id,
      { requestKey, expectedVersion: application.version, reason }));
  }
  function confirmRefund() {
    if (refundLocked || application.status !== "REFUND_PENDING" || !refundConfirmed || pendingRefunds.length !== 1) return;
    send("Confirm actual full cash return", requestKey => applicationApi.refund(token, application.id,
      { requestKey, expectedVersion: application.version, cashReturnedConfirmed: true }));
  }

  return <div class="application-review-actions" aria-busy={action.busy}>
    {action.busy && <p class="bank-notice" role="status">{action.label}… Keep this page open until the result is confirmed.</p>}
    {(validation || action.error) && <p class="bank-error application-action-error" role="alert">{validation || action.error}</p>}
    {action.uncertain && <div class="bank-notice application-uncertain" role="status"><p>The outcome is unknown. Do not collect or return cash again, change these details or submit a new action. Recover the result of the original request. Use Refresh application if needed; it will preserve the original request.</p><button class="bank-button" disabled={!canPerform(action.label) || action.busy} onClick={() => void action.retry()}>Retry the same request</button></div>}

    {application.status === "PENDING_REVIEW" && <>
      <Panel title="Application decision"><p>Approval permits cash collection; it does not create an account or credit funds.</p>
        <form class="bank-form" onSubmit={event => { event.preventDefault(); saveDecision(); }}><fieldset disabled={locked}>
          <label>Application decision<select value={decision} onChange={event => { setDecision(event.currentTarget.value as typeof decision); setInPersonChecked(false); }}><option value="APPROVED" disabled={!identityReady}>Approve after in-person check</option><option value="CHANGES_REQUESTED">Request corrections</option><option value="REJECTED">Reject application</option></select></label>
          {decision === "APPROVED" && <label class="application-check"><input type="checkbox" checked={inPersonChecked} disabled={!identityReady} onChange={event => setInPersonChecked(event.currentTarget.checked)}/>I checked the original identity document in person and matched it to this applicant.</label>}
          <label>Application review reason<textarea required maxLength={500} value={reviewReason} onInput={event => setReviewReason(event.currentTarget.value)} aria-describedby="application-review-reason"/></label>
          <small id="application-review-reason">Up to 500 UTF-8 bytes. Never include identity numbers.{reviewReason ? ` ${validateReviewReason(reviewReason.trim())}` : ""}</small>
          {!identityReady && <p class="bank-notice">Approval is temporarily unavailable. You can still request corrections or reject the application.</p>}
          <button class="bank-button" type="submit" disabled={locked || !!validateReviewReason(reviewReason.trim()) || (decision === "APPROVED" && (!canApprove || !identityReady))}>Save application decision</button>
        </fieldset></form>
      </Panel>
    </>}

    {application.status === "APPROVED_AWAITING_CASH" && <Panel title="Acknowledge actual opening cash"><p>The reviewed opening amount is <strong>{formatMoney(application.openingAmount, "INR")}</strong>. Confirm only this exact amount. The bank generates a receipt number when you record the deposit. No account exists yet.</p>
      <form class="bank-form" onSubmit={event => { event.preventDefault(); recordCash(); }}><fieldset disabled={cashLocked}>
        <label>Opening amount (INR)<input type="text" value={application.openingAmount} readOnly aria-describedby="opening-cash-help"/></label>
        <small id="opening-cash-help">This is the customer's saved opening deposit. It cannot be changed during approval.</small>
        <label>Receipt number<input type="text" value="Generated when deposit is recorded" readOnly aria-describedby="opening-receipt-help"/></label>
        <small id="opening-receipt-help">The bank creates one unique receipt and shows it in this application's receipt history after confirmation.</small>
        <label class="application-check"><input type="checkbox" checked={cashConfirmed} onChange={event => setCashConfirmed(event.currentTarget.checked)}/>I physically received and counted this exact opening amount.</label>
        <button class="bank-button" type="submit" disabled={cashLocked || !cashConfirmed || !requestedAmount}>Record actual cash receipt</button>
      </fieldset></form>
    </Panel>}

    {application.status === "CASH_RECEIVED" && <>
      <Panel title="Create the funded account"><p>The server has recorded the cash receipt. Account creation will allocate exactly <strong>{formatMoney(application.openingAmount, "INR")}</strong> from the held receipt to one savings account, in a single transaction.</p>
        {heldReceipts.length !== 1 && <p class="bank-error" role="alert">A matching, unallocated receipt is missing or inconsistent. Reconcile the receipt before continuing.</p>}
        <button class="bank-button" disabled={openingLocked || heldReceipts.length !== 1} onClick={openAccount}>Create account from received cash</button>
      </Panel>
      <Panel title="Return unallocated cash instead"><p>If this application cannot proceed, request a full refund. This step does not say that cash has already been returned. Recording the refund request remains possible during a cash-service outage; physical return must wait until the bank can safely complete and record it.</p><form class="bank-form" onSubmit={event => { event.preventDefault(); requestRefund(); }}><fieldset disabled={locked}>
        <label>Reason for cash refund<textarea required maxLength={500} value={refundReason} onInput={event => setRefundReason(event.currentTarget.value)} aria-describedby="opening-refund-reason"/></label>
        <small id="opening-refund-reason">Up to 500 UTF-8 bytes, without identity numbers.{refundReason ? ` ${validateReviewReason(refundReason.trim())}` : ""}</small>
        <button class="bank-button secondary" type="submit" disabled={locked || heldReceipts.length !== 1 || !!validateReviewReason(refundReason.trim())}>Request full cash refund</button>
      </fieldset></form></Panel>
    </>}

    {application.status === "REFUND_PENDING" && <Panel title="Confirm actual cash return"><p>Return the full recorded amount, <strong>{formatMoney(application.openingAmount, "INR")}</strong>, to the applicant. A refund request alone is not proof of repayment.</p>
      {pendingRefunds.length !== 1 && <p class="bank-error" role="alert">No matching pending refund receipt is available. Reconcile this application first.</p>}
      <form class="bank-form" onSubmit={event => { event.preventDefault(); confirmRefund(); }}><fieldset disabled={refundLocked}>
        <label class="application-check"><input type="checkbox" checked={refundConfirmed} onChange={event => setRefundConfirmed(event.currentTarget.checked)}/>I physically returned the full recorded cash amount to this applicant.</label>
        <button class="bank-button" type="submit" disabled={refundLocked || pendingRefunds.length !== 1 || !refundConfirmed}>Confirm actual cash return</button>
      </fieldset></form>
    </Panel>}

    {!(["PENDING_REVIEW", "APPROVED_AWAITING_CASH", "CASH_RECEIVED", "REFUND_PENDING"] as string[]).includes(application.status) &&
      <Panel title="Application state"><p>{application.status === "OPENED" ? "The account has been opened. This receipt cannot be reused or refunded through an application request." : application.status === "DRAFT" || application.status === "CHANGES_REQUESTED" ? "The applicant must submit their saved identity details for in-person review before an administrator can decide this application." : "This application has ended. Its masked details, receipt and audit history remain available for authorised review."}</p></Panel>}
  </div>;
}
