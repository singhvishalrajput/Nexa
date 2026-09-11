import { authenticatedRequest } from "./auth";

export type BankAccount = {
  id: string;
  displayName: string;
  accountNumberMasked: string;
  accountType: "SAVINGS" | "CURRENT";
  currencyCode: "INR";
  availableBalance: number;
  ledgerBalance: number;
  status: "ACTIVE" | "BLOCKED" | "CLOSED";
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

export type TransactionPage = {
  content: BankTransaction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function getTransactionPage(accessToken: string, accountId: string, page = 0): Promise<TransactionPage> {
  return authenticatedRequest<TransactionPage>(`/accounts/${encodeURIComponent(accountId)}/transactions?size=10&page=${page}`, accessToken);
}

export function getAccounts(accessToken: string): Promise<BankAccount[]> {
  return authenticatedRequest<BankAccount[]>("/accounts", accessToken);
}

export function openAccount(
  accessToken: string,
  account: { displayName: string; accountType: "SAVINGS" | "CURRENT"; dateOfBirth: string; address: string }
): Promise<BankAccount> {
  return authenticatedRequest<BankAccount>("/accounts", accessToken, {
    method: "POST",
    body: JSON.stringify({ ...account, currencyCode: "INR" })
  });
}
