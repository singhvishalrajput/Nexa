import { ComponentChildren } from "preact";
import { BankingIcon, BankingIconName } from "./BankingIcon";
import { Route } from "../features/banking/utils";
import { t } from "../services/locale";

type NavigationItem = { page: Route; label: string; icon: BankingIconName };
export const primaryNavigation: NavigationItem[] = [
  { page: "assistant", label: "Chat", icon: "chat" },
  { page: "accounts", label: "Accounts", icon: "accounts" },
  { page: "payments", label: "Payments", icon: "payments" },
  { page: "cards", label: "Cards", icon: "cards" },
  { page: "transactions", label: "Transactions", icon: "transactions" }
];
export const secondaryNavigation: NavigationItem[] = [
  { page: "overview", label: "Overview", icon: "overview" },
  { page: "send-money", label: "Send money", icon: "arrow" },
  { page: "beneficiaries", label: "Payees", icon: "people" },
  { page: "bills", label: "Bills", icon: "transactions" },
  { page: "mandates", label: "Direct debits", icon: "repeat" },
  { page: "scheduled-payments", label: "Scheduled payments", icon: "clock" },
  { page: "loans", label: "Loans", icon: "accounts" }
];

export function SidebarBrand({ action, textOnly = false }: { action?: ComponentChildren; textOnly?: boolean }) {
  return <header class="sidebar-brand-row">
    <a class="sidebar-brand" href="#/overview" aria-label={t("Overview")}>
      {!textOnly && <img src="styles/images/nexa.svg" width="28" height="28" alt=""/>}<span>Nexa</span>
    </a>{action}
  </header>;
}

export function SidebarNavigation({ page, admin = false, children }: {
  page: string; admin?: boolean; children?: ComponentChildren;
}) {
  const secondary = admin ? [...secondaryNavigation, { page: "operations", label: "Banking operations", icon: "shield" } as NavigationItem] : secondaryNavigation;
  const link = (item: NavigationItem) => <a key={item.page} class={item.page === "assistant" ? "sidebar-link sidebar-chat-link" : "sidebar-link"} href={"#/" + item.page} aria-current={page === item.page ? "page" : undefined}>
    <BankingIcon name={item.icon}/><span>{t(item.label)}</span>
  </a>;
  return <nav class="sidebar-navigation" aria-label={t("Main navigation")}>
    <div class="sidebar-primary">{primaryNavigation.map(link)}{children}</div>
    <div class="sidebar-secondary">{secondary.map(link)}</div>
  </nav>;
}

export function SidebarFooter({ page, onLogout, disabled, children }: {
  page: string; onLogout: () => void | Promise<void>; disabled?: boolean; children?: ComponentChildren;
}) {
  return <footer class="sidebar-footer">
    {children}
    <a class="sidebar-link" href="#/settings" aria-current={page === "settings" ? "page" : undefined}><BankingIcon name="profile"/><span>{t("Profile & settings")}</span></a>
    <a class="sidebar-link" href="#/security" aria-current={page === "security" ? "page" : undefined}><BankingIcon name="shield"/><span>{t("Security & session")}</span></a>
    <div class="sidebar-session"><button class="sidebar-link" type="button" disabled={disabled} onClick={onLogout}><BankingIcon name="logout"/><span>{t("Sign out")}</span></button></div>
  </footer>;
}
