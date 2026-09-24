import { WorkspaceRail } from "../../components/design/WorkspaceRail";
import { useEffect, useRef, useState } from "preact/hooks";
import { AdminLoanQueue, LoanRequest } from "./AdminLoanQueue";
import { AuthSession, authenticatedRequest } from "../../services/auth";
import { formatMoney, formatDate } from "../../services/banking-content";
import { PageHeading, Panel, State, Status, Detail, Modal, useLoad } from "./ui";
import { parseRoute, validAmount } from "./utils";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
type Account = {
    id: number;
    number: string;
    name: string;
    type: string;
    category: string;
    status: string;
    currency: string;
    balance: string;
    version: number;
    customerName: string | null;
    customerEmail: string | null;
};
const path = "/admin/accounts";
type RecordRow = Record<string, string>;
type Workspace = {
    account: Account;
    createdAt: string;
    updatedAt: string;
    terms: RecordRow;
    relatedAccounts: Account[];
    mandates: RecordRow[];
};
const accountHref = (id: number | string, section = "overview") => `#/admin/accounts/${id}/${section}`;
const sections = [["overview", "Overview"], ["transactions", "Transactions"], ["related", "Related accounts & mandates"], ["audit", "Audit history"]];
export function filterAccounts(accounts: Account[], search: string, status: string, type: string) {
    const words = search.trim().toLowerCase().split(/\s+/);
    return accounts.filter(a => (!status || a.status === status) && (!type || a.type === type) && words.every(word => [a.name, a.number, a.customerName, a.customerEmail, a.type].join(" ").toLowerCase().includes(word)));
}
export function AdminApp({ session, signOut, route }: {
    session: AuthSession;
    signOut: () => Promise<void>;
    route: ReturnType<typeof parseRoute>;
}) {
    const token = session.accessToken;
    const data = useLoad(() => authenticatedRequest<Account[]>(path, token), [token]);
    const [queueRevision,setQueueRevision]=useState(0);
    useEffect(()=>{const timer=window.setInterval(()=>setQueueRevision(n=>n+1),30000);return()=>window.clearInterval(timer);},[]);
    const loans=useLoad(()=>authenticatedRequest<LoanRequest[]>("/admin/loans",token),[token,queueRevision]);
    function refreshLoans(){loans.reload();data.reload();}
    const [search, setSearch] = useState(""), [status, setStatus] = useState(""), [type, setType] = useState(""), [page, setPage] = useState(0);
    const [signingOut, setSigningOut] = useState(false);
    const rows = filterAccounts(data.data || [], search, status, type), current = Math.min(page, Math.max(0, Math.ceil(rows.length / 20) - 1));
    async function exit() { if (signingOut)
        return; setSigningOut(true); try {
        await signOut();
    }
    finally {
        setSigningOut(false);
    } }
    return <div class="bank-app admin-app experience-admin"><WorkspaceRail page={route.section === "loans" ? "admin/loans" : "admin"} name={session.profile.fullName} email={session.user.email} admin onLogout={exit}/><div class="experience-admin-body"><header class="admin-topbar"><a class="admin-brand" href="#home" aria-label="Nexa home"><strong>Nexa</strong><span>Administration</span></a></header><main class="bank-main">
 {route.section==="loans"?<AdminLoanQueue token={token} requests={loans.data||[]} loading={loans.loading} error={loans.error} reload={refreshLoans}/>:route.page === "admin" && route.id ? <AccountWorkspace key={route.id} id={route.id} section={route.section || "overview"} token={token} onChanged={data.reload}/> : <>
 <PageHeading eyebrow="" title="Find an account" description="Search by account number, customer name or email. Open an account to see everything connected to it." action={<button class="bank-button secondary" onClick={data.reload}>Refresh accounts</button>}/>
 <Panel title="Account directory"><div class="admin-filters"><label class="bank-search-label">Search accounts<input type="search" value={search} onInput={e => { setSearch(e.currentTarget.value); setPage(0); }} placeholder="Account number, name or email"/></label>
 <label>Status<select value={status} onChange={e => { setStatus(e.currentTarget.value); setPage(0); }}><option value="">All statuses</option>{["ACTIVE", "BLOCKED", "CLOSED"].map(s => <option value={s}>{s}</option>)}</select></label>
 <label>Account type<select value={type} onChange={e => { setType(e.currentTarget.value); setPage(0); }}><option value="">All types</option>{["SAVINGS", "CURRENT", "LOAN", "CARD", "CASH", "CLEARING"].map(s => <option value={s}>{s}</option>)}</select></label>
 {(search || status || type) && <button class="bank-button secondary" onClick={() => { setSearch(""); setStatus(""); setType(""); setPage(0); }}>Clear filters</button>}</div>
 <State loading={data.loading} error={data.error} retry={data.reload}><p class="admin-result-count" role="status">{rows.length} {rows.length === 1 ? "account" : "accounts"} found</p>
 {!rows.length ? <p class="bank-notice">No matching accounts. Try another name or number, or clear the filters.</p> : <><AccountTable accounts={rows.slice(current * 20, current * 20 + 20)}/><Pagination page={current} total={rows.length} size={20} setPage={setPage}/></>}
 </State></Panel></>}
 </main></div></div>;
}
function AccountTable({ accounts }: {
    accounts: Account[];
}) { return <div class="admin-table-scroll"><table class="admin-table"><thead><tr><th>Account</th><th>Customer</th><th>Type</th><th>Status</th><th>Balance / outstanding</th></tr></thead><tbody>{accounts.map(a => <tr key={a.id} class="admin-account-row" tabIndex={0} role="link" aria-label={`Open ${a.name}`} onClick={() => { window.location.hash = accountHref(a.id).slice(1); }} onKeyDown={event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); window.location.hash = accountHref(a.id).slice(1); } }}><td><a class="admin-account-link" href={accountHref(a.id)}>{a.name} <span aria-hidden="true">→</span></a><small>{a.number}</small></td><td>{a.customerName || "System"}<small>{a.customerEmail}</small></td><td>{a.type}</td><td><Status value={a.status}/></td><td>{formatMoney(a.balance, a.currency)}</td></tr>)}</tbody></table></div>; }
function Pagination({ page, total, size, setPage }: {
    page: number;
    total: number;
    size: number;
    setPage: (page: number) => void;
}) {
    if (total <= size)
        return null;
    return <nav class="admin-pagination" aria-label="Result pages"><span>{page * size + 1}–{Math.min((page + 1) * size, total)} of {total}</span><button class="bank-button secondary" disabled={!page} onClick={() => setPage(page - 1)}>Previous</button><button class="bank-button secondary" disabled={(page + 1) * size >= total} onClick={() => setPage(page + 1)}>Next</button></nav>;
}
function AccountWorkspace({ id, section, token, onChanged }: {
    id: string;
    section: string;
    token: string;
    onChanged: () => void;
}) {
    const data = useLoad(() => authenticatedRequest<Workspace>(path + "/" + id, token), [id, token]);
    const [editing, setEditing] = useState(false), [receipt, setReceipt] = useState(""), [revision, setRevision] = useState(0);
    function refresh() { data.reload(); setRevision(n => n + 1); onChanged(); }
    const w = data.data, a = w?.account;
    return <><nav class="admin-breadcrumb" aria-label="Breadcrumb"><a href="#/admin">← Account search</a><span aria-hidden="true">/</span><span>{a?.name || "Account details"}</span></nav>
 <State loading={data.loading} error={data.error} retry={data.reload}>{w && a && <>
 <PageHeading eyebrow={`${a.type} · ${a.number}`} title={a.name} description={a.customerName ? `${a.customerName} · ${a.customerEmail}` : "System account"} action={<div class="admin-actions"><button class="bank-button secondary" onClick={refresh}>Refresh</button><button class="bank-button" onClick={() => setEditing(true)}>Manage account</button></div>}/>
 <div class="admin-account-summary"><div><span>{["LOAN", "CARD"].includes(a.type) ? "Outstanding balance" : "Current balance"}</span><strong>{formatMoney(a.balance, a.currency)}</strong></div><Status value={a.status}/><span>{a.category === "CUSTOMER" ? "Customer account" : "System account"}</span></div>
 {receipt && <p role="status" class="bank-notice">{receipt}</p>}
 <nav class="admin-sections" aria-label="Account sections">{sections.map(([key, label]) => <a key={key} href={accountHref(id, key)} aria-current={section === key ? "page" : undefined}>{label}</a>)}</nav>
 {section === "overview" && <div class="admin-overview-grid"><Panel title="Account details"><dl><Detail label="Account number">{a.number}</Detail><Detail label="Account type">{a.type}</Detail><Detail label="Currency">{a.currency}</Detail><Detail label="Opened">{formatDate(w.createdAt)}</Detail><Detail label="Last updated">{formatDate(w.updatedAt)}</Detail></dl></Panel><Panel title="Customer & connected accounts"><dl><Detail label="Customer">{a.customerName || "System-owned account"}</Detail><Detail label="Email">{a.customerEmail}</Detail></dl><p>{w.relatedAccounts.length} other accounts · {w.mandates.length} mandates</p><a class="bank-button secondary" href={accountHref(id, "related")}>View related accounts & mandates</a></Panel>
 {["LOAN", "CARD"].includes(a.type) && <Panel title={a.type === "LOAN" ? "Loan terms" : "Card terms"}><dl>{[["PRODUCT_STATUS", "Product status"], ["PRINCIPAL_AMOUNT", "Original principal"], ["INTEREST_RATE", "Annual interest rate (%)"], ["PERIODIC_PAYMENT", "Periodic payment"], ["CREDIT_LIMIT", "Credit limit"], ["MINIMUM_PAYMENT", "Minimum payment"]].filter(([key]) => w.terms[key] != null).map(([key, label]) => <Detail key={key} label={label}>{["PRODUCT_STATUS", "INTEREST_RATE"].includes(key) ? w.terms[key] : formatMoney(w.terms[key], a.currency)}</Detail>)}{w.terms.FUNDING_ACCOUNT_ID && <Detail label="Funding account"><a href={accountHref(w.terms.FUNDING_ACCOUNT_ID)}>Open funding account →</a></Detail>}</dl></Panel>}</div>}
 {section === "transactions" && <AccountActivity key={`${id}-${revision}`} id={id} token={token} currency={a.currency}/>}
 {section === "related" && <><Panel title="Other accounts belonging to this customer">{w.relatedAccounts.length ? <AccountTable accounts={w.relatedAccounts}/> : <p>No other customer accounts.</p>}</Panel><Panel title="Mandates involving this account">{!w.mandates.length ? <p>No mandates linked to this account.</p> : w.mandates.map(m => <article class="admin-mandate" key={m.ID}><header><strong>{m.DISPLAY_NAME || m.ID}</strong><Status value={m.STATUS}/></header><dl><Detail label="Source account">{m.SOURCE_ACCOUNT_ID ? <a href={accountHref(m.SOURCE_ACCOUNT_ID)}>{m.SOURCE_NAME || m.SOURCE_ACCOUNT_ID} →</a> : "Not linked"}</Detail><Detail label="Beneficiary account">{m.DESTINATION_ACCOUNT_ID ? <a href={accountHref(m.DESTINATION_ACCOUNT_ID)}>{m.DESTINATION_NAME || m.DESTINATION_ACCOUNT_ID} →</a> : "Not linked"}</Detail><Detail label="Amount / limit">{m.AMOUNT != null ? formatMoney(m.AMOUNT, m.CURRENCY_CODE || a.currency) : "No amount specified"}</Detail><Detail label="Effective dates">{m.EFFECTIVE_DATE ? formatDate(m.EFFECTIVE_DATE) : "Not specified"} – {m.END_DATE ? formatDate(m.END_DATE) : "No expiry"}</Detail><Detail label="Mandate reference">{m.ID}</Detail></dl></article>)}</Panel></>}
 {section === "audit" && <AccountAudit key={`${id}-${revision}`} id={id} token={token} currency={a.currency}/>}
 {editing && <AccountEditor token={token} account={a} close={() => setEditing(false)} done={message => { setEditing(false); setReceipt(message); refresh(); }}/>}
 </>}</State></>;
}
function AccountActivity({ id, token, currency }: {
    id: string;
    token: string;
    currency: string;
}) {
    const [page, setPage] = useState(0);
    const data = useLoad(() => authenticatedRequest<{
        items: RecordRow[];
        total: number;
    }>(`${path}/${id}/transactions?page=${page}&size=20`, token), [id, token, page]);
    return <Panel title="Posted transactions"><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !data.data?.items.length ? "No transactions for this account" : undefined}>
 <div class="admin-table-scroll"><table class="admin-table"><thead><tr><th>Transaction / date</th><th>Type</th><th>Accounts</th><th>Status</th><th>Amount</th></tr></thead><tbody>{data.data?.items.map(t => <tr key={t.ID}><td>{t.ID}<small>{formatDate(t.CREATED_AT)}</small></td><td>{(t.OPERATION || t.TRANSACTION_TYPE).replace(/_/g, " ")}</td><td>{t.SOURCE_ACCOUNT_ID && <a href={accountHref(t.SOURCE_ACCOUNT_ID)}>Source →</a>}<small>{t.DESTINATION_ACCOUNT_ID && <a href={accountHref(t.DESTINATION_ACCOUNT_ID)}>Destination →</a>}</small></td><td><Status value={t.STATUS}/></td><td>{formatMoney(t.AMOUNT, currency)}</td></tr>)}</tbody></table></div>
 <Pagination page={page} total={data.data?.total || 0} size={20} setPage={setPage}/></State></Panel>;
}
function AccountAudit({ id, token, currency }: {
    id: string;
    token: string;
    currency: string;
}) {
    const history = useLoad(() => authenticatedRequest<RecordRow[]>(`${path}/${id}/audit`, token), [id, token]);
    return <Panel title="Administrative audit history"><p>Most recent 100 changes, including the administrator and reason.</p><State loading={history.loading} error={history.error} retry={history.reload} empty={!history.loading && !history.error && !history.data?.length ? "No administrative changes recorded" : undefined}>{history.data?.map(e => <div class="bank-record" key={e.ID}><div><strong>{e.OPERATION.replace(/_/g, " ")} · {e.ACTOR}</strong><p>{e.AUDIT_REASON}</p>{e.BEFORE_NAME && <small>{e.BEFORE_NAME} ({e.BEFORE_STATUS}) → {e.AFTER_NAME} ({e.AFTER_STATUS})</small>}<small>{formatDate(e.CREATED_AT)} {e.TRANSACTION_REFERENCE}</small></div>{e.AMOUNT && <span>{formatMoney(e.AMOUNT, currency)}</span>}</div>)}</State></Panel>;
}
export function AccountEditor({ token, account, close, done }: {
    token: string;
    account: Account;
    close: () => void;
    done: (message: string) => void;
}) {
    const [name, setName] = useState(account.name), [status, setStatus] = useState(account.status), [reason, setReason] = useState("");
    const [direction, setDirection] = useState("CREDIT"), [amount, setAmount] = useState(""), [review, setReview] = useState(false);
    const [busy, setBusy] = useState(false), [error, setError] = useState(""), [uncertain, setUncertain] = useState(false);
    const requestId = useRef(crypto.randomUUID()), lock = useRef(false);
    useNavigationGuard(name !== account.name || status !== account.status || !!reason || !!amount, busy);
    async function save(adjust: boolean) {
        if (lock.current)
            return;
        lock.current = true;
        setBusy(true);
        setError("");
        try {
            const result = await authenticatedRequest<{
                transactionId?: string;
            }>(path + "/" + account.id + (adjust ? "/adjustments" : ""), token, { method: adjust ? "POST" : "PUT", body: JSON.stringify(adjust ? { direction, amount, reason, requestId: requestId.current } : { name, status, reason, version: account.version }) });
            done(adjust ? "Balance adjustment posted. Transaction: " + result.transactionId : "Account updated and audit recorded.");
        }
        catch (e) {
            setError(e instanceof Error ? e.message : "Unable to save.");
            if (adjust && (!(e as {
                status: number;
            }).status || (e as {
                status: number;
            }).status >= 500))
                setUncertain(true);
        }
        finally {
            lock.current = false;
            setBusy(false);
        }
    }
    return <Modal title={account.name} className="admin-dialog" onClose={close} locked={busy}><div class="bank-form"><dl class="admin-dialog-summary"><Detail label="Account number">{account.number}</Detail><Detail label="Customer">{account.customerName || "System"}</Detail><Detail label="Current balance / outstanding">{formatMoney(account.balance, account.currency)}</Detail></dl>
 <fieldset disabled={busy || uncertain} class="admin-dialog-fields admin-editor-fields"><legend>Account settings</legend><label>Account name<input required value={name} maxLength={120} onInput={e => setName(e.currentTarget.value)}/></label><label>Status<select value={status} onChange={e => setStatus(e.currentTarget.value)}>{["ACTIVE", "BLOCKED", "CLOSED"].map(s => <option value={s}>{s}</option>)}</select></label><label class="admin-field-wide">Reason for change<textarea required maxLength={500} value={reason} onInput={e => { setReason(e.currentTarget.value); setReview(false); }}/></label><div class="admin-field-wide"><button class="bank-button" disabled={!name.trim() || !reason.trim()} onClick={() => save(false)}>Save account details</button></div>
 {account.category === "CUSTOMER" && ["SAVINGS", "CURRENT"].includes(account.type) && <><div class="admin-adjustment-heading admin-field-wide"><h3>Post a balance adjustment</h3><p>Credits and debits create a transaction and balanced accounting entries. The account must be active.</p></div><label>Adjustment<select value={direction} onChange={e => { setDirection(e.currentTarget.value); setReview(false); }}><option value="CREDIT">Credit / deposit</option><option value="DEBIT">Debit / withdrawal</option></select></label><label>Amount ({account.currency})<input inputMode="decimal" value={amount} onInput={e => { setAmount(e.currentTarget.value); setReview(false); }}/></label><div class="admin-field-wide"><button class="bank-button secondary" disabled={!validAmount(amount) || !reason.trim()} onClick={() => setReview(true)}>Review adjustment</button></div></>}
 </fieldset>
 {review && <div class="bank-confirm-summary"><p>{direction} {formatMoney(amount, account.currency)} to account {account.number}.</p><p>{reason}</p><button class="bank-button" disabled={busy} onClick={() => save(true)}>{uncertain ? "Retry same adjustment" : "Confirm adjustment"}</button></div>}
 {uncertain && <p role="alert">Result not confirmed. Retry uses the same request ID and cannot post twice. Keep the details unchanged.</p>}{error && <p role="alert" class="bank-error">{error}</p>}
 </div></Modal>;
}
