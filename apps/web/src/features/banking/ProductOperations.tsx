import { useEffect, useRef, useState } from "preact/hooks";
import { authenticatedRequest, ApiRequestError } from "../../services/auth";
import { PrepaymentCalculator, PrepaymentComparison, PrepaymentOption, RepaymentPreview, isPartialPrepayment, validRepayment } from "./LoanPrepayment";
import { bankApi, Product } from "./api";
import { Panel, useLoad } from "./ui";
import { formatMoney, formatDate } from "../../services/banking-content";
import { SalarySlipFields, appendSalarySlips } from "./LoanSalarySlips";
import { LoanApplication, LoanApplicationDraft, verifiedLoanApplication } from "../../services/loan-applications";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
type Kind = "mandates" | "loans";
type LoanRepaymentOptions = {
    minimumAmount: number;
    maximumAmount: number;
    interestAmount: number;
    principalOnly: boolean;
    regularEmi: number | null;
    remainingInstallments: number;
    finalDueDate: string | null;
    minimumExtraPrincipal?: number | null;
    nextInstallment?: {
        dueDate: string;
        principalAmount: number;
        interestAmount: number;
        totalAmount: number;
        status: string;
    };
};
const write = (token: string, path: string, value: unknown = {}) => authenticatedRequest<Record<string, unknown>>(path, token, { method: "POST", body: JSON.stringify(value) });
export function ProductCreate({ token, kind, reload, initiallyOpen = false, applicationKey: suppliedApplicationKey, onCreated, onSubmissionUncertain, onSubmissionLocked, disabled = false }: {
    token: string;
    kind: Kind;
    reload: () => void;
    initiallyOpen?: boolean;
    applicationKey?: string;
    onCreated?: (id: string, application: LoanApplication) => void;
    onSubmissionUncertain?: (unknown: boolean) => void;
    onSubmissionLocked?: (locked: boolean) => void;
    disabled?: boolean;
}) {
    const inFlight = useRef(false);
    const applicationKey = useRef(suppliedApplicationKey || crypto.randomUUID());
    const pendingLoan = useRef<{ body: FormData; draft: LoanApplicationDraft } | null>(null);
    const unknownOutcome = useRef(false);
    const completed = useRef(false);
    const accounts = useLoad(() => bankApi.accounts(token), [token]);
    const salaryMonths = useLoad(() => kind === "loans"
        ? authenticatedRequest<string[]>("/loans/salary-slip-requirements", token) : Promise.resolve([]), [token, kind]);
    const [open, setOpen] = useState(initiallyOpen), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const [uncertain, setUncertain] = useState(false);
    useNavigationGuard(busy || uncertain, busy || uncertain);
    const [recipientMode, setRecipientMode] = useState("saved");
    const payees = useLoad(() => kind === "mandates" && open
        ? authenticatedRequest<Product[]>("/beneficiaries", token) : Promise.resolve([]), [token, kind, open]);
    const recipients = (payees.data || []).filter(payee => payee.status === "ACTIVE" && payee.transferType !== "EXTERNAL_BANK" && payee.bankName?.toLowerCase() === "nexa");
    const fundingAccounts = (accounts.data || []).filter(account => ["SAVINGS", "CURRENT"].includes(account.accountType) && account.status === "ACTIVE" && (kind !== "loans" || account.currencyCode === "INR"));
    const loanUnavailable = kind === "loans" && (accounts.loading || !!accounts.error || !fundingAccounts.length || salaryMonths.loading || !!salaryMonths.error || salaryMonths.data?.length !== 3);
    const formId = "create-" + kind + (suppliedApplicationKey ? "-" + suppliedApplicationKey : "");
    useEffect(() => { setOpen(initiallyOpen); }, [initiallyOpen]);
    async function submit(event: Event) {
        event.preventDefault();
        if (inFlight.current || disabled || suppliedApplicationKey && completed.current || loanUnavailable && !pendingLoan.current)
            return;
        inFlight.current = true;
        if (kind === "loans") onSubmissionLocked?.(true);
        setBusy(true);
        setError("");
        let posted = false;
        const retrying = unknownOutcome.current;
        try {
            if (kind === "loans") {
                if (!pendingLoan.current) {
                    const form = new FormData(event.currentTarget as HTMLFormElement);
                    const draft = { purpose: String(form.get("name") || ""), accountId: Number(form.get("account")), amount: String(form.get("amount") || ""), tenureMonths: Number(form.get("tenure")), applicationKey: applicationKey.current };
                    const body = new FormData();
                    body.append("application", new Blob([JSON.stringify(draft)], { type: "application/json" }));
                    appendSalarySlips(body, form, salaryMonths.data || []);
                    pendingLoan.current = { body, draft };
                }
                posted = true;
                const result = verifiedLoanApplication(await authenticatedRequest<unknown>("/loans", token, { method: "POST", body: pendingLoan.current.body }), applicationKey.current, pendingLoan.current.draft);
                completed.current = true;
                pendingLoan.current = null;
                unknownOutcome.current = false;
                setUncertain(false);
                onCreated?.(result.id, result);
            } else {
                const form = new FormData(event.currentTarget as HTMLFormElement);
                const recipient = recipientMode === "saved" ? { payeeId: form.get("payeeId") }
                    : { beneficiaryAccountNumber: form.get("beneficiary"), payee: form.get("name") };
                await write(token, "/" + kind, { sourceAccountId: Number(form.get("account")), ...recipient, limit: form.get("amount"), startDate: form.get("start"), endDate: form.get("end") || null });
            }
            if (!suppliedApplicationKey) applicationKey.current = crypto.randomUUID();
            setOpen(false);
            reload();
        }
        catch (e) {
            const unknown = posted && (retrying || !(e instanceof ApiRequestError && [400, 403, 404, 409, 413, 415, 422].includes(e.status)));
            if (posted && !unknown) pendingLoan.current = null;
            unknownOutcome.current = unknown;
            setUncertain(unknown);
            setError(unknown ? "We could not confirm the application result. Check application status or retry the same application below. Your original details and files are kept for this retry." : e instanceof Error ? e.message : "Unable to create this request.");
            if (posted) onSubmissionUncertain?.(unknown);
        }
        finally {
            inFlight.current = false;
            setBusy(false);
            if (kind === "loans" && !unknownOutcome.current) onSubmissionLocked?.(false);
        }
    }
    return <Panel className="bank-create-panel" title={kind === "loans" ? "Apply for a loan" : "Set up a direct debit"} action={<button class="bank-button secondary" type="button" disabled={busy || disabled || uncertain} aria-expanded={open} aria-controls={formId} onClick={() => setOpen(!open)}>{open ? "Close" : kind === "loans" ? "Request a loan" : "Create mandate"}</button>}>
 <p class="bank-create-description">{kind === "loans" ? "Choose an amount and repayment period, then submit your documents for review." : "Choose a recipient, payment limit and dates for a recurring authorization."}</p>
 {open && <form id={formId} class="bank-form bank-editor-form" onSubmit={submit}>
 <fieldset disabled={busy || disabled || uncertain} class="loan-application-fields bank-fields-grid">
 {kind === "loans" ? <label>Loan type<select name="name" required defaultValue=""><option value="" disabled>Choose a loan type</option>{["Personal loan", "Car loan", "Home loan", "Education loan", "Business loan", "Other loan"].map(type => <option key={type} value={type}>{type}</option>)}</select></label> : <label>Recipient<select value={recipientMode} onChange={event => setRecipientMode(event.currentTarget.value)}><option value="saved">Saved Nexa payee</option><option value="manual">Enter a Nexa account</option></select></label>}
 <label>{kind === "loans" ? "Disbursement and repayment account" : "Pay from"}<select name="account" required disabled={kind === "loans" && (accounts.loading || !!accounts.error)}><option value="">Choose an account</option>{fundingAccounts.map(a => <option key={a.id} value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label>
 {kind === "mandates" && (recipientMode === "saved" ? <label>Saved payee<select name="payeeId" required disabled={payees.loading || !!payees.error}><option value="">Choose a payee</option>{recipients.map(payee => <option key={payee.id} value={payee.id}>{payee.displayName} · {payee.accountNumberMasked}</option>)}</select><small>Choose the Nexa account authorised to receive this direct debit.</small></label> : <><label>Payee name<input name="name" required maxLength={120}/></label><label>Beneficiary Nexa account number<input name="beneficiary" required inputMode="numeric"/></label></>)}
 <label>{kind === "loans" ? "Principal (INR)" : "Maximum per payment (INR)"}<input name="amount" type="number" min={kind === "loans" ? "1000" : "0.01"} max={kind === "loans" ? "1000000" : undefined} step="0.01" required/></label>
 {kind === "loans" ? <label>Loan tenure (months)<input name="tenure" type="number" min="1" max="60" step="1" required/><small>The bank sets the annual rate. Review your approved terms before accepting the loan.</small></label> : <><label>Effective date<input name="start" type="date" required/></label><label>End date (optional)<input name="end" type="date"/></label></>}
 {kind === "loans" && <div class="bank-field-wide">{salaryMonths.loading && <p role="status">Loading required salary-slip months…</p>}{salaryMonths.error && <p role="alert">{salaryMonths.error}<button class="bank-button secondary" type="button" onClick={salaryMonths.reload}>Retry</button></p>}{salaryMonths.data?.length === 3 && <SalarySlipFields months={salaryMonths.data}/>}</div>}
 <p class="bank-form-note bank-field-wide">{kind === "loans" ? "Your request and salary slips go to the administrator for verification. No money moves until approval and your acceptance." : "Creation records a pending authorization. Activate it from its details page before making payments."}</p>
 </fieldset>
 {kind === "loans" && (accounts.loading ? <p role="status">Loading repayment accounts…</p> : accounts.error ? <p role="alert">Your accounts could not be loaded. <button type="button" class="bank-button secondary" disabled={busy || disabled} onClick={accounts.reload}>Retry accounts</button></p> : !fundingAccounts.length && <p>An active INR savings or current account is required. <a href="#/accounts">View your accounts</a>.</p>)}
 {kind === "mandates" && recipientMode === "saved" && (payees.loading ? <p role="status">Loading saved payees…</p> : payees.error ? <p role="alert">Saved payees could not be loaded. <button type="button" onClick={payees.reload}>Retry payees</button></p> : !recipients.length && <p>No active Nexa payee is available. <a href="#/beneficiaries">Add or link a payee</a>, or enter a Nexa account.</p>)}
 {error && <p role="alert">{error}</p>}<div class="bank-editor-actions"><button class="bank-button" type="submit" disabled={busy || disabled || loanUnavailable && !pendingLoan.current || kind === "mandates" && recipientMode === "saved" && (payees.loading || !!payees.error || !recipients.length)}>{busy ? "Saving…" : kind === "loans" ? uncertain ? "Retry same application" : "Submit loan application" : "Create"}</button></div></form>}</Panel>;
}
export function BillCreate({ token, reload, initiallyOpen = false }: { token: string; reload: () => void; initiallyOpen?: boolean; }) {
    const inFlight = useRef(false);
    const [open, setOpen] = useState(initiallyOpen), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const [payeeId, setPayeeId] = useState(""), [billerName, setBillerName] = useState("");
    const payees = useLoad(() => open ? authenticatedRequest<Product[]>("/beneficiaries", token) : Promise.resolve([]), [token, open]);
    const recipients = (payees.data || []).filter(payee => payee.status === "ACTIVE" && payee.transferType !== "EXTERNAL_BANK" && payee.bankName?.toLowerCase() === "nexa");
    useEffect(() => { setOpen(initiallyOpen); }, [initiallyOpen]);
    async function submit(event: Event) {
        event.preventDefault();
        if (inFlight.current)
            return;
        inFlight.current = true;
        const form = new FormData(event.currentTarget as HTMLFormElement);
        setBusy(true);
        setError("");
        try {
            await bankApi.createBill(token, { billerName: String(form.get("billerName") || "").trim(), amount: String(form.get("amount") || ""), dueAt: String(form.get("dueAt") || ""), category: String(form.get("category") || "").trim(), customerNumber: String(form.get("customerNumber") || "").trim(), ...(payeeId ? { payeeId } : {}) });
            setOpen(false);
            setPayeeId(""); setBillerName("");
            reload();
        }
        catch (e) {
            setError(e instanceof Error ? e.message : "The bill could not be added.");
        }
        finally {
            inFlight.current = false;
            setBusy(false);
        }
    }
    return <Panel className="bank-create-panel" title="Keep your bills together" action={<button class="bank-button secondary" type="button" disabled={busy} aria-expanded={open} aria-controls="create-bill" onClick={() => setOpen(!open)}>{open ? "Close" : "Add bill"}</button>}>
 <p class="bank-create-description">Enter the details printed on your bill. You can link a saved Nexa recipient now, then review and confirm payment from the bill details.</p>
 {open && <form id="create-bill" class="bank-form bank-editor-form" onSubmit={submit}>
 <fieldset disabled={busy} class="bank-fields-grid">
 <label>Saved Nexa bill recipient (optional)<select name="payeeId" value={payeeId} disabled={payees.loading || !!payees.error} onChange={event => { const id = event.currentTarget.value; setPayeeId(id); const recipient = recipients.find(item => item.id === id); if (recipient) setBillerName(recipient.displayName || ""); }} aria-describedby="bill-recipient-help"><option value="">Choose a recipient when paying</option>{recipients.map(recipient => <option key={recipient.id} value={recipient.id}>{recipient.displayName} · {recipient.accountNumberMasked}</option>)}</select><small id="bill-recipient-help">Select the account that should receive this bill payment. Check that it belongs to your biller.</small></label>
 <label>Biller name on invoice<input name="billerName" required maxLength={160} value={billerName} onInput={event => setBillerName(event.currentTarget.value)}/></label>
 <label>Biller customer / consumer number<input name="customerNumber" required maxLength={40} aria-describedby="bill-customer-number-help"/><small id="bill-customer-number-help">Enter the consumer number printed on your bill. This is different from the recipient’s bank account number.</small></label>
 <label>Category<select name="category" required defaultValue=""><option value="" disabled>Select a bill category</option><option value="Electricity">Electricity bill</option><option value="Water">Water bill</option><option value="Gas">Gas bill</option><option value="Mobile">Mobile bill</option><option value="Internet">Internet / broadband bill</option><option value="TV / DTH">TV / DTH bill</option><option value="Other">Other bill</option></select></label>
 <label>Amount on your bill (INR)<input name="amount" type="number" min="0.01" max="9999999999999.99" step="0.01" required/></label>
  <label>Due date<input name="dueAt" type="date" min="0001-01-01" max="9999-12-31" required aria-describedby="bill-due-date-help"/><small id="bill-due-date-help">Past due dates are saved as overdue.</small></label>
 </fieldset>
 {payees.loading ? <p role="status">Loading saved recipients…</p> : payees.error ? <p role="alert">Saved recipients could not be loaded. You can still add the bill and choose a recipient later. <button class="bank-button secondary" type="button" onClick={payees.reload}>Retry recipients</button></p> : <p class="bank-form-note">{recipients.length ? "Need another recipient? " : "No saved Nexa recipient is available. "}<a href="#/beneficiaries">Add or link a payee</a>.</p>}
 <p class="bank-form-note">The amount is entered from your invoice; Nexa does not fetch utility bills. Saving a bill does not transfer money.</p>
 {error && <p role="alert">{error}</p>}<div class="bank-editor-actions"><button class="bank-button" type="submit" disabled={busy}>{busy ? "Saving…" : "Add bill"}</button></div>
 </form>}</Panel>;
}
export function ProductStatusControl({ token, kind, product, reload, onPosted }: {
    token: string;
    kind: "mandates";
    product: Product;
    reload: () => void;
    onPosted?: (message: string) => void;
}) {
    const [status, setStatus] = useState(product.status);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const states = ["PENDING", "ACTIVE", "PAUSED", "CANCELLED", "EXPIRED", "ACTION_REQUIRED"];
    async function save() {
        setBusy(true);
        setError("");
        try {
            await bankApi.setProductStatus(token, kind, product.id, status);
            onPosted?.("Status updated");
            reload();
        }
        catch (e) {
            setError(e instanceof Error ? e.message : "The status could not be changed.");
        }
        finally { setBusy(false); }
    }
    return <Panel title="Payment status"><div class="bank-form"><label>Status<select value={status} disabled={busy} onChange={e => setStatus(e.currentTarget.value)}>{states.map(value => <option value={value}>{value.replace(/_/g, " ").toLowerCase()}</option>)}</select></label><button disabled={busy || status === product.status} onClick={save}>{busy ? "Saving…" : "Save status"}</button>{error && <p role="alert">{error}</p>}</div></Panel>;
}
export function ProductOperations({ token, kind, product, reload, onPosted }: {
    token: string;
    kind: Kind;
    product: Product;
    reload: () => void;
    onPosted?: (message: string) => void;
}) {
    const inFlight = useRef(false);
    const terms = useLoad(() => authenticatedRequest<Record<string, string | number>>("/" + kind + "/" + encodeURIComponent(product.id) + (kind === "loans" ? "/account" : "/authorization"), token), [token, kind, product.id, product.status]);
    const [busy, setBusy] = useState(false), [error, setError] = useState(""), [receipt, setReceipt] = useState("");
    const [amount, setAmount] = useState("");
    const [key, setKey] = useState(() => crypto.randomUUID());
    const [review, setReview] = useState(false);
    const [comparison, setComparison] = useState<{ basis: string; preview: RepaymentPreview } | null>(null);
    const [prepaymentOption, setPrepaymentOption] = useState<PrepaymentOption>();
    const activeLoan = kind === "loans" && ["ACTIVE", "OVERDUE"].includes(product.status);
    const repayment = useLoad(async () => {
        if (!activeLoan) return null;
        const path = "/loans/" + encodeURIComponent(product.id);
        const [limits, schedule] = await Promise.all([
            authenticatedRequest<LoanRepaymentOptions>(path + "/repayment-options", token),
            authenticatedRequest<NonNullable<LoanRepaymentOptions["nextInstallment"]>[]>(path + "/schedule", token)
        ]);
        return { ...limits, nextInstallment: schedule.find(row => row.status === "PENDING" || row.status === "OVERDUE") };
    }, [token, kind, product.id, product.status, product.outstanding, product.nextEmi]);
    const options = repayment.data;
    const nextInstallment = options?.nextInstallment;
    const annualRate = terms.data?.INTEREST_RATE ?? product.interestRate;
    const money = (value: string | number) => formatMoney(String(value), "INR");
    const amountInPaise = Math.round(Number(amount) * 100);
    const basis = JSON.stringify([product.id, product.outstanding, product.nextEmi, options, amount]);
    const preview = comparison?.basis === basis ? comparison.preview : null;
    const needsChoice = activeLoan && !!options && isPartialPrepayment(amount, options);
    const validAmount = Number.isFinite(Number(amount)) && Number(amount) > 0
        && /^\d+(\.\d{1,2})?$/.test(amount)
        && (!activeLoan || !!options && validRepayment(amount, options));
    function chooseAmount(value: string) {
        setAmount(value);
        setKey(crypto.randomUUID());
        setReview(false);
        setComparison(null);
        setPrepaymentOption(undefined);
    }
    async function reviewPayment() {
        if (!validAmount || inFlight.current) return;
        if (!needsChoice) { setReview(true); return; }
        inFlight.current = true; setBusy(true); setError(""); setReview(false);
        setComparison(null); setPrepaymentOption(undefined);
        try {
            const result = await authenticatedRequest<RepaymentPreview>("/loans/" + encodeURIComponent(product.id) + "/repayment-preview", token, { method: "POST", body: JSON.stringify({ amount }) });
            setComparison({ basis, preview: result }); setReview(true);
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to compare repayment options."); }
        finally { inFlight.current = false; setBusy(false); }
    }
    async function act(operation: string, money = false) { if (inFlight.current)
        return;
        if (money && (!validAmount || needsChoice && (!preview || !prepaymentOption || !preview.options.some(o => o.option === prepaymentOption && o.available)))) return;
        inFlight.current = true; setBusy(true); setError(""); try {
        const result = await write(token, "/" + kind + "/" + encodeURIComponent(product.id) + "/" + operation, money ? { amount, requestId: key, ...(needsChoice ? { prepaymentOption, previewToken: preview!.previewToken } : {}) } : {});
        const message = result.transactionId ? "Payment posted. Transaction: " + result.transactionId : "Status updated";
        setReceipt(message);
        onPosted?.(message);
        setReview(false);
        setKey(crypto.randomUUID());
        setAmount("");
        setComparison(null); setPrepaymentOption(undefined);
        repayment.reload();
        terms.reload();
        reload();
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "The operation could not be completed.");
        if (e instanceof ApiRequestError && e.status === 409) {
            setReview(false); setComparison(null); setPrepaymentOption(undefined);
            repayment.reload(); terms.reload(); reload();
        }
    }
    finally {
        inFlight.current = false;
        setBusy(false);
    } }
    return <><Panel title={kind === "loans" ? "Loan payments" : "Mandate controls"} className={kind === "loans" ? "loan-payment-panel" : undefined}>
 <div class={kind === "loans" ? "loan-payment-body" : "bank-operation-body"}>
 {terms.error && <p role="alert">{terms.error}<button onClick={terms.reload}>Retry account details</button></p>}
 {terms.data && !activeLoan && <p>{kind === "loans" ? "Original loan: " + (terms.data.PRINCIPAL_AMOUNT != null ? money(terms.data.PRINCIPAL_AMOUNT) : "not recorded") + " · Annual rate: " + terms.data.INTEREST_RATE + "%" : "Effective: " + terms.data.EFFECTIVE_DATE + (terms.data.END_DATE ? " to " + terms.data.END_DATE : "")}</p>}
 {kind === "mandates" && terms.data && !terms.data.DESTINATION_ACCOUNT_ID && <p>This imported mandate has no verified beneficiary account. Create a new mandate before executing payments.</p>}
 {kind === "loans" && product.status === "APPROVED" && <button class="bank-button" disabled={busy} onClick={() => act("disburse")}>Accept approved loan and receive funds</button>}
 {kind === "loans" && product.status === "PENDING_APPROVAL" && <p role="status">Your loan request is awaiting administrator approval.</p>}
 {kind === "loans" && product.status === "REJECTED" && <p role="status">This loan request was not approved.</p>}
 {kind === "mandates" && ["PENDING", "PAUSED", "ACTION_REQUIRED"].includes(product.status) && <button class="bank-button" disabled={busy} onClick={() => act("activate")}>Activate mandate</button>}
 {activeLoan && repayment.error && <p role="alert">{repayment.error}<button onClick={repayment.reload}>Retry repayment details</button></p>}
 {activeLoan && repayment.loading && <p role="status">Loading repayment limits…</p>}
 {activeLoan && options && <>
 <div class="loan-payment-status"><span class={options.principalOnly ? "loan-payment-badge is-paid" : "loan-payment-badge"}>{options.principalOnly ? "Principal prepayment available" : "EMI payment available"}</span><span>{options.principalOnly ? "Extra payments go to principal." : "Pay your EMI or add extra principal."} Fixed rate: {annualRate}%.</span></div>
 <div class="loan-payment-comparison">
 <div class="loan-payment-figure is-payoff"><span>Close loan today</span><strong>{money(options.maximumAmount)}</strong><small>{options.principalOnly ? "Remaining principal only" : "Principal + interest due"}</small></div>
 {nextInstallment && <div class="loan-payment-figure"><span>{options.remainingInstallments === 1 ? "Final EMI" : "Next EMI"} <span class="loan-payment-date">· {formatDate(nextInstallment.dueDate)}</span></span><strong>{money(nextInstallment.totalAmount)}</strong><small>Includes {money(nextInstallment.interestAmount)} interest</small></div>}
 </div>
 {nextInstallment && <details class="loan-interest-details">
 <summary>Why these amounts differ</summary>
 <div class="loan-interest-content">
 <dl class="loan-interest-breakdown">
 <div><dt>Outstanding principal</dt><dd>{product.outstanding != null ? money(product.outstanding) : "Unavailable"}</dd></div>
 <div><dt>Next EMI’s principal portion</dt><dd>{money(nextInstallment.principalAmount)}</dd></div>
 <div><dt>Monthly interest ({annualRate}% yearly)</dt><dd>{money(nextInstallment.interestAmount)}</dd></div>
 <div class="loan-interest-total"><dt>Next EMI, including interest</dt><dd>{money(nextInstallment.totalAmount)}</dd></div>
 </dl>
 {product.outstanding != null && annualRate != null && <p class="loan-interest-equation">{money(product.outstanding)} × {annualRate}% ÷ 12 = <strong>{money(nextInstallment.interestAmount)}</strong> interest</p>}
 <p>{options.principalOnly ? "Paying off now clears the principal. The scheduled EMI includes monthly interest if that balance remains." : "An EMI repays part of the loan; the payoff amount clears all remaining principal and the interest due."}</p>
 <p>Monthly calculation, rounded to paise; not daily interest accrual. Extra principal payments reduce future interest. Choose to keep EMI and shorten tenure, or reduce EMI and keep the current end date. Your annual rate stays fixed.</p>
 {options.finalDueDate && <p>Scheduled finish: {formatDate(options.finalDueDate)}</p>}
 </div></details>}
 <dl class="loan-payment-facts">
 {terms.data?.PRINCIPAL_AMOUNT != null && <div><dt>Original loan</dt><dd>{money(terms.data.PRINCIPAL_AMOUNT)}</dd></div>}
 {options.regularEmi != null && <div><dt>Regular EMI</dt><dd>{money(options.regularEmi)}</dd></div>}
 {options.regularEmi != null && <div><dt>EMIs left</dt><dd>{options.remainingInstallments}</dd></div>}
 </dl>
 </>}
 {(product.status === "ACTIVE" || (kind === "loans" && product.status === "OVERDUE")) && <div class={kind === "loans" ? "loan-repayment-form" : "bank-operation-form"}>
 <label>{kind === "loans" ? "Repayment amount (INR)" : "Amount (INR)"}<input type="number" min={activeLoan && options ? options.minimumAmount : "0.01"} max={activeLoan && options ? options.maximumAmount : undefined} step="0.01" placeholder={activeLoan && options ? Number(options.minimumAmount).toFixed(2) : undefined} disabled={busy || activeLoan && !options} value={amount} onInput={e => chooseAmount(e.currentTarget.value)}/></label>
 {activeLoan && options && <div class="loan-payment-shortcuts"><span>Min. {money(options.minimumAmount)}</span><div>{!options.principalOnly && <button type="button" disabled={busy} onClick={() => chooseAmount(Number(options.minimumAmount).toFixed(2))}>Use EMI amount</button>}<button type="button" disabled={busy} onClick={() => chooseAmount(Number(options.maximumAmount).toFixed(2))}>Use payoff amount</button></div></div>}
 {activeLoan && options?.regularEmi != null && <p class="loan-calculation-note">Extra principal minimum: {money(options.minimumExtraPrincipal ?? options.regularEmi)}. A smaller full payoff is allowed. When an EMI is payable, this minimum is in addition to that EMI.</p>}
 <button class="bank-button loan-review-button" disabled={busy || !validAmount} onClick={reviewPayment}>{kind === "loans" ? "Review repayment" : "Review mandate payment"}</button>
 {review && needsChoice && preview && <PrepaymentComparison preview={preview} selected={prepaymentOption} disabled={busy} onSelect={option => { setPrepaymentOption(option); setKey(crypto.randomUUID()); }}/>}
 {review && (!needsChoice || !!preview) && <div class={kind === "loans" ? "loan-payment-review" : undefined} role="region" aria-label="Payment review"><p>Pay {money(amount)} from the linked account.</p>{activeLoan && options && <dl class="loan-interest-breakdown"><div><dt>Interest in this payment</dt><dd>{money(options.interestAmount)}</dd></div><div><dt>Principal reduction</dt><dd>{money(((amountInPaise - Math.round(options.interestAmount * 100)) / 100).toFixed(2))}</dd></div></dl>}<div class={kind === "loans" ? "loan-review-actions" : undefined}><button class={kind === "loans" ? "bank-button" : undefined} disabled={busy || !validAmount || needsChoice && (!preview || !prepaymentOption)} onClick={() => act(kind === "loans" ? "repay" : "execute", true)}>Confirm payment</button><button class={kind === "loans" ? "bank-button secondary" : undefined} disabled={busy} onClick={() => setReview(false)}>Back</button></div></div>}
 </div>}
 {kind === "mandates" && !["CANCELLED", "REVOKED"].includes(product.status) && <button class="bank-button secondary" disabled={busy} onClick={() => act("revoke")}>Revoke mandate</button>}
 {error && <p role="alert">{error}</p>}{receipt && <p role="status">{receipt}</p>}</div></Panel>
 {activeLoan && options?.regularEmi != null && <PrepaymentCalculator token={token} loanId={product.id} limits={options} revision={JSON.stringify([product.outstanding, product.nextEmi, options])}/>}</>;
}
