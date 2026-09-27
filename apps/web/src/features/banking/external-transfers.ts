import { authenticatedRequest } from "../../services/auth";

export type ExternalTransferRequest = { requestKey: string; sourceAccountId: string; payeeId: string; amount: string };
export type ExternalTransferReadiness = { ready: boolean; environment: string; provider: string; reason: string | null };
export type ExternalTransferReceipt = {
    id: string; environment: "SANDBOX"; provider: string; sourceAccountId: string; sourceName: string; sourceMasked: string;
    payeeId: string; recipientName: string; bankName: string; destinationMasked: string; ifsc: string; amount: string; currencyCode: string;
    status: "READY" | "SUBMITTING" | "PENDING" | "COMPLETED" | "FAILED" | "CANCELLED" | "EXPIRED" | "REVERSED";
    providerTransferId: string | null; providerStatus: string | null; statusCode: string | null; utr: string | null;
    expiresAt: string; completedAt: string | null; failureReason: string | null; updatedAt: string;
};
const endpoint = "/external-transfers";
export const externalTransfers = {
    readiness: (token: string) => authenticatedRequest<ExternalTransferReadiness>(endpoint + "/readiness", token, {cache: "no-store"}),
    prepare: (token: string, request: ExternalTransferRequest) => authenticatedRequest<ExternalTransferReceipt>(endpoint + "/prepare", token, {method: "POST", body: JSON.stringify(request)}),
    status: (token: string, id: string) => authenticatedRequest<ExternalTransferReceipt>(endpoint + "/" + encodeURIComponent(id), token, {cache: "no-store"}),
    confirm: (token: string, id: string) => authenticatedRequest<ExternalTransferReceipt>(endpoint + "/" + encodeURIComponent(id) + "/confirm", token, {method: "POST"}),
    cancel: (token: string, id: string) => authenticatedRequest<ExternalTransferReceipt>(endpoint + "/" + encodeURIComponent(id) + "/cancel", token, {method: "POST"}),
    refresh: (token: string, id: string) => authenticatedRequest<ExternalTransferReceipt>(endpoint + "/" + encodeURIComponent(id) + "/refresh", token, {method: "POST"}),
    history: (token: string) => authenticatedRequest<ExternalTransferReceipt[]>(endpoint, token, {cache: "no-store"})
};
