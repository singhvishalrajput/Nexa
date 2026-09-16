import { t } from "../../services/locale";
import { Turn } from "../../services/conversations";
import { BankingResponse, supportsBankingContent } from "./BankingResponse";
import { MessageBubble } from "./MessageBubble";
import { WorkflowCard } from "./WorkflowCard";
import { ActionCommand } from "../../services/conversations";

/** Choose presentation without changing message persistence or chat interactions. */
export function AssistantResponse({ turn, accessToken, active = false, busy = false, onAction = () => {} }: { turn: Turn; accessToken: string; active?: boolean; busy?: boolean; onAction?: (command: ActionCommand) => void }) {
  if (turn.workflow) return <WorkflowCard workflow={turn.workflow} active={active} busy={busy} onAction={onAction} />;
  const textOnly = turn.banking?.type === "TEXT" || turn.banking?.type === "ERROR" || (turn.banking?.type === "ACTION_REQUIRED" && !turn.banking.action);
  if (!turn.banking || textOnly || !supportsBankingContent(turn.banking)) {
    return <MessageBubble role="assistant" text={turn.assistantText} timestamp={turn.createdAt}>
      {turn.banking && !textOnly && <p>{t("This banking summary needs a newer version of Nexa.")}</p>}
    </MessageBubble>;
  }
  return <article class="messenger-banking-response" aria-label={t("Nexa’s banking response")}>
    {turn.assistantText && <p class="messenger-banking-intro" dir="auto" lang={/[\u0900-\u097f]/.test(turn.assistantText) ? "hi-IN" : "en-IN"}>{turn.assistantText}</p>}
    <div class="messenger-banking-surface">
      <BankingResponse content={turn.banking} accessToken={accessToken} capturedAt={turn.createdAt} />
    </div>
  </article>;
}
