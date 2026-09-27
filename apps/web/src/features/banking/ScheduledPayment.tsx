import { useEffect, useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApiRequestError } from "../../services/auth";
import { formatDate, formatMoney, statusPresentation } from "../../services/banking-content";
import { bankApi } from "./api";
import { scheduledPayments, ScheduledPaymentReceipt, ScheduleRequest } from "./scheduled-payments";
import { Detail, Panel, State, Status, useLoad } from "./ui";
import { validAmount } from "./utils";

const unknown = (cause: unknown) => !(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500;
const notice = "This is a one-time transfer between Nexa accounts. No money is reserved now. Once authorized, automatic processing starts from midnight on the scheduled date (India time). The bank’s server must be running; if it is offline, processing resumes when it is available. Keep enough funds available: insufficient funds cause the payment to fail without moving money.";
const checkNotice = "Checking refreshes the saved status. It does not authorize the payment or send it early.";
type CheckedStatus = { status: string; at: string };
function StatusCheckFeedback({ checked }: { checked?: CheckedStatus }) {
    return checked ? <p class="bank-notice" role="status" aria-atomic="true">Status checked {formatDate(checked.at, true)}: {statusPresentation(checked.status).label}.</p> : null;
}

function ScheduleReview({ item }: { item: ScheduledPaymentReceipt }) {
    return <dl><Detail label="From">{item.sourceName} · {item.sourceMasked}</Detail>
        <Detail label="Recipient">{item.recipientName || item.payee} · {item.destinationMasked}</Detail>
        <Detail label="Amount">{formatMoney(item.amount, item.currencyCode)}</Detail>
        <Detail label="Scheduled date">{formatDate(item.dueAt)} · {item.timezone}</Detail>
        <Detail label="Frequency">One time</Detail><Detail label="Status"><Status value={item.status}/></Detail>
        {item.reference && <Detail label="Transaction reference">{item.reference}</Detail>}</dl>;
}

export function ScheduledPaymentCreate({ token, reload, initiallyOpen = false }: { token: string; reload: () => void; initiallyOpen?: boolean }) {
    const [open, setOpen] = useState(initiallyOpen), [source, setSource] = useState(""), [payee, setPayee] = useState("");
    const [amount, setAmount] = useState(""), [dueAt, setDueAt] = useState(""), [authorized, setAuthorized] = useState(false);
    const [review, setReview] = useState<ScheduledPaymentReceipt>(), [pending, setPending] = useState<ScheduleRequest>();
    const [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [error, setError] = useState("");
    const [checked, setChecked] = useState<CheckedStatus>();
    const lock = useRef(false);
    const requirements = useLoad(() => scheduledPayments.requirements(token), [token]);
    const accounts = useLoad(() => bankApi.accounts(token), [token]);
    const payees = useLoad(() => bankApi.products(token, "beneficiaries"), [token]);
    const activeAccounts = (accounts.data || []).filter(item => item.status === "ACTIVE" && item.currencyCode === "INR" && ["SAVINGS", "CURRENT"].includes(item.accountType));
    const activePayees = (payees.data || []).filter(item => item.status === "ACTIVE" && item.transferType !== "EXTERNAL_BANK" && item.bankName?.toLowerCase() === "nexa");
    const ready = requirements.data?.executionEnabled === true;
    const valid = ready && activeAccounts.some(item => item.id === source) && activePayees.some(item => item.id === payee)
        && validAmount(amount) && !!dueAt && dueAt >= (requirements.data?.earliestDueDate || "9999") && dueAt <= (requirements.data?.latestDueDate || "");
    const terminal = !!review && review.status !== "READY";
    useNavigationGuard(uncertain || !!pending || (!!review && !terminal) || (!review && !!(payee || amount || dueAt)), busy || uncertain);
    useEffect(() => { if (initiallyOpen) setOpen(true); }, [initiallyOpen]);
    useEffect(() => { if (!source && activeAccounts.length) setSource(activeAccounts[0].id); }, [accounts.data]);

    function accept(item: ScheduledPaymentReceipt) {
        if (!item.managed || (review && item.id !== review.id) || (pending && item.id !== "SP-" + pending.requestKey))
            throw new Error("The schedule response could not be verified. Check the saved schedule before continuing.");
        setReview(item); setPending(undefined); setUncertain(false); setError("");
        if (item.status !== "READY") reload();
    }
    async function prepare(retry?: ScheduleRequest) {
        if (lock.current || !ready || (!retry && !valid)) return;
        const request = retry || { requestKey: crypto.randomUUID(), sourceAccountId: source, payeeId: payee, amount, dueAt };
        lock.current = true; setBusy(true); setError(""); setChecked(undefined); setPending(request);
        try {
            const item = await scheduledPayments.prepare(token, request);
            if (item.id !== "SP-" + request.requestKey || !item.managed) throw new Error("The schedule response could not be verified.");
            setReview(item); setPending(undefined); setUncertain(false); setAuthorized(false);
        } catch (cause) {
            setError(cause instanceof Error ? cause.message : "The schedule review could not be saved.");
            setUncertain(unknown(cause)); if (!unknown(cause)) setPending(undefined);
        } finally { lock.current = false; setBusy(false); }
    }
    async function act(operation: "confirm" | "cancel" | "check") {
        if (lock.current || !review || operation === "confirm" && (!authorized || !ready || uncertain)) return;
        lock.current = true; setBusy(true); setError(""); setChecked(undefined);
        try {
            const changed = await (operation === "check" ? scheduledPayments.detail(token, review.id) : scheduledPayments[operation](token, review.id));
            accept(changed);
            if (operation === "check") setChecked({ status: changed.status, at: new Date().toISOString() });
        }
        catch (cause) { setError(cause instanceof Error ? cause.message : "The schedule result could not be checked."); setUncertain(unknown(cause)); }
        finally { lock.current = false; setBusy(false); }
    }
    function reset() { if (lock.current || uncertain) return; setReview(undefined); setPending(undefined); setUncertain(false); setAuthorized(false); setAmount(""); setPayee(""); setDueAt(""); setError(""); setChecked(undefined); }

    return <Panel title="Schedule a payment" className="bank-create-panel" action={<button type="button" class="bank-button secondary" disabled={busy || uncertain || !!review || !!pending} onClick={() => setOpen(!open)} aria-expanded={open}>{open ? "Close" : "Schedule payment"}</button>}>
        <p class="bank-create-description">Choose a future date, review the recipient and authorize one transfer from your Nexa account.</p>
        {open && <State loading={requirements.loading || accounts.loading || payees.loading} error={requirements.error || accounts.error || payees.error} retry={() => { requirements.reload(); accounts.reload(); payees.reload(); }}>
            {!ready && <p role="status">Scheduled payment execution is currently unavailable.</p>}
            {review ? <div class="bank-form"><ScheduleReview item={review}/>{review.failureReason && <p role="status">{review.failureReason}</p>}
                {review.status === "READY" ? <><p>{notice}</p><label class="application-check"><input type="checkbox" checked={authorized} disabled={busy || uncertain} onChange={event => setAuthorized(event.currentTarget.checked)}/>I authorize this one-time transfer of the reviewed amount to this recipient on the scheduled date.</label>
                    <div class="bank-form-actions"><button type="button" class="bank-button" disabled={!authorized || !ready || busy || uncertain} onClick={() => void act("confirm")}>Authorize scheduled payment</button>
                        <button type="button" class="bank-button secondary" disabled={busy || uncertain} onClick={() => void act("cancel")}>Cancel review</button></div></>
                    : <><p role="status">{review.status === "SCHEDULED" ? "Your payment is scheduled. No money has been transferred yet." : "The schedule's current result is shown above."}</p><a class="bank-button secondary" href={"#/scheduled-payments/" + encodeURIComponent(review.id)}>View scheduled payment</a><button type="button" class="bank-button secondary" disabled={busy || uncertain} onClick={reset}>Create another schedule</button></>}
                {uncertain && <p role="alert">The result is not confirmed. Check this same schedule before starting another.</p>}
                <p class="bank-form-note">{checkNotice}</p>
                <button type="button" class="bank-button secondary" disabled={busy} onClick={() => void act("check")}>{busy ? "Checking…" : "Check schedule status"}</button>
                <StatusCheckFeedback checked={checked}/>
            </div> : <form class="bank-form bank-editor-form" onSubmit={event => { event.preventDefault(); void prepare(); }}>
                <fieldset class="bank-fields-grid" disabled={busy || uncertain || !ready}>
                    <label>From account<select required value={source} onChange={event => setSource(event.currentTarget.value)}><option value="">Select your account</option>{activeAccounts.map(item => <option key={item.id} value={item.id}>{item.displayName} · {item.accountNumberMasked}</option>)}</select></label>
                    <label>Saved Nexa recipient<select required value={payee} onChange={event => setPayee(event.currentTarget.value)}><option value="">Select a saved recipient</option>{activePayees.map(item => <option key={item.id} value={item.id}>{item.displayName} · {item.accountNumberMasked}</option>)}</select></label>
                    <label>Amount (INR)<input required inputMode="decimal" value={amount} maxLength={16} onInput={event => setAmount(event.currentTarget.value)}/></label>
                    <label>Payment date (Asia/Kolkata)<input required type="date" min={requirements.data?.earliestDueDate} max={requirements.data?.latestDueDate} value={dueAt} onInput={event => setDueAt(event.currentTarget.value)}/></label>
                </fieldset><p class="bank-form-note">{notice}</p>{!activePayees.length && <p><a href="#/beneficiaries">Add or link a Nexa recipient</a> before scheduling.</p>}
                <button type="submit" class="bank-button" disabled={busy || uncertain || !valid}>{busy ? "Preparing…" : "Review scheduled payment"}</button>
                {uncertain && pending && <><p role="alert">The review may have been saved. Retry this same request; no payment is authorized until you confirm its review.</p><button type="button" class="bank-button secondary" disabled={busy || !ready} onClick={() => void prepare(pending)}>Recover this schedule review</button></>}
            </form>}{error && <p role="alert" class="bank-error">{error}</p>}
        </State>}
    </Panel>;
}

export function ScheduledPaymentDetails({ token, id, onChanged }: { token: string; id: string; onChanged?: () => void }) {
    const result = useLoad(() => scheduledPayments.detail(token, id), [token, id]);
    const [authorized, setAuthorized] = useState(false), [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [error, setError] = useState("");
    const [checked, setChecked] = useState<CheckedStatus>();
    const lock = useRef(false), item = result.data;
    useNavigationGuard(uncertain, busy || uncertain);
    useEffect(() => { setAuthorized(false); setError(""); setChecked(undefined); }, [token, id]);
    async function act(operation: "confirm" | "cancel" | "check") {
        if (lock.current || !item?.managed || operation === "confirm" && (!authorized || uncertain)) return;
        lock.current = true; setBusy(true); setError(""); setChecked(undefined);
        try {
            const changed = await (operation === "check" ? scheduledPayments.detail(token, id) : scheduledPayments[operation](token, id));
            if (changed.id !== id || !changed.managed) throw new Error("The returned schedule could not be verified.");
            setUncertain(false); setAuthorized(false); result.reload();
            if (operation === "check") setChecked({ status: changed.status, at: new Date().toISOString() });
            // An unchanged status check must not reload the parent and discard its acknowledgement.
            if (operation !== "check" || changed.status !== item.status || changed.reference !== item.reference || changed.failureReason !== item.failureReason) onChanged?.();
        } catch (cause) { setError(cause instanceof Error ? cause.message : "The schedule result is unavailable."); setUncertain(unknown(cause)); }
        finally { lock.current = false; setBusy(false); }
    }
    return <Panel title="Scheduled payment authorization"><State loading={result.loading} error={result.error} retry={result.reload}>{item && <div class="bank-form"><ScheduleReview item={item}/>
        {!item.managed ? <p role="status">This is a historical reminder. It has no automatic payment authorization and will not execute. Create a new schedule to authorize a transfer.</p>
            : <><p>{notice}</p>{item.failureReason && <p role="status">{item.failureReason}</p>}
                {item.status === "READY" && <><label class="application-check"><input type="checkbox" checked={authorized} disabled={busy || uncertain} onChange={event => setAuthorized(event.currentTarget.checked)}/>I authorize this one-time transfer of the reviewed amount to this recipient on the scheduled date.</label><button type="button" class="bank-button" disabled={!authorized || busy || uncertain} onClick={() => void act("confirm")}>Authorize scheduled payment</button></>}
                {["READY", "SCHEDULED"].includes(item.status) && <button type="button" class="bank-button secondary" disabled={busy || uncertain} onClick={() => void act("cancel")}>{item.status === "READY" ? "Cancel review" : "Cancel scheduled payment"}</button>}
                {uncertain && <p role="alert">The result is not confirmed. Check this same schedule before continuing.</p>}
                <p class="bank-form-note">{checkNotice}</p>
                <button type="button" class="bank-button secondary" disabled={busy} onClick={() => void act("check")}>Check schedule status</button>
                <StatusCheckFeedback checked={checked}/>
            </>}{error && <p role="alert" class="bank-error">{error}</p>}</div>}</State></Panel>;
}
