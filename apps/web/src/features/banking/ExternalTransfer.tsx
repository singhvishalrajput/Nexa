import { useEffect, useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApiRequestError } from "../../services/auth";
import { formatDate, formatMoney, humanize, moneyInMinorUnits } from "../../services/banking-content";
import { bankApi } from "./api";
import { externalTransfers, ExternalTransferReceipt, ExternalTransferRequest } from "./external-transfers";
import { Detail, PageHeading, Panel, State, useLoad } from "./ui";
import { validAmount } from "./utils";

const sandboxNotice = "Cashfree sandbox: no real money moves. Nexa balances and bills remain unchanged.";
const states = ["READY", "SUBMITTING", "PENDING", "COMPLETED", "FAILED", "CANCELLED", "EXPIRED", "REVERSED"];
const pendingStates = ["SUBMITTING", "PENDING"];
type SavedReview = {id: string; request?: ExternalTransferRequest; uncertain?: boolean};
function readSaved(key: string): SavedReview | null {
    try {
        const saved = JSON.parse(window.sessionStorage.getItem(key) || "null") as SavedReview | null;
        if (!saved || typeof saved.id !== "string" || !/^XT-[0-9a-f-]{36}$/i.test(saved.id)) return null;
        if (saved.request && saved.id !== "XT-" + saved.request.requestKey) return null;
        return saved;
    } catch { return null; }
}
function statusLabel(status: string) {
    return status === "COMPLETED" ? "Sandbox transfer confirmed" : status === "READY" ? "Review transfer" : "Transfer " + humanize(status).toLowerCase();
}

export function ExternalTransfer({token, userId, initialPayee, embedded = false}: {token: string; userId: string; initialPayee?: string; embedded?: boolean}) {
    const storageKey = "nexa-external-review:" + userId;
    const saved = useRef(readSaved(storageKey));
    const [source, setSource] = useState("");
    const [payee, setPayee] = useState(initialPayee || "");
    const [amount, setAmount] = useState("");
    const [receipt, setReceipt] = useState<ExternalTransferReceipt>();
    const [pendingPrepare, setPendingPrepare] = useState<ExternalTransferRequest>();
    const [restoring, setRestoring] = useState(!!saved.current);
    const [uncertain, setUncertain] = useState(!!saved.current?.uncertain);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const lock = useRef(false);
    const readiness = useLoad(() => externalTransfers.readiness(token), [token]);
    const accounts = useLoad(() => bankApi.accounts(token), [token]);
    const payees = useLoad(() => bankApi.products(token, "beneficiaries"), [token]);
    const history = useLoad(() => externalTransfers.history(token), [token]);
    const ready = readiness.data?.ready === true && readiness.data.environment === "SANDBOX";
    const activeAccounts = (accounts.data || []).filter(item => item.status === "ACTIVE" && item.currencyCode === "INR" && ["SAVINGS", "CURRENT"].includes(item.accountType));
    const activePayees = (payees.data || []).filter(item => item.status === "ACTIVE" && item.transferType === "EXTERNAL_BANK");
    const value = moneyInMinorUnits(amount);
    const valid = ready && activeAccounts.some(item => item.id === source) && activePayees.some(item => item.id === payee) && validAmount(amount) && value !== null && value >= BigInt(100);
    const processing = !!receipt && pendingStates.includes(receipt.status);
    const terminal = !!receipt && receipt.status !== "READY" && !processing;
    useNavigationGuard(restoring || !!pendingPrepare || uncertain || (!terminal && (!!amount || !!receipt)), busy);
    useEffect(() => { if (!source && activeAccounts.length) setSource(activeAccounts[0].id); }, [accounts.data]);
    useEffect(() => { if (saved.current) void check(); }, []);

    function remember(review: SavedReview | null) {
        saved.current = review;
        try { if (review) window.sessionStorage.setItem(storageKey, JSON.stringify(review)); else window.sessionStorage.removeItem(storageKey); }
        catch { /* Server review remains authoritative if storage is unavailable. */ }
    }
    function accept(result: ExternalTransferReceipt) {
        if (result.id !== saved.current?.id || result.environment !== "SANDBOX" || !states.includes(result.status)) throw new Error("The returned transfer result could not be verified.");
        remember({...saved.current, id: result.id, uncertain: false});
        setReceipt(result); setPendingPrepare(undefined); setRestoring(false); setUncertain(false); history.reload();
    }
    async function prepare(retry?: ExternalTransferRequest) {
        if (lock.current || !ready || (!retry && !valid)) return;
        const request = retry || {requestKey: crypto.randomUUID(), sourceAccountId: source, payeeId: payee, amount};
        lock.current = true; setBusy(true); setError(""); remember({id: "XT-" + request.requestKey, request});
        try { accept(await externalTransfers.prepare(token, request)); }
        catch (cause) {
            if (!(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500) {
                remember({...saved.current!, uncertain: true}); setUncertain(true); setPendingPrepare(request);
                setError("We could not confirm whether this review was saved. Check its status or retry the same review.");
            } else { remember(null); setPendingPrepare(undefined); setRestoring(false); setUncertain(false); setError(cause.message); }
        } finally { lock.current = false; setBusy(false); }
    }
    async function check(provider = false) {
        const id = saved.current?.id;
        if (lock.current || !id || (provider && !ready)) return;
        lock.current = true; setBusy(true); setError("");
        try { accept(await (provider ? externalTransfers.refresh(token, id) : externalTransfers.status(token, id))); }
        catch (cause) {
            if (cause instanceof ApiRequestError && cause.status === 404 && saved.current?.request) {
                setPendingPrepare(saved.current.request); setRestoring(false); setUncertain(true);
                setError("This review was not found. Retry the same review before creating another transfer.");
            } else setError(cause instanceof Error ? cause.message : "The transfer status is unavailable. Try again.");
        } finally { lock.current = false; setBusy(false); }
    }
    async function confirm() {
        if (lock.current || !ready || uncertain || receipt?.status !== "READY") return;
        lock.current = true; setBusy(true); setError(""); remember({...saved.current!, uncertain: true});
        try { accept(await externalTransfers.confirm(token, receipt.id)); }
        catch (cause) { setUncertain(true); setError(cause instanceof Error ? cause.message : "The submission result is unknown. Check this request’s status before starting another transfer."); }
        finally { lock.current = false; setBusy(false); }
    }
    async function cancel() {
        if (lock.current || uncertain || receipt?.status !== "READY") return;
        lock.current = true; setBusy(true); setError("");
        try { accept(await externalTransfers.cancel(token, receipt.id)); }
        catch { setUncertain(true); remember({...saved.current!, uncertain: true}); setError("Cancellation is not confirmed. Check this request’s status."); }
        finally { lock.current = false; setBusy(false); }
    }
    function reset() {
        if (lock.current || !terminal || uncertain) return;
        remember(null); setReceipt(undefined); setAmount(""); setError(""); setPendingPrepare(undefined);
    }
    async function show(item: ExternalTransferReceipt) {
        if (lock.current || restoring || uncertain || pendingPrepare || (receipt && !terminal)) return;
        remember({id: item.id}); setRestoring(true); setReceipt(undefined); await check();
    }

    return <section class={`bank-service-page external-transfer-page${embedded ? " external-transfer-embedded" : ""}`}>{!embedded && <PageHeading title="Payments" description="Transfer to a saved bank account." action={<a class="bank-button secondary" href="#/beneficiaries">Manage payees</a>}/>}
        <p class="bank-notice external-test-notice" role="note"><strong>Sandbox mode</strong> · {sandboxNotice}</p>
        <div class="external-transfer-layout"><Panel title={receipt ? statusLabel(receipt.status) : "Transfer details"}><div class="bank-form" aria-busy={busy}>
            <State loading={readiness.loading} error={readiness.error} retry={readiness.reload}>
                {!ready && <div class="external-readiness"><p role="status">{readiness.data?.reason || "The sandbox connection is not configured yet. You can save an other-bank payee now and return after setup."}</p><button class="bank-button secondary" type="button" disabled={busy || readiness.loading} onClick={readiness.reload}>Check connection</button></div>}
            </State>
            {restoring || pendingPrepare ? <><h3>Check your saved request</h3><p>Recover this request before starting another transfer.</p><div class="bank-form-actions"><button class="bank-button secondary" type="button" disabled={busy} onClick={() => void check()}>Check transfer status</button>{pendingPrepare && <button class="bank-button" type="button" disabled={busy || !ready} onClick={() => void prepare(pendingPrepare)}>Retry same review</button>}</div></>
                : receipt ? <><dl><Detail label="From Nexa account">{receipt.sourceName} · {receipt.sourceMasked}</Detail><Detail label="Recipient name entered">{receipt.recipientName}</Detail><Detail label="Bank">{receipt.bankName}</Detail><Detail label="Bank account">{receipt.destinationMasked}</Detail><Detail label="IFSC">{receipt.ifsc}</Detail><Detail label="Amount">{formatMoney(receipt.amount, receipt.currencyCode)}</Detail><Detail label="Status">{statusLabel(receipt.status)}</Detail>{receipt.providerTransferId && <Detail label="Provider reference">{receipt.providerTransferId}</Detail>}{receipt.utr && <Detail label="Sandbox UTR">{receipt.utr}</Detail>}{receipt.updatedAt && <Detail label="Last checked">{formatDate(receipt.updatedAt, true)}</Detail>}</dl>
                    <p class="bank-form-note">{sandboxNotice}</p>{receipt.failureReason && !processing && <p role={receipt.status === "FAILED" ? "alert" : "status"}>{receipt.failureReason}</p>}
                    {receipt.status === "READY" ? <><small>Review expires {formatDate(receipt.expiresAt, true)}.</small><div class="bank-form-actions">{uncertain ? <button class="bank-button" type="button" disabled={busy} onClick={() => void check()}>Check transfer status</button> : <><button class="bank-button" type="button" disabled={busy || !ready} onClick={() => void confirm()}>{busy ? "Submitting…" : "Confirm transfer"}</button><button class="bank-button secondary" type="button" disabled={busy} onClick={() => void cancel()}>Cancel review</button></>}</div></>
                        : processing ? <><p role="status">{receipt.failureReason || "The provider has not returned a final result. Refresh this transfer to check again; do not create a replacement transfer."}</p><button class="bank-button" type="button" disabled={busy || !ready} onClick={() => void check(true)}>{busy ? "Checking provider…" : "Refresh provider status"}</button></>
                            : <><p role="status">{receipt.status === "COMPLETED" ? "Cashfree confirmed this sandbox request. No real money moved." : receipt.status === "REVERSED" ? "Cashfree reversed this sandbox request. Nexa balances remain unchanged." : "This sandbox request is closed."}</p><div class="bank-form-actions">{receipt.status === "COMPLETED" && <button class="bank-button secondary" type="button" disabled={busy || !ready} onClick={() => void check(true)}>{busy ? "Checking provider…" : "Refresh provider status"}</button>}<button class="bank-button secondary" type="button" disabled={busy} onClick={reset}>New transfer</button></div></>}
                </> : <State loading={accounts.loading || payees.loading} error={accounts.error || payees.error} retry={() => { accounts.reload(); payees.reload(); }}>
                    <form onSubmit={event => { event.preventDefault(); void prepare(); }}><fieldset class="bank-fields-grid" disabled={busy || !ready}>
                        <label>From Nexa account<select value={source} required onChange={event => setSource(event.currentTarget.value)}><option value="">Select an account</option>{activeAccounts.map(item => <option key={item.id} value={item.id}>{item.displayName} · {item.accountNumberMasked}</option>)}</select><small>Used for the sandbox request. Its balance will not change.</small></label>
                        <label>Other-bank payee<select value={payee} required onChange={event => setPayee(event.currentTarget.value)}><option value="">Select a payee</option>{activePayees.map(item => <option key={item.id} value={item.id}>{item.displayName} · {item.bankName} · {item.accountNumberMasked}</option>)}</select></label>
                        <label>Amount (INR)<input inputMode="decimal" required maxLength={16} value={amount} onInput={event => setAmount(event.currentTarget.value)} aria-describedby="external-amount-help"/><small id="external-amount-help">Minimum ₹1, with at most two decimal places.</small></label>
                    </fieldset>{!activeAccounts.length && <p>An active Nexa INR savings or current account is required.</p>}{!activePayees.length && <p><a href="#/beneficiaries">Save an other-bank payee</a> to begin.</p>}<div class="bank-editor-actions"><button class="bank-button" disabled={busy || !valid}>{busy ? "Checking details…" : "Review transfer"}</button></div></form>
                </State>}
            {error && <p role="alert" class="bank-error">{error}</p>}
        </div></Panel><Panel title="Other-bank transfer history" action={<button class="bank-button secondary" type="button" disabled={busy} onClick={history.reload}>Refresh history</button>}><State loading={history.loading} error={history.error} retry={history.reload} empty={!history.loading && !history.error && !history.data?.length ? "No other-bank transfers yet." : undefined}>
            {history.data?.map(item => <div class="bank-record" key={item.id}><div><strong>{item.recipientName}</strong><small>{item.bankName} · {item.destinationMasked}</small><small>{statusLabel(item.status)}</small></div><strong>{formatMoney(item.amount, item.currencyCode)}</strong><button class="bank-button secondary" type="button" disabled={busy || restoring || uncertain || !!pendingPrepare || (!!receipt && !terminal)} onClick={() => void show(item)}>View transfer</button></div>)}
        </State></Panel></div>
    </section>;
}
