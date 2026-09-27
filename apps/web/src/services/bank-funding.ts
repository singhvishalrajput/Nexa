import { authenticatedRequest } from "./auth";
import { moneyInMinorUnits } from "./banking-content";

export type BankFundingRequest = {
  requestId: string; amount: string; source: string; reference: string; reason: string; confirmed: true;
};
export type BankFundingReceipt = {
  id: string; requestId: string; receiptNumber: string; source: string; reference: string; reason: string;
  amount: string; currencyCode: string; transactionId: string; recordedBy: string; recordedAt: string;
  reserveBalanceAfter: string; cashBalanceAfter: string;
};
export type BankFundingOverview = {
  reserveBalance: string | null; cashBalance: string | null; currencyCode: string;
  ready: boolean; reason: string | null; receipts: BankFundingReceipt[];
};
export const bankFunding = {
  overview: (token: string) => authenticatedRequest<BankFundingOverview>("/admin/bank-funding", token),
  record: (token: string, request: BankFundingRequest) => authenticatedRequest<BankFundingReceipt>("/admin/bank-funding/receipts", token, {method: "POST", body: JSON.stringify(request)}),
  byRequest: (token: string, requestId: string) => authenticatedRequest<BankFundingReceipt>("/admin/bank-funding/receipts/by-request/" + encodeURIComponent(requestId), token)
};

export const fundingText = (value: string) => value.replace(/\p{White_Space}+/gu, " ").replace(/^ +| +$/g, "");
const referenceCharacters = (value: string) => value.replace(/\p{White_Space}+/gu, "");
export const fundingReference = (value: string) => referenceCharacters(value).toUpperCase();
export function fundingFieldErrors(fields: {amount: string; source: string; reference: string; reason: string}) {
  const amount = moneyInMinorUnits(fields.amount);
  return {
    amount: /^(?:0|[1-9]\d{0,7})(?:\.\d{1,2})?$/.test(fields.amount) && amount !== null && amount > BigInt(0) && amount <= BigInt(1000000000) ? "" : "Enter a positive amount up to ₹1,00,00,000 with at most two decimal places.",
    source: validText(fundingText(fields.source), 160) ? "" : "Enter a shorter source name without hidden control characters. Names in some languages need fewer characters.",
    reference: /^[A-Za-z0-9][A-Za-z0-9/._-]{0,79}$/.test(referenceCharacters(fields.reference)) ? "" : "Start the reference with a letter or number. Use up to 80 English letters, numbers or / . _ -.",
    reason: validText(fundingText(fields.reason), 500) ? "" : "Enter a shorter reason without hidden control characters. Text in some languages needs fewer characters."
  };
}
export function validFundingFields(fields: {amount: string; source: string; reference: string; reason: string}): boolean {
  return Object.values(fundingFieldErrors(fields)).every(error => !error);
}
function validText(value: string, maximumBytes: number) {
  return !!value && !/[\p{Cc}\p{Cf}]/u.test(value) && new TextEncoder().encode(value).length <= maximumBytes;
}
export function fundingRequest(fields: {amount: string; source: string; reference: string; reason: string}): BankFundingRequest {
  if (!validFundingFields(fields)) throw new Error("Check the amount, cash source, evidence reference and reason.");
  const minor = moneyInMinorUnits(fields.amount)!;
  return {requestId: crypto.randomUUID(), amount: `${minor / BigInt(100)}.${String(minor % BigInt(100)).padStart(2, "0")}`,
    source: fundingText(fields.source), reference: fundingReference(fields.reference), reason: fundingText(fields.reason), confirmed: true};
}
export function validFundingRequest(value: unknown): value is BankFundingRequest {
  if (!value || typeof value !== "object") return false;
  const item = value as BankFundingRequest;
  return typeof item.requestId === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.requestId)
    && [item.amount, item.source, item.reference, item.reason].every(field => typeof field === "string")
    && item.confirmed === true && validFundingFields(item) && /^\d+\.\d{2}$/.test(item.amount)
    && item.source === fundingText(item.source) && item.reason === fundingText(item.reason) && item.reference === fundingReference(item.reference);
}
export function verifiedFundingReceipt(receipt: BankFundingReceipt, request: BankFundingRequest): boolean {
  return !!receipt && receipt.requestId === request.requestId && receipt.currencyCode === "INR"
    && moneyInMinorUnits(receipt.amount) === moneyInMinorUnits(request.amount)
    && receipt.source === request.source && receipt.reference === request.reference && receipt.reason === request.reason
    && [receipt.id, receipt.receiptNumber, receipt.transactionId, receipt.recordedBy].every(value => typeof value === "string" && !!value.trim())
    && typeof receipt.recordedAt === "string" && Number.isFinite(Date.parse(receipt.recordedAt))
    && [receipt.reserveBalanceAfter, receipt.cashBalanceAfter].every(value => typeof value === "string" && /^-?\d+\.\d{2}$/.test(value));
}
