import { t } from "../../services/locale";
import { DemoAction, DemoHistory } from "./Showcase";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { useRef, useState } from "preact/hooks";
import { bankApi, BankAccount, PreparedAction } from "./api";
import { PageHeading, Panel, State, Status, Detail, Modal, useLoad } from "./ui";
import { productTitle } from "./Products";
import { formatMoney, safeMask } from "../../services/banking-content";
import { validAmount } from "./utils";
export function PaymentsPage({ token, accounts }: {
    token: string;
    accounts: BankAccount[];
}) {
    const [targetPage, setTargetPage] = useState(0);
    const [operation, setOperation] = useState("START_TRANSFER");
    const [account, setAccount] = useState(accounts.find(a => a.status === "ACTIVE")?.id || "");
    const [target, setTarget] = useState("");
    const [amount, setAmount] = useState("");
    const [result, setResult] = useState<PreparedAction>();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const lock = useRef(false);
    useNavigationGuard(!!(target || amount) && !result, busy);
    const kind = operation === "START_TRANSFER" ? "beneficiaries" : operation === "PAY_BILL" ? "bills" : operation === "PAY_CARD" ? "cards" : "mandates";
    const targets = useLoad(() => bankApi.products(token, kind, targetPage), [token, kind, targetPage]);
    async function prepare(e: Event) { e.preventDefault(); if (lock.current || !account || !target || (operation !== "CANCEL_MANDATE" && !validAmount(amount)))
        return; lock.current = true; setBusy(true); setError(""); try {
        setResult(await bankApi.prepare(token, { operation, accountId: account, targetId: target, ...(operation !== "CANCEL_MANDATE" ? { amount } : {}) }));
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "We could not review this payment.");
    }
    finally {
        lock.current = false;
        setBusy(false);
    } }
    return <><PageHeading title={t("Payments")} action={<a class="bank-button" href="#/send-money">{t("Send money")}</a>}/><div class="bank-detail-grid"><Panel title={t("Review a payment")}><form class="bank-form" onSubmit={prepare}><label>{t("What would you like to review?")}<select value={operation} disabled={busy} onChange={e => { setOperation(e.currentTarget.value); setTargetPage(0); setTarget(""); setResult(undefined); }}><option value="START_TRANSFER">{t("Transfer to a payee")}</option><option value="PAY_BILL">{t("Bill payment")}</option><option value="PAY_CARD">{t("Credit card payment")}</option><option value="CANCEL_MANDATE">{t("Cancel a direct debit")}</option></select></label><label>{t("From account")}<select required disabled={busy} value={account} onChange={e => setAccount(e.currentTarget.value)}><option value="">{t("Select an account")}</option>{accounts.filter(a => a.status === "ACTIVE").map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label><State loading={targets.loading} error={targets.error} retry={targets.reload}><label>{operation === "START_TRANSFER" ? "Payee" : "Bill, card or direct debit"}<select required disabled={busy} value={target} onChange={e => setTarget(e.currentTarget.value)}><option value="">{t("Select a record")}</option>{targets.data?.map(p => <option value={p.id}>{productTitle(p)} · {p.status.toLowerCase()}</option>)}</select></label>{!targets.data?.length && <p>{t("There is nothing to choose here yet. Contact your bank if you expected to see an item.")}</p>}{kind !== "beneficiaries" && <div class="bank-pagination"><span>{t("Records page")} {targetPage + 1}</span><button type="button" disabled={busy || targetPage === 0} onClick={() => { setTarget(""); setTargetPage(p => p - 1); }}>{t("Previous records")}</button><button type="button" disabled={busy || (targets.data?.length || 0) < 12} onClick={() => { setTarget(""); setTargetPage(p => p + 1); }}>{t("Next records")}</button></div>}</State>{operation !== "CANCEL_MANDATE" && <label>{t("Amount (INR)")}<input required inputMode="decimal" pattern="(?:0|[1-9][0-9]{0,12})(?:\.[0-9]{1,2})?" placeholder="0.00" value={amount} disabled={busy} onInput={e => setAmount(e.currentTarget.value)}/><small>{t("Enter an amount above ₹0, for example 100 or 100.50.")}</small></label>}{account && target && (operation === "CANCEL_MANDATE" || validAmount(amount)) && <section class="bank-confirm-summary" aria-label={t("Check these details")}><h3>{t("Check these details")}</h3><dl><Detail label={t("From account")}>{accounts.find(a => a.id === account)?.accountNumberMasked}</Detail><Detail label={t("Recipient")}>{targets.data?.find(p => p.id === target) && productTitle(targets.data.find(p => p.id === target)!)}</Detail>{operation !== "CANCEL_MANDATE" && <Detail label={t("Amount")}>{formatMoney(amount)}</Detail>}</dl></section>}{error && <p role="alert" class="bank-error">{error}</p>}<button class="bank-button" disabled={busy || !account || !target || (operation !== "CANCEL_MANDATE" && !validAmount(amount))}>{busy ? "Checking details…" : "Check payment details"}</button></form></Panel><Panel title={t("Payments")}><div class="bank-form"><a href="#/beneficiaries">{t("View saved payees ↗")}</a><a href="#/scheduled-payments">{t("View scheduled payments ↗")}</a></div></Panel></div>{result && <Modal title={t("Payment details")} onClose={() => setResult(undefined)}><div class="bank-form"><Status value={result.status}/><dl><Detail label={t("From account")}>{accounts.find(a => a.id === result.accountId)?.accountNumberMasked}</Detail><Detail label={t("Recipient")}>{targets.data?.find(p => p.id === result.targetId) && productTitle(targets.data.find(p => p.id === result.targetId)!)}</Detail>{result.amount && <Detail label={t("Amount")}>{formatMoney(result.amount, result.currencyCode)}</Detail>}</dl><DemoAction token={token} request={{operation: result.operation, accountId: result.accountId, targetId: result.targetId, ...(result.amount ? {amount: result.amount} : {})}} label={t("Continue")}/><button class="bank-button" onClick={() => { setAmount(""); setTarget(""); setResult(undefined); }}>{t("Back to payments")}</button></div></Modal>}<DemoHistory token={token}/></>;
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
