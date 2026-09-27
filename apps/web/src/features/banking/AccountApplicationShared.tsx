import { AccountApplication, applicationStatusLabel, identityLabel } from "../../services/account-applications";
import { formatDate, formatMoney } from "../../services/banking-content";
import { t } from "../../services/locale";
import { Detail, Panel } from "./ui";

export function ApplicationStatus({status}: {status: string}) {
  const tone = ["OPENED", "REFUNDED"].includes(status) ? "positive"
    : ["REJECTED", "CANCELLED"].includes(status) ? "neutral" : "pending";
  return <span class={"application-status " + tone}>{t(applicationStatusLabel(status))}</span>;
}

const receiptLabels: Record<string, string> = {RECEIVED: "Cash received", APPLIED: "Allocated to the opened account", REFUND_PENDING: "Return of cash pending", REFUNDED: "Cash returned"};
const eventLabels: Record<string, string> = {
  APPLICATION_CREATED: "Application created", APPLICATION_UPDATED: "Application details updated", IDENTITY_UPDATED: "Identity details updated", DOCUMENT_ADDED: "Document uploaded", SUBMITTED: "Submitted for review",
  DOCUMENT_REVIEWED: "Document reviewed", APPROVED: "Review approved", REJECTED: "Application rejected",
  CHANGES_REQUESTED: "Changes requested", CANCELLED: "Application cancelled", CASH_RECEIVED: "Cash receipt recorded",
  ACCOUNT_OPENED: "Account opened", REFUND_REQUESTED: "Refund requested", CASH_REFUNDED: "Cash return recorded"
};

/** Display only the saved application metadata and masked identity. */
export function ApplicationRecord({application, admin = false}: {
  application: AccountApplication; token: string; admin?: boolean; downloadsEnabled?: boolean;
}) {
  return <div class="application-record">
    <Panel title={t("Application details")}>
      <div class="application-record-heading"><ApplicationStatus status={application.status}/><span class="application-reference">{t("Application reference")}: {application.id}</span></div>
      <dl class="application-details-grid">
        <Detail label={t("Applicant")}>{application.fullName}</Detail>
        <Detail label={t("Email")}>{application.email}</Detail>
        <Detail label={t("Phone")}>{application.phoneNumber || t("Not provided")}</Detail>
        <Detail label={t("Date of birth")}>{formatDate(application.dateOfBirth)}</Detail>
        <Detail label={t("Account type")}>{t("Personal savings · INR")}</Detail>
        <Detail label={t("Requested opening deposit")}>{formatMoney(application.openingAmount, "INR")}</Detail>
        <Detail label={t("Created")}>{formatDate(application.createdAt, true)}</Detail>
        <Detail label={t("Last updated")}>{formatDate(application.updatedAt, true)}</Detail>
      </dl>
      {application.reviewReason && <div class="application-review-note"><strong>{t("Review feedback")}</strong><p>{application.reviewReason}</p></div>}
      <p class="application-muted">{t("Details are saved with this application. You can update these details before submitting or when the bank requests corrections.")}</p>
      {application.status === "OPENED" && application.accountId != null && <a class="bank-button secondary" href={admin ? `#/admin/accounts/${application.accountId}/overview` : `#/accounts/${application.accountId}`}>{t("View opened account")}</a>}
    </Panel>
    <Panel title={t("Identity for in-person review")}>
      <dl class="application-details-grid"><Detail label={t("Identity document")}>{t(identityLabel(application.identityType))}</Detail><Detail label={t("Saved identifier (masked)")}>{application.identityMasked}</Detail></dl>
      <p>{t("An authorised administrator must check the original identity document in person. A valid format or a saved identifier does not prove identity and is not government verification.")}</p>
      {application.identityType === "AADHAAR" && <p>{t("Only the last four Aadhaar digits are retained. Do not provide the full Aadhaar number.")}</p>}
      <p class="application-muted">{t("No document upload is required in this workflow.")}</p>
    </Panel>
    <Panel title={t("Opening cash receipts")}>
      {!application.receipts.length ? <p>{t("No opening cash receipt has been recorded. An entered amount or approved application is not proof of payment.")}</p>
        : application.receipts.map(receipt => <article key={receipt.id} class="application-receipt">
          <strong>{t(receiptLabels[receipt.status] || receipt.status)}</strong>
          <dl class="application-details-grid">
            <Detail label={t("Receipt number")}>{receipt.receiptNumber}</Detail>
            <Detail label={t("Recorded amount")}>{formatMoney(receipt.amount, receipt.currencyCode)}</Detail>
            <Detail label={t("Cash received")}>{formatDate(receipt.cashReceivedAt, true)}</Detail>
            <Detail label={t("Recorded")}>{formatDate(receipt.recordedAt, true)}</Detail>
            {receipt.allocatedAt && <Detail label={t("Allocated")}>{formatDate(receipt.allocatedAt, true)}</Detail>}
            {receipt.refundedAt && <Detail label={t("Cash returned")}>{formatDate(receipt.refundedAt, true)}</Detail>}
          </dl>
          {receipt.refundReason && <p>{receipt.refundReason}</p>}
        </article>)}
    </Panel>
    <Panel title={t("Application history")}>
      <ol class="application-timeline">{application.events.map(event => <li key={event.id}>
        <div><strong>{t(eventLabels[event.eventType] || event.eventType.replace(/_/g, " "))}</strong><time dateTime={event.createdAt}>{formatDate(event.createdAt, true)}</time></div>
        <p>{t("Recorded by")}: {t(event.actorRole === "ADMIN" ? "Administrator" : "Customer")}{event.toStatus ? " · " + t(applicationStatusLabel(event.toStatus)) : ""}</p>
        {event.reviewReason && <p class="application-review-note">{event.reviewReason}</p>}
      </li>)}</ol>
    </Panel>
  </div>;
}
