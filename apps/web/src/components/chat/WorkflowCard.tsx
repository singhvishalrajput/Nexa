import { ActionCommand, Workflow } from "../../services/conversations";
import { formatMoney } from "../../services/banking-content";

export function WorkflowCard({ workflow: w, active, busy, onAction }: {workflow: Workflow; active: boolean; busy: boolean; onAction: (command: ActionCommand) => void}) {
  if (w.version !== 1) return <p>This proposal requires a newer version of Nexa.</p>;
  if (!active && ["COLLECTING", "REVIEW"].includes(w.status)) return <details class="conversation-proposal-history"><summary>Earlier step · {w.operation === "OWN_TRANSFER" ? "Transfer between accounts" : "Payment review"}</summary><p>{w.message}</p>{w.accountLabel && <p>From: {w.accountLabel}</p>}{w.targetLabel && <p>To: {w.targetLabel}</p>}{w.amount && <p>Amount: {w.currency ? formatMoney(w.amount,w.currency) : w.amount}</p>}</details>;
  const expired = Date.parse(w.expiresAt) <= Date.now() && ["COLLECTING", "REVIEW"].includes(w.status);
  const enabled = active && !busy && !expired;
  const action = (type: ActionCommand["type"], value?: string) => onAction({actionId: w.id, type, value});
  return <article class="conversation-proposal" aria-label="Banking proposal" aria-busy={busy && active}>
    <div class="conversation-proposal-status">{expired ? "Expired" : w.status === "REVIEW" ? "Awaiting your confirmation" : w.status.replace(/_/g, " ").toLowerCase()}</div>
    <h3>{w.operation === "OWN_TRANSFER" ? "Between your accounts" : w.operation === "PAY_BILL" ? "Bill payment" : w.operation === "CARD_CONTROL" ? "Card controls" : "Pay a beneficiary"}</h3>
    <p>{w.message}</p>
    {(w.accountLabel || w.targetLabel || w.amount) && <dl>
      {w.accountLabel && <><dt>From</dt><dd>{w.accountLabel}</dd></>}
      {w.targetLabel && <><dt>To</dt><dd>{w.targetLabel}</dd></>}
      {w.amount && <><dt>Amount</dt><dd class="conversation-proposal-amount">{w.currency ? formatMoney(w.amount,w.currency) : w.amount}</dd></>}
      {w.reference && <><dt>Transaction reference</dt><dd>{w.reference}</dd></>}
    </dl>}
    {active && w.status === "COLLECTING" && <div class="conversation-choices">{w.choices.map(c => <button key={c.id} type="button" disabled={!enabled} onClick={() => action("SELECT",c.id)}>{c.label}<span aria-hidden="true">↗</span></button>)}</div>}
    {active && ["COLLECTING", "REVIEW"].includes(w.status) && <div class="messenger-inline-actions">
      {w.status === "REVIEW" && w.confirmationRequired && w.executionAvailable && <button class="conversation-confirm" type="button" disabled={!enabled} onClick={() => action("CONFIRM")}>{busy ? "Processing…" : "Confirm transfer"}</button>}
      <button type="button" disabled={!enabled} onClick={() => action("CANCEL")}>Cancel</button>
    </div>}
    {w.status === "REVIEW" && <small>Proposal expires {new Date(w.expiresAt).toLocaleTimeString([], {hour: "2-digit", minute: "2-digit"})}. Your balance will be checked again.</small>}
    {!active && ["COLLECTING", "REVIEW"].includes(w.status) && <small>Earlier proposal. Use the latest action in this conversation.</small>}
    {w.status === "COMPLETED" && w.reference && <a href={"#/transactions/"+encodeURIComponent(w.reference)}>View transaction ↗</a>}
    {w.status === "UNAVAILABLE" && <a href={w.operation === "CARD_CONTROL" ? "#/cards" : "#/payments"}>Open banking details ↗</a>}
  </article>;
}
