import { t } from "../../services/locale";
import { Conversation } from "../../services/conversations";

type Props = { conversation: Conversation; current: boolean; disabled: boolean; onSelect: () => void; selecting?: boolean; selected?: boolean; onToggleSelection?: () => void };

export function ConversationHistoryItem({ conversation, current, disabled, onSelect, selecting, selected, onToggleSelection }: Props) {
  const title = conversation.title.replace(/\.$/, "").replace(/^./, letter => letter.toUpperCase());
  return <div class={`messenger-history-item${current ? " is-current" : ""}`}>
    <div class="messenger-history-row">
      {selecting && <input type="checkbox" checked={!!selected} disabled={disabled} aria-label={`${t("Select conversation")}: ${title}`} onChange={onToggleSelection}/>}
      <button type="button" class="messenger-history-open" disabled={disabled} aria-current={current ? "true" : undefined} onClick={onSelect}>
        <span title={title}>{title}</span>
      </button>
    </div>
  </div>;
}
