import { BankingContent, formatDate, formatMoney, moneyInMinorUnits } from "../../services/banking-content";
import { t } from "../../services/locale";

type Category = Extract<BankingContent, {type: "INSIGHTS"}>["meta"]["categories"][number];
/** Keep currencies separate and arithmetic exact; percentages are presentation only. */
export function spendingGroups(categories: Category[]) {
  const groups = new Map<string, { currency: string; total: bigint; rows: {category: string; units: bigint}[] }>();
  for (const row of categories) {
    const units = moneyInMinorUnits(row.amount);
    if (units === null || units < BigInt(0)) return null;
    const group = groups.get(row.currency) || {currency: row.currency, total: BigInt(0), rows: []};
    group.total += units; group.rows.push({category: row.category, units}); groups.set(row.currency, group);
  }
  return [...groups.values()].map(group => ({...group, rows: group.rows.sort((a, b) => a.units === b.units ? 0 : a.units > b.units ? -1 : 1)}));
}
const decimal = (units: bigint) => `${units / BigInt(100)}.${String(units % BigInt(100)).padStart(2, "0")}`;
export function SpendingSummary({ content }: {content: Extract<BankingContent, {type: "INSIGHTS"}>}) {
  const groups = spendingGroups(content.meta.categories);
  return <section class="conversation-insights" aria-label={t("Spending summary")}>
    <header class="bank-section-header"><h3>{t("Spending summary")}</h3><p>{formatDate(content.meta.from)} — {formatDate(content.meta.to)}</p></header>
    {groups === null ? <p role="alert">{t("Amount unavailable")}</p> : !groups.length ? <p>{t("No spending in this period.")}</p> : groups.map(group => <div key={group.currency}>
      <span>{t("Total spending")}</span><strong class="conversation-spending-total">{formatMoney(decimal(group.total), group.currency)}</strong>
      <ul aria-label={t("Category breakdown")}>{group.rows.map((row, index) => {
        const percent = group.total ? Number(row.units * BigInt(10000) / group.total) / 100 : 0;
        return <li key={index}><div><span>{row.category}</span><strong>{formatMoney(decimal(row.units), group.currency)}</strong></div><div class="conversation-bar" aria-hidden="true"><span style={{width: `${percent}%`}}/></div><small>{percent.toFixed(1)}%</small></li>;
      })}</ul>
    </div>)}
    <p class="bank-secondary">{t("Previous-period comparison is not available.")}</p><a href="#/transactions">{t("Explore transaction history ↗")}</a>
  </section>;
}
