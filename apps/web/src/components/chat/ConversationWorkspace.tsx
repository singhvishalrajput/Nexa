import { h } from "preact";
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError, AuthSession } from "../../services/auth";
import { Conversation, Turn, TurnRequest, createConversation, deleteConversation, listConversations, loadTurns, sendTurn } from "../../services/conversations";
import { useVoiceInput } from "../../hooks/useVoiceInput";
import { ChatIcon, MessageBubble } from "./MessageBubble";
import { AssistantResponse } from "./AssistantResponse";
import { formatMoney, safeMask } from "../../services/banking-content";

type Props = { session: AuthSession; onClose: () => void; onLogout: () => void | Promise<void> };
const greeting = "Hi! How can I help with your banking today?";
const spokenResponse = (turn: Turn) => {
  const data = turn.banking;
  if (data?.type === "ACCOUNTS") return turn.assistantText + " " + data.accounts.map(account => `${account.displayName}, account ending ${safeMask(account.accountNumberMasked).slice(-4)}. Available balance ${formatMoney(account.availableBalance, account.currencyCode)}.`).join(" ");
  if (data?.type === "TRANSACTIONS") return turn.assistantText + " " + data.transactions.map(item => `${item.merchantName || item.type}, ${formatMoney(item.amount, item.currencyCode, true)}, ${item.status.toLowerCase()}.`).join(" ");
  if (data?.type === "MANDATES") return turn.assistantText + " " + data.mandates.map(item => item.payee + ", up to " + formatMoney(item.limit,item.currencyCode) + ", " + item.status).join(". ");
  if (data?.type === "BILLS") return turn.assistantText + " " + data.bills.map(item => item.billerName + ", " + formatMoney(item.amount,item.currencyCode) + ", " + item.status).join(". ");
  if (data?.type === "CARDS") return turn.assistantText + " " + data.cards.map(item => item.displayName + ", " + item.status + (item.cardType === "DEBIT" ? "" : ", outstanding " + formatMoney(item.outstanding,item.currencyCode))).join(". ");
  if (data?.type === "BENEFICIARIES") return turn.assistantText + " " + data.beneficiaries.map(item => item.displayName + ", " + item.status).join(". ");
  if (data?.type === "SCHEDULED_PAYMENTS" || data?.type === "UPCOMING") return turn.assistantText + " " + data.payments.map(item => item.payee + ", " + formatMoney(item.amount,item.currencyCode) + ", " + item.dueAt + ", " + item.status).join(". ");
  if (data?.type === "LOANS") return turn.assistantText + " " + data.loans.map(item => item.displayName + ", next EMI " + formatMoney(item.nextEmi,item.currencyCode) + ", " + item.status).join(". ");
  return turn.assistantText;
};

export function ConversationWorkspace({ session, onClose, onLogout }: Props) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [current, setCurrent] = useState<Conversation | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(greeting);
  const [language, setLanguage] = useState<"en-IN" | "hi-IN">("en-IN");
  const [menuOpen, setMenuOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [outgoing, setOutgoing] = useState<{ text: string; source: "TEXT" | "VOICE"; createdAt: string } | null>(null);
  const [hasNew, setHasNew] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [wideLayout, setWideLayout] = useState(() => window.matchMedia("(min-width: 900px)").matches);
  const historyModal = historyOpen && !wideLayout;
  const [older, setOlder] = useState(false);
  const [moreHistory, setMoreHistory] = useState(false);
  const [readAloud, setReadAloud] = useState(false);
  const page = useRef(0);
  const animatedTurn = useRef<string | null>(null);
  const smoothNext = useRef(false);
  const locked = useRef(true);
  const pending = useRef<{ conversationId: string | null; request: TurnRequest } | null>(null);
  const pendingDelete = useRef<string | null>(null);
  const alive = useRef(true);
  const log = useRef<HTMLElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const historyToggle = useRef<HTMLButtonElement>(null);
  const historyPanel = useRef<HTMLElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const menuToggle = useRef<HTMLButtonElement>(null);
  const previousOverlay = useRef<"history" | "menu" | null>(null);
  const followLatest = useRef(true);
  const prependHeight = useRef<number | null>(null);
  const voice = useVoiceInput(setError);
  const listening = voice.phase !== "idle";
  const token = session.accessToken;

  useEffect(() => {
    const media = window.matchMedia("(min-width: 900px)");
    const update = () => { setWideLayout(media.matches); setHistoryOpen(false); };
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    if (wideLayout && historyOpen) {
      historyPanel.current?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
      setHistoryOpen(false);
    }
  }, [wideLayout, historyOpen]);

  const speak = (text: string, force = false) => {
    if ((!readAloud && !force) || !window.speechSynthesis) return;
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = /[\u0900-\u097f]/.test(text) ? "hi-IN" : "en-IN";
    window.speechSynthesis.speak(utterance);
  };
  const tell = (text: string, force = false) => { setNotice(text); speak(text, force); };
  const startBusy = () => { locked.current = true; setBusy(true); setError(""); };
  const endBusy = () => { locked.current = false; if (alive.current) setBusy(false); };

  useEffect(() => {
    alive.current = true;
    listConversations(token).then(async (items) => {
      const recent = items[0] ? await loadTurns(token, items[0].id) : [];
      if (!alive.current) return;
      setConversations(items); setMoreHistory(items.length === 30);
      setCurrent(items[0] || null); setTurns([...recent].reverse()); setOlder(recent.length === 30);
    }).catch((e) => alive.current && setError(e instanceof ApiRequestError && e.status === 404
      ? "Chat is temporarily unavailable. Please try again shortly."
      : "I could not load your conversations. Please reload the page to try again.")).finally(endBusy);
    return () => { alive.current = false; window.speechSynthesis?.cancel(); pending.current = null; };
  }, [token]);

  const latest = (smooth = true) => {
    followLatest.current = true; setHasNew(false);
    log.current?.scrollTo({ top: log.current.scrollHeight, behavior: smooth && !window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "smooth" : "auto" });
  };
  useLayoutEffect(() => {
    const element = log.current;
    if (!element) return;
    if (prependHeight.current !== null) {
      element.scrollTop += element.scrollHeight - prependHeight.current;
      prependHeight.current = null;
    } else if (followLatest.current) latest(smoothNext.current);
    else setHasNew(true);
    smoothNext.current = false;
  }, [turns, notice, outgoing, sending]);

  useLayoutEffect(() => {
    if (!textarea.current) return;
    textarea.current.style.height = "auto";
    textarea.current.style.height = `${Math.min(textarea.current.scrollHeight, 144)}px`;
  }, [input, voice.phase]);

  useEffect(() => {
    // Match the visible area when a mobile keyboard reduces the viewport.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const viewport = window.visualViewport;
    const update = () => {
      if (viewport && viewport.scale === 1) {
        document.documentElement.style.setProperty("--messenger-height", `${viewport.height}px`);
        document.documentElement.style.setProperty("--messenger-top", `${viewport.offsetTop}px`);
      }
    };
    update(); viewport?.addEventListener("resize", update); viewport?.addEventListener("scroll", update);
    return () => {
      viewport?.removeEventListener("resize", update); viewport?.removeEventListener("scroll", update);
      document.documentElement.style.removeProperty("--messenger-height");
      document.documentElement.style.removeProperty("--messenger-top");
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  useEffect(() => {
    if (!historyModal && !menuOpen) return;
    const panel = historyModal ? historyPanel.current : menu.current;
    panel?.querySelector<HTMLButtonElement>("button:not(:disabled)")?.focus();
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setHistoryOpen(false); setMenuOpen(false);
        (historyModal ? historyToggle : menuToggle).current?.focus();
      }
      if (event.key === "Tab" && historyModal && panel) {
        const controls = Array.from(panel.querySelectorAll<HTMLElement>("button:not(:disabled)"));
        const first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    const outside = (event: PointerEvent) => {
      if (menuOpen && !menu.current?.contains(event.target as Node) && !menuToggle.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    window.addEventListener("keydown", close); window.addEventListener("pointerdown", outside);
    return () => { window.removeEventListener("keydown", close); window.removeEventListener("pointerdown", outside); };
  }, [historyModal, menuOpen]);

  useLayoutEffect(() => {
    const previous = previousOverlay.current;
    previousOverlay.current = historyModal ? "history" : menuOpen ? "menu" : null;
    if (!historyModal && !menuOpen && previous) {
      // Wait until the background is no longer inert before restoring focus.
      (previous === "history" ? historyToggle : menuToggle).current?.focus();
    }
  }, [historyModal, menuOpen]);

  const select = async (conversation: Conversation) => {
    if (locked.current || listening || pending.current) return;
    startBusy();
    try {
      const items = await loadTurns(token, conversation.id);
      if (!alive.current) return;
      followLatest.current = true; setHasNew(false); animatedTurn.current = null;
      setCurrent(conversation); setTurns([...items].reverse()); setOlder(items.length === 30);
      setHistoryOpen(false); setInput(""); pendingDelete.current = null;
      setNotice(""); (wideLayout ? textarea : historyToggle).current?.focus();
    } catch (_) { setError("This conversation couldn’t be opened. Please try again."); }
    finally { endBusy(); }
  };

  const earlier = async () => {
    if (locked.current) return;
    if (!current || !older) { tell("You have reached the start of this conversation."); return; }
    startBusy();
    try {
      const items = await loadTurns(token, current.id, turns[0]?.id);
      prependHeight.current = log.current?.scrollHeight || 0;
      setTurns((existing) => [...items.reverse(), ...existing]); setOlder(items.length === 30);
    } catch (_) { setError("Earlier messages couldn’t be loaded. Please try again."); }
    finally { endBusy(); }
  };

  const more = async () => {
    if (locked.current) return;
    startBusy();
    try {
      const items = await listConversations(token, page.current + 1);
      page.current += 1;
      setConversations((existing) => [...existing, ...items]); setMoreHistory(items.length === 30);
    } catch (_) { setError("History couldn’t be loaded. Please try again."); }
    finally { endBusy(); }
  };

  const submit = async (value = input, source: "TEXT" | "VOICE" = "TEXT") => {
    if (locked.current || !value.trim()) return;
    const command = value.trim().toLowerCase().replace(/[.!?]+$/, "");
    if (command === "discard unsent message") { pending.current = null; setOutgoing(null); setError(""); setInput(""); tell("Removed from this device. If the message reached Nexa, it may still appear in your history."); return; }
    if (pending.current && command !== "retry") { tell("Say ‘retry’ to save the pending message, or ‘discard unsent message’. It is held only in memory until you leave."); return; }
    if (command === "new conversation") {
      followLatest.current = true; setHasNew(false); setHistoryOpen(false); setError("");
      setCurrent(null); setTurns([]); setInput(""); setOlder(false); pendingDelete.current = null; tell(greeting); textarea.current?.focus(); return;
    }
    if (command === "show history") { setHistoryOpen(true); setInput(""); tell(conversations.length ? conversations.map((item, i) => `Conversation ${i + 1}: ${item.title}`).join(". ") + ". Say ‘open conversation’ followed by its number." : "You have no saved conversations yet."); return; }
    const open = command.match(/^open conversation (\d+)$/);
    if (open) { const item = conversations[Number(open[1]) - 1]; if (item) await select(item); else tell("I could not find that conversation number."); return; }
    if (command === "earlier messages") { setInput(""); await earlier(); return; }
    if (command === "more conversations") { setInput(""); if (moreHistory) await more(); else tell("All conversations are loaded."); return; }
    if (command === "read aloud") { setReadAloud(true); setInput(""); tell("Spoken replies are on. Say ‘stop reading’ to turn them off.", true); return; }
    if (command === "stop reading") { window.speechSynthesis?.cancel(); setReadAloud(false); setInput(""); setNotice("Spoken replies are off."); return; }
    if (command === "repeat") { speak(turns.length ? spokenResponse(turns[turns.length - 1]) : notice, true); setInput(""); return; }
    if (command === "sign out") { await onLogout(); return; }
    if (command === "delete conversation") {
      pendingDelete.current = current?.id || null; setInput("");
      tell(current ? "This will permanently delete this conversation. Say ‘confirm delete’ or ‘cancel’." : "There is no saved conversation to delete."); return;
    }
    if (command === "cancel") { pendingDelete.current = null; setInput(""); tell("Cancelled. Nothing has changed."); return; }
    if (command === "confirm delete") {
      if (!current || pendingDelete.current !== current.id) { tell("There is no deletion waiting for confirmation."); return; }
      startBusy();
      try {
        await deleteConversation(token, current.id);
        setConversations((items) => items.filter((item) => item.id !== current.id));
        setCurrent(null); setTurns([]); setOlder(false); pendingDelete.current = null; setInput(""); tell("This conversation has been deleted.");
      } catch (_) { setError("This conversation couldn’t be deleted. Please try again."); }
      finally { endBusy(); }
      return;
    }
    pendingDelete.current = null;
    if (command === "retry" && !pending.current) { tell("There is no message waiting to be retried."); return; }
    if (value.length > 2000) { tell("Please keep each message under 2,000 characters."); setInput(""); return; }
    followLatest.current = true; setHasNew(false);
    const queued = pending.current || { conversationId: current?.id || null, request: { clientId: crypto.randomUUID(), source, text: value.trim() } };
    pending.current = queued;
    smoothNext.current = true;
    setOutgoing({ text: queued.request.source === "VOICE" ? "Voice message" : queued.request.text, source: queued.request.source, createdAt: new Date().toISOString() });
    setInput(""); setNotice(""); setSending(true); startBusy();
    try {
      let conversation = current;
      if (!queued.conversationId) {
        conversation = await createConversation(token);
        if (!alive.current) return;
        setCurrent(conversation); setConversations((items) => [conversation!, ...items]);
        queued.conversationId = conversation.id;
      }
      const result = await sendTurn(token, queued.conversationId!, queued.request);
      pending.current = null;
      if (!alive.current) return;
      setOutgoing(null);
      animatedTurn.current = result.id; smoothNext.current = true;
      setTurns((items) => items.some((item) => item.id === result.id) ? items : [...items, result]);
      setNotice(""); speak(spokenResponse(result));
      // Refresh titles without loading message bodies for every conversation.
      // A history refresh failure must not turn a successful send into a failed one.
      try {
        const fresh = await listConversations(token);
        if (alive.current) { setConversations(fresh); page.current = 0; setMoreHistory(fresh.length === 30); }
      } catch (_) { /* Existing history remains usable; the exchange is saved. */ }
    } catch (e) {
      setInput("");
      setError(e instanceof ApiRequestError && e.status === 401
        ? "Your session has expired. Sign in again to continue."
        : "We couldn’t confirm your message was saved. Retry safely to check without sending it twice.");
    } finally { setSending(false); endBusy(); }
  };

  const sendVoice = () => {
    const transcript = voice.transcript;
    voice.cancel();
    void submit(transcript, "VOICE");
  };
  const runCommand = (command: string) => { setMenuOpen(false); void submit(command); };
  const dayFor = (date: string) => new Date(date).toLocaleDateString([], { day: "numeric", month: "long", year: "numeric" });
  const closeHistory = () => { setHistoryOpen(false); historyToggle.current?.focus(); };

  const historyContents = <>
      <header><h2 id="messenger-history-title">Conversations</h2>{!wideLayout && <button type="button" class="messenger-icon" aria-label="Close history" onClick={closeHistory}><ChatIcon name="close" /></button>}</header>
      <button type="button" class="messenger-new-chat" disabled={busy || listening || !!pending.current} onClick={() => void submit("new conversation")}>New conversation</button>
      <div class="messenger-history-list">{conversations.length ? conversations.map((item) => <button type="button" disabled={busy || listening || !!pending.current} aria-current={item.id === current?.id ? "true" : undefined} class={item.id === current?.id ? "is-current" : ""} onClick={() => select(item)} key={item.id}><span>{item.title.replace(/\.$/, "").replace(/^./, letter => letter.toUpperCase())}</span><time>{dayFor(item.createdAt)}</time></button>) : <p>Your conversations will appear here.</p>}
        {moreHistory && <button type="button" disabled={busy || listening} onClick={more}>Load more conversations</button>}
      </div>
  </>;

  return <div class="nexa-messenger">
    {wideLayout && <aside ref={historyPanel} id="messenger-history" class="messenger-history messenger-history-sidebar" aria-labelledby="messenger-history-title">{historyContents}</aside>}
    <main class="messenger-main" aria-label="Nexa banking conversation" inert={historyModal}>
      <header class="messenger-header">
        <button type="button" class="messenger-icon" onClick={onClose} aria-label="Back to Nexa home"><ChatIcon name="back" /></button>
        <div class="messenger-identity"><h1>Nexa</h1><span>{sending ? "Thinking…" : "Your banking assistant"}</span></div>
        <button ref={historyToggle} type="button" class="messenger-icon" onClick={() => { setMenuOpen(false); setHistoryOpen(true); }} aria-label="Conversation history" aria-expanded={wideLayout || historyOpen} aria-controls="messenger-history"><ChatIcon name="history" /></button>
        <div class="messenger-menu-wrap">
          <button ref={menuToggle} type="button" class="messenger-icon" aria-label="Conversation options" aria-expanded={menuOpen} aria-controls="messenger-options" onClick={() => setMenuOpen(!menuOpen)}><ChatIcon name="more" /></button>
          {menuOpen && <div ref={menu} id="messenger-options" class="messenger-options" aria-label="Conversation options">
            <button type="button" disabled={busy || listening || !!pending.current} onClick={() => runCommand("new conversation")}>New conversation</button>
            <button type="button" disabled={busy || listening} onClick={() => runCommand(readAloud ? "stop reading" : "read aloud")}>{readAloud ? "Turn off spoken replies" : "Read replies aloud"}</button>
            <button type="button" disabled={busy || listening} onClick={() => runCommand("repeat")}>Read last reply</button>
            <button type="button" disabled={!current || busy || listening || !!pending.current} onClick={() => runCommand("delete conversation")}>Delete conversation</button>
            <button type="button" disabled={busy || listening} onClick={() => { setMenuOpen(false); void onLogout(); }}>Sign out</button>
          </div>}
        </div>
      </header>
      <div class="messenger-feed-wrap">
        <section ref={log} class="messenger-feed" aria-label="Messages" tabIndex={0} onScroll={() => {
          const element = log.current;
          if (!element) return;
          followLatest.current = element.scrollHeight - element.scrollTop - element.clientHeight < 90;
          if (followLatest.current) setHasNew(false);
        }}>
          <div class="messenger-timeline">
            {older && <div class="messenger-load"><button type="button" disabled={busy || listening} onClick={earlier}>Load earlier messages</button></div>}
            {!turns.length && !outgoing && <>
              <div class="messenger-date">Your conversation with Nexa</div>
              <MessageBubble role="assistant" text={greeting} />
              {!busy && <div class="messenger-suggestions" aria-label="Suggested messages">
                {["What's my account balance?", "Show my recent transactions.", "Show my bills."].map((suggestion) => <button type="button" disabled={listening} onClick={() => void submit(suggestion)} key={suggestion}>{suggestion}</button>)}
              </div>}
            </>}
            <div role="log" aria-label="Conversation messages" aria-live="polite" aria-relevant="additions text">
              {turns.map((turn, index) => <div class="messenger-exchange" key={turn.id}>
                {(index === 0 || dayFor(turn.createdAt) !== dayFor(turns[index - 1].createdAt)) && <div class="messenger-date">{dayFor(turn.createdAt)}</div>}
                <MessageBubble role="user" text={turn.userText} timestamp={turn.createdAt} voice={turn.source === "VOICE"} status="Saved" />
                <div class={animatedTurn.current === turn.id ? "messenger-arrival" : undefined}><AssistantResponse turn={turn} accessToken={token} /></div>
              </div>)}
              {outgoing && <MessageBubble animate role="user" text={outgoing.text} timestamp={outgoing.createdAt} status={sending ? "Sending…" : "Not confirmed"} />}
              {sending && <MessageBubble animate role="assistant"><span class="messenger-thinking" role="status"><i /><i /><i /><span>Nexa is thinking</span></span></MessageBubble>}
            </div>
            {busy && !sending && <p class="messenger-loading" role="status">Loading conversation…</p>}
            {notice && notice !== greeting && <MessageBubble key={notice} animate role="assistant" text={notice}>
              {pendingDelete.current && <div class="messenger-inline-actions"><button type="button" disabled={busy} onClick={() => void submit("cancel")}>Keep conversation</button><button type="button" disabled={busy} onClick={() => void submit("confirm delete")}>Confirm delete</button></div>}
            </MessageBubble>}
            {error && <div class="messenger-error" role="alert"><p>{error}</p>
              {pending.current ? <div class="messenger-inline-actions"><button type="button" disabled={busy || listening} onClick={() => void submit("retry")}>Retry message</button><button type="button" disabled={busy || listening} onClick={() => void submit("discard unsent message")}>Dismiss</button></div>
                : <button type="button" disabled={busy || listening} onClick={() => window.location.reload()}>Reload conversation</button>}
            </div>}
          </div>
        </section>
        {hasNew && <button type="button" class="messenger-new" onClick={() => latest()}><ChatIcon name="down" />New messages</button>}
      </div>
      <footer class="messenger-bottom">
        <form class="messenger-composer" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          {listening ? <div class="messenger-voice-session">
            <div class="messenger-voice-state" role="status"><span class={voice.phase === "listening" ? "recording-dot" : ""} /><strong>{voice.phase === "review" ? "Voice message ready" : voice.phase === "stopping" ? "Finishing…" : "Listening"}</strong><time>{Math.floor(voice.seconds / 60)}:{String(voice.seconds % 60).padStart(2, "0")}</time></div>
            {voice.phase === "review" && <p class="messenger-voice-preview" dir="auto">{voice.transcript}</p>}
            <div class="messenger-voice-actions"><button type="button" onClick={voice.cancel}>Cancel</button>{voice.phase === "review" ? <button type="button" class="messenger-primary" onClick={sendVoice} aria-label="Send voice message"><ChatIcon name="send" />Send</button> : <button type="button" disabled={voice.phase === "stopping"} onClick={voice.stop} aria-label="Stop listening"><ChatIcon name="stop" />Stop</button>}</div>
          </div> : <div class="messenger-input-row">
            <label class="messenger-sr-only" for="nexa-conversation-input">Message Nexa</label>
            <textarea ref={textarea} id="nexa-conversation-input" rows={1} maxLength={2000} value={input} readOnly={busy || !!pending.current} dir="auto" enterkeyhint="send" onInput={(event) => setInput(event.currentTarget.value)} onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey && !event.isComposing && window.matchMedia("(pointer: fine)").matches) { event.preventDefault(); void submit(); }
            }} placeholder="Message…" />
            <button type="button" class={`messenger-icon messenger-mic ${input.trim() ? "" : "messenger-primary"}`} disabled={busy || !!pending.current} onClick={() => { setError(""); voice.start(language); }} aria-label="Record voice message"><ChatIcon name="mic" /></button>
            <button type="submit" class={`messenger-icon messenger-send ${input.trim() ? "messenger-primary" : ""}`} disabled={busy || !!pending.current || !input.trim()} aria-label="Send message"><ChatIcon name="send" /></button>
          </div>}
        </form>
        <div class="messenger-composer-meta"><label>Voice language <select aria-label="Voice language" value={language} disabled={listening} onChange={(event) => setLanguage(event.currentTarget.value as "en-IN" | "hi-IN")}><option value="en-IN">English</option><option value="hi-IN">हिन्दी</option></select></label><details><summary>Voice privacy</summary><p>Nexa saves the meaning, not audio or a word-for-word transcript. Your browser’s speech service may process audio. Stop to review before sending. Hindi voice capture is supported; banking understanding currently supports basic English requests.</p></details></div>
      </footer>
    </main>
    {!wideLayout && <div hidden={!historyModal} inert={!historyModal} class="messenger-drawer-backdrop" onClick={closeHistory}><aside ref={historyPanel} id="messenger-history" class="messenger-history" role="dialog" aria-modal="true" aria-labelledby="messenger-history-title" onClick={(event) => event.stopPropagation()}>{historyContents}</aside></div>}
  </div>;
}
