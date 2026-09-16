import { authenticatedRequest } from "../../services/auth";
export type TransferReceipt = {
  id: string; sourceAccountId: string; sourceName: string; sourceMasked: string;
  recipientName: string; destinationMasked: string; amount: string; currencyCode: string;
  status: "READY" | "COMPLETED" | "EXPIRED"; reference: string | null;
  expiresAt: string; completedAt: string | null;
};
export type TransferDraft = { sourceAccountId: string; destinationAccountId?: string; destinationAccountNumber?: string; amount: string };
export const moneyTransfers = {
  prepare: (token: string, draft: TransferDraft) => authenticatedRequest<TransferReceipt>("/money-transfers/prepare", token, {method: "POST", body: JSON.stringify(draft)}),
  confirm: (token: string, id: string) => authenticatedRequest<TransferReceipt>("/money-transfers/" + encodeURIComponent(id) + "/confirm", token, {method: "POST"}),
  status: (token: string, id: string) => authenticatedRequest<TransferReceipt>("/money-transfers/" + encodeURIComponent(id), token),
};
