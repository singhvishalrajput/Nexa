import { SensitiveLabel } from "../../features/banking/SensitiveNumber";
import { getLocale, t } from "../../services/locale";
import { localizeReply } from "../../services/reply-localization";
import { ActionCommand, Workflow } from "../../services/conversations";
import { formatMoney } from "../../services/banking-content";

export function workflowConfirmationLabel(operation: string) {
  if (["OWN_TRANSFER", "START_TRANSFER"].includes(operation)) return "Confirm transfer";
  if (operation === "CANCEL_MANDATE") return "Confirm cancellation";
  if (["CARD_CONTROL", "FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD"].includes(operation)) return "Confirm card change";
  return "Confirm payment";
}

export function WorkflowCard({ workflow: w, active, busy, onAction }: {workflow: Workflow; active: boolean; busy: boolean; onAction: (command: ActionCommand) => void}) {
  if (w.version !== 1) return <p>{t("This proposal requires a newer version of Nexa.")}</p>;
  const targetKind = w.operation === "PAY_CARD" || w.operation.includes("CARD") ? "cards" : w.operation === "PAY_BILL" ? "bills" : w.operation === "REPAY_LOAN" ? "loans" : w.operation === "START_TRANSFER" ? "beneficiaries" : w.operation === "CANCEL_MANDATE" ? "mandates" : "accounts";
  const message = localizeReply(w.message);
  if (!active && ["COLLECTING", "REVIEW"].includes(w.status)) return <details class="conversation-proposal-history"><summary>{t("Earlier step")} · {w.operation === "OWN_TRANSFER" ? t("Transfer between accounts") : t("Payment review")}</summary><p lang={/[\u0900-\u097f]/.test(message) ? "hi-IN" : "en-IN"}>{message}</p>{w.accountLabel && <p>{t("From")}: <SensitiveLabel label={w.accountLabel} id={w.accountId}/></p>}{w.targetLabel && <p>{t("To")}: <SensitiveLabel label={w.targetLabel} kind={targetKind} id={w.targetId}/></p>}{w.amount && <p>{t("Amount")}: {w.currency ? formatMoney(w.amount,w.currency) : w.amount}</p>}</details>;
  const simulated = !["OWN_TRANSFER", "START_TRANSFER", "PAY_CARD", "PAY_BILL", "REPAY_LOAN"].includes(w.operation);
  const expired = Date.parse(w.expiresAt) <= Date.now() && ["COLLECTING", "REVIEW"].includes(w.status);
  const enabled = active && !busy && !expired;
  const action = (type: ActionCommand["type"], value?: string) => { if (enabled && Date.parse(w.expiresAt) > Date.now()) onAction({actionId: w.id, type, value}); };
  const navigable = (w.status === "COMPLETED" && !!w.reference) || w.status === "UNAVAILABLE";
  return <article class={"conversation-proposal" + (navigable ? " bank-clickable-card" : "")} tabIndex={navigable ? undefined : 0} aria-label={t("Banking proposal")} aria-busy={busy && active}>
    {w.status === "REVIEW" && <div class="conversation-proposal-status">{expired ? t("Confirmation expired") : t("Please confirm")}</div>}
    <h3>{w.operation === "OWN_TRANSFER" ? t("Between your accounts") : w.operation === "PAY_BILL" ? t("Bill payment") : ["CARD_CONTROL", "FREEZE_CARD", "UNFREEZE_CARD", "REPLACE_CARD"].includes(w.operation) ? t("Card controls") : w.operation === "PAY_CARD" ? t("Card payment") : w.operation === "CANCEL_MANDATE" ? t("Cancel direct debit") : t("Transfer")}</h3>
    <p lang={/[\u0900-\u097f]/.test(message) ? "hi-IN" : "en-IN"}>{message}</p>
    {(w.accountLabel || w.targetLabel || w.amount) && <dl>
      {w.accountLabel && <><dt>{t("From")}</dt><dd><SensitiveLabel label={w.accountLabel} id={w.accountId}/></dd></>}
      {w.targetLabel && <><dt>{t("To")}</dt><dd><SensitiveLabel label={w.targetLabel} kind={targetKind} id={w.targetId}/></dd></>}
      {w.amount && <><dt>{t("Amount")}</dt><dd class="conversation-proposal-amount">{w.currency ? formatMoney(w.amount,w.currency) : w.amount}</dd></>}
      {w.reference && <><dt>{t("Reference")}</dt><dd>{w.reference.replace(/^DEMO-/, "REQ-")}</dd></>}
      {w.status === "REVIEW" && w.amount && <><dt>{t("Payment method")}</dt><dd>{w.operation === "PAY_CARD" ? "Bank account payment" : w.operation === "OWN_TRANSFER" ? "Nexa" : t("Not provided by the bank")}</dd><dt>{t("Fees")}</dt><dd>{t("Not provided by the bank")}</dd><dt>{t("Completion")}</dt><dd>{!simulated ? t("On confirmation") : t("Not provided by the bank")}</dd></>}
    </dl>}
    {active && w.status === "COLLECTING" && <div class="conversation-choices">{w.choices.map(c => <div key={c.id}
      class="conversation-choice" role="button" tabIndex={enabled ? 0 : -1} aria-disabled={!enabled}
      aria-label={t("Select") + " " + c.label} onClick={() => action("SELECT", c.id)}
      onKeyDown={event => {
        // Reveal buttons handle their own keyboard activation.
        if (event.target !== event.currentTarget || !["Enter", " "].includes(event.key)) return;
        event.preventDefault();
        if (!event.repeat) action("SELECT", c.id);
      }}>
      <span><SensitiveLabel label={c.label} kind={w.field === "account" ? "accounts" : targetKind} id={c.id}/></span>
      <span class="conversation-choice-affordance" aria-hidden="true">{t("Select")}<span>↗</span></span>
    </div>)}</div>}
    {active && ["COLLECTING", "REVIEW"].includes(w.status) && <div class="messenger-inline-actions">
      {w.status === "REVIEW" && w.confirmationRequired && w.executionAvailable && <button class="conversation-confirm" type="button" disabled={!enabled} onClick={() => action("CONFIRM")}>{busy ? t("Processing…") : t(workflowConfirmationLabel(w.operation))}</button>}
      <button type="button" disabled={!enabled} onClick={() => action("CANCEL")}>{t("Cancel")}</button>
    </div>}
    {w.status === "REVIEW" && <small>{t("Confirm by")} {new Date(w.expiresAt).toLocaleTimeString(getLocale(), {hour: "2-digit", minute: "2-digit"})}.</small>}
    {!active && ["COLLECTING", "REVIEW"].includes(w.status) && <small>Earlier proposal. Use the latest action in this conversation.</small>}
    {w.status === "COMPLETED" && w.reference && <a class="bank-card-primary" href={simulated ? "#/payments" : "#/transactions/"+encodeURIComponent(w.reference)}>{simulated ? t("View receipt ↗") : t("View transaction ↗")}</a>}
    {w.status === "UNAVAILABLE" && <a class="bank-card-primary" href={w.operation === "CARD_CONTROL" ? "#/cards" : "#/payments"}>{t("Open banking details ↗")}</a>}
  </article>;
}
