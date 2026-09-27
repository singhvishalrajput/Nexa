import { useEffect, useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApiRequestError } from "../../services/auth";
import { bankFunding, BankFundingReceipt, BankFundingRequest, fundingFieldErrors, fundingRequest, validFundingFields, validFundingRequest, verifiedFundingReceipt } from "../../services/bank-funding";
import { formatDate, formatMoney } from "../../services/banking-content";
import { Detail, PageHeading, Panel, State, useLoad } from "./ui";

type Recovery = {request?: BankFundingRequest; error?: string};
function recovery(key: string, userId: string): Recovery {
  try {
    const raw = window.sessionStorage.getItem(key);
    if (!raw) return {};
    const saved = JSON.parse(raw);
    if (saved?.version !== 1 || saved.userId !== userId || !validFundingRequest(saved.request)) throw new Error();
    return {request: saved.request};
  } catch {
    return {error: "Saved cash-receipt recovery details could not be read. Do not record a replacement. Check recent receipts and resolve this saved request before recording more cash."};
  }
}

export function AdminBankFunding({token, userId}: {token: string; userId: string}) {
  return <FundingWorkspace key={userId} token={token} userId={userId}/>;
}

function FundingWorkspace({token, userId}: {token: string; userId: string}) {
  const storageKey = "nexa-bank-funding-recovery:" + userId;
  const [saved] = useState(() => recovery(storageKey, userId));
  const [amount, setAmount] = useState(""), [source, setSource] = useState(""), [reference, setReference] = useState(""), [reason, setReason] = useState("");
  const [review, setReview] = useState<BankFundingRequest | undefined>(saved.request);
  const [attempted, setAttempted] = useState(!!saved.request), [acknowledged, setAcknowledged] = useState(false);
  const [busy, setBusy] = useState(false), [error, setError] = useState(saved.error || "");
  const [receipt, setReceipt] = useState<BankFundingReceipt>();
  const lock = useRef(false), active = useRef(true);
  const data = useLoad(() => bankFunding.overview(token), [token]);
  const ready = data.data?.ready === true && data.data.currencyCode === "INR";
  const valid = validFundingFields({amount, source, reference, reason});
  const fieldErrors = fundingFieldErrors({amount, source, reference, reason});
  useNavigationGuard(!receipt && (!!review || !!amount || !!source || !!reference || !!reason), busy);
  useEffect(() => { active.current = true; if (saved.request) void check(saved.request); return () => {active.current = false;}; }, []);

  function remember(request: BankFundingRequest) {
    window.sessionStorage.setItem(storageKey, JSON.stringify({version: 1, userId, request}));
  }
  function forget() {
    window.sessionStorage.removeItem(storageKey);
  }
  function finish(result: BankFundingReceipt, request: BankFundingRequest) {
    if (!verifiedFundingReceipt(result, request)) throw new Error("The receipt returned by the server did not match this request. Check its status before recording anything else.");
    if (!active.current) return;
    setReceipt(result); setAttempted(false); setAcknowledged(false); setError(""); data.reload();
    try { forget(); }
    catch { setError("The cash receipt is recorded, but this browser could not clear its recovery details. Check this receipt after reloading before starting another."); }
  }
  function prepare() {
    if (lock.current || !ready || !valid || review || saved.error) return;
    setReview(fundingRequest({amount, source, reference, reason})); setAcknowledged(false); setError("");
  }
  async function record() {
    if (lock.current || !review || !acknowledged || !ready || saved.error || receipt) return;
    const request = review, wasAttempted = attempted;
    try { remember(request); }
    catch { setError("This browser could not save the recovery details. No request was sent. Allow session storage and retry this same receipt."); return; }
    lock.current = true; setBusy(true); setAttempted(true); setError("");
    try { finish(await bankFunding.record(token, request), request); }
    catch (cause) {
      if (!active.current) return;
      if (!wasAttempted && cause instanceof ApiRequestError && [400, 403, 409, 422].includes(cause.status)) {
        try { forget(); setAttempted(false); setReview(undefined); setAcknowledged(false); }
        catch { setAttempted(true); }
        setError(cause.message + (cause.status === 409 ? " Check recent receipts for this source document reference before correcting the details. Do not record the same cash under another reference." : ""));
      } else {
        setAttempted(true);
        setError(cause instanceof Error ? cause.message + " Check this receipt or retry the same request; do not record a replacement." : "The result is not confirmed. Check this receipt or retry the same request; do not record a replacement.");
      }
    } finally { lock.current = false; if (active.current) setBusy(false); }
  }
  async function check(request = review) {
    if (lock.current || !request) return;
    lock.current = true; setBusy(true); setError("");
    try { finish(await bankFunding.byRequest(token, request.requestId), request); }
    catch (cause) {
      if (!active.current) return;
      setAttempted(true);
      setError(cause instanceof ApiRequestError && cause.status === 404
        ? "No receipt was found yet. Keep this request and retry it with the same reference; do not record a replacement."
        : cause instanceof Error ? cause.message : "Receipt status is unavailable. Keep this request and check again.");
    } finally { lock.current = false; if (active.current) setBusy(false); }
  }
  function startAnother() {
    if (lock.current || !receipt) return;
    try { forget(); }
    catch { setError("The browser could not clear the saved recovery details. Reload and check this receipt before recording more cash."); return; }
    setReceipt(undefined); setReview(undefined); setAttempted(false); setAcknowledged(false); setAmount(""); setSource(""); setReference(""); setReason(""); setError("");
  }

  return <section class="bank-service-page admin-bank-funding"><PageHeading title="Bank funding" description="View lending reserves and record bank-owned cash already received." action={<button class="bank-button secondary" disabled={busy || data.loading} onClick={data.reload}>Refresh funding</button>}/>
    <p class="bank-notice" role="note">Record only bank-owned cash already received. This adds a cash receipt to the bank’s lending reserve. It does not start an external bank transfer or take money from customer deposits.</p>
    <Panel title="Current bank balances"><State loading={data.loading} error={data.error} retry={data.reload}>{data.data && <div class="bank-form"><dl class="admin-dialog-summary"><Detail label="Lending reserve">{data.data.reserveBalance === null ? "Unavailable" : formatMoney(data.data.reserveBalance, data.data.currencyCode)}</Detail><Detail label="Bank cash account">{data.data.cashBalance === null ? "Unavailable" : formatMoney(data.data.cashBalance, data.data.currencyCode)}</Detail></dl>{!ready && <p role="status">{data.data.reason || "Bank funding is not available. Check the bank’s account setup."}</p>}<a href="#/admin/loans">View loan requests →</a></div>}</State></Panel>
    <Panel title={receipt ? "Cash receipt recorded" : attempted ? "Recover cash receipt" : review ? "Review cash receipt" : "Record bank cash receipt"}><div class="bank-form admin-review-form" aria-busy={busy}>
      {receipt ? <><p class="bank-notice" role="status">Receipt {receipt.receiptNumber} recorded. The bank’s cash and lending-reserve balances were updated.</p><ReceiptDetails receipt={receipt}/><button class="bank-button secondary" type="button" disabled={busy} onClick={startAnother}>Record another receipt</button></>
        : saved.error ? <p>Cash recording is paused until the saved request is resolved. The recent receipt list below can help identify it.</p>
        : review ? <><dl class="admin-dialog-summary"><Detail label="Cash amount">{formatMoney(review.amount, "INR")}</Detail><Detail label="Source of cash">{review.source}</Detail><Detail label="Source document reference">{review.reference}</Detail><Detail label="Reason">{review.reason}</Detail><Detail label="Request reference">{review.requestId}</Detail></dl>
          {attempted && <p class="bank-notice" role="status">This request may already have been recorded. Check its receipt or retry these exact details. The request reference stays the same.</p>}
          <label class="bank-check"><input type="checkbox" checked={acknowledged} disabled={busy} onChange={event => setAcknowledged(event.currentTarget.checked)}/>I confirm the bank has received this cash and the amount, source and source document reference are correct.</label>
          <div class="bank-form-actions">{attempted && <button class="bank-button secondary" type="button" disabled={busy} onClick={() => void check()}>Check receipt status</button>}<button class="bank-button" type="button" disabled={busy || !acknowledged || !ready} onClick={() => void record()}>{busy ? "Checking receipt…" : attempted ? "Retry same receipt" : "Confirm cash received"}</button>{!attempted && <button class="bank-button secondary" type="button" disabled={busy} onClick={() => {setReview(undefined); setAcknowledged(false); setError("");}}>Edit receipt</button>}</div></>
        : <form onSubmit={event => {event.preventDefault(); prepare();}}><fieldset class="admin-dialog-fields" disabled={busy || !ready}><legend>Cash received by the bank</legend><label>Amount (INR)<input required inputMode="decimal" maxLength={11} value={amount} onInput={event => setAmount(event.currentTarget.value)}/><small>Positive amount, up to ₹1,00,00,000, with at most two decimal places.</small>{amount && fieldErrors.amount && <small role="alert" class="bank-error">{fieldErrors.amount}</small>}</label><label>Source of cash<input required maxLength={160} value={source} onInput={event => setSource(event.currentTarget.value)} placeholder="For example, bank owner capital"/><small>Identify the person or organisation providing the bank’s funds.</small>{source && fieldErrors.source && <small role="alert" class="bank-error">{fieldErrors.source}</small>}</label><label class="admin-field-wide">Source document reference<input required maxLength={120} value={reference} onInput={event => setReference(event.currentTarget.value)} placeholder="Cash voucher or deposit reference"/><small>Enter the reference from the original cash voucher or supporting receipt. Nexa generates its own receipt number after you confirm. Use up to 80 letters, numbers or / . _ -. Spaces are removed and letters capitalised on review.</small>{reference && fieldErrors.reference && <small role="alert" class="bank-error">{fieldErrors.reference}</small>}</label><label class="admin-field-wide">Reason<textarea required maxLength={500} value={reason} onInput={event => setReason(event.currentTarget.value)}/>{reason && fieldErrors.reason && <small role="alert" class="bank-error">{fieldErrors.reason}</small>}</label></fieldset><p class="bank-form-note">The source and reason must be concise. The receipt, administrator, reference and balanced accounting entries will be kept for audit.</p><button class="bank-button" disabled={busy || !ready || !valid}>Review cash receipt</button></form>}
      {error && <p class="bank-error" role="alert">{error}</p>}
    </div></Panel>
    <Panel title="Recent bank cash receipts"><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !data.data?.receipts.length ? "No bank cash receipts recorded yet" : undefined}>
      <p class="bank-form-note">Most recent 20 receipts. These are records of cash received, not confirmation from an external payment provider.</p>
      {data.data?.receipts.map(item => <article class="admin-mandate" key={item.id}><header><strong>{item.receiptNumber}</strong><strong>{formatMoney(item.amount, item.currencyCode)}</strong></header><p>{item.source} · {item.reference}</p><small>{formatDate(item.recordedAt, true)} · Recorded by {item.recordedBy}</small><details><summary>Receipt and audit details</summary><ReceiptDetails receipt={item}/></details></article>)}
    </State></Panel>
  </section>;
}

function ReceiptDetails({receipt}: {receipt: BankFundingReceipt}) {
  return <dl class="admin-dialog-summary"><Detail label="Receipt number">{receipt.receiptNumber}</Detail><Detail label="Amount">{formatMoney(receipt.amount, receipt.currencyCode)}</Detail><Detail label="Source of cash">{receipt.source}</Detail><Detail label="Source document reference">{receipt.reference}</Detail><Detail label="Reason">{receipt.reason}</Detail><Detail label="Recorded by">{receipt.recordedBy}</Detail><Detail label="Recorded at">{formatDate(receipt.recordedAt, true)}</Detail><Detail label="Lending reserve after receipt">{formatMoney(receipt.reserveBalanceAfter, receipt.currencyCode)}</Detail><Detail label="Cash balance after receipt">{formatMoney(receipt.cashBalanceAfter, receipt.currencyCode)}</Detail><Detail label="Transaction reference">{receipt.transactionId}</Detail><Detail label="Request reference">{receipt.requestId}</Detail></dl>;
}
