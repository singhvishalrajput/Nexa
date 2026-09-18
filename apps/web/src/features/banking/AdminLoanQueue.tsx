import { useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { formatDate, formatMoney } from "../../services/banking-content";
import { Detail, Modal, PageHeading, Panel, State } from "./ui";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
export type LoanRequest = {
    ID: number;
    PRODUCT_ID: string;
    ACCOUNT_NAME: string;
    FULL_NAME: string;
    EMAIL: string;
    PRINCIPAL_AMOUNT: string;
    INTEREST_RATE: string;
    TENURE_MONTHS: number | null;
    PERIODIC_PAYMENT: string | null;
    LOAN_PURPOSE: string | null;
    CREATED_AT: string;
    FUNDING_ACCOUNT_ID: number;
};
export function AdminLoanQueue({ token, requests, loading, error, reload }: {
    token: string;
    requests: LoanRequest[];
    loading: boolean;
    error: string;
    reload: () => void;
}) {
    const [selected, setSelected] = useState<LoanRequest>(), [receipt, setReceipt] = useState("");
    return <><PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title="Loan requests" description="Review customer applications. Approval allows the customer to accept and receive the loan." action={<button class="bank-button secondary" onClick={reload}>Refresh requests</button>}/>
 {receipt && <p class="bank-notice" role="status">{receipt}</p>}
 <Panel title={`${requests.length} awaiting approval`}><State loading={loading} error={error} retry={reload} empty={!loading && !error && !requests.length ? "No loan requests awaiting approval" : undefined}>
 {requests.map(r => <article class="admin-mandate" key={r.PRODUCT_ID}><header><div><strong>{r.ACCOUNT_NAME}</strong><p>{r.FULL_NAME} · {r.EMAIL}</p></div><strong>{formatMoney(r.PRINCIPAL_AMOUNT)}</strong></header><p>{r.TENURE_MONTHS ? `${r.TENURE_MONTHS} months · ` : ""}{r.INTEREST_RATE}% annual interest · Requested {formatDate(r.CREATED_AT)}</p><div class="admin-actions"><button class="bank-button" onClick={() => setSelected(r)}>Review request</button><a href={`#/admin/accounts/${r.ID}/overview`}>Open loan account →</a><a href={`#/admin/accounts/${r.FUNDING_ACCOUNT_ID}/overview`}>Borrower account →</a></div></article>)}
 </State></Panel>{selected && <LoanDecision key={selected.PRODUCT_ID} token={token} request={selected} close={() => setSelected(undefined)} done={message => { setReceipt(message); setSelected(undefined); reload(); }}/>}</>;
}
export function LoanDecision({ token, request, close, done }: {
    token: string;
    request: LoanRequest;
    close: () => void;
    done: (message: string) => void;
}) {
    const [reason, setReason] = useState(""), [decision, setDecision] = useState("approve"), [review, setReview] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const lock = useRef(false);
    useNavigationGuard(!!reason, busy);
    async function submit() { if (lock.current)
        return; lock.current = true; setBusy(true); setError(""); try {
        await authenticatedRequest(`/admin/loans/${request.PRODUCT_ID}/${decision}`, token, { method: "POST", body: JSON.stringify({ reason }) });
        done(decision === "approve" ? "Loan approved. The customer can now accept and receive the funds." : "Loan rejected. The decision is recorded in the account audit.");
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "Unable to record decision. Refresh the queue to check its status.");
    }
    finally {
        lock.current = false;
        setBusy(false);
    } }
    return <Modal title="Review loan request" onClose={close} locked={busy}><div class="bank-form"><dl><Detail label="Customer">{request.FULL_NAME} · {request.EMAIL}</Detail><Detail label="Purpose">{request.LOAN_PURPOSE || request.ACCOUNT_NAME}</Detail><Detail label="Principal">{formatMoney(request.PRINCIPAL_AMOUNT)}</Detail><Detail label="Annual rate">{request.INTEREST_RATE}%</Detail><Detail label="Tenure">{request.TENURE_MONTHS ? `${request.TENURE_MONTHS} months` : "Principal-only loan"}</Detail>{request.PERIODIC_PAYMENT && <Detail label="Monthly payment">{formatMoney(request.PERIODIC_PAYMENT)}</Detail>}</dl>
 <fieldset disabled={busy}><label>Decision<select value={decision} onChange={e => { setDecision(e.currentTarget.value); setReview(false); }}><option value="approve">Approve</option><option value="reject">Reject</option></select></label><label>Reason for decision<textarea maxLength={500} required value={reason} onInput={e => { setReason(e.currentTarget.value); setReview(false); }}/></label></fieldset>
 {!review ? <button class="bank-button" disabled={!reason.trim() || busy} onClick={() => setReview(true)}>Review decision</button> : <div class="bank-confirm-summary"><p>{decision === "approve" ? "Approve" : "Reject"} {formatMoney(request.PRINCIPAL_AMOUNT)} for {request.FULL_NAME}?</p><p>{reason}</p><button class="bank-button" disabled={busy} onClick={submit}>Confirm {decision === "approve" ? "approval" : "rejection"}</button></div>}{error && <p role="alert" class="bank-error">{error}</p>}</div></Modal>;
}
