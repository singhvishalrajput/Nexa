import { t } from "../../services/locale";
import { ComponentChildren } from "preact";

/** Object collections compare side by side; records read from top to bottom. */
export function collectionLayout(type: string, count: number): "direct" | "comparison" | "vertical" {
  if (count < 2) return "direct";
  return ["ACCOUNTS", "CARDS", "LOANS"].includes(type) ? "comparison" : "vertical";
}

export function collectionNotice(items: { status: string }[]): string {
  const labels: Record<string, string> = { FAILED: "failed", OVERDUE: "overdue", ACTION_REQUIRED: "need attention", PENDING: "pending", PENDING_VERIFICATION: "awaiting verification", BLOCKED: "blocked" };
  return Object.entries(labels).map(([status, label]) => {
    const count = items.filter(item => item.status === status).length;
    return count ? count + " " + t(label) : "";
  }).filter(Boolean).join(" · ");
}

export function BankingCollection({ type, count, label, attention, children }: {
  type: string; count: number; label: string; attention?: string; children: ComponentChildren;
}) {
  const layout = collectionLayout(type, count);
  // CSS wraps comparisons into one column on phones. The feed is the only scroll area.
  return <section class={"bank-collection is-" + layout} aria-label={label}>
    <div class="bank-collection-caption"><span>{count} {count === 1 ? t("item") : t("items")}</span></div>
    {attention && <p class="bank-collection-attention">{attention}</p>}
    <div class="bank-collection-items">{children}</div>
  </section>;
}
