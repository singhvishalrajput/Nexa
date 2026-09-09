import { h } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { FinancialDashboard } from "./FinancialDashboard";
import { CardControls as BaseCardControls, MoneyTransfer, SubscriptionManager, TransactionAnalysis, TransferDraft } from "./FinancialExperiences";
import { GoalDraft, GoalsWorkspace } from "./GoalsWorkspace";
import { BillDraft, BillsWorkspace } from "./BillsWorkspace";
import { BeneficiaryManager, ConnectedAccounts } from "./AccountBankingWorkspaces";
import { BudgetManager, ScheduledTransferDraft, ScheduledTransfers } from "./PlanningWorkspaces";
import { CashFlowForecast, FinancialNotifications, FraudDisputeCentre, StatementsReports } from "./InsightsSecurityWorkspaces";
import { AuthSession } from "../../services/auth";
import { ApiRequestError } from "../../services/auth";
import { BankAccount, BankTransaction, getAccounts, getTransactions, openAccount } from "../../services/banking";
import { AccountOpeningCard } from "./AccountOpeningCard";
import { AccountSettings } from "./AccountSettings";

type ChatWorkspaceProps = {
  session: AuthSession;
  onClose: () => void;
  onLogout: () => void | Promise<void>;
};
type Attachment = { id: string; name: string; kind: "image" | "document"; size: number };
type MessageAction = "open-account" | "open-dashboard" | "open-transactions" | "open-transfer" | "open-scheduled-transfers" | "open-subscriptions" | "open-cards" | "open-goals" | "open-budgets" | "open-bills" | "open-accounts" | "open-beneficiaries" | "open-notifications" | "open-disputes" | "open-reports" | "open-forecast";
type Message = { role: "user" | "assistant"; text: string; action?: MessageAction; attachments?: Attachment[]; transfer?: TransferDraft; scheduledTransfer?: ScheduledTransferDraft; goal?: GoalDraft; bill?: BillDraft; insight?: "last-month-transactions" };
type ChatThread = { id: string; title: string; messages: Message[]; updatedAt: number };
type Profile = { name: string; email: string; phone: string };

const greetingFor = (name: string): Message => ({
  role: "assistant",
  text: `Good evening, ${name.split(" ")[0] || "there"}. What would you like to do with your money today?`
});
const suggestions = ["What can I safely spend this week?", "Show subscriptions I rarely use", "Move ₹5,000 to savings"];
const isDashboardRequest = (value: string) => /\b(dashboard|financial\s+(overview|summary)|money\s+(overview|summary))\b/i.test(value);
const isBalanceRequest = (value: string) => /\b(balance|available\s+money|how\s+much\s+(?:money|funds))\b/i.test(value);
const isTransactionSummaryRequest = (value: string) => /\b(summarize|summary|analyse|analyze)\b.*\b(last|previous)\s+month\b.*\btransactions?\b|\b(last|previous)\s+month\b.*\btransactions?\b.*\b(summarize|summary|analyse|analyze)\b/i.test(value);
const isTransactionPageRequest = (value: string) => /\b(show|open|view|see)\b.*\btransactions?\b/i.test(value);
const isSubscriptionRequest = (value: string) => /\b(show|open|view|manage|review|find|cancel)\b.*\bsubscriptions?\b|\bsubscriptions?\b.*\b(manager|manage|rarely|unused|active)\b/i.test(value);
const isCardRequest = (value: string) => /\b(show|open|view|manage|add|connect|link|modify|change|update|set|enable|disable|freeze|unfreeze|lock|unlock|control|block|report|lost|stolen)\b.*\b(cards?|debit\s+cards?|credit\s+cards?)\b|\b(cards?|debit\s+cards?|credit\s+cards?)\b.*\b(show|open|view|manage|add|connect|link|modify|change|update|set|enable|disable|freeze|unfreeze|lock|unlock|control|block|report|lost|stolen|limits?|settings?|international|online|contactless)\b|\b(monthly|daily|atm|international|online|contactless)\s+(card\s+)?(limits?|usage|payments?)\b/i.test(value);
const isConnectedAccountsRequest = (value: string) => /\b(show|open|view|manage|add|connect|link|remove|disconnect)\b.*\b(connected\s+accounts?|bank\s+accounts?|accounts?\s+hub)\b|\b(connected\s+accounts?|bank\s+accounts?|accounts?\s+hub)\b.*\b(show|open|view|manage|add|connect|link|remove|disconnect)\b/i.test(value);
const isBeneficiaryRequest = (value: string) => /\b(show|open|view|manage|add|verify|edit|update|remove|delete)\b.*\b(beneficiar(?:y|ies)|recipients?|payees?)\b|\b(beneficiar(?:y|ies)|recipients?|payees?)\b.*\b(show|open|view|manage|add|verify|edit|update|remove|delete)\b/i.test(value);
const isBudgetRequest = (value: string) => /\b(budget\s+manager|monthly\s+budgets?|category\s+(?:limits?|budgets?)|spending\s+limits?)\b|\b(show|open|view|manage|create|adjust|update|set|change|review)\b.*\bbudgets?\b|\bbudgets?\b.*\b(show|open|view|manage|create|adjust|update|set|change|review|limits?|categories)\b/i.test(value);
const isNotificationRequest = (value: string) => /\b(show|open|view|manage|check|review)\b.*\b(financial\s+)?(notifications?|alerts?)\b|\b(financial\s+)?(notifications?|alerts?)\b.*\b(show|open|view|manage|check|review|settings?)\b/i.test(value);
const isDisputeRequest = (value: string) => /\b(fraud|disputes?|suspicious\s+(?:activity|transactions?|payments?)|unrecognized\s+(?:transactions?|payments?)|chargeback)\b/i.test(value);
const isReportRequest = (value: string) => /\b(statements?|financial\s+reports?|spending\s+reports?|account\s+reports?|download\s+(?:my\s+)?transactions?)\b/i.test(value);
const isForecastRequest = (value: string) => /\b(cash[-\s]?flow\s+(?:forecast|forecasting|projection|outlook)|forecast\s+(?:my\s+)?balance|future\s+balance|projected\s+balance|balance\s+forecast)\b/i.test(value);
const parseGoalRequest = (value: string): GoalDraft | null => {
  if (!/\b(goals?|saving|savings|emergency\s+fund)\b/i.test(value)) return null;
  const lakhMatch = value.match(/(?:₹|rs\.?|inr)?\s*([\d.]+)\s*(?:lakh|lac|l)\b/i);
  const amountMatch = value.match(/(?:₹|rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)/i);
  const target = lakhMatch ? Number(lakhMatch[1]) * 100000 : amountMatch ? Number(amountMatch[1].replace(/,/g, "")) : 0;
  const name = /emergency/i.test(value) ? "Emergency fund" : /travel|trip|vacation|holiday/i.test(value) ? "Travel fund" : /car|vehicle/i.test(value) ? "New vehicle" : /home|house/i.test(value) ? "Home fund" : "Savings goal";
  return { name, target };
};
const parseBillRequest = (value: string): BillDraft | null => {
  if (!/\b(bills?|electricity|bescom|water|gas|mobile|airtel|broadband|internet|rent|emi|insurance|autopay|credit\s+card\s+(?:payment|bill))\b/i.test(value)) return null;
  const billId = /electricity|bescom/i.test(value) ? "electricity" : /mobile|airtel/i.test(value) ? "mobile" : /broadband|internet|act\s+fibernet/i.test(value) ? "broadband" : /rent/i.test(value) ? "rent" : /credit\s+card/i.test(value) ? "credit-card" : "";
  const intent: BillDraft["intent"] = /\b(add|connect|track|create)\b/i.test(value) ? "add" : /autopay/i.test(value) ? "autopay" : /schedule/i.test(value) ? "schedule" : /why|higher|increase|analy[sz]e|explain/i.test(value) ? "analyze" : /pay|payment/i.test(value) ? "pay" : "overview";
  return { billId, intent };
};
const parseTransferRequest = (value: string): TransferDraft | null => {
  if (!/\b(send|sent|transfer|pay)\b/i.test(value)) return null;
  const amountMatch = value.match(/(?:₹|rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)/i) || value.match(/([\d,]+(?:\.\d{1,2})?)\s*(?:rupees?|rs\.?|inr)\b/i);
  const recipientMatch = value.match(/\bto\s+([a-z][a-z\s.'-]{1,40}?)(?=\s+(?:from|using|for|with|via|on)\b|[,.]|$)/i);
  return { amount: amountMatch ? Number((amountMatch[1] || "0").replace(/,/g, "")) : 0, recipient: recipientMatch?.[1]?.trim() || "" };
};
const parseScheduledTransferRequest = (value: string): ScheduledTransferDraft | null => {
  const hasScheduleIntent = /\b(schedule(?:d)?|recurring|repeat(?:ing)?|future[-\s]?dated|every\s+(?:week|month)|weekly|monthly)\b/i.test(value);
  const hasTransferContext = /\b(transfer|send|pay|payment)\b/i.test(value) || /(?:₹|rs\.?|inr)\s*[\d,]+(?:\.\d{1,2})?\s+to\b/i.test(value);
  if (!hasScheduleIntent || !hasTransferContext) return null;
  const amountMatch = value.match(/(?:₹|rs\.?|inr)\s*([\d,]+(?:\.\d{1,2})?)/i) || value.match(/([\d,]+(?:\.\d{1,2})?)\s*(?:rupees?|rs\.?|inr)\b/i);
  const recipientMatch = value.match(/\bto\s+([a-z][a-z\s.'-]{1,40}?)(?=\s+(?:from|using|for|with|via|on|every|weekly|monthly|once)\b|[,.]|$)/i);
  return { recipient: recipientMatch?.[1]?.trim() || "", amount: amountMatch ? Number(amountMatch[1].replace(/,/g, "")) : 0, cadence: /\bweekly|every\s+week\b/i.test(value) ? "weekly" : /\bonce|one[-\s]?time|future[-\s]?dated\b/i.test(value) ? "once" : "monthly" };
};
const isExperienceRequest = (value: string) => isDashboardRequest(value) || isTransactionSummaryRequest(value) || isTransactionPageRequest(value) || isSubscriptionRequest(value) || isCardRequest(value) || isConnectedAccountsRequest(value) || isBeneficiaryRequest(value) || isBudgetRequest(value) || isNotificationRequest(value) || isDisputeRequest(value) || isReportRequest(value) || isForecastRequest(value) || !!parseGoalRequest(value) || !!parseBillRequest(value) || !!parseScheduledTransferRequest(value) || !!parseTransferRequest(value);
const formatFileSize = (bytes: number) => bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
const actionMeta: Record<MessageAction, { eyebrow: string; title: string }> = {
  "open-account": { eyebrow: "Nexa banking", title: "Open a Nexa account" },
  "open-dashboard": { eyebrow: "Financial overview", title: "View dashboard" },
  "open-transactions": { eyebrow: "Spending intelligence", title: "View transactions" },
  "open-transfer": { eyebrow: "Secure payment", title: "Review transfer" },
  "open-scheduled-transfers": { eyebrow: "Transfer schedule", title: "Manage transfers" },
  "open-subscriptions": { eyebrow: "Recurring payments", title: "Manage subscriptions" },
  "open-cards": { eyebrow: "Card security", title: "Manage card" },
  "open-goals": { eyebrow: "Savings plan", title: "View goals" },
  "open-budgets": { eyebrow: "Monthly planning", title: "Manage budgets" },
  "open-bills": { eyebrow: "Bills and payments", title: "View bills" },
  "open-accounts": { eyebrow: "Connected banking", title: "Manage accounts" },
  "open-beneficiaries": { eyebrow: "Transfer recipients", title: "Manage beneficiaries" },
  "open-notifications": { eyebrow: "Financial signals", title: "View notifications" },
  "open-disputes": { eyebrow: "Account protection", title: "Review suspicious activity" },
  "open-reports": { eyebrow: "Financial records", title: "View statements" },
  "open-forecast": { eyebrow: "Balance outlook", title: "View forecast" }
};

function ChatActionIcon({ action }: { action: MessageAction }) {
  return <svg viewBox="0 0 24 24" aria-hidden="true">{action === "open-notifications" ? <><path d="M6 9a6 6 0 0 1 12 0v5l2 3H4l2-3Z"/><path d="M9.5 20h5"/></> : action === "open-disputes" ? <><path d="M12 3 4.5 6v5c0 5 3 8 7.5 10 4.5-2 7.5-5 7.5-10V6Z"/><path d="M12 8v5M12 16h.01"/></> : action === "open-reports" ? <><path d="M6 3h9l3 3v15H6Z"/><path d="M15 3v4h4M9 11h6M9 15h6"/></> : action === "open-forecast" ? <><path d="M4 18 9 12l4 3 7-9"/><path d="M15 6h5v5"/></> : action === "open-dashboard" ? <><rect x="4" y="4" width="6" height="6"/><rect x="14" y="4" width="6" height="6"/><rect x="4" y="14" width="6" height="6"/><rect x="14" y="14" width="6" height="6"/></> : action === "open-transactions" ? <><path d="M5 7h14M5 12h9M5 17h11"/><path d="m16 14 3 3-3 3"/></> : action === "open-transfer" ? <><path d="M5 12h14"/><path d="m15 8 4 4-4 4"/><circle cx="7" cy="12" r="3"/></> : action === "open-scheduled-transfers" ? <><circle cx="8" cy="12" r="4"/><path d="M8 10v2l1.5 1M13 7h7M13 12h7M13 17h5"/></> : action === "open-subscriptions" ? <><path d="M6.5 8A7 7 0 0 1 19 10"/><path d="m19 6v4h-4"/><path d="M17.5 16A7 7 0 0 1 5 14"/><path d="m5 18v-4h4"/></> : action === "open-goals" ? <><circle cx="12" cy="12" r="8"/><circle cx="12" cy="12" r="3"/><path d="M12 4V2M20 12h2"/></> : action === "open-budgets" ? <><path d="M4 18V9M10 18V5M16 18v-7M22 18H2"/><path d="M3 5h4M15 7h4"/></> : action === "open-bills" ? <><path d="M6 3h12v18l-3-2-3 2-3-2-3 2Z"/><path d="M9 8h6M9 12h6M9 16h4"/></> : action === "open-accounts" ? <><path d="M3 9h18L12 4 3 9Z"/><path d="M5 9v9M9.5 9v9M14.5 9v9M19 9v9M3 20h18"/></> : action === "open-beneficiaries" ? <><circle cx="9" cy="8" r="3"/><path d="M3.5 19c.5-4 2.3-6 5.5-6s5 2 5.5 6M17 8v7M13.5 11.5h7"/></> : <><rect x="4" y="6" width="16" height="12" rx="2"/><path d="M4 10h16M8 15h3"/></>}</svg>;
}

function loadThreads(userId: string, name: string): ChatThread[] {
  try {
    const stored = window.localStorage.getItem(`nexa-chat-history:${userId}`);
    if (stored) return JSON.parse(stored);
  } catch (_) {}
  return [{ id: "welcome", title: "New conversation", messages: [greetingFor(name)], updatedAt: Date.now() }];
}

function loadProfile(session: AuthSession): Profile {
  return { name: session.profile.fullName, email: session.profile.email, phone: session.profile.phoneNumber || "" };
}

export function ChatWorkspace({ session, onClose, onLogout }: ChatWorkspaceProps) {
  const greeting = greetingFor(session.profile.fullName);
  const [threads, setThreads] = useState<ChatThread[]>(() => loadThreads(session.user.id, session.profile.fullName));
  const [currentId, setCurrentId] = useState(() => loadThreads(session.user.id, session.profile.fullName)[0]?.id || "welcome");
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [mainView, setMainView] = useState<"chat" | "account" | "dashboard" | "transactions" | "transfer" | "scheduled-transfers" | "subscriptions" | "cards" | "goals" | "budgets" | "bills" | "connected-accounts" | "beneficiaries" | "notifications" | "disputes" | "reports" | "forecast">("chat");
  const [activeTransfer, setActiveTransfer] = useState<TransferDraft>({ recipient: "", amount: 0 });
  const [activeScheduledTransfer, setActiveScheduledTransfer] = useState<ScheduledTransferDraft>({});
  const [activeGoalDraft, setActiveGoalDraft] = useState<GoalDraft>({ name: "", target: 0 });
  const [activeBillDraft, setActiveBillDraft] = useState<BillDraft>({ billId: "", intent: "overview" });
  const [profile, setProfile] = useState<Profile>(() => loadProfile(session));
  const [accounts, setAccounts] = useState<BankAccount[]>([]);
  const [bankTransactions, setBankTransactions] = useState<BankTransaction[]>([]);
  const [accountLoading, setAccountLoading] = useState(true);
  const [accountError, setAccountError] = useState("");
  const [accountOpeningOpen, setAccountOpeningOpen] = useState(false);
  const [openingAccount, setOpeningAccount] = useState(false);
  const [openingError, setOpeningError] = useState("");
  const [input, setInput] = useState("");
  const [listening, setListening] = useState(false);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [uploadMenuOpen, setUploadMenuOpen] = useState(false);
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null);
  const recognitionRef = useRef<any>(null);
  const conversationRef = useRef<HTMLElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const uploadWrapRef = useRef<HTMLDivElement>(null);
  const photoInputRef = useRef<HTMLInputElement>(null);
  const documentInputRef = useRef<HTMLInputElement>(null);
  const deleteConfirmRef = useRef<HTMLButtonElement>(null);
  const accountOpeningRef = useRef<HTMLDivElement>(null);

  const currentThread = threads.find((thread) => thread.id === currentId) || threads[0];
  const messages = currentThread?.messages || [greeting];
  const primaryAccount = accounts[0];
  const profileInitials = profile.name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "N";
  const CardControls = ({ onBack: returnToChat, onAsk }: { onBack: () => void; onAsk: (prompt: string) => void }) => mainView === "notifications" ? <FinancialNotifications onBack={returnToChat} onAsk={onAsk} /> : mainView === "disputes" ? <FraudDisputeCentre onBack={returnToChat} onAsk={onAsk} /> : mainView === "reports" ? <StatementsReports onBack={returnToChat} onAsk={onAsk} /> : mainView === "forecast" ? <CashFlowForecast onBack={returnToChat} onAsk={onAsk} /> : <BaseCardControls onBack={returnToChat} onAsk={onAsk} />;

  useEffect(() => {
    window.localStorage.setItem(`nexa-chat-history:${session.user.id}`, JSON.stringify(threads));
  }, [session.user.id, threads]);

  useEffect(() => {
    let active = true;
    setAccountLoading(true);
    getAccounts(session.accessToken)
      .then(async (loadedAccounts) => {
        if (!active) return;
        setAccounts(loadedAccounts);
        if (loadedAccounts[0]) {
          const loadedTransactions = await getTransactions(session.accessToken, loadedAccounts[0].id);
          if (active) setBankTransactions(loadedTransactions);
        }
      })
      .catch((error) => active && setAccountError(error instanceof Error ? error.message : "Unable to load your Nexa accounts."))
      .finally(() => active && setAccountLoading(false));
    return () => { active = false; };
  }, [session.accessToken]);

  useEffect(() => {
    if (!pendingDeleteId) return;
    deleteConfirmRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setPendingDeleteId(null);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [pendingDeleteId]);

  useEffect(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = "auto";
    textarea.style.height = `${Math.min(Math.max(textarea.scrollHeight, 54), 140)}px`;
  }, [input]);

  useEffect(() => {
    if (mainView !== "chat") return;
    const conversation = conversationRef.current;
    if (!conversation) return;
    window.requestAnimationFrame(() => {
      conversation.scrollTo({ top: conversation.scrollHeight, behavior: messages.length > 1 ? "smooth" : "auto" });
    });
  }, [currentId, mainView, messages.length]);

  useEffect(() => {
    if (!accountOpeningOpen || mainView !== "chat") return;
    const frame = window.requestAnimationFrame(() => {
      accountOpeningRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [accountOpeningOpen, mainView]);

  useEffect(() => {
    if (!uploadMenuOpen) return;
    const closeUploadMenu = (event: KeyboardEvent) => {
      if (event.key === "Escape") setUploadMenuOpen(false);
    };
    const closeUploadMenuOnOutsideClick = (event: PointerEvent) => {
      if (!uploadWrapRef.current?.contains(event.target as Node)) setUploadMenuOpen(false);
    };
    window.addEventListener("keydown", closeUploadMenu);
    window.addEventListener("pointerdown", closeUploadMenuOnOutsideClick);
    return () => {
      window.removeEventListener("keydown", closeUploadMenu);
      window.removeEventListener("pointerdown", closeUploadMenuOnOutsideClick);
    };
  }, [uploadMenuOpen]);

  const selectThread = (id: string) => {
    setCurrentId(id);
    setMainView("chat");
    if (window.innerWidth < 900) setSidebarOpen(false);
  };

  const newConversation = () => {
    const existingEmpty = threads.find((thread) => thread.title === "New conversation" && thread.messages.length === 1);
    if (existingEmpty) {
      setCurrentId(existingEmpty.id);
      setMainView("chat");
      setInput("");
      setAttachments([]);
      setUploadMenuOpen(false);
      if (window.innerWidth < 900) setSidebarOpen(false);
      return;
    }
    const id = `chat-${Date.now()}`;
    const next: ChatThread = { id, title: "New conversation", messages: [greeting], updatedAt: Date.now() };
    setThreads((current) => [next, ...current]);
    setCurrentId(id);
    setMainView("chat");
    setInput("");
    setAttachments([]);
    setUploadMenuOpen(false);
    if (window.innerWidth < 900) setSidebarOpen(false);
  };

  const deleteConversation = () => {
    if (!pendingDeleteId) return;
    let remaining = threads.filter((thread) => thread.id !== pendingDeleteId);
    if (!remaining.length) {
      remaining = [{ id: `chat-${Date.now()}`, title: "New conversation", messages: [greeting], updatedAt: Date.now() }];
    }
    setThreads(remaining);
    if (currentId === pendingDeleteId) {
      setCurrentId(remaining[0].id);
      setMainView("chat");
    }
    setPendingDeleteId(null);
  };

  const createNexaAccount = async (request: { displayName: string; accountType: "SAVINGS" | "CURRENT" }) => {
    setOpeningAccount(true);
    setOpeningError("");
    try {
      const account = await openAccount(session.accessToken, request);
      const transactions = await getTransactions(session.accessToken, account.id);
      setAccounts([account]);
      setBankTransactions(transactions);
      setAccountOpeningOpen(false);
      setAccountError("");
      const reply: Message = {
        role: "assistant",
        text: `Your ${account.accountType.toLowerCase()} account ${account.accountNumberMasked} is active. A ₹${Number(account.availableBalance).toLocaleString("en-IN")} demo opening credit has been added and recorded in your transaction history.`
      };
      setThreads((current) => current.map((thread) => thread.id === currentId ? { ...thread, messages: [...thread.messages, reply], updatedAt: Date.now() } : thread));
    } catch (error) {
      setOpeningError(error instanceof ApiRequestError || error instanceof Error ? error.message : "The account could not be opened.");
    } finally {
      setOpeningAccount(false);
    }
  };

  const submit = (value?: string) => {
    const next = (value || input).trim();
    if ((!next && !attachments.length) || !currentThread) return;
    const dashboardRequested = isDashboardRequest(next);
    const transactionSummaryRequested = isTransactionSummaryRequest(next);
    const transactionPageRequested = !transactionSummaryRequested && isTransactionPageRequest(next);
    const subscriptionRequested = isSubscriptionRequest(next);
    const cardRequested = isCardRequest(next);
    const connectedAccountsRequested = isConnectedAccountsRequest(next);
    const beneficiaryRequested = isBeneficiaryRequest(next);
    const budgetRequested = isBudgetRequest(next);
    const notificationsRequested = isNotificationRequest(next);
    const disputeRequested = isDisputeRequest(next);
    const reportRequested = isReportRequest(next);
    const forecastRequested = isForecastRequest(next);
    const goalDraft = parseGoalRequest(next);
    const billDraft = parseBillRequest(next);
    const scheduledTransferDraft = parseScheduledTransferRequest(next);
    const transferDraft = parseTransferRequest(next);
    const submittedAttachments = attachments;
    const accountRequired = isBalanceRequest(next) || isExperienceRequest(next) || /\b(account|money|spend|payment|banking)\b/i.test(next);
    if (!accountLoading && !primaryAccount && accountRequired) {
      const userMessage: Message = { role: "user", text: next || "Please review the attached file.", attachments: submittedAttachments.length ? submittedAttachments : undefined };
      const reply: Message = { role: "assistant", text: "You don’t have a Nexa bank account yet. Open your first account to receive demo funds and start using balances, transactions and payments.", action: "open-account" };
      setThreads((current) => current.map((thread) => thread.id === currentId ? { ...thread, title: thread.title === "New conversation" ? (next || "Open a Nexa account").slice(0, 38) : thread.title, messages: [...thread.messages, userMessage, reply], updatedAt: Date.now() } : thread));
      setInput("");
      setAttachments([]);
      setUploadMenuOpen(false);
      return;
    }
    let reply: Message;
    if (dashboardRequested) reply = { role: "assistant", text: "Your financial overview is ready. It brings your balances, spending, goals, upcoming payments, and important insights into one clear view.", action: "open-dashboard" };
    else if (disputeRequested) reply = { role: "assistant", text: "I found three payments that need confirmation and one open dispute case. Review the activity carefully before recognizing or disputing anything.", action: "open-disputes" };
    else if (notificationsRequested) reply = { role: "assistant", text: "Your financial notifications are organized by urgency. Bills, unusual spending, balance warnings, transfer updates and goal progress are ready to review.", action: "open-notifications" };
    else if (reportRequested) reply = { role: "assistant", text: "Your statements and financial reports are ready. Choose the period and account, review the figures, then download the statement or transaction file.", action: "open-reports" };
    else if (forecastRequested) reply = { role: "assistant", text: "Your cash-flow forecast is ready. It combines expected income, bills, subscriptions, transfers and savings contributions to project your upcoming balance.", action: "open-forecast" };
    else if (isBalanceRequest(next) && primaryAccount) reply = { role: "assistant", text: `Your available balance is ₹${Number(primaryAccount.availableBalance).toLocaleString("en-IN")} in ${primaryAccount.displayName} ${primaryAccount.accountNumberMasked}.` };
    else if (transactionSummaryRequested) reply = { role: "assistant", text: `You have ${bankTransactions.length} posted transaction${bankTransactions.length === 1 ? "" : "s"}. Money out totals ₹${Math.abs(bankTransactions.filter((item) => item.amount < 0).reduce((sum, item) => sum + Number(item.amount), 0)).toLocaleString("en-IN")}, and money in totals ₹${bankTransactions.filter((item) => item.amount > 0).reduce((sum, item) => sum + Number(item.amount), 0).toLocaleString("en-IN")}.` };
    else if (transactionPageRequested) reply = { role: "assistant", text: "Your transaction analysis is ready. You can search activity, filter categories, and see where your money moved.", action: "open-transactions" };
    else if (connectedAccountsRequested) reply = { role: "assistant", text: `Your Nexa account ${primaryAccount?.accountNumberMasked || ""} is active with an available balance of ₹${Number(primaryAccount?.availableBalance || 0).toLocaleString("en-IN")}.`, action: "open-dashboard" };
    else if (beneficiaryRequested) reply = { role: "assistant", text: "Your verified beneficiaries are ready. Add a recipient, verify or edit their payment details, or remove someone you no longer pay.", action: "open-beneficiaries" };
    else if (budgetRequested) reply = { role: "assistant", text: "Your dedicated monthly budget is ready. Set category limits, track how much has been used, and adjust the plan as your month changes.", action: "open-budgets" };
    else if (cardRequested) reply = { role: "assistant", text: /\b(freeze|lock|lost|stolen|block)\b/i.test(next) ? "Your card workspace is ready for a security review. Select the correct card before freezing or reporting it." : /\b(add|connect|link)\b/i.test(next) ? "The secure card connection flow is ready. Add the card, verify its details, and it will appear with your existing cards." : /\b(limit|international|online|contactless|setting|modify|change|update|enable|disable|set)\b/i.test(next) ? "Your card settings are ready. Select the card, adjust its limits or payment access, and review everything before applying the change." : "Your card workspace is ready. Select any connected card to manage its security, settings, and recent activity.", action: "open-cards" };
    else if (billDraft) reply = { role: "assistant", text: billDraft.intent === "add" ? "The bill connection flow is ready. Add the provider, verify its account reference, configure reminders, and review everything before saving it." : billDraft.intent === "pay" ? "I found the bill and prepared its payment details. Review the provider, due amount, funding account and final balance before approving it." : billDraft.intent === "schedule" ? "The bill is ready to schedule. Choose a payment date on or before its due date, then review the instruction." : billDraft.intent === "autopay" ? "AutoPay settings are ready for review. Enabling it requires a separate authorization and a payment limit." : billDraft.intent === "analyze" ? "I found a meaningful increase in this bill. Open it to compare the previous amount and review the change before payment." : "Your upcoming bills are organized by due date, payment status and AutoPay protection.", action: "open-bills", bill: billDraft };
    else if (scheduledTransferDraft) reply = { role: "assistant", text: scheduledTransferDraft.recipient && scheduledTransferDraft.amount ? `I prepared a ${scheduledTransferDraft.cadence === "weekly" ? "weekly" : scheduledTransferDraft.cadence === "once" ? "future" : "monthly"} transfer of ₹${scheduledTransferDraft.amount.toLocaleString("en-IN")} to ${scheduledTransferDraft.recipient}. Choose the first payment date and approve the instruction.` : "Your transfer schedule is ready. Review upcoming payments, pause recurring instructions, or create a new future transfer.", action: "open-scheduled-transfers", scheduledTransfer: scheduledTransferDraft };
    else if (transferDraft && transferDraft.amount > 0 && transferDraft.recipient) reply = { role: "assistant", text: `I prepared a transfer of ₹${transferDraft.amount.toLocaleString("en-IN")} to ${transferDraft.recipient}. Review the account, recipient, and final balance before sending.`, action: "open-transfer", transfer: transferDraft };
    else if (transferDraft) reply = { role: "assistant", text: "I can prepare that transfer. Please include both the amount and recipient—for example, “Send ₹5,000 to Rahul.”" };
    else if (goalDraft) reply = { role: "assistant", text: goalDraft.target > 0 ? `I prepared “${goalDraft.name}” with a target of ₹${goalDraft.target.toLocaleString("en-IN")}. Choose the target date and contribution pace before creating it.` : "Your savings workspace is ready. You can create goals, compare contribution plans, and see what your monthly budget can safely support.", action: "open-goals", goal: goalDraft };
    else if (subscriptionRequested) reply = { role: "assistant", text: "I found five active subscriptions. Two show low usage and could save you ₹2,296 each month if you no longer need them.", action: "open-subscriptions" };
    else if (submittedAttachments.length) reply = { role: "assistant", text: `I’ve received ${submittedAttachments.length === 1 ? submittedAttachments[0].name : `${submittedAttachments.length} files`}. I can summarize the content, extract key information, or answer questions about it.` };
    else reply = { role: "assistant", text: "I’ve understood the request. Before anything changes, I’ll show you the account, amount, and expected outcome for review." };
    const userMessage: Message = { role: "user", text: next || "Please review the attached file.", attachments: submittedAttachments.length ? submittedAttachments : undefined };
    const nextTitle = next || `Shared ${submittedAttachments[0]?.name || "a file"}`;
    setThreads((current) => current.map((thread) => thread.id === currentId ? { ...thread, title: thread.title === "New conversation" ? nextTitle.slice(0, 38) : thread.title, messages: [...thread.messages, userMessage, reply], updatedAt: Date.now() } : thread));
    setInput("");
    setAttachments([]);
    setUploadMenuOpen(false);
  };

  const addAttachments = (event: Event, kind: Attachment["kind"]) => {
    const inputElement = event.currentTarget as HTMLInputElement;
    const selected = Array.from(inputElement.files || []).map((file, index) => ({ id: `${Date.now()}-${index}-${file.name}`, name: file.name, kind, size: file.size }));
    setAttachments((current) => [...current, ...selected].slice(0, 5));
    setUploadMenuOpen(false);
    inputElement.value = "";
  };

  const openMessageAction = (message: Message) => {
    if (message.action === "open-account") setAccountOpeningOpen(true);
    if (message.action === "open-dashboard") setMainView("dashboard");
    if (message.action === "open-transactions") setMainView("transactions");
    if (message.action === "open-subscriptions") setMainView("subscriptions");
    if (message.action === "open-budgets") setMainView("budgets");
    if (message.action === "open-cards") setMainView("cards");
    if (message.action === "open-accounts") setMainView("connected-accounts");
    if (message.action === "open-beneficiaries") setMainView("beneficiaries");
    if (message.action === "open-notifications") setMainView("notifications");
    if (message.action === "open-disputes") setMainView("disputes");
    if (message.action === "open-reports") setMainView("reports");
    if (message.action === "open-forecast") setMainView("forecast");
    if (message.action === "open-scheduled-transfers") {
      setActiveScheduledTransfer(message.scheduledTransfer || {});
      setMainView("scheduled-transfers");
    }
    if (message.action === "open-goals") {
      setActiveGoalDraft(message.goal || { name: "", target: 0 });
      setMainView("goals");
    }
    if (message.action === "open-bills") {
      setActiveBillDraft(message.bill || { billId: "", intent: "overview" });
      setMainView("bills");
    }
    if (message.action === "open-transfer") {
      setActiveTransfer(message.transfer || { recipient: "", amount: 0 });
      setMainView("transfer");
    }
  };

  const renderMessageAction = (message: Message) => {
    if (!message.action) return null;
    const meta = actionMeta[message.action];
    return <button class="nexa-chat-action" type="button" onClick={() => openMessageAction(message)}><i><ChatActionIcon action={message.action} /></i><span><small>{meta.eyebrow}</small><strong>{meta.title}</strong></span><b aria-hidden="true">↗</b></button>;
  };

  const toggleVoice = () => {
    if (listening) { recognitionRef.current?.stop(); setListening(false); return; }
    const browserWindow = window as any;
    const SpeechRecognition = browserWindow.SpeechRecognition || browserWindow.webkitSpeechRecognition;
    if (!SpeechRecognition) return;
    const recognition = new SpeechRecognition();
    recognition.lang = "en-IN";
    recognition.interimResults = true;
    let submittedExperienceRequest = false;
    recognition.onresult = (event: any) => {
      const transcript = Array.from(event.results).map((result: any) => result[0].transcript).join("");
      setInput(transcript);
      const latestResult = event.results[event.results.length - 1];
      if (!submittedExperienceRequest && latestResult?.isFinal && isExperienceRequest(transcript)) {
        submittedExperienceRequest = true;
        submit(transcript);
        recognition.stop();
      }
    };
    recognition.onend = () => setListening(false);
    recognition.onerror = () => setListening(false);
    recognitionRef.current = recognition;
    recognition.start();
    setListening(true);
  };

  return <div class={`nexa-workspace ${sidebarOpen ? "is-sidebar-open" : ""}`}>
    <button class="nexa-sidebar-scrim" type="button" aria-label="Close sidebar" onClick={() => setSidebarOpen(false)} />
    <aside class="nexa-workspace-sidebar" id="nexa-chat-sidebar" aria-label="Chat workspace sidebar">
      <button class="nexa-create-thread" type="button" onClick={newConversation}><span class="nexa-create-icon">＋</span><span>New conversation</span></button>
      <div class="nexa-thread-list"><p>Recent conversations</p>{threads.map((thread) => <div class={`nexa-thread-item ${thread.id === currentId && mainView === "chat" ? "is-active" : ""}`} key={thread.id}><button class="nexa-thread-select" type="button" onClick={() => selectThread(thread.id)}><span>{thread.title}</span><small>{thread.messages.length > 1 ? `${thread.messages.length - 1} messages` : "Not started"}</small></button><button class="nexa-thread-delete" type="button" onClick={() => setPendingDeleteId(thread.id)} aria-label={`Delete ${thread.title}`}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3M7 7l1 13h8l1-13M10 11v5M14 11v5" /></svg></button></div>)}</div>
      <button class={`nexa-sidebar-profile ${mainView === "account" ? "is-active" : ""}`} type="button" onClick={() => { setMainView("account"); if (window.innerWidth < 900) setSidebarOpen(false); }}><span>{profileInitials}</span><div><strong>{profile.name}</strong><small>Profile & bank settings</small></div><b>›</b></button>
    </aside>

    <main class="nexa-chat-main">
      <header class="nexa-chat-header">
        <div class="nexa-chat-header-start"><button class={`nexa-sidebar-toggle${sidebarOpen ? " is-open" : ""}`} type="button" onClick={() => setSidebarOpen(!sidebarOpen)} aria-expanded={sidebarOpen} aria-controls="nexa-chat-sidebar" aria-label={sidebarOpen ? "Close chat history" : "Open chat history"}><svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.25" y="4.25" width="17.5" height="15.5" rx="1.75" /><path d="M8.75 4.5v15" /><path class="nexa-sidebar-toggle-arrow" d="m14.75 9-3 3 3 3" /></svg></button><button class="nexa-chat-brand" type="button" onClick={onClose}>Nexa</button></div>
        <span class="nexa-current-thread">{mainView === "account" ? "Account settings" : mainView === "dashboard" ? "Financial overview" : mainView === "transactions" ? "Transaction analysis" : mainView === "transfer" ? "Secure transfer" : mainView === "scheduled-transfers" ? "Transfer schedule" : mainView === "subscriptions" ? "Subscription manager" : mainView === "cards" ? "Card controls" : mainView === "goals" ? "Savings goals" : mainView === "budgets" ? "Budget manager" : mainView === "bills" ? "Bills and payments" : mainView === "connected-accounts" ? "Connected accounts" : mainView === "beneficiaries" ? "Beneficiaries" : mainView === "notifications" ? "Financial notifications" : mainView === "disputes" ? "Fraud and disputes" : mainView === "reports" ? "Statements and reports" : mainView === "forecast" ? "Cash-flow forecast" : currentThread?.title || "New conversation"}</span>
      </header>
      {mainView === "chat" ? <><section ref={conversationRef} class="nexa-conversation" aria-live="polite">
        <div class="nexa-conversation-intro"><h2>{messages.length === 1 ? "How can I help?" : currentThread.title}</h2><p>{messages.length === 1 ? "Ask naturally. You’ll review every action before anything changes." : "Continue the conversation or start a new one from your history."}</p></div>
        {!accountLoading && !accountError && !primaryAccount && <section class="nexa-no-account"><span>Nexa account required</span><h3>Open your first bank account.</h3><p>Receive ₹1,00,000 in demo funds and use Nexa’s balance, transaction and payment experiences.</p><button type="button" onClick={() => setAccountOpeningOpen(true)}>Open a Nexa account <b>↗</b></button></section>}
        {accountError && <p class="nexa-banking-load-error" role="alert">{accountError}</p>}
        <div class="nexa-message-list">{messages.map((message, index) => <div class={`nexa-message is-${message.role}`} key={`${message.role}-${index}`}><span>{message.role === "assistant" ? "N" : "You"}</span><div class="nexa-message-content"><p>{message.text}</p>{message.insight === "last-month-transactions" && <div class="nexa-inline-analysis"><span><b>₹38,420</b><small>Total spent</small></span><span><b>−11.2%</b><small>Versus May</small></span><span><b>₹14,180</b><small>Home & bills</small></span></div>}{message.attachments?.length ? <div class="nexa-message-attachments">{message.attachments.map((file) => <span key={file.id}><b>{file.kind === "image" ? "IMG" : "DOC"}</b><i>{file.name}</i><small>{formatFileSize(file.size)}</small></span>)}</div> : null}{renderMessageAction(message)}</div></div>)}</div>
        {accountOpeningOpen && !primaryAccount && <div ref={accountOpeningRef} class="nexa-account-opening-anchor"><AccountOpeningCard opening={openingAccount} error={openingError} onCancel={() => { setAccountOpeningOpen(false); setOpeningError(""); }} onOpen={createNexaAccount} /></div>}
        {messages.length === 1 && <div class="nexa-chat-suggestions">{suggestions.map((suggestion) => <button type="button" onClick={() => submit(suggestion)} key={suggestion}>{suggestion}<span>↗</span></button>)}</div>}
      </section>
      <form class="nexa-chat-composer" onSubmit={(event) => { event.preventDefault(); submit(); }}><div class="nexa-composer-label"><label for="nexa-chat-input">{listening ? "Listening…" : "Ask Nexa"}</label></div>{attachments.length > 0 && <div class="nexa-attachment-tray">{attachments.map((file) => <div key={file.id}><b>{file.kind === "image" ? "IMG" : "DOC"}</b><span><strong>{file.name}</strong><small>{formatFileSize(file.size)}</small></span><button type="button" onClick={() => setAttachments((current) => current.filter((item) => item.id !== file.id))} aria-label={`Remove ${file.name}`}>×</button></div>)}</div>}<div class="nexa-composer-controls"><div ref={uploadWrapRef} class="nexa-upload-wrap"><button class={`nexa-upload-button ${uploadMenuOpen ? "is-open" : ""}`} type="button" onClick={() => setUploadMenuOpen((open) => !open)} aria-expanded={uploadMenuOpen} aria-haspopup="menu" aria-label="Add an attachment"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg></button>{uploadMenuOpen && <div class="nexa-upload-menu" role="menu"><span>Add to conversation</span><button type="button" role="menuitem" onClick={() => photoInputRef.current?.click()}><b>▧</b><span><strong>Photos</strong><small>JPG, PNG, WEBP and more</small></span></button><button type="button" role="menuitem" onClick={() => documentInputRef.current?.click()}><b>≡</b><span><strong>Documents</strong><small>PDF, Word, sheets and text</small></span></button></div>}<input ref={photoInputRef} class="nexa-file-input" type="file" accept="image/*" multiple onChange={(event) => addAttachments(event, "image")} /><input ref={documentInputRef} class="nexa-file-input" type="file" accept=".pdf,.doc,.docx,.txt,.csv,.xls,.xlsx,.ppt,.pptx" multiple onChange={(event) => addAttachments(event, "document")} /></div><textarea ref={textareaRef} id="nexa-chat-input" rows={1} value={input} onInput={(event) => setInput((event.currentTarget as HTMLTextAreaElement).value)} onKeyDown={(event) => { if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); submit(); } }} placeholder={listening ? "Speak now…" : "Type or speak a request…"} /><button class={`nexa-voice-button ${listening ? " is-listening" : ""}`} type="button" onClick={toggleVoice} aria-label={listening ? "Stop listening" : "Speak to Nexa"}><svg aria-hidden="true" viewBox="0 0 24 24"><rect x="8" y="3" width="8" height="12" rx="4" fill="none" stroke="currentColor" stroke-width="1.7"/><path d="M5.5 11.5v.5a6.5 6.5 0 0 0 13 0v-.5M12 18.5V22M9 22h6" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg></button><button class="nexa-send-button" type="submit" aria-label="Send message">↑</button></div><span>{listening ? "Voice input is active" : attachments.length ? `${attachments.length} of 5 files ready` : "Nothing happens without your approval"}</span></form></> : mainView === "account" ? <AccountSettings session={{ ...session, profile: { ...session.profile, fullName: profile.name, phoneNumber: profile.phone || null } }} account={primaryAccount} onBack={() => setMainView("chat")} onLogout={onLogout} onProfileUpdated={(updated) => setProfile({ name: updated.fullName, email: updated.email, phone: updated.phoneNumber || "" })} /> : mainView === "dashboard" && primaryAccount ? <FinancialDashboard userName={profile.name} accounts={accounts} transactions={bankTransactions} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "transactions" && primaryAccount ? <TransactionAnalysis account={primaryAccount} transactions={bankTransactions} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "transfer" ? <MoneyTransfer draft={activeTransfer} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "scheduled-transfers" ? <ScheduledTransfers draft={activeScheduledTransfer} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "subscriptions" ? <SubscriptionManager onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "goals" ? <GoalsWorkspace draft={activeGoalDraft} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "budgets" ? <BudgetManager onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "bills" ? <BillsWorkspace draft={activeBillDraft} onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "connected-accounts" ? <ConnectedAccounts onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : mainView === "beneficiaries" ? <BeneficiaryManager onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} /> : <CardControls onBack={() => setMainView("chat")} onAsk={(prompt) => { setMainView("chat"); submit(prompt); }} />}
    </main>
    {pendingDeleteId && <div class="nexa-delete-dialog-backdrop" onClick={() => setPendingDeleteId(null)}><section class="nexa-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="nexa-delete-title" onClick={(event) => event.stopPropagation()}><span>Delete conversation</span><h2 id="nexa-delete-title">Remove this conversation?</h2><p>This will permanently remove “{threads.find((thread) => thread.id === pendingDeleteId)?.title}” from your chat history on this device.</p><div><button type="button" onClick={() => setPendingDeleteId(null)}>Keep conversation</button><button ref={deleteConfirmRef} class="is-destructive" type="button" onClick={deleteConversation}>Delete conversation</button></div></section></div>}
  </div>;
}
