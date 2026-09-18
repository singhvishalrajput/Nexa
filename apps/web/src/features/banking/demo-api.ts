import { authenticatedRequest } from "../../services/auth";

export type DemoRequest = {operation: string; accountId?: string; targetId: string; amount?: string};
export type DemoReceipt = DemoRequest & {id: string; status: string; currencyCode: string; expiresAt: string; completedAt?: string; reference?: string; simulated: boolean};
const base = "/demo/actions";
export const demoApi = {
  prepare: (token: string, request: DemoRequest) => authenticatedRequest<DemoReceipt>(base + "/prepare", token, {method: "POST", body: JSON.stringify(request)}),
  confirm: (token: string, id: string) => authenticatedRequest<DemoReceipt>(base + "/" + encodeURIComponent(id) + "/confirm", token, {method: "POST"}),
  cancel: (token: string, id: string) => authenticatedRequest<DemoReceipt>(base + "/" + encodeURIComponent(id) + "/cancel", token, {method: "POST"}),
  history: (token: string) => authenticatedRequest<DemoReceipt[]>(base, token)
};
