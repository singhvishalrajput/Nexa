import { useEffect, useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApiRequestError } from "../../services/auth";
import { formatDate, formatMoney, humanize, moneyInMinorUnits } from "../../services/banking-content";
import { bankApi, BillPaymentReceipt, BillPaymentRequest, Product } from "./api";
import { Detail, Panel, State, Status, useLoad } from "./ui";
import { validAmount } from "./utils";

type SavedReview = { id: string; request?: BillPaymentRequest; notified?: boolean; uncertain?: boolean };

function savedReview(key: string, billId: string): SavedReview | null {
    try {
        const saved = JSON.parse(window.sessionStorage.getItem(key) || "null") as SavedReview | null;
        if (!saved || typeof saved.id !== "string" || !/^BP-[0-9a-f-]{36}$/i.test(saved.id)) return null;
        if (saved.request && (saved.request.billId !== billId || saved.id !== "BP-" + saved.request.requestKey)) return null;
        return saved;
    } catch { return null; }
}

/** A durable server review is reused after a lost response; only confirmation moves funds. */
export function BillPayment({ token, bill, onPaid, onBillChanged }: { token: string; bill: Product; onPaid: () => void; onBillChanged?: () => void }) {
    const storageKey = "nexa-bill-review:" + bill.id;
    const saved = useRef(savedReview(storageKey, bill.id));
    const [open, setOpen] = useState(false);
    const [source, setSource] = useState("");
    const [payee, setPayee] = useState("");
    const [changeRecipient, setChangeRecipient] = useState(false);
    const [amount, setAmount] = useState(String(bill.outstandingAmount ?? bill.amount ?? ""));
    const [receipt, setReceipt] = useState<BillPaymentReceipt>();
    const [busy, setBusy] = useState(false);
    const [restoring, setRestoring] = useState(!!saved.current);
    const [uncertain, setUncertain] = useState(!!saved.current?.uncertain);
    const [pendingPrepare, setPendingPrepare] = useState<BillPaymentRequest>();
    const [error, setError] = useState("");
    const lock = useRef(false);
    const accounts = useLoad(() => bankApi.accounts(token), [token]);
    const useExistingRecipient = !!bill.payeeId && !changeRecipient;
    const payees = useLoad(() => useExistingRecipient ? Promise.resolve([]) : bankApi.products(token, "beneficiaries"), [token, useExistingRecipient]);
    const history = useLoad(() => bankApi.billPaymentHistory(token, bill.id), [token, bill.id]);
    const activeAccounts = (accounts.data || []).filter(account => account.status === "ACTIVE" && account.currencyCode === "INR" && ["SAVINGS", "CURRENT"].includes(account.accountType));
    const activePayees = (payees.data || []).filter(item => item.transferType !== "EXTERNAL_BANK" && item.status === "ACTIVE" && item.bankName?.toLowerCase() === "nexa");
    const from = activeAccounts.find(account => account.id === source);
    const outstanding = moneyInMinorUnits(bill.outstandingAmount ?? bill.amount ?? "0");
    const value = moneyInMinorUnits(amount);
    const minimum = moneyInMinorUnits(bill.minimumAmount ?? "0");
    const available = from ? moneyInMinorUnits(from.availableBalance) : null;
    const payable = bill.status !== "PAID" && outstanding !== null && outstanding > BigInt(0);
    const canChangeRecipient = payable && moneyInMinorUnits(bill.paidAmount ?? "0") === BigInt(0);
    const tooMuch = value !== null && ((outstanding !== null && value > outstanding) || (available !== null && value > available));
    const belowMinimum = value !== null && minimum !== null && outstanding !== null && value < minimum && value < outstanding;
    const valid = payable && !!from && (useExistingRecipient || activePayees.some(item => item.id === payee)) && validAmount(amount) && !tooMuch && !belowMinimum;
    const activeReview = receipt?.status === "READY";
    useNavigationGuard(open || activeReview || uncertain || restoring, busy);
    useEffect(() => { if (!source && activeAccounts.length) setSource(activeAccounts[0].id); }, [accounts.data]);
    useEffect(() => { if (saved.current) void checkStatus(); }, []);

    function remember(value: SavedReview | null) {
        saved.current = value;
        try {
            if (value) window.sessionStorage.setItem(storageKey, JSON.stringify(value));
            else window.sessionStorage.removeItem(storageKey);
        } catch { /* The same review remains authoritative while this page is open. */ }
    }

    function accept(result: BillPaymentReceipt, wasUncertain = false) {
        if (result.billId !== bill.id || result.id !== saved.current?.id || !["READY", "COMPLETED", "FAILED", "CANCELLED", "EXPIRED"].includes(result.status)
            || (result.status === "COMPLETED" && !result.reference)) throw new Error("The payment response could not be verified. Check the saved request again.");
        const terminal = result.status !== "READY";
        const needsRefresh = terminal && !saved.current?.notified;
        const stillUncertain = wasUncertain && result.status === "READY";
        remember({ ...saved.current, id: result.id, notified: saved.current?.notified || terminal, uncertain: stillUncertain });
        setReceipt(result); setRestoring(false); setPendingPrepare(undefined); setOpen(false); setUncertain(stillUncertain);
        history.reload();
        if (needsRefresh) {
            // A rendering/refresh failure cannot turn a confirmed outcome into another payment.
            try {
                if (result.status === "COMPLETED") { accounts.reload(); onPaid(); }
                else onBillChanged?.();
            } catch { setError("This payment result is saved. Refresh the page to update the bill details."); }
        }
    }

    async function prepare(retry?: BillPaymentRequest) {
        if (lock.current || (!retry && !valid)) return;
        const request = retry || { requestKey: crypto.randomUUID(), billId: bill.id, sourceAccountId: source, amount, ...(useExistingRecipient ? {} : { payeeId: payee }) };
        lock.current = true; setBusy(true); setError("");
        remember({ id: "BP-" + request.requestKey, request });
        try { accept(await bankApi.prepareBillPayment(token, request)); }
        catch (cause) {
            const unknown = !(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500;
            if (unknown) {
                remember({ ...saved.current!, uncertain: true }); setPendingPrepare(request); setUncertain(true); setOpen(false);
                setError("We could not confirm whether your review was saved. Check its status or retry the same review before starting another payment.");
            } else { remember(null); setPendingPrepare(undefined); setUncertain(false); setRestoring(false); setOpen(true); setError(cause.message); }
        } finally { lock.current = false; setBusy(false); }
    }

    async function checkStatus(id = saved.current?.id) {
        if (lock.current || !id) return;
        lock.current = true; setBusy(true); setError("");
        try {
            const result = await bankApi.billPayment(token, id);
            accept(result, uncertain || !!saved.current?.uncertain);
            if (result.status === "READY" && (uncertain || saved.current?.uncertain)) setError("This review is still awaiting confirmation. Retry the same payment to safely finish it.");
        } catch (cause) {
            if (cause instanceof ApiRequestError && cause.status === 404 && saved.current?.request) {
                setPendingPrepare(saved.current.request); setRestoring(false); setUncertain(true);
                setError("This review was not found. Retry the same review to safely check the payment details.");
            } else setError("We could not check this payment. Keep this request and try again before making another payment.");
        } finally { lock.current = false; setBusy(false); }
    }

    async function confirm() {
        if (lock.current || !receipt || receipt.status !== "READY") return;
        lock.current = true; setBusy(true); setError("");
        remember({ ...saved.current!, id: receipt.id, uncertain: true });
        try { accept(await bankApi.confirmBillPayment(token, receipt.id)); }
        catch (cause) {
            setUncertain(true);
            setError(cause instanceof ApiRequestError && cause.status > 0 && cause.status < 500 ? cause.message : "The payment result is not confirmed. Check its status before starting another payment. A retry will use the same request.");
        } finally { lock.current = false; setBusy(false); }
    }

    async function cancel() {
        if (lock.current || !receipt || receipt.status !== "READY" || uncertain) return;
        lock.current = true; setBusy(true); setError("");
        try { accept(await bankApi.cancelBillPayment(token, receipt.id)); }
        catch { setUncertain(true); remember({ ...saved.current!, uncertain: true }); setError("We could not confirm cancellation. Check this payment’s status before starting another one."); }
        finally { lock.current = false; setBusy(false); }
    }

    function reset() {
        if (lock.current || activeReview || uncertain || restoring) return;
        remember(null); setReceipt(undefined); setPendingPrepare(undefined); setError(""); setOpen(false); setChangeRecipient(false); setPayee("");
        setAmount(String(bill.outstandingAmount ?? bill.amount ?? "")); accounts.reload(); history.reload();
    }

    async function showAttempt(item: BillPaymentReceipt) {
        if (lock.current || activeReview || uncertain || restoring) return;
        remember({ id: item.id, notified: item.status !== "READY" });
        setRestoring(true); setOpen(false); setReceipt(undefined);
        await checkStatus(item.id);
    }

    return <Panel title="Bill payment" className="bill-payment-panel" action={!restoring && !pendingPrepare && !receipt && payable && !open
        ? <button class="bank-button" type="button" onClick={() => { setAmount(String(bill.outstandingAmount ?? bill.amount ?? "")); setOpen(true); setError(""); }}>Pay now</button> : undefined}><div class="bank-form bill-payment-body" aria-busy={busy}>
        <p class="bank-form-note">Payments are made only after you confirm. Automatic debit is off.</p>
        {restoring || pendingPrepare ? <div class="bill-payment-recovery"><h3>Check your previous request</h3>
            <p>Your saved request will be checked before another payment can begin.</p>
            <div class="bank-form-actions"><button class="bank-button secondary" type="button" disabled={busy} onClick={() => void checkStatus()}>Check payment status</button>
                {pendingPrepare && <button class="bank-button" type="button" disabled={busy} onClick={() => void prepare(pendingPrepare)}>Retry same review</button>}</div>
        </div> : receipt ? <section aria-label="Bill payment review"><h3>{receipt.status === "COMPLETED" ? "Payment completed" : receipt.status === "READY" ? "Review bill payment" : "Payment " + humanize(receipt.status).toLowerCase()}</h3>
            <dl><Detail label="Bill">{receipt.billerName}</Detail><Detail label="From account">{receipt.sourceName} · {receipt.sourceMasked}</Detail><Detail label="Recipient account holder">{receipt.recipientName}</Detail><Detail label="To Nexa account">{receipt.destinationMasked}</Detail><Detail label="Amount">{formatMoney(receipt.amount, receipt.currencyCode)}</Detail>{receipt.reference && <Detail label="Reference">{receipt.reference}</Detail>}</dl>
            {receipt.status === "READY" ? <><p>Check the recipient’s name and account carefully. Confirming transfers this amount to their Nexa account.</p><small>Review expires {formatDate(receipt.expiresAt, true)}.</small>
                <div class="bank-form-actions">{uncertain && <button class="bank-button secondary" type="button" disabled={busy} onClick={() => void checkStatus()}>Check payment status</button>}
                    <button class="bank-button" type="button" disabled={busy} onClick={() => void confirm()}>{busy ? "Please wait…" : uncertain ? "Retry same payment" : "Confirm payment"}</button>
                    {!uncertain && <button class="bank-button secondary" type="button" disabled={busy} onClick={() => void cancel()}>Cancel review</button>}</div></>
                : <>{receipt.status === "COMPLETED" ? <p role="status">Payment completed. The account balances and this bill’s paid amount have been updated.</p> : <p role={receipt.status === "FAILED" ? "alert" : "status"}>{receipt.failureReason || "No payment was made by this request."}</p>}
                    <div class="bank-form-actions">{receipt.reference && receipt.status === "COMPLETED" && <a class="bank-button" href={"#/transactions/" + encodeURIComponent(receipt.reference)}>View transaction</a>}<button class="bank-button secondary" type="button" disabled={busy} onClick={reset}>{receipt.status === "COMPLETED" ? "Done" : "Start a new review"}</button></div></>}
        </section> : !payable ? <p role="status">{bill.status === "PAID" && moneyInMinorUnits(bill.paidAmount ?? "0") === BigInt(0) ? "Previously recorded paid. No new payment is available for this bill." : "This bill has no outstanding amount to pay."}</p>
            : open ? <State loading={accounts.loading || payees.loading} error={accounts.error || payees.error} retry={() => { accounts.reload(); payees.reload(); }}>
                {!activeAccounts.length ? <p>You need an active INR savings or current account to pay this bill.</p> : <form onSubmit={event => { event.preventDefault(); void prepare(); }}>
                    <fieldset class="bank-fields-grid" disabled={busy}><label>From account<select required value={source} onChange={event => setSource(event.currentTarget.value)}><option value="">Select an account</option>{activeAccounts.map(account => <option key={account.id} value={account.id}>{account.displayName} · {account.accountNumberMasked}</option>)}</select>{from && <small>Available: {formatMoney(from.availableBalance, "INR")}</small>}</label>
                        {useExistingRecipient ? <div class="bill-payment-recipient"><strong>Recipient account holder</strong><span>{bill.recipientName}</span><small>{bill.recipientAccountMasked}</small>{canChangeRecipient && <button class="bank-button secondary" type="button" onClick={() => setChangeRecipient(true)}>Change recipient</button>}</div> : <label>Saved Nexa payee<select required value={payee} onChange={event => setPayee(event.currentTarget.value)}><option value="">Select the bill recipient</option>{activePayees.map(item => <option key={item.id} value={item.id}>{item.displayName} · {item.accountNumberMasked}</option>)}</select><small>Choose the Nexa account that should receive this bill payment.</small></label>}
                        <label>Payment amount (INR)<input required inputMode="decimal" maxLength={16} value={amount} onInput={event => setAmount(event.currentTarget.value)} aria-describedby="bill-amount-help"/><small id="bill-amount-help">Outstanding: {formatMoney(bill.outstandingAmount ?? bill.amount ?? "0", "INR")}{minimum !== null && minimum > BigInt(0) && <> · Minimum: {formatMoney(bill.minimumAmount!, "INR")}, or the remaining balance if lower.</>}</small></label>
                    </fieldset>
                    {!useExistingRecipient && <p class="bank-form-note">{!activePayees.length ? "No active Nexa payee is available. " : "Need another recipient? "}<a href="#/beneficiaries">Add or link a payee</a>. Payments here transfer money between Nexa accounts.{bill.payeeId && <button class="bank-button secondary" type="button" onClick={() => { setChangeRecipient(false); setPayee(""); }}>Keep linked recipient</button>}</p>}
                    {tooMuch && <p role="alert">The payment must not exceed the outstanding bill amount or your available balance.</p>}{belowMinimum && <p role="alert">Enter at least the minimum amount, or pay the full remaining balance.</p>}
                    <div class="bank-form-actions"><button class="bank-button" disabled={busy || !valid}>{busy ? "Checking details…" : "Review payment"}</button><button type="button" class="bank-button secondary" disabled={busy} onClick={() => setOpen(false)}>Close</button></div>
                </form>}
            </State> : null}
        {error && <p role="alert" class="bank-error">{error}</p>}
        <details class="bill-payment-attempts"><summary>Payment attempts</summary><State loading={history.loading} error={history.error} retry={history.reload} empty={!history.loading && !history.error && !history.data?.length ? "No payment attempts yet." : undefined}>
            {history.data?.map(item => <div class="bank-record" key={item.id}><div><strong>{formatMoney(item.amount, item.currencyCode)}</strong><small>{item.recipientName} · {item.destinationMasked}</small>{item.failureReason && <small>{item.failureReason}</small>}</div><Status value={item.status}/><button type="button" class="bank-button secondary" disabled={busy || activeReview || uncertain || restoring} onClick={() => void showAttempt(item)}>{item.status === "READY" ? "Resume review" : "View"}</button></div>)}
        </State></details>
    </div></Panel>;
}
