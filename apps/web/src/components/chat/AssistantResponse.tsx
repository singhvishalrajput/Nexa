import { h } from "preact";
import { Turn } from "../../services/conversations";
import { BankingResponse, supportsBankingContent } from "./BankingResponse";
import { MessageBubble } from "./MessageBubble";

/** Choose presentation without changing message persistence or chat interactions. */
export function AssistantResponse({ turn, accessToken }: { turn: Turn; accessToken: string }) {
  const textOnly = turn.banking?.type === "TEXT" || turn.banking?.type === "ERROR" || (turn.banking?.type === "ACTION_REQUIRED" && !turn.banking.action);
  if (!turn.banking || textOnly || !supportsBankingContent(turn.banking)) {
    return <MessageBubble role="assistant" text={turn.assistantText} timestamp={turn.createdAt}>
      {turn.banking && !textOnly && <p>This banking summary needs a newer version of Nexa.</p>}
    </MessageBubble>;
  }
  return <article class="messenger-banking-response" aria-label="Nexa’s banking response">
    {turn.assistantText && <p class="messenger-banking-intro" dir="auto">{turn.assistantText}</p>}
    <div class="messenger-banking-surface">
      <BankingResponse content={turn.banking} accessToken={accessToken} capturedAt={turn.createdAt} />
    </div>
  </article>;
}
