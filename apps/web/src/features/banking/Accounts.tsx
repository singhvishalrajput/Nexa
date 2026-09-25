import { SensitiveNumber } from "./SensitiveNumber";
import "ojs/ojcollapsible";
import { ActivitySummary, TransactionList } from "./Activity";
import { t } from "../../services/locale";
import { useNavigationGuard, confirmNavigation } from "../../hooks/useNavigationGuard";
import { useRef, useState } from "preact/hooks";
import { bankApi, BankAccount, BankTransaction, TransactionPage, Filters } from "./api";
import { PageHeading, Panel, State, Status, Detail, Modal, useLoad } from "./ui";
import { formatMoney, formatDate, humanize } from "../../services/banking-content";
import { openAccount } from "../../services/banking";
import { go, sumMoney } from "./utils";
export function AccountTile({ account }: {
    account: BankAccount;
}) { return <section class="bank-account-tile"><div><span class="bank-tile-symbol" aria-hidden="true">▤</span><Status value={account.status}/></div><h3><a href={"#/accounts/" + encodeURIComponent(account.id)}>{account.displayName}</a></h3><span>{account.accountType.toLowerCase()} · <SensitiveNumber id={account.id} masked={account.accountNumberMasked}/></span><strong>{formatMoney(account.availableBalance, account.currencyCode)}</strong><footer>{t("Available balance")}<span aria-hidden="true">↗</span></footer></section>; }
export function Overview({ token, name, accounts, reload }: {
    token: string;
    name: string;
    accounts: BankAccount[];
    reload: () => void;
}) {
    const [selected, setSelected] = useState(accounts[0]?.id || "");
    const [opening, setOpening] = useState(false);
    const account = accounts.find(a => a.id === selected) || accounts[0];
    const recent = useLoad(() => account ? bankApi.transactions(token, account.id) : Promise.resolve(null), [token, account?.id]);
    return <><PageHeading eyebrow="A CLEARER VIEW OF YOUR MONEY" title={"Welcome back, " + name.split(" ")[0] + "."} description={t("Your accounts, activity and next steps. All in one place.")} action={<button class="bank-button" onClick={() => setOpening(true)}>{t("＋ Open an account")}</button>}/>
 <div class="bank-overview-grid"><section class="bank-balance-hero"><div><span class="bank-eyebrow">{t("TOTAL AVAILABLE BALANCE")}</span><span class="bank-live"><i />{t("Connected accounts")}</span></div><strong>{formatMoney(sumMoney(accounts.map(a => a.availableBalance)))}</strong><p>{t("Across")} {accounts.length} {accounts.length === 1 ? "account" : "accounts"} · INR</p><footer><a class="bank-button bank-white" href="#/accounts">{t("View accounts")}<span>↗</span></a><a href="#/send-money">{t("Send money →")}</a></footer></section>
 <Panel className="bank-next-step"><span class="bank-tile-symbol">✧</span><p class="bank-eyebrow">{t("BANKING, MADE SIMPLE")}</p><h2>{t("Start with a conversation.")}</h2><p>{t("Type or speak to check your balance, see recent payments, or get help. English and Hindi voice available.")}</p><a class="bank-button" href="#/assistant">{t("Ask Nexa")}<span>↗</span></a></Panel></div>
 <div class="bank-section-title"><h2>{t("Your accounts")}<span>{accounts.length}</span></h2><a href="#/accounts">{t("View all accounts ↗")}</a></div><div class="bank-account-grid bank-overview-accounts">{accounts.slice(0, 3).map(a => <AccountTile key={a.id} account={a}/>)}{!accounts.length && <Panel><State empty={t("Your first account starts here")}/><button class="bank-button" onClick={() => setOpening(true)}>{t("Open a bank account")}</button></Panel>}</div>
 <div class="bank-activity-grid"><Panel title={t("Recent activity")} action={<a href={"#/transactions?account=" + encodeURIComponent(account?.id || "")}>{t("View history ↗")}</a>}><div class="bank-inline-filter"><label>{t("Account")}<select value={account?.id || ""} onChange={e => setSelected(e.currentTarget.value)} disabled={!accounts.length}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label></div><State loading={recent.loading} error={recent.error} retry={recent.reload} empty={!recent.loading && !recent.error && !recent.data?.content.length ? "No transactions yet" : undefined}>{recent.data && <TransactionList items={recent.data.content.slice(0, 5)} compact/>}</State></Panel>
 <Panel title={t("Activity at a glance")} className="activity-summary"><State loading={recent.loading} error={recent.error} retry={recent.reload}>{account && recent.data ? <ActivitySummary items={recent.data.content} currency={account.currencyCode} accountName={account.displayName}/> : <p class="activity-summary-empty">{t("Choose an account to see its activity.")}</p>}</State></Panel></div>
 {opening && <OpenAccount token={token} close={() => setOpening(false)} done={() => { setOpening(false); reload(); }}/>}</>;
}
export function AccountsPage({ token, id, accounts, reload }: {
    token: string;
    id?: string;
    accounts: BankAccount[];
    reload: () => void;
}) {
    const [opening, setOpening] = useState(false);
    const detail = useLoad(() => id ? bankApi.account(token, id) : Promise.resolve(null), [token, id]);
    return <><PageHeading title={id ? "Account details" : t("Your accounts")} description={t("A clear view of every account you hold with Nexa.")} action={!id && <button class="bank-button" onClick={() => setOpening(true)}>{t("＋ Open account")}</button>}/>{id ? <State loading={detail.loading} error={detail.error} retry={detail.reload}>{detail.data && <div class="bank-detail-grid"><AccountTile account={detail.data}/><Panel title={t("Account information")}><dl><Detail label={t("Account name")}>{detail.data.displayName}</Detail><Detail label={t("Account number")}><SensitiveNumber id={id!} masked={detail.data.accountNumberMasked}/></Detail><Detail label={t("Status")}><Status value={detail.data.status}/></Detail><Detail label={t("Ledger balance")}>{formatMoney(detail.data.ledgerBalance, detail.data.currencyCode)}</Detail><Detail label={t("Last updated")}>{detail.data.updatedAt ? formatDate(detail.data.updatedAt, true) : "Not available"}</Detail></dl><a class="bank-button" href={"#/transactions?account=" + encodeURIComponent(id)}>{t("View transactions")}</a></Panel></div>}</State> : <div class="bank-account-grid">{accounts.map(a => <AccountTile account={a} key={a.id}/>)}{!accounts.length && <State empty={t("No bank accounts yet")}/>}</div>}{opening && <OpenAccount token={token} close={() => setOpening(false)} done={() => { setOpening(false); reload(); }}/>}</>;
}
function OpenAccount({ token, close, done }: {
    token: string;
    close: () => void;
    done: () => void;
}) {
    const [name, setName] = useState("");
    const [type, setType] = useState<"SAVINGS" | "CURRENT">("SAVINGS");
    const [birth, setBirth] = useState("");
    const [address, setAddress] = useState("");
    const [review, setReview] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [success, setSuccess] = useState<BankAccount>();
    const [uncertain, setUncertain] = useState(false);
    const lock = useRef(false);
    useNavigationGuard(!success && !!(name || birth || address), busy);
    async function confirm() { if (lock.current)
        return; lock.current = true; setBusy(true); setError(""); try {
        setSuccess(await openAccount(token, { displayName: name.trim(), accountType: type, dateOfBirth: birth, address: address.trim() }));
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "Account opening failed.");
        if ((e as {
            status: number;
        }).status === 0 || (e as {
            status: number;
        }).status >= 500)
            setUncertain(true);
    }
    finally {
        setBusy(false);
        lock.current = false;
    } }
    return <Modal title={success ? "Your account is ready" : review ? "Review your new account" : t("Open a bank account")} onClose={success ? done : () => { if (confirmNavigation()) close(); }} locked={busy}>{success ? <div class="bank-form"><span class="bank-success-mark">✓</span><h3>{success.displayName}</h3><p>{t("Account")} <SensitiveNumber id={success.id} masked={success.accountNumberMasked}/> {t("has been opened.")}</p><strong>{formatMoney(success.availableBalance)} {t("available")}</strong><button class="bank-button" onClick={done}>{t("Done")}</button></div> : review ? <div class="bank-form"><dl><Detail label={t("Account name")}>{name}</Detail><Detail label={t("Account type")}>{type}</Detail><Detail label={t("Date of birth")}>{formatDate(birth)}</Detail><Detail label={t("Address")}>{address}</Detail><Detail label={t("Opening balance")}>{formatMoney(0)}</Detail><Detail label={t("Currency")}>INR</Detail></dl><p>{t("Your account starts with a zero balance. Please confirm the details before opening.")}</p>{error && <p class="bank-error" role="alert">{error}</p>}<div class="bank-form-actions"><button class="bank-button secondary" disabled={busy} onClick={() => uncertain ? done() : setReview(false)}>{uncertain ? "Check my accounts" : "Back"}</button><button class="bank-button" disabled={busy || uncertain} onClick={confirm}>{busy ? "Opening…" : "Confirm and open"}</button></div></div> : <form class="bank-form" onSubmit={e => { e.preventDefault(); setReview(true); }}><p>{t("Complete your customer details to open a zero-balance account.")}</p><label>{t("Account name")}<input required maxLength={100} value={name} onInput={e => setName(e.currentTarget.value)} placeholder={t("e.g. Everyday savings")}/></label><label>{t("Account type")}<select value={type} onChange={e => setType(e.currentTarget.value as typeof type)}><option value="SAVINGS">{t("Savings")}</option><option value="CURRENT">{t("Current")}</option></select></label><label>{t("Date of birth")}<input type="date" required max={new Date(Date.now() - 86400000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)} value={birth} onInput={e => setBirth(e.currentTarget.value)}/></label><label>{t("Address")}<textarea required maxLength={255} value={address} onInput={e => setAddress(e.currentTarget.value)}/></label><button class="bank-button" disabled={!name.trim() || !address.trim()}>{t("Review account")}</button></form>}</Modal>;
}
export function TransactionsPage({ token, id, accounts, initialAccount }: {
    token: string;
    id?: string;
    accounts: BankAccount[];
    initialAccount?: string;
}) {
    const [accountId, setAccountId] = useState(accounts.some(a => a.id === initialAccount) ? initialAccount! : accounts[0]?.id || "");
    const [draft, setDraft] = useState<Filters>({});
    const [filters, setFilters] = useState<Filters>({ page: 0 });
    const [validation, setValidation] = useState("");
    const [advancedFilters, setAdvancedFilters] = useState(false);
    const activeFilterCount = [filters.direction, filters.from, filters.to, filters.category].filter(Boolean).length;
    const data = useLoad<BankTransaction | TransactionPage | null>(() => id ? bankApi.transaction(token, id) : accountId ? bankApi.transactions(token, accountId, filters) : Promise.resolve(null), [token, id, accountId, JSON.stringify(filters)]);
    const detail = data.data && "reference" in data.data ? data.data : null;
    const page = data.data && "content" in data.data ? data.data : null;
    return <><PageHeading title={id ? t("Transaction details") : "Transaction history"} description={id ? "The recorded details of your transaction." : "Find and review activity across your accounts."}/>{!id && <form class="bank-filters" onSubmit={e => { e.preventDefault(); if (draft.from && draft.to && draft.from > draft.to) {
        setValidation("The end date must be on or after the start date.");
        setAdvancedFilters(true);
        return;
    } setValidation(""); setFilters({ ...draft, page: 0 }); }}><label>{t("Account")}<select value={accountId} onChange={e => { setAccountId(e.currentTarget.value); setFilters({ ...filters, page: 0 }); }}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label><label class="bank-search-label">{t("Search")}<input type="search" maxLength={100} placeholder={t("Merchant or reference")} value={draft.search || ""} onInput={e => setDraft({ ...draft, search: e.currentTarget.value })}/></label><oj-collapsible class="activity-advanced-filters" expanded={advancedFilters} onexpandedChanged={event => setAdvancedFilters(event.detail.value)}><span slot="header">{t("More filters")}{activeFilterCount > 0 && <span class="activity-filter-count">{activeFilterCount} {t("active")}</span>}</span><div class="activity-filter-fields"><label>{t("Direction")}<select value={draft.direction || ""} onChange={e => setDraft({ ...draft, direction: e.currentTarget.value })}><option value="">{t("All activity")}</option><option value="CREDIT">{t("Money in")}</option><option value="DEBIT">{t("Money out")}</option></select></label><label>{t("From")}<input type="date" value={draft.from || ""} onInput={e => setDraft({ ...draft, from: e.currentTarget.value })}/></label><label>{t("To")}<input type="date" value={draft.to || ""} onInput={e => setDraft({ ...draft, to: e.currentTarget.value })}/></label><label>{t("Category")}<input maxLength={80} value={draft.category || ""} onInput={e => setDraft({ ...draft, category: e.currentTarget.value })} placeholder={t("Any category")}/></label></div></oj-collapsible><button class="bank-button">{t("Apply filters")}</button><button type="button" class="bank-button secondary" onClick={() => { setDraft({}); setFilters({ page: 0 }); setValidation(""); setAdvancedFilters(false); }}>{t("Reset")}</button>{validation && <p role="alert" class="bank-error">{validation}</p>}</form>}
 <Panel><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !id && !page?.content.length ? "No transactions match your selection" : undefined}>{detail && <div class="bank-transaction-detail"><span class="bank-tile-symbol">{detail.amount < 0 ? "↗" : "↙"}</span><h2>{detail.merchantName || detail.type}</h2><strong>{formatMoney(detail.amount, detail.currencyCode, true)}</strong><Status value={detail.status}/><dl><Detail label={t("Reference")}>{detail.reference}</Detail><Detail label={t("Transaction type")}>{humanize(detail.type)}</Detail><Detail label={t("Date and time")}>{formatDate(detail.occurredAt, true)}</Detail><Detail label={t("Category")}>{detail.category}</Detail><Detail label={t("Account")}><SensitiveNumber id={detail.accountId} masked={accounts.find(a => a.id === detail.accountId)?.accountNumberMasked}/></Detail></dl><button class="bank-button secondary" onClick={() => go("transactions?account=" + encodeURIComponent(detail.accountId))}>{t("Back to history")}</button></div>}{page && <><TransactionList items={page.content}/><div class="bank-pagination"><span>{page.totalElements} {page.totalElements === 1 ? "transaction" : "transactions"} {t("· Page")} {page.page + 1} {t("of")} {Math.max(page.totalPages, 1)}</span><button disabled={page.page === 0} onClick={() => setFilters({ ...filters, page: page.page - 1 })}>{t("← Previous")}</button><button disabled={page.page + 1 >= page.totalPages} onClick={() => setFilters({ ...filters, page: page.page + 1 })}>{t("Next →")}</button></div></>}</State></Panel></>;
}
