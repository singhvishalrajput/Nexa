import { WorkspaceRail } from "../design/WorkspaceRail";
import { Action } from "../design/Action";
import "ojs/ojinputtext";
import { BankingIcon } from "../BankingIcon";
import { SidebarBrand, SidebarNavigation, SidebarFooter } from "../SidebarNavigation";
import { getLocale, setLocale, t } from "../../services/locale";
import { AccountContext, QuickAction } from "./ConversationTools";
import { AccountSnapshot } from "../../services/banking-content";
import { Modal } from "../../features/banking/ui";
import { workflowConfirmationLabel } from "./WorkflowCard";
import { ConnectionNotice } from "../ConnectionNotice";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { useEffect, useLayoutEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError, AuthSession } from "../../services/auth";
import { ActionCommand, Conversation, Turn, TurnRequest, createConversation, deleteConversation, listConversations, loadTurns, sendTurn } from "../../services/conversations";
import { useVoiceInput } from "../../hooks/useVoiceInput";
import { ChatIcon, MessageBubble } from "./MessageBubble";
import { AssistantResponse } from "./AssistantResponse";
import { ConversationHistoryItem } from "./ConversationHistoryItem";
import { formatMoney, safeMask } from "../../services/banking-content";

type Props = { session: AuthSession; onClose: () => void; onLogout: () => void | Promise<void>; accounts?: AccountSnapshot[]; accountsLoading?: boolean; accountsError?: string; onRefreshAccounts?: () => void };
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

export function ConversationWorkspace({ session, onClose, onLogout, accounts = [], accountsLoading = false, accountsError = "", onRefreshAccounts = () => {} }: Props) {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [current, setCurrent] = useState<Conversation | null>(null);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [, updateWorkflowClock] = useState(0);
  useEffect(() => {
    const workflow = [...turns].reverse().find(turn => turn.workflow)?.workflow;
    if (!workflow || !["COLLECTING", "REVIEW"].includes(workflow.status)) return;
    const remaining = Date.parse(workflow.expiresAt) - Date.now();
    if (!Number.isFinite(remaining) || remaining <= 0) return;
    const timer = window.setTimeout(() => updateWorkflowClock(value => value + 1), Math.min(remaining + 25, 2147483647));
    return () => window.clearTimeout(timer);
  }, [turns]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [voiceError, setVoiceError] = useState("");
  const [guidance, setGuidance] = useState<"help" | null>(null);
  const [notice, setNotice] = useState(greeting);
  const [language, setLanguage] = useState<"en-IN" | "hi-IN">(getLocale);
  const [contextOpen, setContextOpen] = useState(false);
  const [balancesHidden, setBalancesHidden] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [sending, setSending] = useState(false);
  const [outgoing, setOutgoing] = useState<{ text: string; source: "TEXT" | "VOICE"; createdAt: string } | null>(null);
  const [hasNew, setHasNew] = useState(false);
  const [historySearch, setHistorySearch] = useState("");
  const [historyOpen, setHistoryOpen] = useState(false);
  const [wideLayout, setWideLayout] = useState(() => window.matchMedia("(min-width: 900px)").matches);
  const historyModal = historyOpen && !wideLayout;
  const [older, setOlder] = useState(false);
  const [moreHistory, setMoreHistory] = useState(false);
  const [readAloud, setReadAloud] = useState(false);
  const page = useRef(0);
  const animatedTurn = useRef<string | null>(null);
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
  const voice = useVoiceInput(setVoiceError);
  const hindi = language === "hi-IN";
  const listening = voice.phase !== "idle";
  const token = session.accessToken;
  useNavigationGuard(!!input.trim() || listening || !!pending.current, sending);
  const discardDraft = () => !input.trim() || window.confirm(t("Discard your typed message and continue?"));

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
    if (!turns.length && !outgoing && !sending && !guidance) { element.scrollTop = 0; return; }
    if (prependHeight.current !== null) {
      element.scrollTop += element.scrollHeight - prependHeight.current;
      prependHeight.current = null;
    } else if (followLatest.current) latest(false);
    else setHasNew(true);

  }, [turns, notice, outgoing, sending, guidance]);

  useLayoutEffect(() => {
    if (!textarea.current) return;
    textarea.current.style.height = "auto";
    textarea.current.style.height = `${Math.min(textarea.current.scrollHeight + 2, 144)}px`;
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
      if (event.key === "Tab" && panel) {
        const controls = Array.from(panel.querySelectorAll<HTMLElement>("button:not(:disabled), a[href], select:not(:disabled), summary")).filter(control => control.getClientRects().length > 0);
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
    if (!discardDraft()) return;
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
      setConversations((existing) => [...existing, ...items.filter(item => !existing.some(old => old.id === item.id))]); setMoreHistory(items.length === 30);
    } catch (_) { setError("History couldn’t be loaded. Please try again."); }
    finally { endBusy(); }
  };

  const removeConversation = async (conversation: Conversation) => {
    if (locked.current || listening || pending.current) return;
    startBusy();
    try {
      try { await deleteConversation(token, conversation.id); }
      catch (e) { if (!(e instanceof ApiRequestError && e.status === 404)) throw e; }
      if (!alive.current) return;
      setConversations(items => items.filter(item => item.id !== conversation.id));
      if (current?.id === conversation.id) {
        window.speechSynthesis?.cancel();
        setCurrent(null); setTurns([]); setOlder(false); setInput(""); setOutgoing(null);
        setGuidance(null); setHasNew(false); animatedTurn.current = null; followLatest.current = true;
        tell("This conversation has been deleted.");
      }
      pendingDelete.current = null;
      // Refill the last loaded page so offset pagination cannot skip an item after deletion.
      try {
        const lastPage = await listConversations(token, page.current);
        if (alive.current) {
          setConversations(items => [...items, ...lastPage.filter(item => !items.some(old => old.id === item.id))]);
          setMoreHistory(lastPage.length === 30);
        }
      } catch (_) {
        page.current = Math.max(-1, page.current - 1); setMoreHistory(true);
      }
    } finally { endBusy(); }
  };

  const submit = async (value = input, source: "TEXT" | "VOICE" = "TEXT", action?: ActionCommand) => {
    if (locked.current || !value.trim()) return;
    const command = value.trim().toLowerCase().replace(/[.!?]+$/, "");
    if (command === "discard unsent message") { pending.current = null; setOutgoing(null); setError(""); setInput(""); tell("Removed from this device. If the message reached Nexa, it may still appear in your history."); return; }
    if (pending.current && command !== "retry") { tell("Your last message needs attention. Choose Try again or Remove message below before sending another."); return; }
    if (command === "new conversation") {
      if (!discardDraft()) return;
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
    if (command === "cancel" && pendingDelete.current) { pendingDelete.current = null; setInput(""); tell("Cancelled. Nothing has changed."); return; }
    if (command === "confirm delete") {
      if (!current || pendingDelete.current !== current.id) { tell("There is no deletion waiting for confirmation."); return; }
      try {
        await removeConversation(current);
      } catch (_) { setError("This conversation couldn’t be deleted. Please try again."); }
      return;
    }
    pendingDelete.current = null;
    if (command === "retry" && !pending.current) { tell("There is no message waiting to be retried."); return; }
    if (value.length > 2000) { tell("Please keep each message under 2,000 characters."); setInput(""); return; }
    if (value !== input && input.trim() && !pending.current && !discardDraft()) return;
    followLatest.current = true; setHasNew(false);
    const queued = pending.current || { conversationId: current?.id || null, request: { clientId: crypto.randomUUID(), source, text: value.trim(), ...(action ? {action} : {}) } };
    pending.current = queued;

    setOutgoing({ text: queued.request.source === "VOICE" ? "Voice message" : queued.request.text, source: queued.request.source, createdAt: new Date().toISOString() });
    setInput(""); setNotice(""); setGuidance(null); setVoiceError(""); setSending(true); startBusy();
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
      animatedTurn.current = result.id;
      setTurns((items) => items.some((item) => item.id === result.id) ? items : [...items, result]);
      setNotice(""); if (result.workflow?.status === "COMPLETED" || result.banking?.type === "ACCOUNTS") onRefreshAccounts(); speak(spokenResponse(result)); if (action?.type === "SELECT") textarea.current?.focus();
      // Refresh titles without loading message bodies for every conversation.
      // A history refresh failure must not turn a successful send into a failed one.
      try {
        const fresh = await listConversations(token);
        if (alive.current) { setConversations(fresh); page.current = 0; setMoreHistory(fresh.length === 30); }
      } catch (_) { /* Existing history remains usable; the exchange is saved. */ }
    } catch (e) {
      setInput("");
      if (e instanceof ApiRequestError && [400, 404, 409].includes(e.status)) {
        pending.current = null; setOutgoing(null); setError(t("This request could not be completed. Review the proposal or cancel it to start again."));
        return;
      }
      setError(e instanceof ApiRequestError && e.status === 401
        ? "Your session has expired. Sign in again to continue."
        : "We could not check whether your message arrived. Choose Try again. We will check the same message without adding it twice.");
    } finally { setSending(false); endBusy(); }
  };

  const sendVoice = () => {
    const transcript = voice.transcript;
    voice.cancel();
    void submit(transcript, "VOICE");
  };
  const showGuidance = (kind: "help") => {
    setGuidance(kind); setNotice(""); followLatest.current = true; latest();
  };
  const runCommand = (command: string) => { if (command !== "new conversation" && !discardDraft()) return; setMenuOpen(false); void submit(command); };
  const dayFor = (date: string) => new Date(date).toLocaleDateString(language, { day: "numeric", month: "long", year: "numeric" });
  const closeHistory = () => { setHistoryOpen(false); historyToggle.current?.focus(); };

  const historyContents = <>
      <SidebarBrand action={!wideLayout && <button type="button" class="messenger-icon" aria-label={t("Close history")} onClick={closeHistory}><ChatIcon name="close"/></button>}/>
      <Action className="experience-new-chat" primary disabled={busy || listening || !!pending.current} onAction={() => void submit("new conversation")} label={t("New conversation")}/>
      <oj-input-text class="experience-history-search" labelHint={t("Search conversations")} labelEdge="inside" value={historySearch} onrawValueChanged={event => setHistorySearch(event.detail.value || "")}/>
      <header class="sidebar-history-heading"><h2 id="messenger-history-title">{t("Recent conversations")}</h2></header>
      <div class="messenger-history-list" role="region" aria-labelledby="messenger-history-title">{conversations.length ? conversations.filter(item => item.title.toLocaleLowerCase().includes(historySearch.toLocaleLowerCase())).map(item => <ConversationHistoryItem key={item.id} conversation={item} current={item.id === current?.id} hasDraft={!!input.trim()} disabled={busy || listening || !!pending.current} onSelect={() => void select(item)} onDelete={() => removeConversation(item)}/>) : <p>{t("Your conversations will appear here.")}</p>}
        {moreHistory && <button type="button" disabled={busy || listening} onClick={more}>{t("Load more conversations")}</button>}
      </div>
      {historySearch && !conversations.some(item => item.title.toLocaleLowerCase().includes(historySearch.toLocaleLowerCase())) && <p role="status">{t("No matching conversations.")}</p>}
      <details class="experience-banking-menu"><summary>{t("More banking")}</summary><SidebarNavigation page="assistant" admin={session.user.role === "ADMIN"}/></details>
      <SidebarFooter page="assistant" onLogout={() => runCommand("sign out")} disabled={busy || listening || !!pending.current}>
        <button class="sidebar-link" type="button" onClick={() => { setHistoryOpen(false); showGuidance("help"); }}><BankingIcon name="support"/><span>{t("Support")}</span></button>
      </SidebarFooter>
  </>;
  const context = <AccountContext accounts={accounts} loading={accountsLoading} error={accountsError} hidden={balancesHidden} onToggle={() => setBalancesHidden(value => !value)} onRetry={onRefreshAccounts}/>;

  return <div class="nexa-messenger experience-chat" lang={language}>
    <div class="experience-rail-container" inert={historyModal}><WorkspaceRail page="assistant" name={session.profile.fullName}/></div>
    <a class="conversation-skip" href="#nexa-conversation-input" onClick={event => { event.preventDefault(); textarea.current?.focus(); }}>{t("Skip to conversation")}</a>
    {wideLayout && <aside ref={historyPanel} id="messenger-history" class="nexa-sidebar messenger-history messenger-history-sidebar" aria-label={t("Banking navigation and history")}>{historyContents}</aside>}
    <main class="messenger-main" aria-label={t("Nexa banking conversation")} inert={historyModal}>
      <header class="messenger-header">
        <button type="button" class="messenger-icon" onClick={onClose} aria-label={t("Open banking overview")}><ChatIcon name="back" /><span>{t("Banking")}</span></button>
        <div class="messenger-identity"><h1>{t("Your conversation")}</h1></div>
        <button type="button" class="messenger-icon conversation-context-toggle" aria-label={t("Account context")} aria-expanded={contextOpen} onClick={() => setContextOpen(true)}><BankingIcon name="accounts"/></button>
        <button ref={historyToggle} type="button" class="messenger-icon" onClick={() => { setMenuOpen(false); setHistoryOpen(true); }} aria-label={t("Conversation history")} aria-expanded={wideLayout || historyOpen} aria-controls="messenger-history"><ChatIcon name="history" /><span>{t("History")}</span></button>
        <div class="messenger-menu-wrap">
          <button ref={menuToggle} type="button" class="messenger-icon" aria-label={t("Conversation options")} aria-expanded={menuOpen} aria-controls="messenger-options" onClick={() => setMenuOpen(!menuOpen)}><ChatIcon name="more" /><span>{t("Options")}</span></button>
          {menuOpen && <div ref={menu} id="messenger-options" class="messenger-options" aria-label={t("Conversation options")}>
            <button type="button" disabled={busy || listening || !!pending.current} onClick={() => runCommand("new conversation")}>{t("New conversation")}</button>
            <button type="button" disabled={busy || listening} onClick={() => runCommand(readAloud ? "stop reading" : "read aloud")}>{readAloud ? t("Turn off spoken replies") : t("Read replies aloud")}</button>
            <button type="button" disabled={busy || listening} onClick={() => runCommand("repeat")}>{t("Read last reply")}</button>
            <button type="button" disabled={!current || busy || listening || !!pending.current} onClick={() => runCommand("delete conversation")}>{t("Delete conversation")}</button>
            <button type="button" disabled={busy || listening} onClick={() => { setMenuOpen(false); void onLogout(); }}>{t("Sign out")}</button>
          </div>}
        </div>
      </header>
      <div class="messenger-feed-wrap"><ConnectionNotice/>
        <section ref={log} class="messenger-feed" aria-label={t("Messages")} tabIndex={0} onScroll={() => {
          const element = log.current;
          if (!element) return;
          followLatest.current = element.scrollHeight - element.scrollTop - element.clientHeight < 90;
          if (followLatest.current) setHasNew(false);
        }}>
          <div class="messenger-timeline">
            {older && <div class="messenger-load"><button type="button" disabled={busy || listening} onClick={earlier}>{t("Load earlier messages")}</button></div>}
            {!turns.length && !outgoing && <>
              <div class="conversation-welcome"><span class="conversation-welcome-mark" aria-hidden="true"><img src="styles/images/nexa.svg" width="40" height="40" alt=""/></span><h2>{t("How can I help?")}</h2>
                <div class="conversation-starters">
                  <QuickAction icon="accounts" title={t("Check Balance")} description={t("See what’s available across your accounts")} disabled={busy || listening} onClick={() => void submit(hindi ? "मेरा बैलेंस कितना है?" : "What’s my balance?")}/>
                  <QuickAction icon="payments" title={t("Transfer between accounts")} description={t("Review a transfer before sending")} disabled={busy || listening} onClick={() => void submit(hindi ? "मेरे खातों के बीच पैसे भेजें" : "Transfer between my accounts")}/>
                  <QuickAction icon="transactions" title={t("Recent Transactions")} description={t("A closer look at money in and out")} disabled={busy || listening} onClick={() => void submit(hindi ? "पिछले 10 ट्रांज़ैक्शन दिखाओ।" : "Show my latest transactions")}/>
                  <QuickAction icon="insights" title={t("Spending by category")} description={t("Explore your spending by category")} disabled={busy || listening} onClick={() => void submit(hindi ? "इस महीने मैंने कितना खर्च किया?" : "Show my spending this month")}/>
                </div>
              </div>

            </>}
            <div role="log" aria-label={t("Conversation messages")} aria-live="polite" aria-relevant="additions text">
              {turns.map((turn, index) => <div class="messenger-exchange" key={turn.id}>
                {(index === 0 || dayFor(turn.createdAt) !== dayFor(turns[index - 1].createdAt)) && <div class="messenger-date">{dayFor(turn.createdAt)}</div>}
                <MessageBubble role="user" text={turn.userText} timestamp={turn.createdAt} voice={turn.source === "VOICE"} status="Saved" />
                <div class={animatedTurn.current === turn.id ? "messenger-arrival" : undefined}><AssistantResponse turn={turn} accessToken={token} busy={busy} active={!!turn.workflow && !turns.slice(index + 1).some(t => !!t.workflow)} onAction={action => void submit(action.type === "CONFIRM" ? workflowConfirmationLabel(turn.workflow!.operation) : action.type === "CANCEL" ? "Cancel" : turn.workflow?.choices.find(c => c.id === action.value)?.label || "Choose account", "TEXT", action)} /></div>
              </div>)}
              {!sending && turns.length > 0 && !turns[turns.length - 1].workflow && <div class="messenger-suggestions" role="group" aria-label={t("Follow-up suggestions")}>
                {(turns[turns.length - 1].banking?.type === "ACCOUNTS" ? ["Show my latest transactions", "Move money between my accounts"] : ["What’s my balance?", "Show transactions above ₹5,000"]).map(suggestion => <button key={suggestion} type="button" disabled={busy || listening || !!pending.current} onClick={() => void submit(t(suggestion))}>{t(suggestion)} <span aria-hidden="true">↗</span></button>)}
              </div>}
              {outgoing && <MessageBubble animate role="user" text={outgoing.text} timestamp={outgoing.createdAt} status={sending ? t("Sending…") : t("Not confirmed")} />}
              {sending && <MessageBubble animate role="assistant"><span class="messenger-thinking" role="status"><i /><i /><i /><span>{t("Finding your answer…")}</span></span></MessageBubble>}
            </div>
            {busy && !sending && <p class="messenger-loading" role="status">{t("Loading conversation…")}</p>}
            {notice && notice !== greeting && <MessageBubble key={notice} animate role="assistant" text={t(notice)}>
              {pendingDelete.current && <div class="messenger-inline-actions"><button type="button" disabled={busy} onClick={() => void submit("cancel")}>{t("Keep conversation")}</button><button type="button" disabled={busy} onClick={() => void submit("confirm delete")}>{t("Confirm delete")}</button></div>}
            </MessageBubble>}
            {guidance && <MessageBubble role="assistant">
              <div role="status" lang={hindi ? "hi" : "en"}>
                <p>{hindi ? "बैंक से सहायता के लिए अपने कार्ड पर दिए नंबर पर कॉल करें या शाखा जाएँ।" : "For bank support, call the number on your card or visit a branch."}</p>
              </div>
              <div class="messenger-inline-actions"><button type="button" disabled={busy || listening || !!pending.current} onClick={() => runCommand(readAloud ? "stop reading" : "read aloud")}>{readAloud ? t("Stop reading replies") : t("Read replies aloud")}</button><button type="button" onClick={() => setGuidance(null)}>{hindi ? "ठीक है" : "Got it"}</button></div>
            </MessageBubble>}
            {error && <div class="messenger-error" role="alert"><p>{t(error)}</p>
              {pending.current ? <div class="messenger-inline-actions"><button type="button" disabled={busy || listening} onClick={() => void submit("retry")}>{t("Try again")}</button><button type="button" disabled={busy || listening} onClick={() => void submit("discard unsent message")}>{t("Remove message")}</button></div>
                : <button type="button" disabled={busy || listening} onClick={() => window.location.reload()}>{t("Try loading again")}</button>}
            </div>}
          </div>
        </section>
        {hasNew && <button type="button" class="messenger-new" onClick={() => latest()}><ChatIcon name="down" />{t("New messages")}</button>}
      </div>
      <footer class="messenger-bottom">
        <div class="messenger-quick-actions" role="group" aria-label={t("Banking quick actions")} lang={hindi ? "hi" : "en"}>
          <button type="button" disabled={busy || listening || !!pending.current} onClick={() => void submit(hindi ? "मेरा बैलेंस कितना है?" : "show my balance")}>{hindi ? "बैलेंस देखें" : t("Check Balance")}</button>
          <button type="button" disabled={busy || listening || !!pending.current} onClick={() => { window.location.hash = "/send-money"; }}>{hindi ? "पैसे भेजें" : t("Send Money")}</button>
          <button type="button" disabled={busy || listening || !!pending.current} onClick={() => void submit(hindi ? "पिछले 10 ट्रांज़ैक्शन दिखाओ।" : "show recent transactions")}>{hindi ? "लेन-देन देखें" : t("Recent Transactions")}</button>
          <button type="button" disabled={listening} onClick={() => showGuidance("help")}>{hindi ? "मदद लें" : t("Get Help")}</button>
        </div>
        {voiceError && <div class="messenger-voice-error" role="alert"><p>{t(voiceError)}</p><button type="button" onClick={() => { setVoiceError(""); textarea.current?.focus(); }}>{t("Type instead")}</button></div>}
        <form class="messenger-composer" onSubmit={(event) => { event.preventDefault(); void submit(); }}>
          {listening ? <div class="messenger-voice-session">
            <div class="messenger-voice-state" role="status"><span class={voice.phase === "listening" ? "recording-dot" : ""} /><strong>{voice.phase === "review" ? (voice.simulated ? t("Message ready") : t("Voice message ready")) : voice.phase === "stopping" ? t("Finishing…") : t("Listening")}</strong><time>{Math.floor(voice.seconds / 60)}:{String(voice.seconds % 60).padStart(2, "0")}</time></div>
            {voice.phase === "review" && <label class="messenger-voice-preview">{t("Your voice message")}<textarea aria-label={t("Your voice message")} lang={hindi ? "hi" : "en"} value={voice.transcript} maxLength={2000} onInput={event => voice.editTranscript(event.currentTarget.value)} dir="auto" /></label>}
            <div class="messenger-voice-actions"><button type="button" onClick={voice.cancel}>{t("Cancel")}</button>{voice.phase === "review" ? <button type="button" class="messenger-primary" disabled={!voice.transcript.trim()} onClick={sendVoice} aria-label={t("Send voice message")}><ChatIcon name="send" />{t("Send")}</button> : <button type="button" disabled={voice.phase === "stopping"} onClick={voice.stop} aria-label={t("Stop listening")}><ChatIcon name="stop" />{t("Stop")}</button>}</div>
          </div> : <div class="messenger-input-row">
            <label class="messenger-input-label" for="nexa-conversation-input">{hindi ? "अपना सवाल लिखें" : t("Write your question")}</label>
            <textarea ref={textarea} id="nexa-conversation-input" rows={1} maxLength={2000} value={input} readOnly={busy || !!pending.current} dir="auto" enterkeyhint="send" onInput={(event) => setInput(event.currentTarget.value)} onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey && !event.isComposing && window.matchMedia("(pointer: fine)").matches) { event.preventDefault(); void submit(); }
            }} placeholder={hindi ? "यहाँ लिखें…" : t("Ask Nexa…")} />
            <button type="button" class="messenger-icon messenger-mic" disabled={busy || !!pending.current} onClick={() => { setVoiceError(""); voice.start(language); }} aria-label={hindi ? "हिन्दी में बोलें" : t("Speak your message")}><ChatIcon name="mic" /><span lang={hindi ? "hi" : "en"}>{hindi ? "बोलें" : t("Speak")}</span></button>
            <button type="submit" class={`messenger-icon messenger-send ${input.trim() ? "messenger-primary" : ""}`} disabled={busy || !!pending.current || !input.trim()} aria-label={t("Send message")}><ChatIcon name="send" /><span>{hindi ? "भेजें" : t("Send")}</span></button>
          </div>}
        </form>
        <div class="messenger-composer-meta"><label>{t("Language")} <select aria-label={t("Voice language / बोलने की भाषा")} value={language} disabled={listening} onChange={(event) => { const next = event.currentTarget.value as "en-IN" | "hi-IN"; setLocale(next); setLanguage(next); }}><option value="en-IN">English</option><option value="hi-IN" lang="hi">हिन्दी (Hindi)</option></select></label><button type="button" disabled={busy || listening || !!pending.current} onClick={() => { setVoiceError(""); voice.startDemo(language); }}>{t("Use suggested message")}</button><details><summary>{t("About voice")}</summary><p>{t("Speech recognition may use your browser’s speech service.")}</p></details></div>
      </footer>
    </main>
    {contextOpen && <Modal title={t("Your accounts")} onClose={() => setContextOpen(false)}>{context}</Modal>}
    {!wideLayout && <div hidden={!historyModal} inert={!historyModal} class="messenger-drawer-backdrop" onClick={closeHistory}><aside ref={historyPanel} id="messenger-history" class="nexa-sidebar messenger-history" role="dialog" aria-modal="true" aria-label={t("Banking navigation and history")} onClick={(event) => event.stopPropagation()}>{historyContents}</aside></div>}
  </div>;
}
