import {ApiRequestError, authenticatedRequest} from "./auth";

export const APPLICATION_STATUSES = ["DRAFT", "PENDING_REVIEW", "CHANGES_REQUESTED", "APPROVED_AWAITING_CASH", "CASH_RECEIVED", "OPENED", "REJECTED", "CANCELLED", "REFUND_PENDING", "REFUNDED"] as const;
export type ApplicationStatus = typeof APPLICATION_STATUSES[number];
export const DOCUMENT_TYPES = ["AADHAAR", "PAN", "ADDRESS_PROOF"] as const;
export type DocumentType = typeof DOCUMENT_TYPES[number];
export type DocumentMediaType = "application/pdf" | "image/jpeg" | "image/png";
export const IDENTITY_TYPES = ["AADHAAR", "PAN", "PASSPORT"] as const;
export type IdentityType = typeof IDENTITY_TYPES[number];
export type IdentityDetails = {identityType: IdentityType; identityNumber: string};
export type RevealedIdentity = IdentityDetails & {verificationMethod: "IN_PERSON_ORIGINAL"};

export type ApplicationRequirements = {
  minimumOpeningAmount: string; maximumOpeningAmount: string; currencyCode: "INR";
  businessDate: string; latestDateOfBirth: string; allowedAccountTypes: "SAVINGS"[];
  identityTypes: IdentityType[]; consentVersion: string; consentNotice: string;
  applicationsAvailable: boolean; identityDetailsAvailable: boolean; cashReceiptAvailable: boolean;
  verificationMethod: "IN_PERSON_ORIGINAL"; governmentVerification: false;
};
export type AdminApplicationReadiness = {
  checkedAt: string;
  applicationsAvailable: boolean;
  identityDetailsAvailable: boolean;
  reviewsAvailable: boolean;
  cashReceiptAvailable: boolean;
  accountOpeningAvailable: boolean;
  cashRefundAvailable: boolean;
  blockers: string[];
};
export type ApplicationSummary = {
  id: string; accountType: "SAVINGS"; currencyCode: "INR"; fullName: string;
  email: string; phoneNumber: string | null; dateOfBirth: string;
  status: ApplicationStatus; reviewDecision: "PENDING" | "APPROVED" | "REJECTED";
  reviewReason: string | null; version: number; createdAt: string; updatedAt: string;
  submittedAt: string | null; reviewedAt: string | null; openedAt: string | null;
  endedAt: string | null; endReasonCode: string | null; accountId: number | null;
  openingAmount: string; identityType: IdentityType; identityMasked: string;
};
export type ApplicationDocument = {
  id: string; documentType: DocumentType; mediaType: DocumentMediaType; byteSize: number;
  storageStatus: "QUARANTINED" | "AVAILABLE" | "PURGED";
  safetyStatus: "PENDING" | "CLEAN" | "REJECTED";
  reviewStatus: "PENDING" | "ACCEPTED" | "REJECTED";
  reviewReason: string | null; uploadedAt: string; reviewedAt: string | null;
};
export type ApplicationEvent = {
  id: string; eventType: string; applicationVersion: number; actorRole: "CUSTOMER" | "ADMIN";
  fromStatus: ApplicationStatus | null; toStatus: ApplicationStatus;
  reasonCode: string | null; reviewReason: string | null; documentId: string | null;
  receiptId: string | null; createdAt: string;
};
export type OpeningCashReceipt = {
  id: string; receiptNumber: string; currencyCode: "INR"; amount: string;
  status: "RECEIVED" | "APPLIED" | "REFUND_PENDING" | "REFUNDED";
  cashReceivedAt: string; recordedAt: string; allocatedAt: string | null;
  refundReason: string | null; refundedAt: string | null;
};
export type AccountApplication = ApplicationSummary & {
  documents: ApplicationDocument[]; events: ApplicationEvent[]; receipts: OpeningCashReceipt[];
};
export type ApplicationPage = {items: ApplicationSummary[]; total: number; page: number; size: number};
export type CreateApplicationRequest = {
  requestKey: string; accountType: "SAVINGS"; currencyCode: "INR"; dateOfBirth: string;
  openingAmount: string; phoneNumber: string; consentVersion: string; consentAccepted: true;
  identityType: IdentityType; identityNumber: string;
};
export type ActionRequest = {requestKey: string; expectedVersion: number};
export type UpdateIdentityRequest = ActionRequest & IdentityDetails;
export type UpdateApplicationDetailsRequest = ActionRequest & {phoneNumber: string; dateOfBirth: string; openingAmount: string; identityType?: IdentityType; identityNumber?: string};
export type ReviewApplicationRequest = ActionRequest & {decision: "APPROVED" | "REJECTED" | "CHANGES_REQUESTED"; reason: string; inPersonChecked: boolean};
export type CashReceiptRequest = ActionRequest & {cashReceivedConfirmed: true};
export type ReasonRequest = ActionRequest & {reason: string};
export type RefundRequest = ActionRequest & {cashReturnedConfirmed: true};

const CUSTOMER = "/account-applications";
const ADMIN = "/admin/account-applications";
const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
const MEDIA_TYPES: readonly string[] = ["application/pdf", "image/jpeg", "image/png"];
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const EVENT_TYPES = ["APPLICATION_CREATED", "APPLICATION_UPDATED", "IDENTITY_UPDATED", "SUBMITTED", "CHANGES_REQUESTED", "APPROVED", "REJECTED", "CANCELLED", "DOCUMENT_ADDED", "DOCUMENT_REVIEWED", "CASH_RECEIVED", "ACCOUNT_OPENED", "REFUND_REQUESTED", "CASH_REFUNDED"];
const own = (value: unknown): value is Record<string, unknown> => !!value && typeof value === "object" && !Array.isArray(value);
const integer = (value: unknown, min = 0, max = Number.MAX_SAFE_INTEGER): value is number => typeof value === "number" && Number.isSafeInteger(value) && value >= min && value <= max;
const text = (value: unknown, max: number, min = 1): value is string => typeof value === "string" && value.length >= min && value.length <= max && !/[\u0000-\u001f\u007f-\u009f]/.test(value);
const oneOf = <T extends string>(value: unknown, options: readonly T[]): value is T => typeof value === "string" && options.includes(value as T);
const idValid = (value: unknown): value is string => typeof value === "string" && UUID.test(value);
const nullable = (value: unknown, guard: (value: unknown) => boolean) => value === null || guard(value);

function validDate(value: unknown): value is string {
  if (typeof value !== "string" || !/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  if (year < 1 || month < 1 || month > 12 || day < 1) return false;
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  return day <= [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1];
}
function timestamp(value: unknown): value is string {
  return typeof value === "string" && value.length <= 64 && validDate(value.slice(0, 10)) && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?(?:Z|[+-]\d{2}:\d{2})?$/.test(value) && Number.isFinite(Date.parse(value));
}
function minorUnits(value: unknown): number | null {
  if (typeof value !== "string" || !/^(?:0|[1-9][0-9]{0,7})(?:\.[0-9]{1,2})?$/.test(value)) return null;
  const [whole, fraction = ""] = value.split(".");
  return Number(whole) * 100 + Number(fraction.padEnd(2, "0"));
}
function canonicalMoney(value: unknown): value is string {
  return typeof value === "string" && normalizeOpeningAmount(value) === value;
}

/** Exact decimal arithmetic in paise, safely below Number.MAX_SAFE_INTEGER. */
export function normalizeOpeningAmount(raw: string, requirements?: Pick<ApplicationRequirements, "minimumOpeningAmount" | "maximumOpeningAmount"> | null): string | null {
  const value = minorUnits(raw);
  const min = requirements ? minorUnits(requirements.minimumOpeningAmount) : 100000;
  const max = requirements ? minorUnits(requirements.maximumOpeningAmount) : 1000000000;
  if (value === null || min === null || max === null || min < 100000 || max > 1000000000 || min > max || value < min || value > max) return null;
  return `${Math.floor(value / 100)}.${String(value % 100).padStart(2, "0")}`;
}

/** Accepts a contact number, not proof that the number is verified. */
export function normalizeApplicationPhone(raw: string): string | null {
  if (typeof raw !== "string") return null;
  const value = raw.trim().replace(/[\s()-]/g, "");
  return /^\+?[0-9]{10,15}$/.test(value) ? value : null;
}

/** Uses the bank's date/cutoff, not the browser clock or UTC Date coercion. */
export function validateDOB(raw: string, requirements: ApplicationRequirements): string {
  if (!validDate(raw)) return "Enter a valid date of birth in YYYY-MM-DD format.";
  if (!validDate(requirements.businessDate) || !validDate(requirements.latestDateOfBirth)) return "The bank's age requirements could not be confirmed. Reload before continuing.";
  if (raw >= requirements.businessDate) return "Date of birth must be before today.";
  if (raw > requirements.latestDateOfBirth) return "You must be at least 18 to apply for this savings account.";
  return "";
}

export function validateReviewReason(raw: string): string {
  if (typeof raw !== "string" || !raw.trim()) return "Enter a review reason.";
  if (raw.length > 500 || new TextEncoder().encode(raw).length > 500) return "Keep the reason within 500 UTF-8 bytes.";
  if (/[\u0000-\u001f\u007f-\u009f]/.test(raw)) return "Remove line breaks and control characters from the reason.";
  if (/\b[A-Z]{5}[0-9]{4}[A-Z]\b/i.test(raw) || /\b[A-Z](?=[A-Z0-9]{0,6}[0-9])[A-Z0-9]{7}\b/i.test(raw) || /(?:^|[^0-9])(?:[0-9][ -]?){11}[0-9](?![0-9])/.test(raw)) return "Do not include identity numbers in comments. Refer to the application instead.";
  return "";
}
/** Format checks only; they do not prove identity or government verification. */
export function normalizeIdentityNumber(type: IdentityType, raw: string): string | null {
  if (typeof raw !== "string" || raw.length > 40 || /[\u0000-\u001f\u007f-\u009f]/.test(raw)) return null;
  const value = raw.trim().toUpperCase();
  const pattern = type === "AADHAAR" ? /^[0-9]{4}$/ : type === "PAN" ? /^[A-Z]{3}P[A-Z][0-9]{4}[A-Z]$/
    : type === "PASSPORT" ? /^(?=.*[0-9])[A-Z][A-Z0-9]{7}$/ : null;
  return pattern?.test(value) ? value : null;
}
export function identityLabel(type: IdentityType): string {
  return type === "AADHAAR" ? "Aadhaar last four digits" : type === "PAN" ? "Personal PAN" : "Indian passport";
}
export function identityInputHelp(type: IdentityType): string {
  return type === "AADHAAR" ? "Enter only the last four digits. Never enter or upload the full Aadhaar number."
    : type === "PAN" ? "Enter your personal PAN: five letters (fourth letter P), four digits, one letter."
    : "Enter the eight letters and digits printed on your Indian passport. Format checks do not verify the document.";
}
function identityMaskValid(type: IdentityType, value: unknown): boolean {
  return typeof value === "string" && (type === "PAN" ? /^••••[0-9]{3}[A-Z]$/ : type === "PASSPORT" ? /^••••[A-Z0-9]{4}$/ : /^••••[0-9]{4}$/).test(value);
}
export function validReceiptNumber(raw: string): boolean {
  return typeof raw === "string" && /^[A-Za-z0-9][A-Za-z0-9/_-]{0,63}$/.test(raw);
}
export function applicationStatusLabel(status: string): string {
  const labels: Record<ApplicationStatus, string> = {
    DRAFT: "Draft", PENDING_REVIEW: "Awaiting admin review", CHANGES_REQUESTED: "Changes requested",
    APPROVED_AWAITING_CASH: "Approved — cash deposit required", CASH_RECEIVED: "Cash received",
    OPENED: "Account opened", REJECTED: "Rejected", CANCELLED: "Cancelled", REFUND_PENDING: "Cash refund pending", REFUNDED: "Cash refunded"
  };
  return oneOf(status, APPLICATION_STATUSES) ? labels[status] : "Unknown status";
}
export function validateRequirements(value: unknown): ApplicationRequirements | null {
  if (!own(value) || !canonicalMoney(value.minimumOpeningAmount) || !canonicalMoney(value.maximumOpeningAmount)
      || Number(value.minimumOpeningAmount) > Number(value.maximumOpeningAmount) || value.currencyCode !== "INR"
      || !validDate(value.businessDate) || !validDate(value.latestDateOfBirth) || value.latestDateOfBirth >= value.businessDate
      || !Array.isArray(value.allowedAccountTypes) || value.allowedAccountTypes.length > 1 || !value.allowedAccountTypes.every(v => v === "SAVINGS")
      || !Array.isArray(value.identityTypes) || !value.identityTypes.length || value.identityTypes.length > 3 || new Set(value.identityTypes).size !== value.identityTypes.length || !value.identityTypes.every(v => oneOf(v, IDENTITY_TYPES))
      || value.consentVersion !== "in-person-identity-v1"
      || !text(value.consentNotice, 4000) || typeof value.applicationsAvailable !== "boolean" || typeof value.identityDetailsAvailable !== "boolean"
      || typeof value.cashReceiptAvailable !== "boolean" || value.verificationMethod !== "IN_PERSON_ORIGINAL" || value.governmentVerification !== false) return null;
  const year = Number(value.businessDate.slice(0, 4)) - 18;
  if (year < 1) return null;
  const candidate = `${String(year).padStart(4, "0")}${value.businessDate.slice(4)}`;
  const cutoff = validDate(candidate) ? candidate : `${String(year).padStart(4, "0")}-02-28`;
  if (value.latestDateOfBirth !== cutoff) return null;
  return value as unknown as ApplicationRequirements;
}

/** Read-only server checks, never a browser flag or a cached authorization grant. */
export function validateAdminReadiness(value: unknown): AdminApplicationReadiness | null {
  if (!own(value) || !timestamp(value.checkedAt)
      || !/^\d{4}-\d{2}-\d{2}T(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?Z$/.test(value.checkedAt)
      || !["applicationsAvailable", "identityDetailsAvailable", "reviewsAvailable", "cashReceiptAvailable", "accountOpeningAvailable", "cashRefundAvailable"].every(key => typeof value[key] === "boolean")
      || !Array.isArray(value.blockers) || value.blockers.length > 16
      || !value.blockers.every(code => typeof code === "string" && /^[A-Z][A-Z0-9_]{0,63}$/.test(code))
      || new Set(value.blockers).size !== value.blockers.length) return null;
  return {
    checkedAt: value.checkedAt,
    applicationsAvailable: value.applicationsAvailable as boolean,
    identityDetailsAvailable: value.identityDetailsAvailable as boolean,
    reviewsAvailable: value.reviewsAvailable as boolean,
    cashReceiptAvailable: value.cashReceiptAvailable as boolean,
    accountOpeningAvailable: value.accountOpeningAvailable as boolean,
    cashRefundAvailable: value.cashRefundAvailable as boolean,
    blockers: [...value.blockers] as string[]
  };
}

function summaryValid(value: unknown): value is ApplicationSummary {
  return own(value) && idValid(value.id) && value.accountType === "SAVINGS" && value.currencyCode === "INR"
    && text(value.fullName, 160) && text(value.email, 254) && nullable(value.phoneNumber, v => text(v, 32))
    && validDate(value.dateOfBirth) && oneOf(value.status, APPLICATION_STATUSES) && oneOf(value.reviewDecision, ["PENDING", "APPROVED", "REJECTED"])
    && nullable(value.reviewReason, v => text(v, 500)) && integer(value.version) && timestamp(value.createdAt) && timestamp(value.updatedAt)
    && ["submittedAt", "reviewedAt", "openedAt", "endedAt"].every(key => nullable(value[key], timestamp))
    && nullable(value.endReasonCode, v => text(v, 60)) && nullable(value.accountId, v => integer(v, 1)) && canonicalMoney(value.openingAmount)
    && oneOf(value.identityType, IDENTITY_TYPES) && identityMaskValid(value.identityType, value.identityMasked)
    && !("identityNumber" in value) && !("identityCiphertext" in value);
}
function documentValid(value: unknown): value is ApplicationDocument {
  return own(value) && idValid(value.id) && oneOf(value.documentType, DOCUMENT_TYPES) && oneOf(value.mediaType, MEDIA_TYPES)
    && integer(value.byteSize, 1, MAX_DOCUMENT_BYTES) && oneOf(value.storageStatus, ["QUARANTINED", "AVAILABLE", "PURGED"])
    && oneOf(value.safetyStatus, ["PENDING", "CLEAN", "REJECTED"]) && oneOf(value.reviewStatus, ["PENDING", "ACCEPTED", "REJECTED"])
    && nullable(value.reviewReason, v => text(v, 500)) && timestamp(value.uploadedAt) && nullable(value.reviewedAt, timestamp);
}
function eventValid(value: unknown): value is ApplicationEvent {
  return own(value) && idValid(value.id) && oneOf(value.eventType, EVENT_TYPES) && integer(value.applicationVersion)
    && oneOf(value.actorRole, ["CUSTOMER", "ADMIN"]) && nullable(value.fromStatus, v => oneOf(v, APPLICATION_STATUSES))
    && oneOf(value.toStatus, APPLICATION_STATUSES) && nullable(value.reasonCode, v => text(v, 60))
    && nullable(value.reviewReason, v => text(v, 500)) && nullable(value.documentId, idValid) && nullable(value.receiptId, idValid) && timestamp(value.createdAt);
}
function receiptValid(value: unknown): value is OpeningCashReceipt {
  return own(value) && idValid(value.id) && typeof value.receiptNumber === "string" && validReceiptNumber(value.receiptNumber)
    && value.currencyCode === "INR" && canonicalMoney(value.amount) && oneOf(value.status, ["RECEIVED", "APPLIED", "REFUND_PENDING", "REFUNDED"])
    && timestamp(value.cashReceivedAt) && timestamp(value.recordedAt) && nullable(value.allocatedAt, timestamp)
    && nullable(value.refundReason, v => text(v, 500)) && nullable(value.refundedAt, timestamp);
}
function arrayOf<T>(value: unknown, max: number, check: (value: unknown) => value is T): value is T[] {
  return Array.isArray(value) && value.length <= max && value.every(check);
}
function distinctIds(values: Array<{id: string}>): boolean { return new Set(values.map(v => v.id.toLowerCase())).size === values.length; }
function detail(value: unknown, expectedId?: string): AccountApplication {
  if (!own(value)) throw responseError();
  const {documents, events, receipts} = value;
  if (!summaryValid(value) || (expectedId && value.id.toLowerCase() !== expectedId.toLowerCase())
      || !arrayOf(documents, 10, documentValid) || !arrayOf(events, 10000, eventValid) || !arrayOf(receipts, 1, receiptValid)
      || !distinctIds(documents) || !distinctIds(events) || !distinctIds(receipts)
      || events.some(event => event.applicationVersion > value.version)) throw responseError();
  return value as AccountApplication;
}
function responseError(): ApiRequestError {
  // Status 0 deliberately preserves mutation retry keys: the server may have committed.
  return new ApiRequestError(0, "The bank's application response could not be confirmed. Refresh the application before trying again.");
}
function requireInput(valid: boolean, message: string): asserts valid {
  if (!valid) throw new ApiRequestError(400, message);
}
function pathId(id: string): string {
  requireInput(idValid(id), "Choose a valid application or document.");
  return encodeURIComponent(id);
}
function action(body: ActionRequest): ActionRequest {
  requireInput(!!body && idValid(body.requestKey) && integer(body.expectedVersion), "A valid request key and application version are required.");
  return {requestKey: body.requestKey, expectedVersion: body.expectedVersion};
}
function reason(raw: string): string {
  const error = validateReviewReason(raw);
  requireInput(!error, error);
  return raw;
}
function identity(body: IdentityDetails): IdentityDetails {
  requireInput(!!body && oneOf(body.identityType, IDENTITY_TYPES), "Choose Aadhaar last four digits, personal PAN or Indian passport.");
  const identityNumber = normalizeIdentityNumber(body.identityType, body.identityNumber);
  requireInput(identityNumber !== null, "Check the identifier format. Aadhaar must contain only its last four digits.");
  return {identityType: body.identityType, identityNumber};
}
async function read(path: string, token: string): Promise<unknown> {
  return authenticatedRequest<unknown>(path, token, {method: "GET", cache: "no-store", credentials: "omit"});
}
async function post(path: string, token: string, body: object, expectedId?: string): Promise<AccountApplication> {
  // UI and its action hook use current server readiness. The server remains authoritative
  // for each mutation; avoid a second read here that could obscure the actual POST outcome.
  return detail(await authenticatedRequest<unknown>(path, token, {method: "POST", body: JSON.stringify(body), cache: "no-store", credentials: "omit"}), expectedId);
}

export const applicationApi = {
  async requirements(token: string): Promise<ApplicationRequirements> {
    const result = validateRequirements(await read(`${CUSTOMER}/requirements`, token));
    if (!result) throw responseError();
    return result;
  },
  async listMine(token: string): Promise<ApplicationSummary[]> {
    const result = await read(CUSTOMER, token);
    if (!arrayOf(result, 100, summaryValid) || !distinctIds(result)) throw responseError();
    return result;
  },
  async getMine(token: string, id: string): Promise<AccountApplication> { return detail(await read(`${CUSTOMER}/${pathId(id)}`, token), id); },
  async create(token: string, body: CreateApplicationRequest): Promise<AccountApplication> {
    requireInput(!!body && idValid(body.requestKey) && body.accountType === "SAVINGS" && body.currencyCode === "INR" && validDate(body.dateOfBirth)
      && normalizeOpeningAmount(body.openingAmount) !== null && normalizeApplicationPhone(body.phoneNumber) !== null && text(body.consentVersion, 40) && /^[A-Za-z0-9._-]+$/.test(body.consentVersion) && body.consentAccepted === true, "Check the application details and accept the review notice.");
    return post(CUSTOMER, token, {requestKey: body.requestKey, accountType: body.accountType, currencyCode: body.currencyCode,
      dateOfBirth: body.dateOfBirth, openingAmount: body.openingAmount, phoneNumber: normalizeApplicationPhone(body.phoneNumber), consentVersion: body.consentVersion, consentAccepted: true, ...identity(body)});
  },
  async updateDetails(token: string, id: string, body: UpdateApplicationDetailsRequest): Promise<AccountApplication> {
    const phoneNumber = normalizeApplicationPhone(body.phoneNumber), openingAmount = normalizeOpeningAmount(body.openingAmount);
    requireInput(phoneNumber !== null && openingAmount !== null && validDate(body.dateOfBirth), "Check the phone number, date of birth and opening deposit.");
    const replacing = body.identityType !== undefined || body.identityNumber !== undefined;
    const replacement = replacing ? identity({identityType: body.identityType!, identityNumber: body.identityNumber!}) : {};
    return post(`${CUSTOMER}/${pathId(id)}/details`, token, {...action(body), phoneNumber, dateOfBirth: body.dateOfBirth, openingAmount, ...replacement}, id);
  },
  async updateIdentity(token: string, id: string, body: UpdateIdentityRequest): Promise<AccountApplication> {
    return post(`${CUSTOMER}/${pathId(id)}/identity`, token, {...action(body), ...identity(body)}, id);
  },
  async submit(token: string, id: string, body: ActionRequest): Promise<AccountApplication> { return post(`${CUSTOMER}/${pathId(id)}/submit`, token, action(body), id); },
  async cancel(token: string, id: string, body: ActionRequest): Promise<AccountApplication> { return post(`${CUSTOMER}/${pathId(id)}/cancel`, token, action(body), id); },
  async adminReadiness(token: string): Promise<AdminApplicationReadiness> {
    const result = validateAdminReadiness(await read(`${ADMIN}/readiness`, token));
    if (!result) throw responseError();
    return result;
  },
  async listAdmin(token: string, query: {status?: ApplicationStatus; page: number; size: number}): Promise<ApplicationPage> {
    requireInput(integer(query.page, 0, 100000) && integer(query.size, 1, 100) && (query.status === undefined || oneOf(query.status, APPLICATION_STATUSES)), "Choose a valid queue page, size and status.");
    const params = new URLSearchParams({page: String(query.page), size: String(query.size)});
    if (query.status) params.set("status", query.status);
    const result = await read(`${ADMIN}?${params.toString()}`, token);
    if (!own(result) || !arrayOf(result.items, query.size, summaryValid) || !distinctIds(result.items) || !integer(result.total)
      || result.page !== query.page || result.size !== query.size || result.total < result.items.length) throw responseError();
    return result as ApplicationPage;
  },
  async getAdmin(token: string, id: string): Promise<AccountApplication> { return detail(await read(`${ADMIN}/${pathId(id)}`, token), id); },
  async revealIdentity(token: string, id: string): Promise<RevealedIdentity> {
    const result = await read(`${ADMIN}/${pathId(id)}/identity`, token);
    if (!own(result) || !oneOf(result.identityType, IDENTITY_TYPES) || typeof result.identityNumber !== "string" || result.verificationMethod !== "IN_PERSON_ORIGINAL"
        || normalizeIdentityNumber(result.identityType, result.identityNumber as string) !== result.identityNumber) throw responseError();
    return {identityType: result.identityType, identityNumber: result.identityNumber as string, verificationMethod: "IN_PERSON_ORIGINAL"};
  },
  async review(token: string, id: string, body: ReviewApplicationRequest): Promise<AccountApplication> {
    requireInput(oneOf(body.decision, ["APPROVED", "REJECTED", "CHANGES_REQUESTED"]), "Choose a valid application decision.");
    requireInput(body.inPersonChecked === (body.decision === "APPROVED"), "Confirm the original identity document was checked in person before approval.");
    return post(`${ADMIN}/${pathId(id)}/review`, token, {...action(body), decision: body.decision, reason: reason(body.reason), inPersonChecked: body.inPersonChecked}, id);
  },
  async receiveCash(token: string, id: string, body: CashReceiptRequest): Promise<AccountApplication> {
    requireInput(body.cashReceivedConfirmed === true, "Confirm actual receipt of the customer’s saved opening amount.");
    return post(`${ADMIN}/${pathId(id)}/cash-receipts`, token, {...action(body), cashReceivedConfirmed: true}, id);
  },
  async open(token: string, id: string, body: ActionRequest): Promise<AccountApplication> { return post(`${ADMIN}/${pathId(id)}/open`, token, action(body), id); },
  async requestRefund(token: string, id: string, body: ReasonRequest): Promise<AccountApplication> { return post(`${ADMIN}/${pathId(id)}/refund-request`, token, {...action(body), reason: reason(body.reason)}, id); },
  async refund(token: string, id: string, body: RefundRequest): Promise<AccountApplication> {
    requireInput(body.cashReturnedConfirmed === true, "Confirm that the physical cash was returned before recording the refund.");
    return post(`${ADMIN}/${pathId(id)}/refund`, token, {...action(body), cashReturnedConfirmed: true}, id);
  }
};
