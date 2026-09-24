import { useEffect, useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { formatDate, formatMoney } from "../../services/banking-content";
import { bankApi, Product } from "./api";
import { Detail, PageHeading, Panel, State, useLoad } from "./ui";

export type LoanQuote = {
    amount: number;
    currencyCode: string;
    annualInterestRate: number;
    tenureMonths: number;
    emiAmount: number;
    finalEmiAmount: number;
    totalInterest: number;
    totalRepayment: number;
};

export function validQuoteInputs(amount: string, months: string): boolean {
    return /^\d+(\.\d{1,2})?$/.test(amount) && Number(amount) >= 1000
        && Number(amount) <= 1000000 && /^\d+$/.test(months)
        && Number(months) >= 1 && Number(months) <= 60;
}

export function loanApplicationStatus(status: string): string {
    if (["ACTIVE", "OVERDUE", "PAID", "CLOSED"].includes(status)) return "Amount disbursed";
    if (status === "APPROVED") return "Approved · awaiting your acceptance";
    if (status === "REJECTED") return "Rejected";
    if (status === "PENDING_APPROVAL") return "Pending review";
    return "Status unavailable";
}

type LoanSection = "new" | "loans" | "calculator" | "applications";

/** Hash URLs remain deep-linkable while loan-tool changes stay client-side. */
export function navigateLoanTool(event: MouseEvent) {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = event.currentTarget as HTMLAnchorElement;
    const destination = link.hash;
    if (!destination || destination === window.location.hash) return;
    event.preventDefault();
    window.location.hash = destination;
}

export function LoanNavigation({ current }: { current: LoanSection }) {
    return <nav class="loan-tools-nav" aria-label="Loan tools">
        <a href="#/loans/new" onClick={navigateLoanTool} aria-current={current === "new" ? "page" : undefined}>Request loan</a>
        <a href="#/loans" onClick={navigateLoanTool} aria-current={current === "loans" ? "page" : undefined}>My loans</a>
        <a href="#/loans/calculator" onClick={navigateLoanTool} aria-current={current === "calculator" ? "page" : undefined}>EMI calculator</a>
        <a href="#/loans/applications" onClick={navigateLoanTool} aria-current={current === "applications" ? "page" : undefined}>Track application</a>
    </nav>;
}

export type LoanCalculatorDraft = { amount: string; months: string };

export function LoanCalculatorPage({ token, draft, onDraftChange }: { token: string; draft?: LoanCalculatorDraft; onDraftChange?: (draft: LoanCalculatorDraft) => void }) {
    const [amount, setAmount] = useState(draft?.amount || "100000"), [months, setMonths] = useState(draft?.months || "12");
    const [quote, setQuote] = useState<LoanQuote | null>(null);
    const [busy, setBusy] = useState(false), [error, setError] = useState("");
    const generation = useRef(0);
    const inFlight = useRef(false);
    useEffect(() => () => { generation.current++; }, []);
    function change(field: "amount" | "months", value: string) {
        generation.current++;
        setQuote(null);
        setError("");
        const next = field === "amount" ? { amount: value, months } : { amount, months: value };
        if (field === "amount") setAmount(value); else setMonths(value);
        onDraftChange?.(next);
    }
    async function calculate(event: Event) {
        event.preventDefault();
        if (inFlight.current || !validQuoteInputs(amount, months)) return;
        inFlight.current = true;
        const epoch = ++generation.current;
        setBusy(true); setError(""); setQuote(null);
        try {
            const result = await authenticatedRequest<LoanQuote>("/loans/quote", token, {
                method: "POST", body: JSON.stringify({ amount, tenureMonths: Number(months) })
            });
            if (generation.current === epoch) setQuote(result);
        } catch (e) {
            if (generation.current === epoch) setError(e instanceof Error ? e.message : "Unable to calculate your quote.");
        } finally { inFlight.current = false; setBusy(false); }
    }
    return <section class="bank-service-page">
        <PageHeading title="EMI calculator" description="Explore a monthly payment before requesting a loan."/>
        <LoanNavigation current="calculator"/>
        <div class="loan-calculator-grid">
            <Panel title="Build your estimate"><form class="bank-form loan-calculator-form" onSubmit={calculate}>
                <label>Loan amount (INR)<input name="amount" type="number" min="1000" max="1000000" step="0.01" required value={amount} onInput={e => change("amount", e.currentTarget.value)}/><small>₹1,000 to ₹10,00,000</small></label>
                <label>Tenure (months)<input name="tenure" type="number" min="1" max="60" step="1" required value={months} onInput={e => change("months", e.currentTarget.value)}/><small>1 to 60 months</small></label>
                <p>The bank supplies a fixed annual interest rate. All figures come from Nexa’s quote API.</p>
                <button class="bank-button" type="submit" disabled={busy || !validQuoteInputs(amount, months)}>{busy ? "Calculating…" : "Calculate EMI"}</button>
                {error && <p role="alert" class="bank-error">{error}</p>}
            </form></Panel>
            <Panel title="Your repayment estimate" className="loan-quote-panel">
                <div class="loan-quote-body" aria-live="polite" aria-busy={busy}>
                    {quote ? <>
                        <div class="loan-quote-hero"><span>Estimated monthly EMI</span><strong>{formatMoney(quote.emiAmount, quote.currencyCode)}</strong><small>{quote.annualInterestRate}% annual interest · {quote.tenureMonths} months</small></div>
                        <dl>
                            <Detail label="Loan principal">{formatMoney(quote.amount, quote.currencyCode)}</Detail>
                            <Detail label="Total scheduled interest">{formatMoney(quote.totalInterest, quote.currencyCode)}</Detail>
                            <Detail label="Total scheduled repayment">{formatMoney(quote.totalRepayment, quote.currencyCode)}</Detail>
                            <Detail label="Final installment">{formatMoney(quote.finalEmiAmount, quote.currencyCode)}</Detail>
                        </dl>
                        <p>Indicative quote, not approval. Assumes every payment follows the schedule. The rate is fixed for the loan; prepayments can reduce total interest or monthly EMI.</p>
                        <a class="bank-button secondary" href="#/loans/new" onClick={navigateLoanTool}>Request a loan →</a>
                    </> : <div class="loan-quote-placeholder"><span aria-hidden="true">◇</span><h3>A clearer view of your repayments</h3><p>Enter an amount and tenure, then calculate to see EMI, interest and total repayment.</p></div>}
                </div>
            </Panel>
        </div>
    </section>;
}

type Application = Product & { terms?: { amount: number; tenureMonths: number | null; emiAmount: number | null } };

export function LoanApplicationsPage({ token }: { token: string }) {
    const [page, setPage] = useState(0);
    const data = useLoad(() => bankApi.products(token, "loans", page) as Promise<Application[]>, [token, page]);
    return <section class="bank-service-page">
        <PageHeading title="Track your loan application" description="Follow review, approval and disbursement. Approval does not move money until you accept." action={<button class="bank-button secondary" onClick={data.reload}>Refresh status</button>}/>
        <LoanNavigation current="applications"/>
        <State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !data.data?.length ? "No applications on this page" : undefined}>
            <div class="bank-product-grid">{data.data?.map(loan => <article class="bank-product loan-application-card" key={loan.id}>
                <span class="loan-application-state">{loanApplicationStatus(loan.status)}</span>
                <h2>{loan.displayName || "Loan application"}</h2>
                <small>Application {loan.id}</small>
                {loan.terms?.amount != null && <div class="bank-product-amount"><span>Requested amount</span><strong>{formatMoney(loan.terms.amount, loan.currencyCode || "INR")}</strong></div>}
                <p>{loan.status === "APPROVED" ? "Your loan is approved. Review the terms and accept to receive funds." : loan.status === "REJECTED" ? "Open the application to view the decision reason." : loan.status === "PENDING_APPROVAL" ? "Your application is awaiting administrator review." : ["CLOSED", "PAID"].includes(loan.status) ? "Disbursed and repaid. Your loan is closed." : ["ACTIVE", "OVERDUE"].includes(loan.status) ? "Funds have been disbursed to your linked account." : "Open the application for details."}</p>
                <a href={"#/loans/" + encodeURIComponent(loan.id)}>View application →</a>
            </article>)}</div>
        </State>
        <div class="bank-pagination"><span>Page {page + 1}</span><button disabled={page === 0 || data.loading} onClick={() => setPage(p => p - 1)}>← Previous</button><button disabled={data.loading || !!data.error || (data.data?.length || 0) < 12} onClick={() => setPage(p => p + 1)}>Next →</button></div>
    </section>;
}

type ApplicationAccount = { CREATED_AT?: string; REVIEWED_AT?: string; REVIEW_REASON?: string; PRINCIPAL_AMOUNT?: number; PRODUCT_STATUS: string };
type LoanPayment = { type?: string; amount: number; paidAt?: string; status: string };

export function LoanApplicationTimeline({ token, product }: { token: string; product: Product }) {
    const data = useLoad(async () => {
        const path = "/loans/" + encodeURIComponent(product.id);
        const [account, payments] = await Promise.all([
            authenticatedRequest<ApplicationAccount>(path + "/account", token),
            authenticatedRequest<LoanPayment[]>(path + "/payments", token)
        ]);
        return { account, disbursement: payments.find(p => p.type === "DISBURSEMENT") };
    }, [token, product.id, product.status]);
    const account = data.data?.account;
    const disbursement = data.data?.disbursement;
    const disbursed = !!account && ["ACTIVE", "OVERDUE", "PAID", "CLOSED"].includes(account.PRODUCT_STATUS);
    const approved = disbursed || account?.PRODUCT_STATUS === "APPROVED";
    const rejected = account?.PRODUCT_STATUS === "REJECTED";
    return <Panel title="Application progress" className="loan-timeline-panel">
        <State loading={data.loading} error={data.error} retry={data.reload}>
            {account && <div class="loan-timeline-body">
                <ol class="loan-timeline">
                    <li class="is-complete"><strong>Application submitted</strong><span>{account.CREATED_AT ? formatDate(account.CREATED_AT) : "Recorded"}</span></li>
                    <li class={rejected ? "is-rejected" : approved ? "is-complete" : "is-current"}><strong>{rejected ? "Rejected" : approved ? "Approved" : "Pending review"}</strong><span>{account.REVIEWED_AT ? formatDate(account.REVIEWED_AT) : "Awaiting administrator decision"}</span></li>
                    {!rejected && <li class={disbursed ? "is-complete" : ""}><strong>Amount disbursed</strong><span>{disbursed ? (disbursement?.paidAt ? formatDate(disbursement.paidAt) : "Disbursement recorded") : approved ? "Awaiting your acceptance" : "After approval and acceptance"}</span>{disbursed && (disbursement?.amount ?? account.PRINCIPAL_AMOUNT) != null && <b>{formatMoney(disbursement?.amount ?? account.PRINCIPAL_AMOUNT!, product.currencyCode || "INR")}</b>}</li>}
                </ol>
                {account.REVIEW_REASON && <p><strong>Decision note:</strong> {account.REVIEW_REASON}</p>}
            </div>}
        </State>
    </Panel>;
}
