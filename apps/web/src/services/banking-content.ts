import { getLocale, t } from "./locale";
export type Money = string | number;
export type AccountSnapshot = {
  id: string; displayName: string; accountNumberMasked: string; accountType: string;
  currencyCode: string; availableBalance: Money; currentBalance?: Money; status: string; updatedAt: string;
};
export type TransactionSnapshot = {
  id: string; accountId: string; reference: string; type: string; merchantName: string | null;
  category: string | null; amount: Money; currencyCode: string; status: string; occurredAt: string;
};
export type MandateSnapshot = {
  id: string; payee: string; status: string; limit: Money; currencyCode: string;
  frequency: string; nextDebit?: string; accountName: string; accountNumberMasked: string;
};
export type UpcomingSnapshot = {
  id: string; payee: string; amount: Money; currencyCode: string; dueAt: string; status: string;
};
export type BeneficiarySnapshot = {
  id: string; displayName: string; bankName?: string; accountNumberMasked: string; status: string;
};
export type CardSnapshot = {
  id: string; displayName: string; numberMasked: string; outstanding: Money;
  cardType?: string; creditLimit?: Money; minimumPayment?: Money; availableLimit: Money; currencyCode: string; dueAt?: string; status: string;
};
export type LoanSnapshot = {
  id: string; displayName: string; numberMasked: string; nextEmi: Money;
  outstanding: Money; currencyCode: string; dueAt?: string; status: string;
};
export type BillSnapshot = {
  id: string; billerName: string; amount: Money; currencyCode: string; status: string;
  dueAt?: string; category?: string; customerNumberMasked?: string;
};
// Versioned views returned by the authenticated conversational domain router.
// Action views describe preparation only; execution is not connected.
export type BankingContent = { version: 1; responseType?: string; errorCode?: string } & (
  { type: "INSIGHTS"; meta: {categories: Array<{category: string; currency: string; amount: string}>; from: string; to: string} } |
  { type: "ACCOUNTS"; accounts: AccountSnapshot[] } |
  { type: "TRANSACTIONS"; account: AccountSnapshot; transactions: TransactionSnapshot[]; totalElements: number } |
  { type: "MANDATES"; mandates: MandateSnapshot[] } |
  { type: "UPCOMING"; payments: UpcomingSnapshot[] } |
  { type: "SCHEDULED_PAYMENTS"; payments: UpcomingSnapshot[] } |
  { type: "BILLS"; bills: BillSnapshot[] } |
  { type: "BENEFICIARIES"; beneficiaries: BeneficiarySnapshot[] } |
  { type: "CARDS"; cards: CardSnapshot[] } |
  { type: "LOANS"; loans: LoanSnapshot[] } |
  { type: "ACTION_REQUIRED"; action?: { operation: string; status: string; accountId: string; targetId: string; amount?: Money; currencyCode: string; confirmationRequired: boolean; executionAvailable: boolean } } |
  { type: "TRANSFER_STATUS"; transfer: { id: string; reference: string; amount: Money; currencyCode: string; status: string } } |
  { type: "TEXT" } | { type: "ERROR" }
);

/** Round decimal strings without losing paise to a floating-point conversion. */
export function moneyInMinorUnits(value: Money): bigint | null {
  const raw = typeof value === "number" && Number.isFinite(value) ? value.toFixed(4) : String(value);
  const match = raw.match(/^(-?)(\d+)(?:\.(\d+))?$/);
  if (!match) return null;
  const fraction = (match[3] || "").padEnd(3, "0");
  let paise = BigInt(match[2]) * BigInt(100) + BigInt(fraction.slice(0, 2));
  if (Number(fraction[2]) >= 5) paise += BigInt(1);
  return match[1] ? -paise : paise;
}
export function formatMoney(value: Money, currency = "INR", signed = false): string {
  const units = moneyInMinorUnits(value);
  if (units === null) return t("Amount unavailable");
  const paise = units < BigInt(0) ? -units : units;
  const whole = (paise / BigInt(100)).toLocaleString(getLocale());
  const decimals = String(paise % BigInt(100)).padStart(2, "0");
  const prefix = paise === BigInt(0) ? "" : units < BigInt(0) ? "− " : signed ? "+ " : "";
  return `${prefix}${currency === "INR" ? "₹" : currency + " "}${whole}${decimals === "00" ? "" : "." + decimals}`;
}

export const safeMask = (value: string) => {
  const tail = value.replace(/\D/g, "").slice(-4);
  return tail ? `•••• ${tail}` : t("Number unavailable");
};
export const humanize = (value: string) => t(value.replace(/_/g, " ").toLowerCase().replace(/^./, (letter) => letter.toUpperCase()));
export const completedStatuses = ["SUCCESS", "POSTED", "COMPLETED", "PAID"];
export function statusPresentation(value: string) {
  const label = value === "PAID" ? "Paid" : completedStatuses.includes(value) ? "Completed" : value === "PREPARED" ? "Details checked" : value === "PENDING_VERIFICATION" ? "Awaiting verification" : humanize(value);
  const tone = completedStatuses.includes(value) || value === "ACTIVE" ? "good" : ["FAILED", "OVERDUE", "BLOCKED", "ACTION_REQUIRED"].includes(value) ? "bad" : ["PENDING", "DUE", "UPCOMING", "SCHEDULED", "PROCESSING", "PREPARED", "PENDING_VERIFICATION"].includes(value) ? "pending" : "neutral";
  return { label: t(label), tone };
}
function calendarDate(value: string): Date {
  return new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? value + "T00:00:00" : value);
}
export function transactionDirection(item: Pick<TransactionSnapshot, "amount" | "status">): string {
  if (["FAILED", "CANCELLED", "EXPIRED"].includes(item.status)) return t("Not completed");
  if (Number(item.amount) === 0) return t("No money moved");
  const completed = completedStatuses.includes(item.status);
  return t(Number(item.amount) > 0 ? completed ? "Received" : "Incoming" : completed ? "Spent" : "Outgoing");
}
export function dueLabel(value: string, now = new Date()): string {
  const date = calendarDate(value);
  const tomorrow = new Date(now); tomorrow.setDate(now.getDate() + 1);
  if (date.toDateString() === now.toDateString()) return t("Due today");
  if (date.toDateString() === tomorrow.toDateString()) return t("Due tomorrow");
  return `${t("Due")} ${formatDate(value)}`;
}
export const formatDate = (value: string, time = false) => {
  const date = calendarDate(value);
  return Number.isNaN(date.getTime()) ? t("Date unavailable") : date.toLocaleString(getLocale(), time
    ? { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" }
    : { day: "numeric", month: "short", year: "numeric" });
};
export function dayLabel(value: string, now = new Date()): string {
  const date = calendarDate(value);
  if (Number.isNaN(date.getTime())) return t("Date unavailable");
  const yesterday = new Date(now); yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === now.toDateString()) return t("Today");
  if (date.toDateString() === yesterday.toDateString()) return t("Yesterday");
  return formatDate(value);
}
