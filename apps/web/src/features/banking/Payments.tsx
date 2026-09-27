import { t } from "../../services/locale";
import { confirmNavigation, useNavigationGuard } from "../../hooks/useNavigationGuard";
import { useRef, useState } from "preact/hooks";
import { bankApi } from "./api";
import { PageHeading, Panel, State, Status, Detail, Modal, useLoad } from "./ui";
import { formatMoney, safeMask } from "../../services/banking-content";
import { validAmount } from "./utils";
import { BankingIcon, BankingIconName } from "../../components/BankingIcon";
import { MoneyTransfer } from "./MoneyTransfer";
import { ExternalTransfer } from "./ExternalTransfer";

export type PaymentDestination = "nexa" | "other-bank";
const paymentShortcuts: {href: string; label: string; description: string; icon: BankingIconName}[] = [
    {href: "#/bills", label: "Bills", description: "View a bill and pay its outstanding amount.", icon: "transactions"},
    {href: "#/beneficiaries", label: "Saved payees", description: "View and manage your recipients.", icon: "people"},
    {href: "#/scheduled-payments", label: "Scheduled payments", description: "View your upcoming payments.", icon: "clock"},
    {href: "#/cards", label: "Cards", description: "Review your card and payment details.", icon: "cards"},
    {href: "#/mandates", label: "Direct debits", description: "View and manage your payment mandates.", icon: "repeat"}
];

export function PaymentsPage({ token, userId, initialDestination = "nexa", initialPayee }: {
    token: string;
    userId: string;
    initialDestination?: PaymentDestination;
    initialPayee?: string;
}) {
    const [destination, setDestination] = useState<PaymentDestination>(initialDestination);
    function changeDestination(event: Event & {currentTarget: HTMLSelectElement}) {
        const next = event.currentTarget.value as PaymentDestination;
        if (next === destination) return;
        if (!confirmNavigation()) { event.currentTarget.value = destination; return; }
        setDestination(next);
    }
    return <div class="bank-service-page bank-payments-page">
      <PageHeading title={t("Payments")} description={t("Transfer money, pay bills and manage your upcoming payments.")}/>
      <div class="bank-payment-destination">
        <label>{t("Transfer destination")}<select value={destination} onChange={changeDestination} aria-describedby="payment-destination-help">
          <option value="nexa">{t("Nexa account")}</option><option value="other-bank">{t("Other bank")}</option>
        </select></label>
        <p id="payment-destination-help">{destination === "nexa" ? t("Send to another Nexa customer or move money between your own accounts.") : t("Use a saved payee’s bank account number and IFSC.")}</p>
      </div>
      <div class="bank-payments-layout">
        <div class="bank-payment-transfer">
          {destination === "nexa" ? <MoneyTransfer token={token} userId={userId} embedded/>
            : <ExternalTransfer token={token} userId={userId} initialPayee={initialPayee} embedded/>}
        </div>
        <Panel title={t("Payment shortcuts")} className="bank-payment-shortcuts">
          <nav class="bank-payment-links" aria-label={t("Payment shortcuts")}>{paymentShortcuts.map(item => <a href={item.href} key={item.href}>
            <span class="bank-payment-link-icon"><BankingIcon name={item.icon}/></span>
            <span class="bank-payment-link-copy"><strong>{t(item.label)}</strong><small>{t(item.description)}</small></span>
            <BankingIcon name="arrow"/>
          </a>)}</nav>
        </Panel>
      </div>
    </div>;
}
export function OperationsPage({ token }: {
    token: string;
}) {
    const data = useLoad(() => bankApi.coreAccounts(token), [token]);
    const [operation, setOperation] = useState<"deposit" | "withdraw" | "transfer">("transfer");
    const [source, setSource] = useState("");
    const [destination, setDestination] = useState("");
    const [amount, setAmount] = useState("");
    const [review, setReview] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [uncertain, setUncertain] = useState(false);
    const [receipt, setReceipt] = useState<{
        id: string;
        status: string;
        amount: number;
    }>();
    const lock = useRef(false);
    useNavigationGuard(!!amount && !receipt, busy);
    const accounts = data.data?.filter(a => a.status === "ACTIVE" && a.accountCategory === "CUSTOMER") || [];
    const label = (id: string) => { const a = accounts.find(a => String(a.id) === id); return a ? a.accountName + " · " + safeMask(a.accountNumber) : "—"; };
    async function execute() { if (lock.current || uncertain || !validAmount(amount) || (operation !== "deposit" && !source) || (operation !== "withdraw" && !destination) || (operation === "transfer" && source === destination))
        return; lock.current = true; setBusy(true); setError(""); try {
        setReceipt(await bankApi.postTransaction(token, operation, { amount, ...(operation !== "deposit" ? { sourceAccountId: Number(source) } : {}), ...(operation !== "withdraw" ? { destinationAccountId: Number(destination) } : {}) }));
        data.reload();
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "The transaction failed.");
        if ((e as {
            status: number;
        }).status === 0 || (e as {
            status: number;
        }).status >= 500)
            setUncertain(true);
    }
    finally {
        lock.current = false;
        setBusy(false);
    } }
    return <><PageHeading eyebrow="ADMINISTRATOR WORKSPACE" title={t("Banking operations")} description={t("Post a deposit, withdrawal or internal account transfer.")}/><div class="bank-notice"><p>{t("These actions move account balances immediately. Verify the account details and amount before confirming.")}</p></div><State loading={data.loading} error={data.error} retry={data.reload}><Panel title={t("New transaction")}><form class="bank-form bank-narrow" onSubmit={e => { e.preventDefault(); setReview(true); setError(""); setReceipt(undefined); }}><label>{t("Operation")}<select value={operation} onChange={e => setOperation(e.currentTarget.value as typeof operation)}><option value="transfer">{t("Internal transfer")}</option><option value="deposit">{t("Deposit")}</option><option value="withdraw">{t("Withdrawal")}</option></select></label>{operation !== "deposit" && <label>{t("Source account")}<select required value={source} onChange={e => setSource(e.currentTarget.value)}><option value="">{t("Select account")}</option>{accounts.map(a => <option value={a.id}>{label(String(a.id))}</option>)}</select></label>}{operation !== "withdraw" && <label>{t("Destination account")}<select required value={destination} onChange={e => setDestination(e.currentTarget.value)}><option value="">{t("Select account")}</option>{accounts.filter(a => operation !== "transfer" || String(a.id) !== source).map(a => <option value={a.id}>{label(String(a.id))}</option>)}</select></label>}<label>{t("Amount (INR)")}<input required inputMode="decimal" value={amount} onInput={e => { setReceipt(undefined); setAmount(e.currentTarget.value); }} placeholder="0.00"/></label><button class="bank-button" disabled={uncertain || !validAmount(amount) || (operation !== "deposit" && !source) || (operation !== "withdraw" && !destination) || (operation === "transfer" && source === destination)}>{t("Review transaction")}</button>{uncertain && <p role="alert">{t("The last result is not confirmed. Check the transaction history before starting another transaction.")}</p>}</form></Panel></State>{review && <Modal title={receipt ? "Transaction posted" : "Confirm " + operation} locked={busy} onClose={() => setReview(false)}><div class="bank-form">{receipt ? <><span class="bank-success-mark">✓</span><Status value={receipt.status}/><strong>{formatMoney(receipt.amount)}</strong><p>{t("Reference:")} {receipt.id}</p><p>{t("Your bank has confirmed this transaction.")}</p><button class="bank-button" onClick={() => { setReview(false); setAmount(""); }}>{t("Back to payments")}</button></> : <><dl>{operation !== "deposit" && <Detail label={t("From")}>{label(source)}</Detail>}{operation !== "withdraw" && <Detail label={t("To")}>{label(destination)}</Detail>}<Detail label={t("Amount")}>{formatMoney(amount)}</Detail></dl><p>{t("This action posts immediately. Only confirm if these details are correct.")}</p>{error && <p role="alert" class="bank-error">{error}</p>}{uncertain ? <p>{t("Do not resubmit. Check the account’s transaction history with your bank before attempting another transaction.")}</p> : null}<div class="bank-form-actions"><button class="bank-button secondary" disabled={busy} onClick={() => setReview(false)}>{t("Cancel")}</button><button class="bank-button" disabled={busy || uncertain} onClick={execute}>{busy ? "Posting…" : "Confirm " + operation}</button></div></>}</div></Modal>}</>;
}
