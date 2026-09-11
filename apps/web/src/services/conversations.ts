import { authenticatedRequest } from "./auth";
import { BankingContent } from "./banking-content";

export type Conversation = { id: string; title: string; createdAt: string };
export type Workflow = { version: number; id: string; operation: string; status: "COLLECTING" | "REVIEW" | "COMPLETED" | "CANCELLED" | "EXPIRED" | "UNAVAILABLE"; field?: string; accountLabel?: string; targetLabel?: string; amount?: string; currency?: string; message: string; choices: Array<{id: string; label: string}>; expiresAt: string; confirmationRequired: boolean; executionAvailable: boolean; reference?: string };
export type ActionCommand = { actionId: string; type: "SELECT" | "CONFIRM" | "CANCEL"; value?: string };
export type Turn = { id: string; clientId: string; source: "TEXT" | "VOICE"; intent: string; userText: string; assistantText: string; createdAt: string; workflow?: Workflow | null; banking?: BankingContent | null; response?: { type: string; message: string; data?: BankingContent | null; errorCode?: string | null; meta?: { intent: string; confidence: number } | null } };
export type TurnRequest = { clientId: string; source: "TEXT" | "VOICE"; text: string; action?: ActionCommand };
export const listConversations = (token: string, page = 0) => authenticatedRequest<Conversation[]>(`/conversations?page=${page}`, token);
export const createConversation = (token: string) => authenticatedRequest<Conversation>("/conversations", token, { method: "POST" });
export const loadTurns = (token: string, id: string, before?: string) => authenticatedRequest<Turn[]>(`/conversations/${encodeURIComponent(id)}/turns${before ? `?before=${encodeURIComponent(before)}` : ""}`, token);
export const sendTurn = (token: string, id: string, request: TurnRequest) => request.action
  ? authenticatedRequest<Turn>(`/conversations/${encodeURIComponent(id)}/actions/${encodeURIComponent(request.action.actionId)}`, token, {method: "POST", body: JSON.stringify({clientId: request.clientId, type: request.action.type, value: request.action.value})})
  : authenticatedRequest<Turn>(`/conversations/${encodeURIComponent(id)}/turns`, token, { method: "POST", body: JSON.stringify(request) });
export const deleteConversation = (token: string, id: string) => authenticatedRequest<void>(`/conversations/${encodeURIComponent(id)}`, token, { method: "DELETE" });
