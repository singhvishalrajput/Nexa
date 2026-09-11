import { useRef, useState } from "preact/hooks";
import { bankApi, BankAccount, BankTransaction, TransactionPage, Filters } from "./api";
import { PageHeading, Panel, State, Status, Detail, Modal, useLoad } from "./ui";
import { formatMoney, formatDate } from "../../services/banking-content";
import { openAccount } from "../../services/banking";
import { go, sumMoney } from "./utils";
export function AccountTile({ account }: {
    account: BankAccount;
}) { return <a class="bank-account-tile" href={"#/accounts/" + encodeURIComponent(account.id)}><div><span class="bank-tile-symbol" aria-hidden="true">▤</span><Status value={account.status}/></div><h3>{account.displayName}</h3><span>{account.accountType.toLowerCase()} · {account.accountNumberMasked}</span><strong>{formatMoney(account.availableBalance, account.currencyCode)}</strong><footer>Available balance <span aria-hidden="true">↗</span></footer></a>; }
export function TransactionList({ items }: {
    items: BankTransaction[];
}) { return <div class="bank-transactions">{items.map(t => <a key={t.id} class="bank-transaction" href={"#/transactions/" + encodeURIComponent(t.id)}><span class={"bank-transaction-icon " + (t.amount < 0 ? "outgoing" : "incoming")} aria-hidden="true">{t.amount < 0 ? "↗" : "↙"}</span><div class="bank-transaction-name"><strong>{t.merchantName || t.type.replace(/_/g, " ").toLowerCase()}</strong><span>{t.category || t.type.toLowerCase()} · {formatDate(t.occurredAt)}</span></div><Status value={t.status}/><strong class={t.amount > 0 ? "bank-positive" : ""}>{formatMoney(t.amount, t.currencyCode, true)}</strong><span class="bank-row-arrow" aria-hidden="true">↗</span></a>)}</div>; }
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
    const incoming = recent.data?.content.filter(t => t.amount > 0 && ["SUCCESS", "POSTED", "COMPLETED"].includes(t.status)) || [];
    const outgoing = recent.data?.content.filter(t => t.amount < 0 && ["SUCCESS", "POSTED", "COMPLETED"].includes(t.status)) || [];
    return <><PageHeading eyebrow="A CLEARER VIEW OF YOUR MONEY" title={"Welcome back, " + name.split(" ")[0] + "."} description="Your accounts, activity and next steps. All in one place." action={<button class="bank-button" onClick={() => setOpening(true)}>＋ Open an account</button>}/>
 <div class="bank-overview-grid"><section class="bank-balance-hero"><div><span class="bank-eyebrow">TOTAL AVAILABLE BALANCE</span><span class="bank-live"><i />Connected accounts</span></div><strong>{formatMoney(sumMoney(accounts.map(a => a.availableBalance)))}</strong><p>Across {accounts.length} {accounts.length === 1 ? "account" : "accounts"} · INR</p><footer><a class="bank-button bank-white" href="#/accounts">View accounts <span>↗</span></a><a href="#/payments">Review a payment →</a></footer><span class="bank-hero-art" aria-hidden="true">n</span></section>
 <Panel className="bank-next-step"><span class="bank-tile-symbol">✧</span><p class="bank-eyebrow">BANKING, MADE SIMPLE</p><h2>A little clarity goes a long way.</h2><p>Ask about your balance, find a transaction, or explore your upcoming bills.</p><a class="bank-button" href="#/assistant">Ask Nexa <span>↗</span></a></Panel></div>
 <div class="bank-section-title"><h2>Your accounts <span>{accounts.length}</span></h2><a href="#/accounts">View all accounts ↗</a></div><div class="bank-account-grid">{accounts.slice(0, 3).map(a => <AccountTile key={a.id} account={a}/>)}{!accounts.length && <Panel><State empty="Your first account starts here"/><button class="bank-button" onClick={() => setOpening(true)}>Open a bank account</button></Panel>}</div>
 <div class="bank-activity-grid"><Panel title="Recent activity" action={<a href="#/transactions">View history ↗</a>}><div class="bank-inline-filter"><label>Account <select value={account?.id || ""} onChange={e => setSelected(e.currentTarget.value)} disabled={!accounts.length}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label></div><State loading={recent.loading} error={recent.error} retry={recent.reload} empty={!recent.loading && !recent.error && !recent.data?.content.length ? "No transactions yet" : undefined}>{recent.data && <TransactionList items={recent.data.content.slice(0, 5)}/>}</State></Panel>
 <Panel title="Activity at a glance" className="bank-summary"><State loading={recent.loading} error={recent.error} retry={recent.reload}><p>Completed activity in the latest {recent.data?.content.length || 0} transactions for this account.</p><div class="bank-summary-number"><span>Money in</span><strong class="bank-positive">{formatMoney(sumMoney(incoming.map(t => t.amount)))}</strong></div><div class="bank-summary-number"><span>Money out</span><strong>{formatMoney(sumMoney(outgoing.map(t => Math.abs(t.amount))))}</strong></div><hr /><a href="#/scheduled-payments">Scheduled payments <span>↗</span></a><a href="#/bills">Bills and due dates <span>↗</span></a><a href="#/security">Session and security <span>↗</span></a></State></Panel></div>
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
    return <><PageHeading title={id ? "Account details" : "Your accounts"} description="A clear view of every account you hold with Nexa." action={!id && <button class="bank-button" onClick={() => setOpening(true)}>＋ Open account</button>}/>{id ? <State loading={detail.loading} error={detail.error} retry={detail.reload}>{detail.data && <div class="bank-detail-grid"><AccountTile account={detail.data}/><Panel title="Account information"><dl><Detail label="Account name">{detail.data.displayName}</Detail><Detail label="Account number">{detail.data.accountNumberMasked}</Detail><Detail label="Status"><Status value={detail.data.status}/></Detail><Detail label="Ledger balance">{formatMoney(detail.data.ledgerBalance, detail.data.currencyCode)}</Detail><Detail label="Last updated">{detail.data.updatedAt ? formatDate(detail.data.updatedAt, true) : "Not available"}</Detail></dl><a class="bank-button" href={"#/transactions?account=" + encodeURIComponent(id)}>View transactions</a></Panel></div>}</State> : <div class="bank-account-grid">{accounts.map(a => <AccountTile account={a} key={a.id}/>)}{!accounts.length && <State empty="No bank accounts yet"/>}</div>}{opening && <OpenAccount token={token} close={() => setOpening(false)} done={() => { setOpening(false); reload(); }}/>}</>;
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
    return <Modal title={success ? "Your account is ready" : review ? "Review your new account" : "Open a bank account"} onClose={success ? done : close} locked={busy}>{success ? <div class="bank-form"><span class="bank-success-mark">✓</span><h3>{success.displayName}</h3><p>Account {success.accountNumberMasked} has been opened.</p><strong>{formatMoney(success.availableBalance)} available</strong><button class="bank-button" onClick={done}>Done</button></div> : review ? <div class="bank-form"><dl><Detail label="Account name">{name}</Detail><Detail label="Account type">{type}</Detail><Detail label="Opening balance">₹0</Detail><Detail label="Currency">INR</Detail></dl><p>Your account starts with a zero balance. Please confirm the details before opening.</p>{error && <p class="bank-error" role="alert">{error}</p>}<div class="bank-form-actions"><button class="bank-button secondary" disabled={busy} onClick={() => uncertain ? done() : setReview(false)}>{uncertain ? "Check my accounts" : "Back"}</button><button class="bank-button" disabled={busy || uncertain} onClick={confirm}>{busy ? "Opening…" : "Confirm and open"}</button></div></div> : <form class="bank-form" onSubmit={e => { e.preventDefault(); setReview(true); }}><p>Complete your customer details to open a zero-balance account.</p><label>Account name<input required maxLength={100} value={name} onInput={e => setName(e.currentTarget.value)} placeholder="e.g. Everyday savings"/></label><label>Account type<select value={type} onChange={e => setType(e.currentTarget.value as typeof type)}><option value="SAVINGS">Savings</option><option value="CURRENT">Current</option></select></label><label>Date of birth<input type="date" required max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)} value={birth} onInput={e => setBirth(e.currentTarget.value)}/></label><label>Address<textarea required maxLength={255} value={address} onInput={e => setAddress(e.currentTarget.value)}/></label><button class="bank-button" disabled={!name.trim() || !address.trim()}>Review account</button></form>}</Modal>;
}
export function TransactionsPage({ token, id, accounts, initialAccount }: {
    token: string;
    id?: string;
    accounts: BankAccount[];
    initialAccount?: string;
}) {
    const [accountId, setAccountId] = useState(initialAccount || accounts[0]?.id || "");
    const [draft, setDraft] = useState<Filters>({});
    const [filters, setFilters] = useState<Filters>({ page: 0 });
    const [validation, setValidation] = useState("");
    const data = useLoad<BankTransaction | TransactionPage | null>(() => id ? bankApi.transaction(token, id) : accountId ? bankApi.transactions(token, accountId, filters) : Promise.resolve(null), [token, id, accountId, JSON.stringify(filters)]);
    const detail = data.data && "reference" in data.data ? data.data : null;
    const page = data.data && "content" in data.data ? data.data : null;
    return <><PageHeading title={id ? "Transaction details" : "Transaction history"} description={id ? "The recorded details of your transaction." : "Find and review activity across your accounts."}/>{!id && <form class="bank-filters" onSubmit={e => { e.preventDefault(); if (draft.from && draft.to && draft.from > draft.to) {
        setValidation("The end date must be on or after the start date.");
        return;
    } setValidation(""); setFilters({ ...draft, page: 0 }); }}><label>Account<select value={accountId} onChange={e => { setAccountId(e.currentTarget.value); setFilters({ ...filters, page: 0 }); }}>{accounts.map(a => <option value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label><label class="bank-search-label">Search<input type="search" maxLength={100} placeholder="Merchant or reference" value={draft.search || ""} onInput={e => setDraft({ ...draft, search: e.currentTarget.value })}/></label><label>Direction<select value={draft.direction || ""} onChange={e => setDraft({ ...draft, direction: e.currentTarget.value })}><option value="">All activity</option><option value="CREDIT">Money in</option><option value="DEBIT">Money out</option></select></label><label>From<input type="date" value={draft.from || ""} onInput={e => setDraft({ ...draft, from: e.currentTarget.value })}/></label><label>To<input type="date" value={draft.to || ""} onInput={e => setDraft({ ...draft, to: e.currentTarget.value })}/></label><label>Category<input maxLength={80} value={draft.category || ""} onInput={e => setDraft({ ...draft, category: e.currentTarget.value })} placeholder="Any category"/></label><button class="bank-button">Apply filters</button><button type="button" class="bank-button secondary" onClick={() => { setDraft({}); setFilters({ page: 0 }); setValidation(""); }}>Reset</button>{validation && <p role="alert" class="bank-error">{validation}</p>}</form>}
 <Panel><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !id && !page?.content.length ? "No transactions match your selection" : undefined}>{detail && <div class="bank-transaction-detail"><span class="bank-tile-symbol">{detail.amount < 0 ? "↗" : "↙"}</span><h2>{detail.merchantName || detail.type}</h2><strong>{formatMoney(detail.amount, detail.currencyCode, true)}</strong><Status value={detail.status}/><dl><Detail label="Reference">{detail.reference}</Detail><Detail label="Transaction type">{detail.type}</Detail><Detail label="Date and time">{formatDate(detail.occurredAt, true)}</Detail><Detail label="Category">{detail.category}</Detail><Detail label="Account">{accounts.find(a => a.id === detail.accountId)?.accountNumberMasked || "Account " + detail.accountId}</Detail></dl><button class="bank-button secondary" onClick={() => go("transactions")}>Back to history</button></div>}{page && <><TransactionList items={page.content}/><div class="bank-pagination"><span>{page.totalElements} {page.totalElements === 1 ? "transaction" : "transactions"} · Page {page.page + 1} of {Math.max(page.totalPages, 1)}</span><button disabled={page.page === 0} onClick={() => setFilters({ ...filters, page: page.page - 1 })}>← Previous</button><button disabled={page.page + 1 >= page.totalPages} onClick={() => setFilters({ ...filters, page: page.page + 1 })}>Next →</button></div></>}</State></Panel></>;
}
