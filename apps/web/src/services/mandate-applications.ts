import { ApiRequestError, authenticatedRequest } from "./auth";
import { moneyInMinorUnits } from "./banking-content";

export type MandateApplicationDraft = {
  applicationKey: string; sourceAccountId: number; payeeId?: string;
  beneficiaryAccountNumber?: string; payee?: string; limit: string; startDate: string; endDate: string | null;
};
/** Existing mandate endpoints return the bank's authorization record. */
export type MandateApplication = {
  ID: string; STATUS: string; APPLICATION_KEY: string; SOURCE_ACCOUNT_ID: number | string;
  AMOUNT: string | number; EFFECTIVE_DATE: string; END_DATE: string | null; TARGET_ID: string | null;
};
export function chatMandateApplicationKey(clientId: string): string | null {
  return /^[A-Za-z0-9_-]{1,67}$/.test(clientId || "") ? "chat-mandate-" + clientId : null;
}
export function verifiedMandateApplication(value: unknown, applicationKey: string, draft?: MandateApplicationDraft): MandateApplication {
  const mandate = value as MandateApplication | null;
  if (!mandate || typeof mandate.ID !== "string" || !mandate.ID.trim()
      || mandate.APPLICATION_KEY !== applicationKey || !/^[1-9]\d*$/.test(String(mandate.SOURCE_ACCOUNT_ID))
      || !["PENDING", "ACTIVE", "PAUSED", "ACTION_REQUIRED", "CANCELLED", "REVOKED", "EXPIRED"].includes(mandate.STATUS)
      || draft && (String(mandate.SOURCE_ACCOUNT_ID) !== String(draft.sourceAccountId)
        || moneyInMinorUnits(mandate.AMOUNT) !== moneyInMinorUnits(draft.limit)
        || mandate.EFFECTIVE_DATE !== draft.startDate || (mandate.END_DATE || null) !== draft.endDate
        || (mandate.TARGET_ID || null) !== (draft.payeeId || null))) {
    throw new Error("The returned mandate did not match this request. Check its status before trying again.");
  }
  return mandate;
}
export async function findMandateApplication(token: string, applicationKey: string): Promise<MandateApplication | null> {
  try {
    return verifiedMandateApplication(await authenticatedRequest<unknown>("/mandates/applications/by-request/" + encodeURIComponent(applicationKey), token), applicationKey);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) return null;
    throw error;
  }
}
