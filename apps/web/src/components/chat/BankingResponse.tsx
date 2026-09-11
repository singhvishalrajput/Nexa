import { h, ComponentChildren } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { AccountSnapshot, BankingContent, Money, TransactionSnapshot, dayLabel, dueLabel, formatDate, formatMoney, humanize, safeMask, transactionDirection, statusPresentation, completedStatuses } from "../../services/banking-content";
import { BankingCollection, collectionNotice } from "./BankingCollection";
import { getTransactionPage } from "../../services/banking";

export function StatusBadge({ status }: { status: string }) {
  const {tone, label} = statusPresentation(status);
  return <span class={`bank-status ${tone === "good" ? "is-positive" : tone === "bad" ? "needs-attention" : tone}`}>{label}</span>;
}
export function MoneyAmount({ amount, currency, signed = false }: { amount: Money; currency: string; signed?: boolean }) {
  return <strong class="bank-money">{formatMoney(amount, currency, signed)}</strong>;
}
export function DetailRow({ label, children }: { label: string; children: ComponentChildren }) {
  return <div class="bank-detail-row"><dt>{label}</dt><dd>{children}</dd></div>;
}
function SectionHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return <header class="bank-section-header"><h3 tabIndex={-1}>{title}</h3>{subtitle && <p>{subtitle}</p>}</header>;
}
function AccountLabel({ account }: { account: AccountSnapshot }) {
  return <span>{account.displayName} <span class="bank-secondary">{safeMask(account.accountNumberMasked)}</span></span>;
}

export function AccountSummary({ accounts, onTransactions }: { accounts: AccountSnapshot[]; onTransactions: (account: AccountSnapshot) => void }) {
  return <BankingCollection type="ACCOUNTS" count={accounts.length} attention={collectionNotice(accounts)} label="Account balances">
    {accounts.map((account) => <div class="bank-account" key={account.id}>
      <h3><AccountLabel account={account} /></h3>
      <span class="bank-secondary">{humanize(account.accountType)} · Available balance</span>
      <MoneyAmount amount={account.availableBalance} currency={account.currencyCode} />
      {account.currentBalance != null && <span class="bank-secondary">Current balance {formatMoney(account.currentBalance, account.currencyCode)}</span>}
      <div class="bank-account-footer"><StatusBadge status={account.status} /><button type="button" class="bank-inline-action" onClick={() => onTransactions(account)}>View transactions <span aria-hidden="true">→</span></button></div>
    </div>)}
  </BankingCollection>;
}

export function TransactionDetails({ transaction, account, onBack }: { transaction: TransactionSnapshot; account: AccountSnapshot; onBack: () => void }) {
  const [copied, setCopied] = useState("");
  const [help, setHelp] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, [transaction.id]);
  const incoming = Number(transaction.amount) > 0;
  return <section class="bank-transaction-detail" aria-label="Transaction details">
    <button type="button" class="bank-inline-action" onClick={onBack}>← Back to transactions</button>
    <h3 ref={heading} tabIndex={-1}>{transaction.merchantName || humanize(transaction.type)}</h3>
    <MoneyAmount amount={transaction.amount} currency={transaction.currencyCode} signed />
    <StatusBadge status={transaction.status} />
    <dl>
      <DetailRow label={completedStatuses.includes(transaction.status) ? incoming ? "Received in" : "Paid from" : "Account"}><AccountLabel account={account} /></DetailRow>
      <DetailRow label="Date & time">{formatDate(transaction.occurredAt, true)}</DetailRow>
      <DetailRow label="Transaction type">{humanize(transaction.type)}</DetailRow>
      {transaction.category && <DetailRow label="Category">{humanize(transaction.category)}</DetailRow>}
    </dl>
    {transaction.reference && <details class="bank-reference"><summary>Transaction reference</summary><code>{transaction.reference}</code><button type="button" class="bank-inline-action" onClick={async () => {
      try { await navigator.clipboard.writeText(transaction.reference); setCopied("Reference copied."); }
      catch (_) { setCopied("Couldn’t copy. Select the reference above to copy it manually."); }
    }}>Copy reference</button><span role="status">{copied}</span></details>}
    <button type="button" class="bank-inline-action" aria-expanded={help} onClick={() => setHelp(!help)}>Need help with this transaction?</button>
    {help && <p class="bank-help">Contact your bank through its official support channel and quote the transaction reference. Reporting an issue through Nexa is not available yet.</p>}
  </section>;
}

export function TransactionList({ items, onSelect }: { items: TransactionSnapshot[]; onSelect: (item: TransactionSnapshot) => void }) {
  // Group by the user's local calendar date. Sorting copies avoids mutating snapshots.
  const groups = new Map<string, TransactionSnapshot[]>();
  [...items].sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime()).forEach((item) => {
    const key = new Date(item.occurredAt).toDateString();
    groups.set(key, [...(groups.get(key) || []), item]);
  });
  return <div class="bank-transactions">{Array.from(groups.entries()).map(([date, rows]) => <section key={date} aria-label={dayLabel(rows[0].occurredAt)}>
    <h4 class="bank-date-heading">{dayLabel(rows[0].occurredAt)}</h4>
    <ul>{rows.map((item) => {
      const direction = transactionDirection(item);
      const merchant = item.merchantName || humanize(item.type);
      return <li key={item.id}><button type="button" class="bank-transaction-row" onClick={() => onSelect(item)} aria-label={`${merchant}, ${direction.toLowerCase()}, ${formatMoney(item.amount, item.currencyCode)}, ${formatDate(item.occurredAt, true)}, ${statusPresentation(item.status).label}. View details`}>
        <span class="bank-transaction-name"><strong dir="auto">{merchant}</strong><small>{item.category ? humanize(item.category) : humanize(item.type)}</small><StatusBadge status={item.status} /></span>
        <span class="bank-transaction-amount"><MoneyAmount amount={item.amount} currency={item.currencyCode} signed /><small>{direction} · {new Date(item.occurredAt).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" })}</small></span>
      </button></li>;
    })}</ul>
  </section>)}</div>;
}

function TransactionPanel({ account, items = [], total = 0, accessToken, all = false, onBack }: {
  account: AccountSnapshot; items?: TransactionSnapshot[]; total?: number; accessToken: string; all?: boolean; onBack?: () => void;
}) {
  const [expanded, setExpanded] = useState(all);
  const [page, setPage] = useState(0);
  const [loaded, setLoaded] = useState<TransactionSnapshot[]>(items);
  const [count, setCount] = useState(total);
  const [pages, setPages] = useState(1);
  const [busy, setBusy] = useState(all);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);
  const [selected, setSelected] = useState<TransactionSnapshot | null>(null);
  const panel = useRef<HTMLDivElement>(null);
  const jumpToStart = useRef(all);
  const lastSelected = useRef<string | null>(null);
  useEffect(() => {
    if (!expanded) return;
    let active = true;
    setBusy(true); setError("");
    getTransactionPage(accessToken, account.id, page).then((result) => {
      if (!active) return;
      setLoaded(result.content); setCount(result.totalElements); setPages(result.totalPages);
    }).catch(() => active && setError("Transactions couldn’t be loaded. Please try again."))
      .finally(() => active && setBusy(false));
    return () => { active = false; };
  }, [expanded, account.id, page, retry, accessToken]);
  useEffect(() => {
    if (expanded && !busy && !error && jumpToStart.current) {
      const heading = panel.current?.querySelector<HTMLHeadingElement>("h3");
      heading?.focus({ preventScroll: true });
      heading?.scrollIntoView({ block: "start", behavior: "auto" });
      jumpToStart.current = false;
    }
  }, [expanded, busy, error, loaded]);
  useEffect(() => {
    if (selected) lastSelected.current = selected.id;
    else if (lastSelected.current) {
      const index = loaded.findIndex((item) => item.id === lastSelected.current);
      panel.current?.querySelectorAll<HTMLButtonElement>(".bank-transaction-row")[index]?.focus();
    }
  }, [selected]);
  if (selected) return <TransactionDetails transaction={selected} account={account} onBack={() => setSelected(null)} />;
  return <div ref={panel} class={all || lastSelected.current ? "bank-panel messenger-arrival" : "bank-panel"}>
    {onBack && <button type="button" class="bank-inline-action" onClick={onBack}>← Back to balances</button>}
    <SectionHeader title={expanded ? "Transactions" : "Recent transactions"} subtitle={`${account.displayName} ${safeMask(account.accountNumberMasked)}`} />
    {busy ? <p role="status" class="bank-secondary">Loading transactions…</p> : error ? <div role="alert"><p>{error}</p><button class="bank-inline-action" type="button" onClick={() => setRetry(retry + 1)}>Retry</button></div> : <>
      {loaded.length ? <BankingCollection type="TRANSACTIONS" count={loaded.length} attention={collectionNotice(loaded)} label="Transactions"><TransactionList items={loaded} onSelect={setSelected} /></BankingCollection> : <p class="bank-secondary">No transactions to show.</p>}
      {!expanded && count > loaded.length && <button type="button" class="bank-inline-action bank-view-all" onClick={() => { jumpToStart.current = true; setBusy(true); setExpanded(true); }}>View all transactions <span class="bank-secondary">({count})</span> →</button>}
      {expanded && pages > 1 && <nav class="bank-pagination" aria-label="Transaction pages"><button type="button" class="bank-inline-action" disabled={page === 0} onClick={() => { jumpToStart.current = true; setBusy(true); setPage(page - 1); }}>Previous</button><span>Page {page + 1} of {pages}</span><button type="button" class="bank-inline-action" disabled={page + 1 >= pages} onClick={() => { jumpToStart.current = true; setBusy(true); setPage(page + 1); }}>Next</button></nav>}
    </>}
  </div>;
}

function EmptySummary({ title }: { title: string }) {
 const messages: Record<string, string> = { "AutoPay mandates": "No mandates to show.", "Upcoming payments": "No scheduled payments.", "Your beneficiaries": "No saved beneficiaries.", "Your cards": "No cards to show.", "Your loans": "No loans to show.", "Your bills": "No bills to show.", "Account balances": "No accounts to show." };
 return <><SectionHeader title={title} /><p class="bank-secondary">{messages[title] || "No records to show."}</p></>;
}
export function MandateList({ content }: { content: Extract<BankingContent, { type: "MANDATES" }> }) {
 if (!content.mandates.length) return <EmptySummary title="AutoPay mandates" />;
 return <><SectionHeader title="AutoPay mandates" /><BankingCollection type="MANDATES" count={content.mandates.length} attention={collectionNotice(content.mandates)} label="AutoPay mandates">{content.mandates.map((item) => <section class="bank-summary-item" key={item.id}><div class="bank-item-heading"><h4>{item.payee}</h4><StatusBadge status={item.status} /></div><span class="bank-secondary">Up to</span><MoneyAmount amount={item.limit} currency={item.currencyCode} /><dl><DetailRow label="Frequency">{humanize(item.frequency)}</DetailRow><DetailRow label="Next debit">{item.nextDebit ? formatDate(item.nextDebit) : "Not scheduled"}</DetailRow><DetailRow label="From">{item.accountName} {safeMask(item.accountNumberMasked)}</DetailRow><DetailRow label="Reference">{item.id}</DetailRow></dl></section>)}</BankingCollection></>;
}
export function ScheduledPaymentList({ content }: { content: Extract<BankingContent, { type: "UPCOMING" }> }) {
 if (!content.payments.length) return <EmptySummary title="Upcoming payments" />;
 return <><SectionHeader title="Upcoming payments" /><BankingCollection type="SCHEDULED_PAYMENTS" count={content.payments.length} attention={collectionNotice(content.payments)} label="Upcoming payments">{content.payments.map((item) => <section class="bank-summary-item" key={item.id}><div class="bank-item-heading"><h4>{item.payee}</h4><MoneyAmount amount={item.amount} currency={item.currencyCode} /></div><div class="bank-item-heading"><span class="bank-secondary">{dueLabel(item.dueAt)}</span><StatusBadge status={item.status} /></div></section>)}</BankingCollection></>;
}
export function BeneficiaryList({ content }: { content: Extract<BankingContent, { type: "BENEFICIARIES" }> }) {
 if (!content.beneficiaries.length) return <EmptySummary title="Your beneficiaries" />;
 return <><SectionHeader title="Your beneficiaries" /><BankingCollection type="BENEFICIARIES" count={content.beneficiaries.length} attention={collectionNotice(content.beneficiaries)} label="Your beneficiaries">{content.beneficiaries.map((item) => <div class="bank-beneficiary" key={item.id}><div><h4>{item.displayName}</h4><p class="bank-secondary">{item.bankName} {safeMask(item.accountNumberMasked)}</p><StatusBadge status={item.status} /><p class="bank-secondary">Reference {item.id}</p></div></div>)}</BankingCollection></>;
}
export function CardSummary({ content }: { content: Extract<BankingContent, { type: "CARDS" }> }) {
 if (!content.cards.length) return <EmptySummary title="Your cards" />;
 return <><SectionHeader title="Your cards" /><BankingCollection type="CARDS" count={content.cards.length} attention={collectionNotice(content.cards)} label="Your cards">{content.cards.map((item) => <section class="bank-summary-item" key={item.id}><h4>{item.displayName} <span class="bank-secondary">{safeMask(item.numberMasked)}</span></h4><StatusBadge status={item.status} />{item.cardType === "DEBIT" ? <p class="bank-secondary">Debit card</p> : <dl><DetailRow label="Outstanding"><MoneyAmount amount={item.outstanding} currency={item.currencyCode} /></DetailRow><DetailRow label="Available limit"><MoneyAmount amount={item.availableLimit} currency={item.currencyCode} /></DetailRow><DetailRow label="Payment due">{item.dueAt ? formatDate(item.dueAt) : "Not scheduled"}</DetailRow></dl>}<p class="bank-secondary">Reference {item.id}</p></section>)}</BankingCollection></>;
}
export function LoanSummary({ content }: { content: Extract<BankingContent, { type: "LOANS" }> }) {
 if (!content.loans.length) return <EmptySummary title="Your loans" />;
 return <><SectionHeader title="Your loans" /><BankingCollection type="LOANS" count={content.loans.length} attention={collectionNotice(content.loans)} label="Your loans">{content.loans.map((item) => <section class="bank-summary-item" key={item.id}><h4>{item.displayName} <span class="bank-secondary">{safeMask(item.numberMasked)}</span></h4><span class="bank-secondary">Next EMI</span><MoneyAmount amount={item.nextEmi} currency={item.currencyCode} /><dl><DetailRow label="Due">{item.dueAt ? formatDate(item.dueAt) : "Not scheduled"}</DetailRow><DetailRow label="Outstanding"><MoneyAmount amount={item.outstanding} currency={item.currencyCode} /></DetailRow></dl><StatusBadge status={item.status} /></section>)}</BankingCollection></>;
}

export function BillList({ content }: { content: Extract<BankingContent, { type: "BILLS" }> }) {
 if (!content.bills.length) return <EmptySummary title="Your bills" />;
 return <><SectionHeader title="Your bills" /><BankingCollection type="BILLS" count={content.bills.length} attention={collectionNotice(content.bills)} label="Your bills">{content.bills.map(bill => <section class="bank-summary-item" key={bill.id}>
 <div class="bank-item-heading"><h4 dir="auto">{bill.billerName}</h4><MoneyAmount amount={bill.amount} currency={bill.currencyCode} /></div>
 <div class="bank-item-heading"><span class="bank-secondary">{bill.dueAt ? dueLabel(bill.dueAt) : "No due date"}</span><StatusBadge status={bill.status} /></div>
 <p class="bank-secondary">Reference {bill.id}</p>
 {bill.category && <p class="bank-secondary">{humanize(bill.category)}</p>}
 {bill.customerNumberMasked && <p class="bank-secondary">Customer {safeMask(bill.customerNumberMasked)}</p>}
 </section>)}</BankingCollection></>;
}
function BalancesResponse({ content, accessToken }: RendererProps<"ACCOUNTS">) {
 const [account, setAccount] = useState<AccountSnapshot | null>(null);
 return account ? <TransactionPanel key={account.id} account={account} accessToken={accessToken} all onBack={() => setAccount(null)} />
 : content.accounts.length ? <><SectionHeader title="Account balances" /><AccountSummary accounts={content.accounts} onTransactions={setAccount} /></> : <EmptySummary title="Account balances" />;
}
type RendererProps<K extends BankingContent["type"]> = { content: Extract<BankingContent, { type: K }>; accessToken: string };
// Register new data variants here without changing the conversation timeline.
const bankingRenderers: { [K in BankingContent["type"]]: (props: RendererProps<K>) => h.JSX.Element } = {
 INSIGHTS: ({ content }) => <><SectionHeader title="Spending summary" /><p>{content.meta.from} — {content.meta.to}</p><dl>{content.meta.categories.map(c => <DetailRow label={c.category}><MoneyAmount amount={c.amount} currency={c.currency} /></DetailRow>)}</dl><a href="#/transactions">Explore transaction history ↗</a></>,
 ACCOUNTS: BalancesResponse,
 TRANSACTIONS: ({ content, accessToken }) => <TransactionPanel account={content.account} items={content.transactions} total={content.totalElements} accessToken={accessToken} />,
 MANDATES: MandateList,
 UPCOMING: ScheduledPaymentList,
 SCHEDULED_PAYMENTS: ({ content }) => <ScheduledPaymentList content={{ ...content, type: "UPCOMING" }} />,
 BENEFICIARIES: BeneficiaryList,
 BILLS: BillList,
 CARDS: CardSummary,
 LOANS: LoanSummary,
 TEXT: () => <p>Ask another banking question to continue.</p>,
 ERROR: () => <p>Banking information could not be retrieved.</p>,
 ACTION_REQUIRED: ({ content }) => <><SectionHeader title="Payment details" />{content.action && <><StatusBadge status={content.action.status} /><dl>
 <DetailRow label="Action">{humanize(content.action.operation)}</DetailRow>
 <DetailRow label="Source">{content.action.accountId}</DetailRow><DetailRow label="Target">{content.action.targetId}</DetailRow>
 {content.action.amount != null && <DetailRow label="Amount"><MoneyAmount amount={content.action.amount} currency={content.action.currencyCode} /></DetailRow>}
 </dl><p class="bank-secondary">These details were checked only. Execution is not connected for this payment type. No money has moved and nothing has been cancelled.</p></>}</>,
 TRANSFER_STATUS: ({ content }) => <><SectionHeader title="Transfer status" /><MoneyAmount amount={content.transfer.amount} currency={content.transfer.currencyCode} /><StatusBadge status={content.transfer.status} /><dl><DetailRow label="Reference">{content.transfer.reference}</DetailRow></dl></>
};
export function supportsBankingContent(content: BankingContent): boolean {
 return content.version === 1 && Object.prototype.hasOwnProperty.call(bankingRenderers, content.type);
}
export function BankingResponse({ content, accessToken, capturedAt }: { content: BankingContent; accessToken: string; capturedAt: string }) {
 if (!supportsBankingContent(content)) return <p class="bank-secondary">This banking summary needs a newer version of Nexa.</p>;
 // The discriminant selects matching props; the union assertion stays at this boundary.
 const Renderer = bankingRenderers[content.type] as (props: { content: BankingContent; accessToken: string }) => h.JSX.Element;
 return <div class="bank-response"><Renderer content={content} accessToken={accessToken} /><p class="bank-as-of">Original summary · {formatDate(capturedAt, true)}</p></div>;
}
