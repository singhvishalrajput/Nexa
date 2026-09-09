import { authenticatedRequest } from "./auth";

export type BankAccount = {
  id: string;
  displayName: string;
  accountNumberMasked: string;
  accountType: "SAVINGS" | "CURRENT";
  currencyCode: "INR";
  availableBalance: number;
  ledgerBalance: number;
  status: "ACTIVE" | "FROZEN" | "CLOSED";
  updatedAt: string;
};

export type BankTransaction = {
  id: string;
  accountId: string;
  reference: string;
  type: string;
  merchantName: string | null;
  category: string | null;
  amount: number;
  currencyCode: string;
  status: string;
  occurredAt: string;
};

type TransactionPage = {
  content: BankTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function getAccounts(accessToken: string): Promise<BankAccount[]> {
  return authenticatedRequest<BankAccount[]>("/accounts", accessToken);
}

export function openAccount(
  accessToken: string,
  account: { displayName: string; accountType: "SAVINGS" | "CURRENT" }
): Promise<BankAccount> {
  return authenticatedRequest<BankAccount>("/accounts", accessToken, {
    method: "POST",
    body: JSON.stringify({ ...account, currencyCode: "INR" })
  });
}

export async function getTransactions(accessToken: string, accountId: string): Promise<BankTransaction[]> {
  const page = await authenticatedRequest<TransactionPage>(`/accounts/${encodeURIComponent(accountId)}/transactions?size=100`, accessToken);
  return page.content;
}
