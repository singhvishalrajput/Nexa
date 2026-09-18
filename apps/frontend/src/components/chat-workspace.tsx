import { h } from "preact";
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import {
  Account,
  Card,
  Proposal,
  initialAccount,
  respond,
  approve,
  money,
} from "./banking-demo";

import { createVoiceSession, Recognition } from "./voice-session";

type Attachment = { id: string; name: string; size: number; type: string };

type Message = {
  id: string;
  role: "user" | "assistant";
  text: string;
  card?: Card;
  proposal?: Proposal;
  attachments?: Attachment[];
};
type Conversation = { id: string; title: string; messages: Message[] };
type Store = {
  version: 1;
  account: Account;
  conversations: Conversation[];
  active: string;
};
const uid = () =>
  `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 9)}`;
const fresh = (): Store => {
  const id = uid();
  return {
    version: 1,
    account: initialAccount(),
    conversations: [{ id, title: "New conversation", messages: [] }],
    active: id,
  };
};
const storageKey = "nexa-chat-demo-v1";
function load(): Store {
  try {
    const data = JSON.parse(sessionStorage.getItem(storageKey) || "null");
    if (
      data?.version === 1 &&
      Number.isFinite(data.account?.balance) &&
      data.account.balance >= 0 &&
      Number.isFinite(data.account?.travel) &&
      Array.isArray(data.account.transactions) &&
      Array.isArray(data.conversations) &&
      data.conversations.length &&
      data.conversations.every(
        (c: Conversation) =>
          typeof c.id === "string" &&
          typeof c.title === "string" &&
          Array.isArray(c.messages) &&
          c.messages.every(
            (m) =>
              typeof m.text === "string" &&
              (m.role === "user" || m.role === "assistant") &&
              (m.attachments === undefined ||
                (Array.isArray(m.attachments) &&
                  m.attachments.every(
                    (file) =>
                      typeof file.id === "string" &&
                      typeof file.name === "string" &&
                      typeof file.type === "string" &&
                      Number.isFinite(file.size),
                  ))),
          ),
      )
    ) {
      if (!data.conversations.some((c: Conversation) => c.id === data.active))
        data.active = data.conversations[0].id;
      return data;
    }
  } catch {
    /* A new session also works when browser storage is unavailable. */
  }
  return fresh();
}
function Glyph({ name }: { name: string }) {
  const paths: Record<string, any> = {
    attach: (
      <path d="m8 13 7-7a3 3 0 0 1 4 4l-9 9a5 5 0 0 1-7-7l9-9M7 14l8-8" />
    ),
    file: (
      <>
        <path d="M14 3H5v18h14V8Z" />
        <path d="M14 3v5h5M8 13h8M8 17h5" />
      </>
    ),
    photo: (
      <>
        <rect x="3" y="3" width="18" height="18" rx="3" />
        <circle cx="8" cy="8" r="1.5" />
        <path d="m3 17 5-5 4 4 4-6 5 7" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    arrow: <path d="M12 19V5m-5 5 5-5 5 5" />,
    diagonal: <path d="M6 18 18 6M6 6h12v12" />,
    chat: <path d="M20 11a8 8 0 0 1-8 8H5l-3 3V11a9 9 0 0 1 18 0Z" />,
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 5 5" />
      </>
    ),
    home: (
      <>
        <path d="m3 10 9-7 9 7v10H3Z" />
        <path d="M9 20v-7h6v7" />
      </>
    ),
    goals: (
      <>
        <circle cx="12" cy="12" r="9" />
        <circle cx="12" cy="12" r="5" />
        <circle cx="12" cy="12" r="1" />
      </>
    ),
    activity: <path d="M3 17h3l4-10 4 13 3-8h4" />,
    settings: (
      <>
        <path d="M5 4v16M12 4v16M19 4v16" />
        <path d="M2 8h6M9 16h6M16 10h6" />
      </>
    ),
    mic: (
      <>
        <rect x="9" y="3" width="6" height="12" rx="3" />
        <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3" />
      </>
    ),
    stop: <rect x="6" y="6" width="12" height="12" rx="2" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    menu: <path d="M4 6h16M4 12h16M4 18h16" />,
    sidebar: (
      <>
        <rect x="3" y="4" width="18" height="16" rx="3" />
        <path d="M9 4v16" />
      </>
    ),
    trash: (
      <>
        <path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7M14 10v7" />
      </>
    ),
    export: <path d="M12 3v12m-4-4 4 4 4-4M4 15v6h16v-6" />,
    copy: (
      <>
        <rect x="8" y="8" width="12" height="13" rx="2" />
        <path d="M16 8V3H3v13h5" />
      </>
    ),
    sound: (
      <>
        <path d="m11 4-6 5H2v6h3l6 5ZM15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    back: <path d="M20 12H4m6-6-6 6 6 6" />,
    wallet: (
      <>
        <rect x="3" y="5" width="18" height="15" rx="3" />
        <path d="M21 10h-6v5h6M4 5l12-3v3" />
      </>
    ),
  };
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.6"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.chat}
    </svg>
  );
}
function messageText(message: Message) {
  const lines = [message.text];
  message.attachments?.forEach((file) =>
    lines.push(`Attachment: ${file.name}`),
  );
  if (message.card?.amount !== undefined) {
    const labels = {
      balance: "Available balance",
      spending: "Total spending",
      goals: "Travel fund",
      activity: "Activity",
    };
    lines.push(
      `${labels[message.card.type]}: ${money(message.card.amount)}${message.card.type === "goals" ? " of ₹50,000" : ""}.`,
    );
  }
  message.card?.rows?.forEach((row) =>
    lines.push(`${row.label}: ${money(row.value)}.`),
  );
  if (message.proposal)
    lines.push(
      `${money(message.proposal.amount)} to ${message.proposal.recipient}. Status: ${message.proposal.status}.`,
    );
  return lines.join("\n");
}
function Brand({ size = 28 }: { size?: number }) {
  return <img src="styles/images/nexa.svg" alt="" width={size} height={size} />;
}
function ResultCard({ card }: { card: Card }) {
  if (card.type === "balance")
    return (
      <div class="nc-result nc-balance">
        <span>
          Everyday account <Glyph name="wallet" />
        </span>
        <strong>{money(card.amount || 0)}</strong>
        <small>Available balance · Demo account •• 2048</small>
      </div>
    );
  if (card.type === "goals")
    return (
      <div class="nc-result nc-goal-result">
        <span>
          Japan, here I come. <Glyph name="goals" />
        </span>
        <strong>
          {money(card.amount || 0)} <small>/ ₹50,000</small>
        </strong>
        <div class="nc-progress">
          <i style={{ width: `${Math.min(100, (card.amount || 0) / 500)}%` }} />
        </div>
        <small>{Math.round((card.amount || 0) / 500)}% of the way there</small>
      </div>
    );
  return (
    <div class="nc-result">
      <span>
        {card.type === "spending"
          ? "Your month, at a glance"
          : "Recent activity"}
        <Glyph name="activity" />
      </span>
      {card.amount !== undefined && (
        <strong>
          {money(card.amount)} <small>spent</small>
        </strong>
      )}
      <div class="nc-result-rows">
        {card.rows?.map((row, i) => (
          <div key={i}>
            <span>{row.label}</span>
            <b>{money(row.value)}</b>
            {card.type === "spending" && (
              <div class="nc-progress">
                <i
                  style={{
                    width: `${(row.value / (card.amount || 1)) * 100}%`,
                  }}
                />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export function ChatWorkspace() {
  const [store, setStore] = useState<Store>(load);
  const data = useRef(store);
  const [draft, setDraft] = useState("");
  const [search, setSearch] = useState("");
  const [drawer, setDrawer] = useState(false);
  const [sidebarClosed, setSidebarClosed] = useState(false);
  const [compact, setCompact] = useState(
    () => window.matchMedia("(max-width: 1000px)").matches,
  );
  const historyOpen = compact ? drawer : !sidebarClosed;
  const [settings, setSettings] = useState(false);
  const [readAloud, setReadAloud] = useState(false);
  const [resetArmed, setResetArmed] = useState(false);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [notice, setNotice] = useState("");
  const [listening, setListening] = useState(false);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);
  const photoInput = useRef<HTMLInputElement>(null);
  const attachWrap = useRef<HTMLDivElement>(null);
  const attachButton = useRef<HTMLButtonElement>(null);
  const [attachOpen, setAttachOpen] = useState(false);
  useEffect(() => {
    if (!attachOpen) return;
    attachWrap.current
      ?.querySelector<HTMLButtonElement>(".nc-attach-menu button")
      ?.focus();
    const outside = (event: PointerEvent) => {
      if (!attachWrap.current?.contains(event.target as Node))
        setAttachOpen(false);
    };
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [attachOpen]);
  const fileUrls = useRef(new Map<string, string>());
  useEffect(() => {
    const retained = new Set(attachments.map((file) => file.id));
    store.conversations.forEach((conversation) =>
      conversation.messages.forEach((message) =>
        message.attachments?.forEach((file) => retained.add(file.id)),
      ),
    );
    fileUrls.current.forEach((url, id) => {
      if (!retained.has(id)) {
        URL.revokeObjectURL(url);
        fileUrls.current.delete(id);
      }
    });
  }, [attachments, store]);
  const [speaking, setSpeaking] = useState<string | null>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const messagesEnd = useRef<HTMLDivElement>(null);
  const scrollArea = useRef<HTMLDivElement>(null);
  const voiceSession = useRef<ReturnType<typeof createVoiceSession> | null>(
    null,
  );
  const sidebar = useRef<HTMLElement>(null);
  const drawerButton = useRef<HTMLButtonElement>(null);
  const active = store.conversations.find((c) => c.id === store.active)!;
  const speechConstructor =
    (
      window as unknown as {
        SpeechRecognition?: new () => Recognition;
        webkitSpeechRecognition?: new () => Recognition;
      }
    ).SpeechRecognition ||
    (window as unknown as { webkitSpeechRecognition?: new () => Recognition })
      .webkitSpeechRecognition;
  const canSpeak = "speechSynthesis" in window;
  const resizeInput = () => {
    const field = input.current;
    if (!field) return;
    const limit = Math.max(48, Math.min(160, window.innerHeight * 0.24));
    field.style.height = "0px";
    const contentHeight = field.scrollHeight;
    field.style.height = `${Math.min(limit, Math.max(48, contentHeight))}px`;
    field.style.overflowY = contentHeight > limit ? "auto" : "hidden";
  };
  useLayoutEffect(resizeInput, [draft]);
  useEffect(() => {
    const media = window.matchMedia("(max-width: 1000px)");
    const change = () => {
      setCompact(media.matches);
      setDrawer(false);
    };
    media.addEventListener("change", change);
    let width = 0;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width !== width) {
        width = entry.contentRect.width;
        resizeInput();
      }
    });
    if (input.current) observer.observe(input.current);
    window.addEventListener("resize", resizeInput);
    return () => {
      observer.disconnect();
      media.removeEventListener("change", change);
      window.removeEventListener("resize", resizeInput);
    };
  }, []);
  const commit = (next: Store) => {
    data.current = next;
    setStore(next);
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(next));
    } catch {
      setNotice(
        "Your browser couldn’t save this session. You can continue chatting here.",
      );
    }
  };
  const stopVoice = () => {
    voiceSession.current?.cancel();
    voiceSession.current = null;
    setListening(false);
    if (canSpeak) window.speechSynthesis.cancel();
    setSpeaking(null);
  };
  const speak = (text: string, id: string) => {
    if (!canSpeak) {
      setNotice("Read aloud isn’t supported in this browser.");
      return;
    }
    window.speechSynthesis.cancel();
    if (speaking === id) {
      setSpeaking(null);
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = "en-IN";
    utterance.rate = 0.95;
    utterance.onend = () => setSpeaking(null);
    utterance.onerror = () => setSpeaking(null);
    setSpeaking(id);
    window.speechSynthesis.speak(utterance);
  };
  useEffect(() => {
    input.current?.focus();
    return () => {
      voiceSession.current?.cancel();
      fileUrls.current.forEach((url) => URL.revokeObjectURL(url));
      if ("speechSynthesis" in window) window.speechSynthesis.cancel();
    };
  }, []);
  useEffect(() => {
    if (!active.messages.length && scrollArea.current)
      scrollArea.current.scrollTop = 0;
    else
      messagesEnd.current?.scrollIntoView({ behavior: "auto", block: "end" });
  }, [active.id, active.messages.length]);
  useEffect(() => {
    if (!drawer) return;
    const media = window.matchMedia("(max-width: 1000px)");
    if (!media.matches) return;
    const focusable = () =>
      Array.from(
        sidebar.current?.querySelectorAll<HTMLElement>(
          "button:not(:disabled), input, a[href]",
        ) || [],
      );
    focusable()[0]?.focus();
    const keys = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setDrawer(false);
        drawerButton.current?.focus();
      }
      if (e.key === "Tab") {
        const all = focusable(),
          first = all[0],
          last = all[all.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last?.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first?.focus();
        }
      }
    };
    document.addEventListener("keydown", keys);
    return () => document.removeEventListener("keydown", keys);
  }, [drawer]);
  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setSettings(false);
      }
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
  }, []);
  const newChat = () => {
    clearAttachments();
    stopVoice();
    setDraft("");
    setSearch("");
    setDrawer(false);
    setNotice("");
    const current = data.current;
    const empty = current.conversations.find((c) => c.messages.length === 0);
    if (empty) commit({ ...current, active: empty.id });
    else {
      const id = uid();
      commit({
        ...current,
        active: id,
        conversations: [
          { id, title: "New conversation", messages: [] },
          ...current.conversations,
        ].slice(0, 30),
      });
    }
    input.current?.focus();
  };
  const send = (text = draft) => {
    const trimmed = text.trim();
    if (listening || (!trimmed && !attachments.length)) return;
    if (trimmed.length > 2000) {
      setNotice("Please keep your message under 2,000 characters.");
      return;
    }
    stopVoice();
    setDraft("");
    setNotice("");
    setDrawer(false);
    const current = data.current,
      convo = current.conversations.find((c) => c.id === current.active)!;
    const response = {
      id: uid(),
      role: "assistant" as const,
      ...(attachments.length
        ? {
            text: "Your files are attached to this conversation. I can’t read their contents yet. Type a banking request to continue.",
          }
        : respond(trimmed, current.account)),
    };
    const updated = {
      ...convo,
      title: convo.messages.length
        ? convo.title
        : (trimmed || attachments[0]?.name || "Attachments").slice(0, 48),
      messages: [
        ...convo.messages,
        {
          id: uid(),
          role: "user" as const,
          text: trimmed,
          attachments: attachments.length ? attachments : undefined,
        },
        response,
      ].slice(-100),
    };
    commit({
      ...current,
      conversations: current.conversations.map((c) =>
        c.id === current.active ? updated : c,
      ),
    });
    setAttachments([]);
    if (readAloud) speak(messageText(response), response.id);
    input.current?.focus();
  };
  const decide = (id: string, confirm: boolean) => {
    const current = data.current,
      convo = current.conversations.find((c) => c.id === current.active)!;
    const message = convo.messages.find((m) => m.id === id);
    if (!message?.proposal || message.proposal.status !== "pending") return;
    const nextAccount = confirm
      ? approve(current.account, message.proposal, id)
      : null;
    const status = confirm
      ? nextAccount
        ? "confirmed"
        : "failed"
      : "cancelled";
    commit({
      ...current,
      account: nextAccount || current.account,
      conversations: current.conversations.map((c) =>
        c.id !== convo.id
          ? c
          : {
              ...c,
              messages: c.messages.map((m) =>
                m.id !== id
                  ? m
                  : {
                      ...m,
                      text:
                        status === "confirmed"
                          ? `Demo transfer complete. Your available balance is ${money(nextAccount!.balance)}.`
                          : status === "cancelled"
                            ? "Transfer cancelled. Your balance hasn’t changed."
                            : "This transfer couldn’t be completed. Check your current balance and try again.",
                      proposal: { ...message.proposal!, status },
                    },
              ),
            },
      ),
    });
  };
  const removeChat = (id: string) => {
    stopVoice();
    const current = data.current;
    let conversations = current.conversations.filter((c) => c.id !== id);
    if (!conversations.length)
      conversations = [{ id: uid(), title: "New conversation", messages: [] }];
    commit({
      ...current,
      conversations,
      active: current.active === id ? conversations[0].id : current.active,
    });
    setDeleteId(null);
    clearAttachments();
    setDraft("");
  };
  const exportChat = () => {
    const text = active.messages
      .map((m) => `${m.role === "user" ? "You" : "Nexa"}: ${messageText(m)}`)
      .join("\n\n");
    const url = URL.createObjectURL(
      new Blob([`Nexa — demo conversation\n\n${text}`], {
        type: "text/plain;charset=utf-8",
      }),
    );
    const link = document.createElement("a");
    link.href = url;
    link.download = "nexa-conversation.txt";
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNotice("Conversation exported.");
  };
  const startVoice = () => {
    if (listening) {
      voiceSession.current?.stop();
      input.current?.focus();
      return;
    }
    if (!speechConstructor) {
      setNotice(
        "Voice input isn’t available in this browser. You can type your message below.",
      );
      return;
    }
    stopVoice();
    setNotice("");
    voiceSession.current = createVoiceSession(speechConstructor, draft, {
      transcript: (text) => setDraft(text.slice(0, 2000)),
      listening: setListening,
      error: (code) =>
        setNotice(
          code === "not-allowed" || code === "service-not-allowed"
            ? "Microphone access was denied. Allow it in browser settings or type instead."
            : "Voice input couldn’t connect. Please try again or type your message.",
        ),
    });
    voiceSession.current.start();
  };
  const removeAttachment = (id: string) => {
    const url = fileUrls.current.get(id);
    if (url) URL.revokeObjectURL(url);
    fileUrls.current.delete(id);
    setAttachments((files) => files.filter((file) => file.id !== id));
  };
  const clearAttachments = () => {
    attachments.forEach((file) => {
      const url = fileUrls.current.get(file.id);
      if (url) URL.revokeObjectURL(url);
      fileUrls.current.delete(file.id);
    });
    setAttachments([]);
  };
  const pickFiles = (files: FileList | null) => {
    if (!files) return;
    const selected = [...attachments];
    const errors: string[] = [];
    Array.from(files).forEach((file) => {
      if (selected.length >= 5) {
        errors.push("You can attach up to 5 files per message.");
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        errors.push(`${file.name} exceeds the 10 MB limit.`);
        return;
      }
      const id = uid();
      fileUrls.current.set(id, URL.createObjectURL(file));
      selected.push({ id, name: file.name, size: file.size, type: file.type });
    });
    setAttachments(selected);
    setNotice([...new Set(errors)].join(" "));
  };
  const attachmentCards = (files: Attachment[], removable = false) => (
    <div class="nc-attachments">
      {files.map((file) => {
        const url = fileUrls.current.get(file.id);
        const preview =
          url && /^image\/(png|jpeg|webp|gif|avif)$/.test(file.type);
        return (
          <div class="nc-attachment" key={file.id}>
            {preview ? (
              <img src={url} alt={file.name} />
            ) : (
              <Glyph name="file" />
            )}
            <div>
              {url ? (
                <a href={url} download={file.name} title="Download attachment">
                  {file.name}
                </a>
              ) : (
                <span>{file.name}</span>
              )}
              <small>
                {file.size < 1024 * 1024
                  ? `${Math.max(1, Math.round(file.size / 1024))} KB`
                  : `${(file.size / (1024 * 1024)).toFixed(1)} MB`}
                {!url && " · File no longer available after reload"}
              </small>
            </div>
            {removable && (
              <button
                type="button"
                class="nc-icon-button"
                aria-label={`Remove ${file.name}`}
                onClick={() => removeAttachment(file.id)}
              >
                <Glyph name="close" />
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
  return (
    <div class={`nc-shell ${sidebarClosed ? "is-sidebar-collapsed" : ""}`}>
      <nav class="nc-rail" aria-label="Nexa workspace">
        <a class="nc-rail-brand" href="#home" aria-label="Back to Nexa home">
          <Brand size={32} />
        </a>
        <button
          class="nc-rail-active"
          onClick={newChat}
          aria-label="New conversation"
        >
          <Glyph name="chat" />
        </button>
        <button
          onClick={() => send("Show recent transactions")}
          aria-label="Show recent activity"
        >
          <Glyph name="activity" />
        </button>
        <button
          onClick={() => send("Show my travel fund")}
          aria-label="Show savings goals"
        >
          <Glyph name="goals" />
        </button>
        <div class="nc-rail-bottom">
          <a href="#home" aria-label="Return to landing page">
            <Glyph name="home" />
          </a>
          <span class="nc-avatar" aria-label="Demo account">
            N
          </span>
        </div>
      </nav>
      {drawer && (
        <button
          class="nc-backdrop"
          aria-label="Close conversation history"
          onClick={() => {
            setDrawer(false);
            drawerButton.current?.focus();
          }}
        />
      )}
      <aside
        id="nc-conversation-history"
        ref={sidebar}
        class={`nc-sidebar ${drawer ? "is-open" : ""}`}
        aria-label="Conversation history"
      >
        <div class="nc-sidebar-heading">
          <a href="#home">
            Nexa<span>Personal banking</span>
          </a>
          <button
            class="nc-sidebar-close nc-icon-button"
            onClick={() => {
              setDrawer(false);
              drawerButton.current?.focus();
            }}
            aria-label="Close history"
          >
            <Glyph name="close" />
          </button>
        </div>
        <button class="nc-new-chat" onClick={newChat}>
          <Glyph name="plus" />
          New conversation
        </button>
        <label class="nc-search">
          <Glyph name="search" />
          <input
            type="search"
            placeholder="Search conversations"
            aria-label="Search conversations"
            value={search}
            onInput={(e) => setSearch(e.currentTarget.value)}
          />
        </label>
        <div class="nc-history-title">
          YOUR CONVERSATIONS <span>{store.conversations.length}</span>
        </div>
        <div class="nc-history">
          {store.conversations
            .filter((c) => c.title.toLowerCase().includes(search.toLowerCase()))
            .map((c) => (
              <div
                key={c.id}
                class={`nc-history-item ${c.id === store.active ? "is-active" : ""}`}
              >
                <button
                  class="nc-history-select"
                  onClick={() => {
                    stopVoice();
                    clearAttachments();
                    commit({ ...data.current, active: c.id });
                    setDraft("");
                    setDrawer(false);
                    setNotice("");
                  }}
                  aria-current={c.id === store.active ? "true" : undefined}
                >
                  <Glyph name="chat" />
                  <span>{c.title}</span>
                </button>
                <button
                  class="nc-delete nc-icon-button"
                  aria-label={`Delete ${c.title}`}
                  onClick={() => setDeleteId(deleteId === c.id ? null : c.id)}
                >
                  <Glyph name="trash" />
                </button>
                {deleteId === c.id && (
                  <div class="nc-delete-confirm">
                    <span>Delete this conversation?</span>
                    <button onClick={() => removeChat(c.id)}>Delete</button>
                    <button onClick={() => setDeleteId(null)}>Keep</button>
                  </div>
                )}
              </div>
            ))}
          {!store.conversations.some((c) =>
            c.title.toLowerCase().includes(search.toLowerCase()),
          ) && <p class="nc-history-empty">No matching conversations.</p>}
        </div>
      </aside>
      <main class="nc-main" id="nexa-chat">
        <header class="nc-header">
          <button
            ref={drawerButton}
            class="nc-sidebar-toggle nc-icon-button"
            aria-label={
              historyOpen
                ? "Close conversation history"
                : "Open conversation history"
            }
            title={historyOpen ? "Close sidebar" : "Open sidebar"}
            aria-controls="nc-conversation-history"
            aria-expanded={historyOpen}
            onClick={() =>
              compact ? setDrawer(!drawer) : setSidebarClosed(!sidebarClosed)
            }
          >
            <Glyph name="sidebar" />
          </button>
          <div class="nc-header-title">Your conversation</div>
          <div class="nc-header-actions">
            <a class="nc-login-link" href="#login">
              Log in <span>↗</span>
            </a>
            <button
              class="nc-header-button"
              onClick={exportChat}
              disabled={!active.messages.length}
              title="Export conversation"
            >
              <Glyph name="export" />
              <span>Export</span>
            </button>
            <div class="nc-settings-wrap">
              <button
                class="nc-header-button"
                aria-label="Chat settings"
                aria-expanded={settings}
                onClick={() => setSettings(!settings)}
              >
                <Glyph name="settings" />
                <span>Settings</span>
              </button>
              {settings && (
                <div class="nc-settings">
                  <strong>Make it yours</strong>
                  <label>
                    <input
                      type="checkbox"
                      checked={readAloud}
                      disabled={!canSpeak}
                      onChange={(e) => {
                        setReadAloud(e.currentTarget.checked);
                        if (!e.currentTarget.checked) stopVoice();
                      }}
                    />
                    Read replies aloud
                  </label>
                  <p>
                    {canSpeak
                      ? "Spoken replies are off by default."
                      : "Read aloud isn’t supported here."}
                  </p>
                  <button
                    onClick={() => {
                      if (!resetArmed) setResetArmed(true);
                      else {
                        stopVoice();
                        clearAttachments();
                        commit(fresh());
                        setDraft("");
                        setResetArmed(false);
                        setSettings(false);
                        setNotice("Demo account and conversations reset.");
                      }
                    }}
                  >
                    {resetArmed
                      ? "Confirm reset of demo & chats"
                      : "Reset demo account"}
                  </button>
                  {resetArmed && (
                    <button onClick={() => setResetArmed(false)}>
                      Cancel reset
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        </header>
        <div
          ref={scrollArea}
          class={`nc-scroll ${active.messages.length ? "has-messages" : ""}`}
        >
          {!active.messages.length ? (
            <section class="nc-welcome" aria-labelledby="nc-welcome-title">
              <div class="nc-welcome-mark">
                <Brand size={45} />
              </div>
              <p class="nc-eyebrow">A LITTLE MORE HUMAN.</p>
              <h1 id="nc-welcome-title">
                Welcome to Nexa.
                <br />
                <span>How can I help?</span>
              </h1>
              <p class="nc-welcome-sub">
                Ask a question or get to know Nexa.
                <br />
                You don’t need an account to explore.
              </p>
              <div class="nc-prompt-chips nc-welcome-prompts">
                <button onClick={() => send("How does Nexa work?")}>
                  <Glyph name="chat" />
                  How does Nexa work?
                </button>
                <button onClick={() => send("I’m new to Nexa")}>
                  <Glyph name="plus" />
                  I’m new to Nexa
                </button>
              </div>
            </section>
          ) : (
            <div
              class="nc-messages"
              role="log"
              aria-label="Conversation"
              aria-live="polite"
              aria-relevant="additions text"
            >
              {active.messages.map((m) => (
                <article key={m.id} class={`nc-message nc-message-${m.role}`}>
                  <span class="nc-message-avatar">
                    {m.role === "assistant" ? <Brand size={22} /> : "You"}
                  </span>
                  <div class="nc-message-body">
                    <span class="nc-message-name">
                      {m.role === "assistant" ? "Nexa" : "You"}
                    </span>
                    {m.text && <p>{m.text}</p>}
                    {!!m.attachments?.length && attachmentCards(m.attachments)}
                    {m.card && <ResultCard card={m.card} />}
                    {m.proposal && (
                      <div
                        class={`nc-result nc-transfer nc-transfer-${m.proposal.status}`}
                      >
                        <span>
                          {m.proposal.status === "pending"
                            ? "Review demo transfer"
                            : m.proposal.status === "confirmed"
                              ? "Demo transfer complete"
                              : m.proposal.status === "cancelled"
                                ? "Transfer cancelled"
                                : "Transfer unsuccessful"}
                          <Glyph
                            name={
                              m.proposal.status === "confirmed"
                                ? "check"
                                : "diagonal"
                            }
                          />
                        </span>
                        <strong>{money(m.proposal.amount)}</strong>
                        <div class="nc-transfer-details">
                          <span>
                            From<strong>Everyday •• 2048</strong>
                          </span>
                          <span>
                            To<strong>{m.proposal.recipient}</strong>
                          </span>
                        </div>
                        {m.proposal.status === "pending" && (
                          <div class="nc-transfer-actions">
                            <button onClick={() => decide(m.id, true)}>
                              Confirm demo transfer
                              <Glyph name="arrow" />
                            </button>
                            <button onClick={() => decide(m.id, false)}>
                              Cancel
                            </button>
                          </div>
                        )}
                      </div>
                    )}
                    {m.role === "assistant" && (
                      <div class="nc-message-actions">
                        <button
                          aria-label="Copy response"
                          onClick={async () => {
                            try {
                              await navigator.clipboard.writeText(
                                messageText(m),
                              );
                              setNotice("Response copied.");
                            } catch {
                              setNotice(
                                "Copy isn’t available. You can select the response text instead.",
                              );
                            }
                          }}
                        >
                          <Glyph name="copy" />
                        </button>
                        {canSpeak && (
                          <button
                            aria-label={
                              speaking === m.id
                                ? "Stop reading response"
                                : "Read response aloud"
                            }
                            onClick={() => speak(messageText(m), m.id)}
                          >
                            <Glyph
                              name={speaking === m.id ? "stop" : "sound"}
                            />
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                </article>
              ))}
              <div ref={messagesEnd} />
            </div>
          )}
        </div>
        <div class="nc-compose-area">
          <div class="nc-notice" role="status">
            {notice}
            {notice && (
              <button
                aria-label="Dismiss notification"
                onClick={() => setNotice("")}
              >
                <Glyph name="close" />
              </button>
            )}
          </div>
          <form
            class={`nc-composer ${listening ? "is-listening" : ""}`}
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
          >
            <label class="nc-sr-only" for="nc-message-input">
              Message Nexa
            </label>
            {!!attachments.length && attachmentCards(attachments, true)}
            {listening && (
              <div
                class="nc-listening"
                role="status"
                aria-label="Listening. Select Stop when you are finished."
              >
                <span class="nc-sr-only">
                  Listening. Select Stop when you are finished.
                </span>
                <div class="nc-listening-wave" aria-hidden="true">
                  {Array.from({ length: 15 }, (_, i) => (
                    <i
                      key={i}
                      style={{
                        animationDelay: `${i * -0.11}s`,
                        height: `${8 + (i % 4) * 5}px`,
                      }}
                    />
                  ))}
                </div>
              </div>
            )}
            <textarea
              id="nc-message-input"
              ref={input}
              value={draft}
              maxLength={2000}
              rows={2}
              placeholder={
                listening ? "" : "Ask Nexa anything about your money…"
              }
              readOnly={listening}
              aria-label={listening ? "Live voice transcript" : "Message Nexa"}
              onInput={(e) => setDraft(e.currentTarget.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && !e.shiftKey && !e.isComposing) {
                  e.preventDefault();
                  send();
                }
              }}
            />
            <div class="nc-compose-bottom">
              <input
                ref={fileInput}
                class="nc-file-input"
                type="file"
                accept=".pdf,.doc,.docx,.txt,.csv,.xls,.xlsx,.ppt,.pptx,.rtf,.odt,.ods,.odp,.md,.json"
                multiple
                tabIndex={-1}
                aria-label="Choose documents"
                onChange={(event) => {
                  pickFiles(event.currentTarget.files);
                  event.currentTarget.value = "";
                }}
              />
              <input
                ref={photoInput}
                class="nc-file-input"
                type="file"
                accept="image/*"
                multiple
                tabIndex={-1}
                aria-label="Choose photos"
                onChange={(event) => {
                  pickFiles(event.currentTarget.files);
                  event.currentTarget.value = "";
                }}
              />
              <div
                class="nc-attach-wrap"
                ref={attachWrap}
                onKeyDown={(event) => {
                  if (event.key === "Escape") {
                    event.stopPropagation();
                    setAttachOpen(false);
                    attachButton.current?.focus();
                  }
                }}
                onBlur={(event) => {
                  if (
                    !event.currentTarget.contains(event.relatedTarget as Node)
                  )
                    setAttachOpen(false);
                }}
              >
                <button
                  ref={attachButton}
                  type="button"
                  class="nc-attach-button"
                  aria-label="Attach photos or files"
                  aria-expanded={attachOpen}
                  aria-controls="nc-attach-options"
                  title="Attach photos or files · up to 5 files, 10 MB each"
                  onClick={() => setAttachOpen(!attachOpen)}
                >
                  <Glyph name="attach" />
                  <span>Attach</span>
                </button>
                {attachOpen && (
                  <div
                    class="nc-attach-menu"
                    id="nc-attach-options"
                    role="group"
                    aria-label="Attachment options"
                  >
                    <button
                      type="button"
                      onClick={() => {
                        setAttachOpen(false);
                        attachButton.current?.focus();
                        photoInput.current?.click();
                      }}
                    >
                      <Glyph name="photo" />
                      <span>Photos</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setAttachOpen(false);
                        attachButton.current?.focus();
                        fileInput.current?.click();
                      }}
                    >
                      <Glyph name="file" />
                      <span>Documents</span>
                    </button>
                  </div>
                )}
              </div>
              <div>
                <button
                  type="button"
                  class={`nc-voice-button ${listening ? "is-active" : ""}`}
                  aria-label={
                    listening ? "Stop voice input" : "Use voice input"
                  }
                  aria-pressed={listening}
                  onClick={startVoice}
                >
                  <Glyph name={listening ? "stop" : "mic"} />
                  <span>{listening ? "Stop" : "Voice"}</span>
                </button>
                <button
                  class="nc-send"
                  type="submit"
                  disabled={listening || (!draft.trim() && !attachments.length)}
                  aria-label="Send message"
                >
                  <Glyph name="arrow" />
                </button>
              </div>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
}
