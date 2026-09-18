import { getLocale, t } from "../../services/locale";
import { ComponentChildren } from "preact";

export function ChatIcon({ name }: { name: "mic" | "send" | "history" | "back" | "more" | "close" | "down" | "stop" | "check" | "trash" }) {
  const paths = {
    mic: <><rect x="9" y="3" width="6" height="12" rx="3" /><path d="M5 11v1a7 7 0 0 0 14 0v-1M12 19v3M9 22h6" /></>,
    send: <><path d="m4 4 17 8-17 8 3-8-3-8ZM7 12h14" /></>,
    history: <><path d="M4 5h16v12H8l-4 4V5Z" /><path d="M8 9h8M8 13h5" /></>,
    back: <path d="m14 5-7 7 7 7M7 12h14" />,
    more: <><circle cx="12" cy="5" r="1" /><circle cx="12" cy="12" r="1" /><circle cx="12" cy="19" r="1" /></>,
    close: <path d="m6 6 12 12M6 18 18 6" />,
    down: <path d="m5 9 7 7 7-7" />,
    stop: <rect x="6" y="6" width="12" height="12" rx="2" />,
    check: <path d="m5 12 4 4L19 6" />,
    trash: <><path d="M3 6h18M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M5 6l1 14a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1l1-14M10 10v7M14 10v7" /></>
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

export function MessageBubble({ role, text, timestamp, voice, children, status, animate = false }: {
  role: "user" | "assistant";
  text?: string;
  timestamp?: string;
  voice?: boolean;
  children?: ComponentChildren;
  status?: string;
  animate?: boolean;

}) {
  const date = timestamp ? new Date(timestamp) : null;
  return <article class={`messenger-message from-${role}${animate ? " messenger-arrival" : ""}`} aria-label={role === "user" ? t("Your message") : t("Nexa’s message")}>
    {role === "assistant" && <img class="messenger-message-avatar" src="styles/images/nexa.svg" width="28" height="28" alt=""/>}
    <div class="messenger-bubble">
      {voice && <span class="messenger-voice-label"><ChatIcon name="mic" />{t("Understood from speech")}</span>}
      {text && <p dir="auto" lang={/[\u0900-\u097f]/.test(text) ? "hi-IN" : "en-IN"}>{text}</p>}
      {children}
      {(date || status) && <footer>{date && <time dateTime={timestamp} title={date.toLocaleString(getLocale())}>{date.toLocaleTimeString(getLocale(), { hour: "2-digit", minute: "2-digit" })}</time>}{status && <span>{t(status)}</span>}</footer>}
    </div>
  </article>;
}
