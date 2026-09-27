import { t } from "../../services/locale";
import { localizeReply } from "../../services/reply-localization";
import { Turn } from "../../services/conversations";
import { BankingResponse, supportsBankingContent } from "./BankingResponse";
import { MessageBubble } from "./MessageBubble";
import { WorkflowCard } from "./WorkflowCard";
import { ActionCommand } from "../../services/conversations";
import { LoanApplicationCard } from "./LoanApplicationCard";

/** Choose presentation without changing message persistence or chat interactions. */
export function AssistantResponse({ turn, accessToken, active = false, applicationActive = false, busy = false, applicationBusy = busy, onApplicationLocked, onAction = () => {} }: { turn: Turn; accessToken: string; active?: boolean; applicationActive?: boolean; busy?: boolean; applicationBusy?: boolean; onApplicationLocked?: (locked: boolean) => void; onAction?: (command: ActionCommand) => void }) {
  if (turn.workflow) return <WorkflowCard workflow={turn.workflow} active={active} busy={busy} onAction={onAction} />;
  if (turn.banking?.version === 1 && turn.banking.type === "LOAN_APPLICATION") return <LoanApplicationCard key={turn.clientId} clientId={turn.clientId} accessToken={accessToken} active={applicationActive} busy={applicationBusy} onSubmissionLocked={onApplicationLocked}/>;
  const text = localizeReply(turn.assistantText);
  const textOnly = turn.banking?.type === "TEXT" || turn.banking?.type === "ERROR" || (turn.banking?.type === "ACTION_REQUIRED" && !turn.banking.action);
  if (!turn.banking || textOnly || !supportsBankingContent(turn.banking)) {
    return <MessageBubble role="assistant" text={text} timestamp={turn.createdAt}>
      {turn.banking && !textOnly && <p>{t("This banking summary needs a newer version of Nexa.")}</p>}
    </MessageBubble>;
  }
  return <article class="messenger-banking-response" aria-label={t("Nexa’s banking response")}>
    {text && <p class="messenger-banking-intro" dir="auto" lang={/[\u0900-\u097f]/.test(text) ? "hi-IN" : "en-IN"}>{text}</p>}
    <div class="messenger-banking-surface">
      <BankingResponse content={turn.banking} accessToken={accessToken} capturedAt={turn.createdAt} />
    </div>
  </article>;
}
