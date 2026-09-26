import { useEffect, useRef, useState } from "preact/hooks";
import {
  AccountApplication, AdminApplicationReadiness, ApplicationStatus, APPLICATION_STATUSES, applicationApi,
  applicationStatusLabel, identityLabel, RevealedIdentity, normalizeOpeningAmount,
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

function ReadinessNotice({readiness, loading, error, refresh}: {
  readiness?: AdminApplicationReadiness; loading: boolean; error: string; refresh: () => void;
}) {
  return <aside class="application-notice" aria-label="Account-opening service readiness">
    <strong>Service readiness</strong>
    {loading ? <p role="status">Checking the bank's account-opening services…</p>
      : error ? <p role="alert">Readiness could not be confirmed. All changes remain unavailable. {error}</p>
      : readiness ? <ul class="application-readiness">
        <li>Private identity details: {readiness.identityDetailsAvailable ? "Available" : "Unavailable"}</li>
        <li>In-person identity review: {readiness.reviewsAvailable ? "Available" : "Unavailable"}</li>
        <li>Cash receipt: {readiness.cashReceiptAvailable ? "Available" : "Unavailable"}</li>
        <li>Funded opening: {readiness.accountOpeningAvailable ? "Available" : "Unavailable"}</li>
        <li>Cash return: {readiness.cashRefundAvailable ? "Available" : "Unavailable"}</li>
      </ul> : <p>No readiness result is available. Do not collect identity details or cash.</p>}
    {readiness?.blockers.length ? <ul>{readiness.blockers.map(code => <li key={code}>{readinessExplanation(code)}</li>)}</ul> : null}
    {readiness && <p class="application-muted">Last server check: <time dateTime={readiness.checkedAt}>{formatDate(readiness.checkedAt, true)}</time>. This snapshot does not update automatically. Check again after setup changes or a service interruption.</p>}
    <p class="application-muted">In-person review is not government verification. Availability is a readiness check, not approval of an application or proof that cash changed hands. The server rechecks each action.</p>
    <button class="bank-button secondary" type="button" disabled={loading} onClick={refresh}>Check service readiness</button>
  </aside>;
}

function readinessExplanation(code: string): string {
  const labels: Record<string, string> = {
    WORKFLOW_DISABLED: "Account opening has not been activated by the bank.",
    IDENTITY_CONFIGURATION_UNAVAILABLE: "Private identity processing or its encryption configuration is unavailable.",
    CASH_ACCOUNT_UNAVAILABLE: "The bank's cash account is unavailable for dedicated cash operations.",
    OPENING_HOLD_UNAVAILABLE: "The opening-deposit holding account has not been activated or is unavailable."
  };
  return labels[code] || "The bank must resolve this readiness check: " + code.replace(/_/g, " ").toLowerCase() + ".";
}

function AdminApplicationQueue({ token }: { token: string }) {
  const readiness = useLoad(() => applicationApi.adminReadiness(token), [token]);
  const [status, setStatus] = useState<ApplicationStatus | "">("PENDING_REVIEW");
  const [page, setPage] = useState(0);
  const result = useLoad(() => applicationApi.listAdmin(token, { status: status || undefined, page, size: pageSize }), [token, status, page]);
  const items = result.data?.items || [], total = result.data?.total || 0;
  return <div class="application-admin">
    <PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title="Account applications" description="Check original identity documents in person, acknowledge actual cash and open eligible savings accounts." action={<button class="bank-button secondary" disabled={result.loading} onClick={result.reload}>Refresh applications</button>}/>
    <ReadinessNotice readiness={readiness.loading || readiness.error ? undefined : readiness.data} loading={readiness.loading} error={readiness.error} refresh={readiness.reload}/>
    <Panel title="Application queue">
      <div class="application-filters"><label>Application status<select value={status} onChange={event => { setStatus(event.currentTarget.value as ApplicationStatus | ""); setPage(0); }}>
        <option value="">All statuses</option>{APPLICATION_STATUSES.map(value => <option key={value} value={value}>{applicationStatusLabel(value)}</option>)}
      </select></label></div>
      <State loading={result.loading} error={result.error} retry={result.reload}>
        <p class="application-result-count" role="status">{total} {total === 1 ? "application" : "applications"} found</p>
        {!items.length ? <p class="bank-notice">No applications on this page. Choose another status or return to the previous page.</p> :
          <div class="admin-table-scroll application-table-scroll" tabIndex={0} role="region" aria-label="Scrollable application results"><table class="admin-table"><caption class="sr-only">Account applications matching the selected status</caption><thead><tr><th scope="col">Applicant</th><th scope="col">Opening amount</th><th scope="col">Status</th><th scope="col">Requested</th><th scope="col">Review</th></tr></thead><tbody>
            {items.map(item => <tr key={item.id}><td><strong>{item.fullName}</strong><small>{item.email}</small></td><td>{formatMoney(item.openingAmount, item.currencyCode)}</td><td><ApplicationStatusBadge status={item.status}/></td><td>{formatDate(item.createdAt)}</td><td><a class="admin-account-link" href={applicationHref(item.id)}>Inspect application<span class="sr-only"> for {item.fullName}</span></a></td></tr>)}
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
  function refresh() { if (locked || !confirmNavigation()) return; setUpdated(undefined); setNotice(""); loaded.reload(); }
  function saved(result: AccountApplication) {
    setUpdated(result);
    setNotice(`Server confirmed: ${applicationStatusLabel(result.status)}. The application record below is updated.`);
  }
  return <div class="application-admin">
    <nav class="admin-breadcrumb" aria-label="Breadcrumb"><a href="#/admin/applications" aria-disabled={locked || undefined} onClick={event => { if (locked) event.preventDefault(); }}>← Application queue</a></nav>
    <PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title="Review account application" description="A review decision does not create an account or credit money." action={<button class="bank-button secondary" disabled={locked || loaded.loading} onClick={refresh}>Refresh application</button>}/>
    <ReadinessNotice readiness={readiness.loading || readiness.error ? undefined : readiness.data} loading={readiness.loading} error={readiness.error} refresh={readiness.reload}/>
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
  const [revealed, setRevealed] = useState<{scope: string; value: RevealedIdentity} | null>(null);
  const [revealing, setRevealing] = useState(false), [revealError, setRevealError] = useState("");
  const revealEpoch = useRef(0);
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
  const identityScope = `${token}:${application.id}:${application.version}:${application.status}`;
  const visibleIdentity = !locked && identityReady && application.status === "PENDING_REVIEW" && revealed?.scope === identityScope ? revealed.value : null;
  const revealAccess = useRef({scope: identityScope, allowed: false, busy: false});
  revealAccess.current.scope = identityScope;
  revealAccess.current.allowed = !locked && identityReady && application.status === "PENDING_REVIEW";
  const heldReceipts = application.receipts.filter(receipt => receipt.status === "RECEIVED" && normalizeOpeningAmount(receipt.amount) === requestedAmount && requestedAmount !== null);
  const pendingRefunds = application.receipts.filter(receipt => receipt.status === "REFUND_PENDING" && normalizeOpeningAmount(receipt.amount) === requestedAmount && requestedAmount !== null);
  const dirty = !!(reviewReason || cashConfirmed || refundReason || refundConfirmed
    || decision !== "APPROVED" || inPersonChecked);
  useNavigationGuard(dirty, action.busy || action.uncertain);
  useEffect(() => {
    onLockedChange?.(action.busy || action.uncertain);
    return () => onLockedChange?.(false);
  }, [action.busy, action.uncertain, onLockedChange]);

  useEffect(() => {
    revealEpoch.current++; revealAccess.current.busy = false; setRevealed(null); setRevealError(""); setRevealing(false);
    return () => { revealEpoch.current++; };
  }, [identityScope, identityReady, action.busy, action.uncertain]);
  function hideIdentity() { revealEpoch.current++; revealAccess.current.busy = false; setRevealed(null); setRevealing(false); setRevealError(""); }
  async function revealIdentity() {
    if (!revealAccess.current.allowed || revealAccess.current.scope !== identityScope || revealAccess.current.busy) return;
    revealAccess.current.busy = true;
    const epoch = ++revealEpoch.current, scope = identityScope;
    setRevealing(true); setRevealError("");
    try {
      const value = await applicationApi.revealIdentity(token, application.id);
      if (epoch !== revealEpoch.current || !revealAccess.current.allowed || revealAccess.current.scope !== scope) return;
      if (value.identityType !== application.identityType || "••••" + value.identityNumber.slice(-4) !== application.identityMasked)
        throw new Error("The identifier response did not match this application.");
      setRevealed({scope, value});
    } catch {
      if (epoch === revealEpoch.current) setRevealError("The identifier could not be revealed. Check your access and service readiness, then try again.");
    } finally { if (epoch === revealEpoch.current) { revealAccess.current.busy = false; setRevealing(false); } }
  }

  function send(label: string, request: (requestKey: string) => Promise<AccountApplication>) {
    if (locked || !canPerform(label)) return;
    hideIdentity();
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
    {action.uncertain && <div class="bank-notice application-uncertain" role="status"><p>The outcome is unknown. Do not collect or return cash again, change these details or submit a new action. Recover the result of the original request. If a service is unavailable, use Check service readiness above without discarding this request.</p><button class="bank-button" disabled={!canPerform(action.label) || action.busy} onClick={() => void action.retry()}>Retry the same request</button></div>}

    {application.status === "PENDING_REVIEW" && <>
      <Panel title="In-person original identity check">
        <p>Meet the applicant and compare the original selected identity document with the application details. No upload, automated document verification or government verification takes place here.</p>
        <dl><dt>Document</dt><dd>{identityLabel(application.identityType)}</dd><dt>Saved identifier</dt><dd>{application.identityMasked}</dd></dl>
        {application.identityType === "AADHAAR" && <p>Only the last four Aadhaar digits are retained. Do not request or enter the full number.</p>}
        {visibleIdentity ? <div class="application-private-identity"><p role="status">For this in-person comparison only: <strong>{visibleIdentity.identityNumber}</strong></p><button class="bank-button secondary" type="button" onClick={hideIdentity}>Hide identifier</button></div>
          : <button class="bank-button secondary" type="button" disabled={locked || !identityReady || revealing} onClick={() => void revealIdentity()}>{revealing ? "Revealing identifier…" : "Reveal identifier for in-person check"}</button>}
        {revealing && <button class="bank-button secondary" type="button" onClick={hideIdentity}>Cancel reveal</button>}
        {revealError && <p class="bank-error" role="alert">{revealError}</p>}
        <p class="application-muted">Revealed details are temporary. Do not copy them into comments, messages or screenshots. They are hidden when this application changes or you leave the review.</p>
      </Panel>
      <Panel title="Application decision"><p>Approval permits cash collection; it does not create an account or credit funds.</p>
        <form class="bank-form" onSubmit={event => { event.preventDefault(); saveDecision(); }}><fieldset disabled={locked}>
          <label>Application decision<select value={decision} onChange={event => { setDecision(event.currentTarget.value as typeof decision); setInPersonChecked(false); }}><option value="APPROVED" disabled={!identityReady}>Approve after in-person check</option><option value="CHANGES_REQUESTED">Request identity corrections</option><option value="REJECTED">Reject application</option></select></label>
          {decision === "APPROVED" && <label class="application-check"><input type="checkbox" checked={inPersonChecked} disabled={!identityReady} onChange={event => setInPersonChecked(event.currentTarget.checked)}/>I checked the original identity document in person and matched it to this applicant.</label>}
          <label>Application review reason<textarea required maxLength={500} value={reviewReason} onInput={event => setReviewReason(event.currentTarget.value)} aria-describedby="application-review-reason"/></label>
          <small id="application-review-reason">Up to 500 UTF-8 bytes. Never include identity numbers.{reviewReason ? ` ${validateReviewReason(reviewReason.trim())}` : ""}</small>
          {!identityReady && <p class="bank-notice">Approval needs private identity processing. A reasoned rejection or request for corrections does not create an account or move money.</p>}
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
