import { authenticatedRequest } from "./auth";
import { Product } from "../features/banking/api";

export type CardApplicationRequest = {
  accountId: number;
  cardType: "DEBIT" | "CREDIT";
  requestId: string;
  displayName?: string;
};
export type CardApplication = {
  id: string;
  displayName: string;
  cardType: "DEBIT" | "CREDIT";
  status: string;
  accountId: string | number;
  applicantName: string;
  applicantUserId: string;
  createdAt: string;
  creditLimit: string | number;
  reviewedAt: string | null;
  reviewReason: string | null;
};
export const cardApplications = {
  apply: (token: string, request: CardApplicationRequest) => authenticatedRequest<Product>("/cards/applications", token, {method: "POST", body: JSON.stringify(request)}),
  card: (token: string, id: string) => authenticatedRequest<Product>("/cards/" + encodeURIComponent(id), token),
  status: (token: string, id: string, action: "block" | "unblock") => authenticatedRequest<Product>(`/cards/${encodeURIComponent(id)}/${action}`, token, {method: "POST"}),
  queue: (token: string, status = "PENDING_APPROVAL") => authenticatedRequest<CardApplication[]>("/admin/card-applications?" + new URLSearchParams({status}), token),
  application: (token: string, id: string) => authenticatedRequest<CardApplication>("/admin/card-applications/" + encodeURIComponent(id), token),
  decide: (token: string, id: string, decision: "approve" | "reject", request: {creditLimit?: string; reason: string}) => authenticatedRequest<CardApplication>(`/admin/card-applications/${encodeURIComponent(id)}/${decision}`, token, {method: "POST", body: JSON.stringify(request)})
};

export const localCardNotice = "These are Nexa card records. Physical cards and card-network payments are not issued here.";
