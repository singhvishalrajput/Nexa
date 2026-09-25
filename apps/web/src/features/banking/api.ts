import { authenticatedRequest, authenticatedCoreRequest } from "../../services/auth";
import { BankAccount, BankTransaction, TransactionPage, getAccounts } from "../../services/banking";
import { BillSnapshot, CardSnapshot, LoanSnapshot, MandateSnapshot, UpcomingSnapshot, BeneficiarySnapshot, TransactionSnapshot } from "../../services/banking-content";
export type { BankAccount, BankTransaction, TransactionPage };
export type ProductKind = "cards" | "bills" | "beneficiaries" | "mandates" | "loans" | "scheduled-payments";
export type Product = Partial<BillSnapshot & CardSnapshot & LoanSnapshot & MandateSnapshot & UpcomingSnapshot & BeneficiarySnapshot> & {
    id: string;
    status: string;
    accountId?: string;
    reference?: string;
    frequency?: string;
    nextDebit?: string;
    interestRate?: string;
    minimumAmount?: string;
    paymentHistory?: UpcomingSnapshot[];
    transactions?: TransactionSnapshot[];
};
export type PreparedAction = {
    operation: string;
    status: string;
    accountId: string;
    targetId: string;
    amount: string | null;
    currencyCode: string;
    executionAvailable: boolean;
    confirmationRequired: boolean;
};
export type Filters = {
    search?: string;
    category?: string;
    from?: string;
    to?: string;
    direction?: string;
    page?: number;
};
export const bankApi = {
    billFundingAccounts: (token: string) => authenticatedRequest<BankAccount[]>("/cards/bill-funding-accounts", token),
    accounts: getAccounts,
    account: (token: string, id: string) => authenticatedRequest<BankAccount>("/accounts/" + encodeURIComponent(id), token),
    transactions: (token: string, accountId: string, filters: Filters = {}) => {
        const params = new URLSearchParams({ accountId, size: "15" });
        Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== "")
            params.set(key, String(value)); });
        return authenticatedRequest<TransactionPage>("/transactions?" + params, token);
    },
    transaction: (token: string, id: string) => authenticatedRequest<BankTransaction>("/transactions/" + encodeURIComponent(id), token),
    products: (token: string, kind: ProductKind, page = 0, status = "") => authenticatedRequest<Product[]>("/" + kind + "?" + new URLSearchParams({ page: String(page), size: "12", ...(status ? { status } : {}) }), token),
    product: (token: string, kind: ProductKind, id: string) => authenticatedRequest<Product>("/" + kind + "/" + encodeURIComponent(id), token),
    prepare: (token: string, request: {
        operation: string;
        accountId: string;
        targetId: string;
        amount?: string;
    }) => authenticatedRequest<PreparedAction>("/actions/prepare", token, { method: "POST", body: JSON.stringify(request) }),
    createBill: (token: string, request: { billerName: string; amount: string; minimumAmount?: string; dueAt: string; category: string; customerNumber: string }) => authenticatedRequest<Product>("/bills", token, { method: "POST", body: JSON.stringify(request) }),
    setProductStatus: (token: string, kind: "bills" | "mandates", id: string, status: string) => authenticatedRequest<Product>("/" + kind + "/" + encodeURIComponent(id) + "/status", token, { method: "POST", body: JSON.stringify({ status }) }),
    coreAccounts: (token: string) => authenticatedCoreRequest<CoreAccount[]>("/accounts", token),
    postTransaction: (token: string, operation: "deposit" | "withdraw" | "transfer", payload: {
        sourceAccountId?: number;
        destinationAccountId?: number;
        amount: string;
    }) => authenticatedCoreRequest<{
        id: string;
        status: string;
        amount: number;
    }>("/transactions/" + operation, token, { method: "POST", body: JSON.stringify(payload) })
};
export type CoreAccount = {
    id: number;
    accountNumber: string;
    accountName: string;
    accountCategory: string;
    accountType: string;
    balance: number;
    status: string;
    currencyCode: string;
};
