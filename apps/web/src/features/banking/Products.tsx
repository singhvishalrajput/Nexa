import { PayeeForm } from "./PayeeForm";
import { BillPayment } from "./BillPayment";
import { LoanNavigation, LoanApplicationTimeline } from "./LoanTools";
import { LoanSalarySlipPanel } from "./LoanSalarySlips";
import { BillCreate, ProductCreate, ProductOperations, ProductStatusControl } from "./ProductOperations";
import { t } from "../../services/locale";
import { BankingIcon, BankingIconName } from "../../components/BankingIcon";
import { CardApplications, CardStatusControl } from "./CardApplications";
import { ScheduledPaymentCreate, ScheduledPaymentDetails } from "./ScheduledPayment";
import { useState } from "preact/hooks";
import { bankApi, Product, ProductKind } from "./api";
import { PageHeading, Panel, State, Status, Detail, useLoad } from "./ui";
import { formatMoney, formatDate, humanize, safeMask, categoryLabel } from "../../services/banking-content";
export const productNames: Record<ProductKind, string> = { cards: "Cards", bills: "Bills", beneficiaries: "Payees", mandates: "Direct debits", loans: "Loans", "scheduled-payments": "Scheduled payments" };
const descriptions: Record<ProductKind, string> = { cards: "Your cards, balances and recorded activity.", bills: "Stay on top of amounts due and payment history.", beneficiaries: "Your saved payees and bank details.", mandates: "Track your recurring payment agreements.", loans: "Outstanding balances and upcoming instalments.", "scheduled-payments": "A clear view of payments scheduled for your accounts." };
export const productTitle = (p: Product) => p.displayName || p.billerName || p.payee || "Banking record";
const productIcons: Record<ProductKind, BankingIconName> = { cards: "cards", bills: "transactions", beneficiaries: "people", mandates: "repeat", loans: "accounts", "scheduled-payments": "clock" };
function amount(p: Product) { if (p.cardType === "DEBIT")
    return undefined; return p.outstanding ?? p.outstandingAmount ?? p.amount ?? p.limit; }
const awaitingCardDecision = (p: Product) => p.status === "PENDING_APPROVAL" || p.status === "REJECTED";
function CardBalance({ product: p }: { product: Product }) {
    if (p.cardType === "DEBIT") return null;
    if (awaitingCardDecision(p)) return <p class="bank-form-note">{p.status === "PENDING_APPROVAL" ? "Credit limit awaits bank approval." : "No credit limit was approved."}</p>;
    const showAvailable = p.status !== "CLOSED" && p.availableLimit != null;
    return <div class="bank-product-amount">{showAvailable && <><span>{t("Available limit")}</span><strong>{formatMoney(p.availableLimit!, p.currencyCode || "INR")}</strong></>}
      {p.outstanding != null && (showAvailable ? <small>{t("Outstanding")} · {formatMoney(p.outstanding, p.currencyCode || "INR")}</small>
        : <><span>{t("Outstanding")}</span><strong>{formatMoney(p.outstanding, p.currencyCode || "INR")}</strong></>)}
    </div>;
}
export function ProductsPage({ token, kind, id, initiallyOpen = false, onAccountsChanged }: {
    token: string;
    kind: ProductKind;
    id?: string;
    initiallyOpen?: boolean;
    onAccountsChanged?: () => void;
}) {
    const [receipt, setReceipt] = useState("");
    const [page, setPage] = useState(0);
    const [filter, setFilter] = useState("");
    const [search, setSearch] = useState("");
    const data = useLoad<Product | Product[]>(() => id ? bankApi.product(token, kind, id) : bankApi.products(token, kind, page, filter), [token, kind, id, page, filter]);
    const rows = Array.isArray(data.data) ? data.data : [];
    const detail = data.data && !Array.isArray(data.data) ? data.data : null;
    const visible = rows.filter(p => productTitle(p).toLowerCase().includes(search.toLowerCase()));
    return <section class={"bank-service-page bank-service-" + kind}><PageHeading title={id ? productNames[kind].replace(/s$/, "") + " details" : productNames[kind]} description={descriptions[kind]}/>
 {receipt && <p role="status" class="bank-notice">{receipt}</p>}
 {kind === "loans" && <LoanNavigation current={initiallyOpen ? "new" : "loans"}/> }
 {kind === "beneficiaries" && (!id || (detail && detail.transferType !== "EXTERNAL_BANK")) && <PayeeForm token={token} id={id} name={detail ? productTitle(detail) : ""} reload={data.reload}/>}
 {!id && kind === "bills" && <BillCreate token={token} initiallyOpen={initiallyOpen} reload={() => { setPage(0); setFilter(""); setSearch(""); data.reload(); }}/>}
 {!id && kind === "cards" && <CardApplications token={token} initiallyOpen={initiallyOpen} onAccountsChanged={onAccountsChanged} reload={() => { setPage(0); setFilter(""); setSearch(""); data.reload(); }}/>}
 {!id && kind === "scheduled-payments" && <ScheduledPaymentCreate token={token} initiallyOpen={initiallyOpen} reload={() => { setPage(0); setFilter(""); setSearch(""); data.reload(); }}/>}
 {!id && (kind === "mandates" || kind === "loans") && <ProductCreate token={token} kind={kind} initiallyOpen={initiallyOpen} reload={() => { setPage(0); setFilter(""); setSearch(""); data.reload(); }}/>}
  {!id && <div class="bank-filters bank-product-filters"><label class="bank-search-label">{t("Search")}<input type="search" placeholder={"Search " + productNames[kind].toLowerCase()} value={search} onInput={e => setSearch(e.currentTarget.value)}/></label>{kind !== "beneficiaries" && <label>{t("Status")}<select value={filter} onChange={e => { setFilter(e.currentTarget.value); setPage(0); }}><option value="">{t("All statuses")}</option>{(kind === "cards" ? ["PENDING_APPROVAL", "ACTIVE", "BLOCKED", "REJECTED", "CLOSED"] : kind === "bills" ? ["UPCOMING", "DUE", "OVERDUE", "PAID"] : kind === "mandates" ? ["PENDING", "ACTIVE", "PAUSED", "CANCELLED", "EXPIRED", "ACTION_REQUIRED"] : kind === "loans" ? ["PENDING_APPROVAL", "APPROVED", "REJECTED", "ACTIVE", "OVERDUE", "PAID", "CLOSED"] : ["READY", "SCHEDULED", "PENDING", "COMPLETED", "FAILED", "CANCELLED", "EXPIRED"]).map(s => <option value={s}>{humanize(s)}</option>)}</select></label>}</div>}
 <State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.error && !id && !visible.length ? "No " + productNames[kind].toLowerCase() + " to show" : undefined}>
   {detail ? <ProductDetail key={detail.id} token={token} product={detail} kind={kind} reload={data.reload} onPosted={setReceipt} onAccountsChanged={onAccountsChanged}/> : <div class={(kind === "cards" ? "bank-card-grid" : "bank-product-grid") + " bank-product-grid--" + kind}>
    {visible.map(p => <a href={"#/" + kind + "/" + encodeURIComponent(p.id)} key={p.id} class={"bank-product " + (kind === "cards" ? "bank-payment-card" : "")}>
      <header><span class="bank-tile-symbol" aria-hidden="true">{kind === "beneficiaries" ? productTitle(p).slice(0, 1).toUpperCase() : <BankingIcon name={productIcons[kind]}/>}</span><Status value={p.status}/></header>
      <h2>{productTitle(p)}</h2>
      <p>{p.numberMasked ? safeMask(p.numberMasked) : p.accountNumberMasked ? safeMask(p.accountNumberMasked) : p.reference || p.bankName || (p.category && (kind === "bills" ? categoryLabel(p.category) : p.category)) || humanize(kind)}</p>
      {kind === "cards" ? <CardBalance product={p}/> : amount(p) !== undefined && <div class="bank-product-amount"><span>{kind === "loans" ? t("Outstanding") : kind === "mandates" ? "Payment limit" : kind === "scheduled-payments" ? "Scheduled amount" : "Amount due"}</span><strong>{formatMoney(amount(p)!, p.currencyCode || "INR")}</strong></div>}
      <footer><span>{p.dueAt ? "Due " + formatDate(p.dueAt) : p.frequency ? humanize(p.frequency) : p.cardType ? humanize(p.cardType) + " card" : t("View details")}</span><BankingIcon name="arrow"/></footer>
    </a>)}
  </div>}
 </State>
 {!id && kind !== "beneficiaries" && !data.loading && !data.error && <div class="bank-pagination"><span>{t("Page")} {page + 1}</span><button disabled={!page} onClick={() => setPage(p => p - 1)}>{t("← Previous")}</button><button disabled={rows.length < 12} onClick={() => setPage(p => p + 1)}>{t("Next →")}</button></div>}
</section>;
}
function ProductDetail({ product: p, kind, token, reload, onPosted, onAccountsChanged }: {
    token: string;
    reload: () => void;
    onPosted: (message: string) => void;
    product: Product;
    kind: ProductKind;
    onAccountsChanged?: () => void;
}) {
    const showCardTerms = p.cardType !== "DEBIT" && !(kind === "cards" && awaitingCardDecision(p));
    return <div class="bank-detail-grid">{kind === "bills" && <BillPayment token={token} bill={p} onBillChanged={reload} onPaid={() => { onAccountsChanged?.(); reload(); }}/>}<Panel className="bank-product-summary"><span class="bank-tile-symbol">◇</span><h2>{productTitle(p)}</h2><Status value={p.status}/>{kind === "cards" ? <CardBalance product={p}/> : amount(p) !== undefined && <>{kind === "loans" && <span class="loan-balance-label">Outstanding principal</span>}<strong>{formatMoney(amount(p)!, p.currencyCode || "INR")}</strong>{kind === "loans" && <small class="loan-balance-note">Borrowed amount still unpaid · excludes future interest</small>}</>}<p>{p.numberMasked ? safeMask(p.numberMasked) : p.accountNumberMasked ? safeMask(p.accountNumberMasked) : p.reference}</p><a href={"#/" + kind}>{t("← Back to")} {productNames[kind].toLowerCase()}</a></Panel><Panel title={t("Details")}><dl>{p.bankName && <Detail label={t("Bank")}>{p.bankName}</Detail>}{p.cardType && <Detail label={t("Card type")}>{humanize(p.cardType)}</Detail>}{showCardTerms && p.availableLimit !== undefined && <Detail label={t("Available limit")}>{formatMoney(p.availableLimit, p.currencyCode)}</Detail>}{showCardTerms && p.creditLimit !== undefined && <Detail label={t("Credit limit")}>{formatMoney(p.creditLimit, p.currencyCode)}</Detail>}{showCardTerms && p.minimumPayment !== undefined && <Detail label={t("Minimum payment")}>{formatMoney(p.minimumPayment, p.currencyCode)}</Detail>}{p.dueAt && <Detail label={t("Due date")}>{formatDate(p.dueAt)}</Detail>}{p.nextDebit && <Detail label={t("Next debit")}>{formatDate(p.nextDebit)}</Detail>}{p.frequency && <Detail label={t("Frequency")}>{humanize(p.frequency)}</Detail>}{p.interestRate && <Detail label={t(kind === "loans" ? "Annual interest rate" : "Interest rate")}>{p.interestRate}%</Detail>}{p.nextEmi != null && <Detail label={t(kind === "loans" ? "Next EMI (includes interest)" : "Next instalment")}>{formatMoney(p.nextEmi, p.currencyCode)}</Detail>}{p.accountId && <Detail label={t("Linked account")}><a href={"#/accounts/" + encodeURIComponent(p.accountId)}>{t("View account ↗")}</a></Detail>}{p.reference && <Detail label={t("Reference")}>{p.reference}</Detail>}</dl></Panel>
 {(kind === "mandates" || kind === "loans") && <ProductOperations token={token} kind={kind} product={p} reload={reload} onPosted={onPosted}/>}
 {kind === "beneficiaries" && p.transferType === "EXTERNAL_BANK" && <Panel title="Other-bank payee"><div class="bank-form"><dl><Detail label="Recipient name entered">{p.recipientName}</Detail><Detail label="IFSC">{p.ifsc}</Detail><Detail label="Bank account">{p.accountNumberMasked}</Detail></dl><p>These details cannot be edited. Save a new payee if the bank details change.</p><a class="bank-button" href={"#/payments?destination=other-bank&payee=" + encodeURIComponent(p.id)}>Make a transfer</a><p class="bank-form-note">Cashfree sandbox mode: no real money moves and bills are not marked paid.</p></div></Panel>}
 {kind === "loans" && <LoanApplicationTimeline token={token} product={p}/>}
 {kind === "loans" && <LoanSalarySlipPanel token={token} loanId={p.id} pending={p.status === "PENDING_APPROVAL"}/>}
  {kind === "bills" && <Panel title="Bill amounts"><dl><Detail label="Original bill amount">{formatMoney(p.amount ?? "0", p.currencyCode || "INR")}</Detail><Detail label="Paid through Nexa">{formatMoney(p.paidAmount ?? "0", p.currencyCode || "INR")}</Detail><Detail label="Outstanding amount">{formatMoney(p.outstandingAmount ?? p.amount ?? "0", p.currencyCode || "INR")}</Detail></dl></Panel>}

  {kind === "mandates" && <ProductStatusControl token={token} kind={kind} product={p} reload={reload} onPosted={onPosted}/>}
 {kind === "cards" && <CardStatusControl token={token} product={p} reload={reload} onPosted={onPosted}/>}
 {kind === "scheduled-payments" && <ScheduledPaymentDetails token={token} id={p.id} onChanged={() => { reload(); onAccountsChanged?.(); }}/>}
 {p.paymentHistory && <Panel title={t("Payment history")}><State empty={!p.paymentHistory.length ? "No payments recorded" : undefined}>{p.paymentHistory.map(t => <div class="bank-record" key={t.id}><div><strong>{t.payee}</strong><small>{formatDate(t.dueAt, true)}</small>{t.reference?.startsWith("TX-") && <a href={"#/transactions/" + encodeURIComponent(t.reference)}>View transaction · {t.reference}</a>}</div><Status value={t.status}/><strong>{formatMoney(t.amount, t.currencyCode)}</strong></div>)}</State></Panel>}
 {p.transactions && <Panel title={t("Card activity")}><State empty={!p.transactions.length ? "No card activity recorded" : undefined}>{p.transactions.map(t => <div class="bank-record" key={t.id}><div><strong>{t.merchantName || t.type}</strong><small>{formatDate(t.occurredAt)}</small></div><Status value={t.status}/><strong>{formatMoney(t.amount, t.currencyCode, true)}</strong></div>)}</State></Panel>}</div>;
}
