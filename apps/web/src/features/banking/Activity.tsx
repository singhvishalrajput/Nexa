import { useMemo } from "preact/hooks";
import ArrayDataProvider = require("ojs/ojarraydataprovider");
import "ojs/ojlistview";
import { ojListView } from "ojs/ojlistview";
import "oj-c/meter-bar";
import { BankTransaction } from "./api";
import { Status } from "./ui";
import { t } from "../../services/locale";
import { completedStatuses, dayLabel, formatDate, formatMoney, humanize, moneyInMinorUnits, statusPresentation, transactionDirection } from "../../services/banking-content";
import { minorUnitsDecimal, summarizeActivity } from "../../services/activity-summary";

export function TransactionRow({item}: {item: BankTransaction}) {
  const completed = completedStatuses.includes(item.status);
  const units = moneyInMinorUnits(item.amount);
  const incoming = units !== null && units > BigInt(0);
  const merchant = item.merchantName || humanize(item.type);
  const direction = transactionDirection(item);
  // A failed or pending amount describes a request, not money already moved.
  const amount = formatMoney(!completed && units !== null ? minorUnitsDecimal(units < BigInt(0) ? -units : units) : item.amount, item.currencyCode, completed);
  return <a class="activity-row" href={"#/transactions/" + encodeURIComponent(item.id)} aria-label={`${merchant}, ${amount}, ${direction}, ${statusPresentation(item.status).label}, ${formatDate(item.occurredAt, true)}. ${t("View details")}`}>
    <span class={`activity-icon ${completed && incoming ? "activity-credit" : ""}`} aria-hidden="true">{["FAILED", "CANCELLED", "EXPIRED"].includes(item.status) ? "×" : incoming ? "↙" : "↗"}</span>
    <span class="activity-identity"><strong dir="auto">{merchant}</strong><span><span dir="auto">{item.category ? humanize(item.category) : humanize(item.type)}</span><span aria-hidden="true"> · </span><time dateTime={item.occurredAt}>{dayLabel(item.occurredAt)}</time></span></span>
    <span class="activity-value"><strong class={completed && incoming ? "activity-credit" : ""}>{amount}</strong><span class="activity-outcome">{completed ? direction : <Status value={item.status}/>}</span></span>
    <span class="activity-chevron" aria-hidden="true">›</span>
  </a>;
}

export function TransactionList({items, compact = false}: {items: BankTransaction[]; compact?: boolean}) {
  const provider = useMemo(() => new ArrayDataProvider<string, BankTransaction>(items, {keyAttributes: "id"}), [items]);
  return <div class={`activity-list ${compact ? "activity-list-compact" : ""}`}>
    {!compact && <div class="activity-list-heading" aria-hidden="true"><span>{t("Transaction")}</span><span>{t("Amount / status")}</span></div>}
    <oj-list-view class="activity-jet-list" data={provider} selectionMode="none" drillMode="none" scrollPolicy="loadAll" gridlines={{item: "hidden"}} item={{enterKeyFocusBehavior: "focusable"}} aria-label={t(compact ? "Recent activity" : "Transaction history")}>
      <template slot="itemTemplate" render={(context: ojListView.ItemTemplateContext<string, BankTransaction>) => <TransactionRow item={context.data}/>}/>
    </oj-list-view>
  </div>;
}

export function ActivitySummary({items, currency, accountName}: {items: BankTransaction[]; currency: string; accountName: string}) {
  const summary = summarizeActivity(items, currency);
  if (!summary) return <p class="activity-summary-empty" role="status">{t("This activity summary is unavailable. View transaction history for details.")}</p>;
  return <div class="activity-summary-body">
    <p class="activity-summary-scope">{accountName} · {t("Latest")} {items.length} {t(items.length === 1 ? "transaction" : "transactions")}</p>
    <div class="activity-totals">
      <div><span>{t("Money in")}</span><strong class="activity-credit">{formatMoney(minorUnitsDecimal(summary.incoming), currency)}</strong></div>
      <div><span>{t("Money out")}</span><strong>{formatMoney(minorUnitsDecimal(summary.outgoing), currency)}</strong></div>
    </div>
    <div class="activity-category-heading"><h3>{t("Spending by category")}</h3><span>{t("Share of money out")}</span></div>
    {summary.categories.length ? <ul class="activity-categories" aria-label={t("Spending by category")}>{summary.categories.map(row => <li key={row.category}>
      <div><span dir="auto">{humanize(row.category)}</span><strong>{formatMoney(minorUnitsDecimal(row.units), currency)}</strong><span class="activity-category-percent">{row.percent.toFixed(1)}%</span></div>
      <oj-c-meter-bar value={row.percent} min={0} max={100} readonly size="sm" color="var(--nexa-accent-text)" plotArea={{color: "var(--nexa-hover)"}} aria-label={`${humanize(row.category)}: ${row.percent.toFixed(1)}%`}/>
    </li>)}</ul> : <p class="activity-summary-empty">{t("No completed spending in these transactions.")}</p>}
    <p class="activity-summary-note">{t("Completed transactions only. Pending and failed payments are excluded.")}</p>
  </div>;
}
