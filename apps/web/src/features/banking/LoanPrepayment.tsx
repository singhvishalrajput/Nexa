import { useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { formatDate, formatMoney } from "../../services/banking-content";
import { Panel } from "./ui";

export type PrepaymentOption = "REDUCE_TENURE" | "REDUCE_EMI";
export type PrepaymentResult = {
    option: PrepaymentOption; available: boolean; unavailableReason: string | null;
    regularEmi: number; finalEmi: number; remainingInstallments: number;
    finalDueDate: string | null; futureInterest: number; futureRepayment: number;
    interestSaved: number; installmentsSaved: number;
    schedule: { installmentNumber: number; dueDate: string; totalAmount: number; principalAmount: number; interestAmount: number }[];
};
export type RepaymentPreview = {
    previewToken: string; paymentAmount: number; interestAmount: number; principalAmount: number;
    extraPrincipalAmount: number; remainingPrincipal: number; annualInterestRate: number;
    currentEmi: number; baselineInstallments: number; baselineFinalDueDate: string | null;
    baselineFutureInterest: number; closesLoan: boolean; options: PrepaymentResult[];
};
export type PrepaymentLimits = {
    minimumAmount: number; maximumAmount: number; principalOnly: boolean;
    regularEmi: number | null; minimumExtraPrincipal?: number | null;
};
const paise = (value: string | number) => Math.round(Number(value) * 100);
export function isPartialPrepayment(amount: string, limits: PrepaymentLimits): boolean {
    return limits.regularEmi != null && paise(amount) < paise(limits.maximumAmount)
        && (limits.principalOnly || paise(amount) > paise(limits.minimumAmount));
}
export function validRepayment(amount: string, limits: PrepaymentLimits): boolean {
    if (!/^\d+(\.\d{1,2})?$/.test(amount) || !Number.isFinite(Number(amount)) || Number(amount) <= 0) return false;
    const paid = paise(amount);
    if (paid < paise(limits.minimumAmount) || paid > paise(limits.maximumAmount)) return false;
    return limits.principalOnly || limits.regularEmi == null
        || paid === paise(limits.minimumAmount) || paid === paise(limits.maximumAmount)
        || paid - paise(limits.minimumAmount) >= paise(limits.minimumExtraPrincipal ?? limits.regularEmi);
}
export const prepaymentLabel = (option: PrepaymentOption) => option === "REDUCE_TENURE" ? "Keep EMI · finish earlier" : "Reduce EMI · keep end date";

export function PrepaymentComparison({ preview, selected, onSelect, disabled = false }: {
    preview: RepaymentPreview; selected?: PrepaymentOption;
    onSelect?: (option: PrepaymentOption) => void; disabled?: boolean;
}) {
    const money = (value: number) => formatMoney(value, "INR");
    const shorter = preview.options.find(o => o.option === "REDUCE_TENURE" && o.available);
    const smaller = preview.options.find(o => o.option === "REDUCE_EMI" && o.available);
    return <div class="loan-prepayment-results">
        <div class="loan-prepayment-summary">
            <span>Fixed annual rate <strong>{preview.annualInterestRate}%</strong></span>
            <span>Principal after payment <strong>{money(preview.remainingPrincipal)}</strong></span>
            <span>Extra principal <strong>{money(preview.extraPrincipalAmount)}</strong></span>
        </div>
        {preview.closesLoan ? <p role="status">This payment closes the loan. No future EMI or tenure choice is needed.</p> : <>
            <p>Without the extra principal: {preview.baselineInstallments} future installments, ending {preview.baselineFinalDueDate ? formatDate(preview.baselineFinalDueDate) : "—"}, with {money(preview.baselineFutureInterest)} future interest.</p>
            {onSelect && <p id="prepayment-choice-help">Choose how your extra principal should change the remaining schedule.</p>}
            <div class="loan-prepayment-options" role={onSelect ? "radiogroup" : undefined} aria-label={onSelect ? "Prepayment preference" : undefined}>
                {preview.options.map(option => <article key={option.option} class={"loan-prepayment-option" + (selected === option.option ? " is-selected" : "")}>
                    {onSelect ? <label class={"loan-prepayment-choice" + (disabled || !option.available ? " is-disabled" : "")}>
                        <input type="radio" name="prepayment-preference" value={option.option} checked={selected === option.option} disabled={disabled || !option.available} onChange={() => onSelect(option.option)}/>
                        <span class="loan-prepayment-choice-copy">
                            <span class="loan-prepayment-choice-title"><strong>{option.option === "REDUCE_TENURE" ? "Keep EMI" : "Reduce EMI"}</strong>{selected === option.option && <span class="loan-prepayment-selected" aria-hidden="true">Selected</span>}</span>
                            <span class="loan-prepayment-choice-description">{option.option === "REDUCE_TENURE" ? "Finish earlier · same monthly payment" : "Pay less monthly · keep the end date"}</span>
                        </span>
                    </label> : <h3>{prepaymentLabel(option.option)}</h3>}
                    {option.available ? <>
                        <div class="loan-prepayment-emi"><strong>{money(option.regularEmi)}</strong><span>regular monthly EMI</span></div>
                        <dl class="loan-interest-breakdown">
                            <div><dt>Future installments</dt><dd>{option.remainingInstallments}</dd></div>
                            <div><dt>Projected finish</dt><dd>{option.finalDueDate ? formatDate(option.finalDueDate) : "Loan closed"}</dd></div>
                            <div><dt>Future interest</dt><dd>{money(option.futureInterest)}</dd></div>
                            <div><dt>Interest saved</dt><dd>{money(option.interestSaved)}</dd></div>
                            <div><dt>Installments removed</dt><dd>{option.installmentsSaved}</dd></div>
                            <div><dt>Final installment</dt><dd>{money(option.finalEmi)}</dd></div>
                        </dl>
                        <p>{option.option === "REDUCE_TENURE" ? "For finishing sooner while keeping your monthly budget unchanged." : "For more breathing room each month without extending the current end date."}</p>
                        <details><summary>See the calculation and schedule</summary>
                            <p>Monthly interest = opening principal × {preview.annualInterestRate}% ÷ 12. Principal repaid = installment − interest.</p>
                            <p>{option.option === "REDUCE_EMI" ? "New EMI = P × r × (1 + r)ⁿ ÷ ((1 + r)ⁿ − 1), where P is the reduced balance, r is annual rate ÷ 1200, and n is the remaining installment count. At 0% interest, EMI = P ÷ n." : "The regular EMI stays unchanged. We subtract each installment’s principal until the balance reaches zero."}</p>
                            <div class="loan-schedule-scroll"><table><caption>Projected unpaid schedule</caption><thead><tr><th>Due date</th><th>Principal</th><th>Interest</th><th>Payment</th></tr></thead><tbody>{option.schedule.map(row => <tr key={row.installmentNumber}><td>{formatDate(row.dueDate)}</td><td>{money(row.principalAmount)}</td><td>{money(row.interestAmount)}</td><td>{money(row.totalAmount)}</td></tr>)}</tbody></table></div>
                        </details>
                    </> : <p>{option.unavailableReason}</p>}
                </article>)}
            </div>
        </>}
        {!preview.closesLoan && shorter && smaller && <p class="loan-prepayment-decision">Keeping EMI saves {money((paise(smaller.futureInterest) - paise(shorter.futureInterest)) / 100)} more in projected interest than reducing EMI. Reducing EMI lowers your regular monthly payment by {money((paise(preview.currentEmi) - paise(smaller.regularEmi)) / 100)}. Choose based on whether interest savings or monthly breathing room matters more to you.</p>}
        <p class="loan-calculation-note">Savings compare future scheduled interest with and without the extra principal; interest in this payment is excluded from both. Monthly projections rounded to paise, not daily interest accrual. The annual rate does not change.</p>
    </div>;
}

export function PrepaymentCalculator({ token, loanId, limits, revision }: {
    token: string; loanId: string; limits: PrepaymentLimits; revision: string;
}) {
    const [amount, setAmount] = useState(""), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const [result, setResult] = useState<{ revision: string; amount: string; preview: RepaymentPreview } | null>(null);
    const inFlight = useRef(false);
    const preview = result?.revision === revision && result.amount === amount ? result.preview : null;
    const valid = validRepayment(amount, limits) && (isPartialPrepayment(amount, limits) || paise(amount) === paise(limits.maximumAmount));
    async function calculate(event: Event) {
        event.preventDefault();
        if (inFlight.current || !valid) return;
        inFlight.current = true; setBusy(true); setError(""); setResult(null);
        try {
            const preview = await authenticatedRequest<RepaymentPreview>("/loans/" + encodeURIComponent(loanId) + "/repayment-preview", token, { method: "POST", body: JSON.stringify({ amount }) });
            setResult({ revision, amount, preview });
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to compare these options."); }
        finally { inFlight.current = false; setBusy(false); }
    }
    return <Panel title="Prepayment calculator · compare your options" className="loan-prepayment-panel">
        <div class="loan-prepayment-body">
            <p>Explore both outcomes using your actual loan balance. This calculator does not move money.</p>
            <form class="loan-prepayment-form" onSubmit={calculate}>
                <label>{limits.principalOnly ? "Extra principal payment (INR)" : "Total payment including the next EMI (INR)"}<input type="number" step="0.01" min={limits.minimumAmount} max={limits.maximumAmount} required value={amount} disabled={busy} onInput={e => { setAmount(e.currentTarget.value); setResult(null); setError(""); }}/></label>
                <button class="bank-button" type="submit" disabled={busy || !valid}>{busy ? "Comparing…" : "Compare both options"}</button>
            </form>
            <p class="loan-calculation-note">{!limits.principalOnly && <>The next EMI is settled first. </>}Extra principal must be at least {formatMoney(limits.minimumExtraPrincipal ?? limits.regularEmi ?? 0)}, or pay the smaller full payoff. No EMI-multiple restriction.</p>
            {error && <p role="alert">{error}</p>}
            {preview && <PrepaymentComparison preview={preview}/>}
        </div>
    </Panel>;
}
