import { ApiRequestError, authenticatedRequest } from "../../services/auth";

export type ScheduleRequirements = { businessDate: string; earliestDueDate: string; latestDueDate: string; timezone: string; executionEnabled: boolean };
export type ScheduleRequest = { requestKey: string; sourceAccountId: string; payeeId: string; amount: string; dueAt: string };
export type ScheduledPaymentReceipt = {
    id: string; payee: string; amount: string; currencyCode: string; dueAt: string;
    status: "READY" | "SCHEDULED" | "COMPLETED" | "FAILED" | "CANCELLED" | "EXPIRED" | string;
    accountId: string; reference: string | null; managed: boolean; sourceName: string | null;
    sourceMasked: string | null; payeeId: string | null; recipientName: string | null; destinationMasked: string | null;
    createdAt: string; expiresAt: string | null; authorizedAt: string | null; completedAt: string | null;
    failureReason: string | null; timezone: string;
};
const path = "/scheduled-payments";
const states = new Set(["READY", "SCHEDULED", "COMPLETED", "FAILED", "CANCELLED", "EXPIRED"]);
function checked(item: ScheduledPaymentReceipt): ScheduledPaymentReceipt {
    if (!item || typeof item.managed !== "boolean" || typeof item.id !== "string" || (item.managed && (!states.has(item.status)
        || !/^SP-[0-9a-f-]{36}$/i.test(item.id) || item.currencyCode !== "INR" || typeof item.amount !== "string"
        || !/^[0-9]{1,13}\.[0-9]{2}$/.test(item.amount) || !/^\d{4}-\d{2}-\d{2}$/.test(item.dueAt))))
        throw new ApiRequestError(0, "The schedule response could not be verified. Check the saved schedule before continuing.");
    return item;
}
export const scheduledPayments = {
    requirements: (token: string) => authenticatedRequest<ScheduleRequirements>(path + "/requirements", token, { cache: "no-store" }),
    prepare: (token: string, body: ScheduleRequest) => authenticatedRequest<ScheduledPaymentReceipt>(path + "/prepare", token, { method: "POST", body: JSON.stringify(body) }).then(checked),
    detail: (token: string, id: string) => authenticatedRequest<ScheduledPaymentReceipt>(path + "/" + encodeURIComponent(id), token, { cache: "no-store" }).then(checked),
    confirm: (token: string, id: string) => authenticatedRequest<ScheduledPaymentReceipt>(path + "/" + encodeURIComponent(id) + "/confirm", token, { method: "POST", body: JSON.stringify({ authorizationAccepted: true }) }).then(checked),
    cancel: (token: string, id: string) => authenticatedRequest<ScheduledPaymentReceipt>(path + "/" + encodeURIComponent(id) + "/cancel", token, { method: "POST" }).then(checked)
};
