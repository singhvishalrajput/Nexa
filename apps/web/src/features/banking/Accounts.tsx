import "ojs/ojcollapsible";
import { ActivitySummary, TransactionList } from "./Activity";
import { authenticatedRequest, CustomerProfile } from "../../services/auth";
import { t } from "../../services/locale";
import { CustomerAccountApplications } from "./AccountApplications";
import { useState } from "preact/hooks";
import { bankApi, BankAccount, BankTransaction, TransactionPage, Filters } from "./api";
import { PageHeading, Panel, State, Status, Detail, useLoad } from "./ui";
import { categoryLabel, formatMoney, formatDate, humanize } from "../../services/banking-content";
import { go, sumMoney } from "./utils";
export function AccountTile({ account }: {
    account: BankAccount;
}) { return <a class="bank-account-tile" href={"#/accounts/" + encodeURIComponent(account.id)}><div><span class="bank-tile-symbol" aria-hidden="true">▤</span><Status value={account.status}/></div><h3>{account.displayName}</h3><span>{account.accountType.toLowerCase()} · {account.accountNumberMasked}</span><strong>{formatMoney(account.availableBalance, account.currencyCode)}</strong><footer>{t("Available balance")}<span aria-hidden="true">↗</span></footer></a>; }
export function Overview({ token, name, accounts }: {
    token: string;
    name: string;
    accounts: BankAccount[];
    reload: () => void;
}) {
    const [selected, setSelected] = useState(accounts[0]?.id || "");
    const account = accounts.find(a => a.id === selected) || accounts[0];
    const recent = useLoad(() => account ? bankApi.transactions(token, account.id) : Promise.resolve(null), [token, account?.id]);
    return <><PageHeading eyebrow="A CLEARER VIEW OF YOUR MONEY" title={"Welcome back, " + name.split(" ")[0] + "."} description={t("Your accounts, activity and next steps. All in one place.")} action={<a class="bank-button" href="#/accounts">{t("Accounts and applications")}</a>}/>
 <div class="bank-overview-grid"><section class="bank-balance-hero"><div><span class="bank-eyebrow">{t("TOTAL AVAILABLE BALANCE")}</span><span class="bank-live"><i />{t("Connected accounts")}</span></div><strong>{formatMoney(sumMoney(accounts.map(a => a.availableBalance)))}</strong><p>{t("Across")} {accounts.length} {accounts.length === 1 ? "account" : "accounts"} · INR</p><footer><a class="bank-button bank-white" href="#/accounts">{t("View accounts")}<span>↗</span></a><a href="#/send-money">{t("Send money →")}</a></footer></section>
 <Panel className="bank-next-step"><span class="bank-tile-symbol">✧</span><p class="bank-eyebrow">{t("BANKING, MADE SIMPLE")}</p><h2>{t("Start with a conversation.")}</h2><p>{t("Type or speak to check your balance, see recent payments, or get help. English and Hindi voice available.")}</p><a class="bank-button" href="#/assistant">{t("Ask Nexa")}<span>↗</span></a></Panel></div>
 <div class="bank-section-title"><h2>{t("Your accounts")}<span>{accounts.length}</span></h2><a href="#/accounts">{t("View all accounts ↗")}</a></div><div class="bank-account-grid bank-overview-accounts">{accounts.slice(0, 3).map(a => <AccountTile key={a.id} account={a}/>)}{!accounts.length && <Panel><State empty={t("Your first account starts here")}/><a class="bank-button" href="#/accounts">{t("Open a bank account")}</a></Panel>}</div>
 <div class="bank-activity-grid"><Panel title={t("Recent activity")} action={<a href={"#/transactions?account=" + encodeURIComponent(account?.id || "")}>{t("View history ↗")}</a>}><div class="bank-inline-filter"><label>{t("Account")}<select value={account?.id || ""} onChange={e => setSelected(e.currentTarget.value)} disabled={!accounts.length}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label></div><State loading={recent.loading} error={recent.error} retry={recent.reload} empty={!recent.loading && !recent.error && !recent.data?.content.length ? "No transactions yet" : undefined}>{recent.data && <TransactionList items={recent.data.content.slice(0, 5)} compact/>}</State></Panel>
 <Panel title={t("Activity at a glance")} className="activity-summary"><State loading={recent.loading} error={recent.error} retry={recent.reload}>{account && recent.data ? <ActivitySummary items={recent.data.content} currency={account.currencyCode} accountName={account.displayName}/> : <p class="activity-summary-empty">{t("Choose an account to see its activity.")}</p>}</State></Panel></div>
 </>;
}
export function AccountsPage({ token, profile, id, accounts, reload }: {
    token: string;
    profile: CustomerProfile;
    id?: string;
    accounts: BankAccount[];
    reload: () => void;
}) {
    const detail = useLoad(() => id ? bankApi.account(token, id) : Promise.resolve(null), [token, id]);
    return <div class="accounts-hub"><PageHeading title={id ? "Account details" : t("Your accounts")} description={t("A clear view of every account you hold with Nexa.")}/>
        {id ? <State loading={detail.loading} error={detail.error} retry={detail.reload}>{detail.data && <div class="bank-detail-grid"><AccountTile account={detail.data}/><Panel title={t("Account information")}><dl><Detail label={t("Account name")}>{detail.data.displayName}</Detail><Detail label={t("Account number")}><AccountNumber token={token} id={id} masked={detail.data.accountNumberMasked}/></Detail><Detail label={t("Status")}><Status value={detail.data.status}/></Detail><Detail label={t("Ledger balance")}>{formatMoney(detail.data.ledgerBalance, detail.data.currencyCode)}</Detail><Detail label={t("Last updated")}>{detail.data.updatedAt ? formatDate(detail.data.updatedAt, true) : "Not available"}</Detail></dl><a class="bank-button" href={"#/transactions?account=" + encodeURIComponent(id)}>{t("View transactions")}</a></Panel></div>}</State> : <>
            {accounts.length > 0 && <div class="bank-account-grid">{accounts.map(a => <AccountTile account={a} key={a.id}/>)}</div>}
            <CustomerAccountApplications token={token} profile={profile} accounts={accounts} onAccountsChanged={reload} embedded/>
        </>}
    </div>;
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
    const categories = useLoad(() => !id && accountId ? bankApi.transactionCategories(token, accountId) : Promise.resolve([]), [token, id, accountId]);
    const categoryOptions = categories.data || (draft.category ? [draft.category] : []);
    const detail = data.data && "reference" in data.data ? data.data : null;
    const page = data.data && "content" in data.data ? data.data : null;
    return <><PageHeading title={id ? t("Transaction details") : "Transaction history"} description={id ? "The recorded details of your transaction." : "Find and review activity across your accounts."}/>{!id && <form class="bank-filters transaction-filters" onSubmit={e => { e.preventDefault(); if (draft.from && draft.to && draft.from > draft.to) {
        setValidation("The end date must be on or after the start date.");
        setAdvancedFilters(true);
        return;
    } setValidation(""); setFilters({ ...draft, page: 0 }); }}>
        <label>{t("Account")}<select value={accountId} onChange={e => { setAccountId(e.currentTarget.value); setDraft({ ...draft, category: "" }); setFilters({ ...filters, category: "", page: 0 }); }}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label>
        <label class="bank-search-label">{t("Search transactions")}<input type="search" maxLength={100} placeholder={t("Business name or transaction reference")} aria-describedby="transaction-search-help" value={draft.search || ""} onInput={e => setDraft({ ...draft, search: e.currentTarget.value })}/></label>
        <small id="transaction-search-help" class="activity-filter-help">{t("Enter a shop or biller's name, or paste the reference shown in transaction details.")}</small>
        <oj-collapsible class="activity-advanced-filters" expanded={advancedFilters} onexpandedChanged={event => setAdvancedFilters(event.detail.value)}>
            <span slot="header">{t("More filters")}{activeFilterCount > 0 && <span class="activity-filter-count">{activeFilterCount} {t("active")}</span>}</span>
            <div class="activity-filter-fields">
                <label>{t("Direction")}<select value={draft.direction || ""} onChange={e => setDraft({ ...draft, direction: e.currentTarget.value })}><option value="">{t("All activity")}</option><option value="CREDIT">{t("Money in")}</option><option value="DEBIT">{t("Money out")}</option></select></label>
                <label>{t("From")}<input type="date" value={draft.from || ""} onInput={e => setDraft({ ...draft, from: e.currentTarget.value })}/></label>
                <label>{t("To")}<input type="date" value={draft.to || ""} onInput={e => setDraft({ ...draft, to: e.currentTarget.value })}/></label>
                <label>{t("Category")}<select value={draft.category || ""} disabled={categories.loading || !accountId} aria-describedby="transaction-category-help" onChange={e => setDraft({ ...draft, category: e.currentTarget.value })}><option value="">{t(categories.loading ? "Loading categories…" : "All categories")}</option>{categoryOptions.map(category => <option key={category} value={category}>{categoryLabel(category)}</option>)}</select><small id="transaction-category-help" class="activity-filter-help">{t("Categories recorded for this account.")}</small></label>
            </div>
            {categories.error && <p role="alert" class="activity-category-error">{t("Categories could not be loaded.")} <button type="button" onClick={categories.reload}>{t("Retry categories")}</button></p>}
        </oj-collapsible>
        <button class="bank-button">{t("Apply filters")}</button><button type="button" class="bank-button secondary" onClick={() => { setDraft({}); setFilters({ page: 0 }); setValidation(""); setAdvancedFilters(false); }}>{t("Reset")}</button>{validation && <p role="alert" class="bank-error">{validation}</p>}
    </form>}
 <Panel><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !id && !page?.content.length ? "No transactions match your selection" : undefined}>{detail && <div class="bank-transaction-detail"><span class="bank-tile-symbol">{detail.amount < 0 ? "↗" : "↙"}</span><h2>{detail.merchantName || detail.type}</h2><strong>{formatMoney(detail.amount, detail.currencyCode, true)}</strong><Status value={detail.status}/><dl><Detail label={t("Reference")}>{detail.reference}</Detail><Detail label={t("Transaction type")}>{humanize(detail.type)}</Detail><Detail label={t("Date and time")}>{formatDate(detail.occurredAt, true)}</Detail><Detail label={t("Category")}>{detail.category ? categoryLabel(detail.category) : detail.category}</Detail><Detail label={t("Your Nexa account")}>{accounts.find(a => a.id === detail.accountId)?.accountNumberMasked || "Account " + detail.accountId}</Detail></dl><button class="bank-button secondary" onClick={() => go("transactions?account=" + encodeURIComponent(detail.accountId))}>{t("Back to history")}</button></div>}{page && <><TransactionList items={page.content}/><div class="bank-pagination"><span>{page.totalElements} {page.totalElements === 1 ? "transaction" : "transactions"} {t("· Page")} {page.page + 1} {t("of")} {Math.max(page.totalPages, 1)}</span><button disabled={page.page === 0} onClick={() => setFilters({ ...filters, page: page.page - 1 })}>{t("← Previous")}</button><button disabled={page.page + 1 >= page.totalPages} onClick={() => setFilters({ ...filters, page: page.page + 1 })}>{t("Next →")}</button></div></>}</State></Panel></>;
}

function AccountNumber({token,id,masked}:{token:string;id:string;masked:string}) {
 const [number,setNumber]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState("");
 async function reveal(){setBusy(true);setError("");try{setNumber((await authenticatedRequest<{accountNumber:string}>("/accounts/"+encodeURIComponent(id)+"/number",token)).accountNumber);}catch(e){setError(e instanceof Error?e.message:"Unable to load number");}finally{setBusy(false);}}
 return <span>{number||masked} {!number&&<button disabled={busy} onClick={reveal}>Show full account number</button>}{error&&<small role="alert">{error}</small>}</span>;
}
