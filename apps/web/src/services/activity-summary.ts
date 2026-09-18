import { completedStatuses, moneyInMinorUnits, TransactionSnapshot } from "./banking-content";

export const minorUnitsDecimal = (units: bigint): string => {
  const absolute = units < BigInt(0) ? -units : units;
  return `${units < BigInt(0) ? "-" : ""}${absolute / BigInt(100)}.${String(absolute % BigInt(100)).padStart(2, "0")}`;
};

/** A snapshot of the supplied records, never a monthly or all-time total. */
export function summarizeActivity(items: TransactionSnapshot[], currency: string) {
  let incoming = BigInt(0), outgoing = BigInt(0), unsettled = 0;
  const categories = new Map<string, {category: string; units: bigint}>();
  for (const item of items) {
    if (!completedStatuses.includes(item.status)) { unsettled++; continue; }
    const units = moneyInMinorUnits(item.amount);
    // Do not display a plausible partial total when a completed amount is unusable.
    if (units === null || item.currencyCode !== currency) return null;
    if (units > BigInt(0)) incoming += units;
    if (units < BigInt(0)) {
      outgoing -= units;
      const category = item.category?.trim().replace(/_/g, " ").replace(/\s+/g, " ") || "Uncategorized";
      const key = category.toLocaleLowerCase("en");
      const row = categories.get(key) || {category, units: BigInt(0)};
      row.units -= units;
      categories.set(key, row);
    }
  }
  const sorted = [...categories.values()].sort((a, b) => a.units === b.units ? a.category.localeCompare(b.category) : a.units > b.units ? -1 : 1);
  const visible = sorted.length > 4 ? [...sorted.slice(0, 3), {category: "Other categories", units: sorted.slice(3).reduce((sum, row) => sum + row.units, BigInt(0))}] : sorted;
  return {incoming, outgoing, unsettled, categories: visible.map(row => ({...row, percent: outgoing ? Number(row.units * BigInt(1000) / outgoing) / 10 : 0}))};
}
