import { ComponentChildren } from "preact";

export type BankingIconName = "chat" | "messages" | "home" | "accounts" | "payments" | "cards" | "transactions" | "insights" | "support" | "plus" | "shield" | "arrow" | "overview" | "people" | "repeat" | "clock" | "more" | "chevron" | "profile" | "logout";
export function BankingIcon({ name }: { name: BankingIconName }) {
  const paths: Record<BankingIconName, ComponentChildren> = {
    chat: <path d="M20 11a8 8 0 0 1-8 8H5l-3 3V11a9 9 0 0 1 18 0Z"/>,
    messages: <><g class="chat-bubble-reply"><path d="M14 14h3l4 3V9a2 2 0 0 0-2-2h-2"/></g><g class="chat-bubble-message"><path d="M5 4h9a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H8l-5 4V6a2 2 0 0 1 2-2Z"/><path d="M6 7h7M6 10h4"/></g></>,
    home: <><path d="m3 10 9-7 9 7v10H3Z"/><path d="M9 20v-7h6v7"/></>,
    accounts: <><rect x="3" y="5" width="18" height="15" rx="3"/><path d="M3 9h18M15 13h6v4h-6Z"/></>,
    payments: <><path d="M4 8h15m-5-5 5 5-5 5M20 16H5m5-5-5 5 5 5"/></>,
    cards: <><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 10h18M7 15h4"/></>,
    transactions: <><path d="M8 3H4v18h16V3h-4M8 3v4h8V3ZM8 12h8M8 16h5"/></>,
    insights: <><path d="M4 3v17h17M8 16v-5M13 16V7M18 16V4"/></>,
    support: <><circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 0 1 6 0c0 2-3 2-3 5M12 17h.01"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    shield: <><path d="m12 3 8 3v6c0 4-5 8-8 9-3-1-8-5-8-9V6Z"/><path d="m8 12 3 3 5-6"/></>,
    overview: <><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></>,
    people: <><circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6M18 15a5 5 0 0 1 3 5"/></>,
    repeat: <><path d="M4 8h15l-4-4M20 16H5l4 4M20 8v4M4 16v-4"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    more: <><circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/></>,
    chevron: <path d="m8 10 4 4 4-4"/>,
    profile: <><circle cx="12" cy="8" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></>,
    logout: <><path d="M10 4H4v16h6M10 12h11m-4-4 4 4-4 4"/></>,
    arrow: <path d="M5 12h14m-6-6 6 6-6 6"/>
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{paths[name]}</svg>;
}

