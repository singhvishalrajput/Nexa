import { ApiRequestError, authenticatedRequest } from "./auth";
import { LoanSnapshot, Money, moneyInMinorUnits } from "./banking-content";

export type LoanApplicationDraft = { applicationKey: string; accountId: number; purpose: string; amount: string; tenureMonths: number };
export type LoanApplication = LoanSnapshot & {
  accountId: string;
  terms: { applicationKey: string; purpose: string; amount: Money; tenureMonths: number };
};
export function chatLoanApplicationKey(clientId: string): string | null {
  return /^[A-Za-z0-9_-]{1,70}$/.test(clientId || "") ? "chat-loan-" + clientId : null;
}
export function verifiedLoanApplication(value: unknown, applicationKey: string, draft?: LoanApplicationDraft): LoanApplication {
  const loan = value as LoanApplication | null;
  if (!loan || typeof loan.id !== "string" || !loan.id.trim() || !/^[1-9]\d*$/.test(String(loan.accountId))
      || !["PENDING_APPROVAL", "APPROVED", "REJECTED", "ACTIVE", "OVERDUE", "PAID", "CLOSED"].includes(loan.status)
      || loan.terms?.applicationKey !== applicationKey
      || draft && (String(loan.accountId) !== String(draft.accountId) || loan.terms.purpose !== draft.purpose.trim()
        || loan.terms.tenureMonths !== draft.tenureMonths || moneyInMinorUnits(loan.terms.amount) !== moneyInMinorUnits(draft.amount))) {
    throw new Error("The returned application did not match this request. Check its status before trying again.");
  }
  return loan;
}
export async function findLoanApplication(token: string, applicationKey: string): Promise<LoanApplication | null> {
  try {
    return verifiedLoanApplication(await authenticatedRequest<unknown>("/loans/applications/by-request/" + encodeURIComponent(applicationKey), token), applicationKey);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) return null;
    throw error;
  }
}
