import type { Turn } from "./conversations";
import type { Locale } from "./locale";
import { formatMoney, safeMask } from "./banking-content";
import { accountDisplayName, replyForSpeech } from "./reply-localization";

const hindiStatuses: Record<string, string> = {
  ACTIVE: "सक्रिय", BLOCKED: "ब्लॉक किया गया", CLOSED: "बंद", PAUSED: "रोका गया",
  POSTED: "पूरा हुआ", SUCCESS: "सफल", COMPLETED: "पूरा हुआ", PAID: "भुगतान हो गया",
  PENDING: "लंबित", DUE: "देय", OVERDUE: "समय सीमा पार", UPCOMING: "आगामी",
  SCHEDULED: "निर्धारित", FAILED: "असफल", CANCELLED: "रद्द", EXPIRED: "समाप्त",
  PROCESSING: "प्रक्रिया में", ACTION_REQUIRED: "कार्रवाई आवश्यक", APPROVED: "स्वीकृत",
  REJECTED: "अस्वीकृत", PENDING_APPROVAL: "स्वीकृति लंबित", PENDING_VERIFICATION: "सत्यापन लंबित"
};

/** Localize known copy for narration, keeping stored replies and banking data intact. */
export function spokenResponse(turn: Pick<Turn, "assistantText" | "banking">, locale: Locale): string {
  const data = turn.banking;
  const hindi = locale === "hi-IN";
  const status = (value: string) => hindi ? hindiStatuses[value] || value : value.toLowerCase().replace(/_/g, " ");
  const money = (value: string | number, currency: string, signed = false) => {
    const formatted = formatMoney(value, currency, signed);
    return hindi && currency === "INR" && formatted.includes("₹") ? formatted.replace("₹", "") + " रुपये" : formatted;
  };
  const date = (value: string) => {
    const parsed = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? value + "T00:00:00" : value);
    return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString(locale, { day: "numeric", month: "long", year: "numeric" });
  };
  let details: string[] = [];
  if (data?.type === "ACCOUNTS") details = data.accounts.map(account => {
    const tail = (account.accountNumberMasked || "").replace(/\D/g, "").slice(-4);
    return hindi
      ? `${accountDisplayName(account.displayName, locale)}${tail ? ", खाते के अंतिम अंक " + tail.split("").join(" ") : ""}। उपलब्ध बैलेंस ${money(account.availableBalance, account.currencyCode)}।`
      : `${account.displayName}, account ending ${safeMask(account.accountNumberMasked).slice(-4)}. Available balance ${money(account.availableBalance, account.currencyCode)}.`;
  });
  if (data?.type === "TRANSACTIONS") details = data.transactions.map(item => `${item.merchantName || (hindi ? "लेन-देन" : item.type)}, ${money(item.amount, item.currencyCode, true)}, ${status(item.status)}.`);
  if (data?.type === "MANDATES") details = data.mandates.map(item => `${item.payee}, ${hindi ? "अधिकतम" : "up to"} ${money(item.limit, item.currencyCode)}, ${status(item.status)}.`);
  if (data?.type === "BILLS") details = data.bills.map(item => `${item.billerName}, ${money(item.amount, item.currencyCode)}, ${status(item.status)}.`);
  if (data?.type === "CARDS") details = data.cards.map(item => `${item.displayName}, ${status(item.status)}${item.cardType === "DEBIT" ? "" : ", " + (hindi ? "बकाया " : "outstanding ") + money(item.outstanding, item.currencyCode)}.`);
  if (data?.type === "BENEFICIARIES") details = data.beneficiaries.map(item => `${item.displayName}, ${status(item.status)}.`);
  if (data?.type === "SCHEDULED_PAYMENTS" || data?.type === "UPCOMING") details = data.payments.map(item => `${item.payee}, ${money(item.amount, item.currencyCode)}, ${date(item.dueAt)}, ${status(item.status)}.`);
  if (data?.type === "LOANS") details = data.loans.map(item => `${item.displayName}, ${hindi ? "अगली किस्त" : "next EMI"} ${money(item.nextEmi, item.currencyCode)}, ${status(item.status)}.`);
  return [replyForSpeech(turn.assistantText, locale), ...details].filter(Boolean).join(" ");
}
