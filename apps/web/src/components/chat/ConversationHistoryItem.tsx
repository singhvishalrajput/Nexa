import { t } from "../../services/locale";
import { useRef, useState } from "preact/hooks";
import { Conversation } from "../../services/conversations";
import { ChatIcon } from "./MessageBubble";

type Props = { conversation: Conversation; current: boolean; disabled: boolean; hasDraft: boolean; onSelect: () => void; onDelete: () => Promise<void>; selecting?: boolean; selected?: boolean; onToggleSelection?: () => void };

export function ConversationHistoryItem({ conversation, current, disabled, hasDraft, onSelect, onDelete, selecting, selected, onToggleSelection }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");
  const locked = useRef(false);
  const title = conversation.title.replace(/\.$/, "").replace(/^./, letter => letter.toUpperCase());
  const remove = async () => {
    if (locked.current || disabled) return;
    locked.current = true; setDeleting(true); setError("");
    try { await onDelete(); }
    catch (_) { setError("Couldn’t delete this conversation. Please try again."); }
    finally { locked.current = false; setDeleting(false); }
  };
  return <div class={`messenger-history-item${current ? " is-current" : ""}`}>
    <div class="messenger-history-row">
      {selecting && <input type="checkbox" checked={!!selected} disabled={disabled} aria-label={`${t("Select conversation")}: ${title}`} onChange={onToggleSelection}/>}
      <button type="button" class="messenger-history-open" disabled={disabled || deleting} aria-current={current ? "true" : undefined} onClick={onSelect}>
        <span title={title}>{title}</span>
      </button>
      <button type="button" class="messenger-history-delete messenger-history-delete-icon" title={t("Delete conversation")} disabled={disabled || deleting} aria-label={`Delete conversation: ${title}`} aria-expanded={confirming} onClick={() => { setConfirming(true); setError(""); }}><ChatIcon name="trash" /></button>
    </div>
    {confirming && !selecting && <div class="messenger-history-confirm" role="group" aria-label={`Confirm deletion of ${title}`}>
      <p>{t("Delete this conversation and its messages? This cannot be undone.")}{current && hasDraft ? " Your unsent draft will also be removed." : ""}</p>
      <div><button type="button" disabled={disabled || deleting} onClick={() => { setConfirming(false); setError(""); }}>{t("Keep conversation")}</button><button type="button" class="messenger-history-delete" disabled={disabled || deleting} onClick={remove}>{deleting ? t("Deleting…") : t("Confirm delete")}</button></div>
      {error && <p role="alert">{error}</p>}
    </div>}
  </div>;
}
