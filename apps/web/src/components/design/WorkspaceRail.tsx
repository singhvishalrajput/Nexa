import { BankingIcon, BankingIconName } from "../BankingIcon";
import { initials } from "../../features/banking/utils";
import { t } from "../../services/locale";

const destinations: { page: string; label: string; icon: BankingIconName }[] = [
  {page: "assistant", label: "Your conversation", icon: "chat"},
  {page: "overview", label: "Overview", icon: "home"},
  {page: "accounts", label: "Accounts", icon: "accounts"},
  {page: "send-money", label: "Send money", icon: "payments"},
  {page: "transactions", label: "Transactions", icon: "transactions"}
];
const adminDestinations: { page: string; label: string; icon: BankingIconName }[] = [
  {page: "admin", label: "Accounts", icon: "accounts"},
  {page: "admin/loans", label: "Loan requests", icon: "transactions"}
];

export function WorkspaceRail({ page, name, email, admin = false, onLogout }: { page: string; name: string; email?: string; admin?: boolean; onLogout?: () => void | Promise<void> }) {
  const links = admin ? adminDestinations : destinations;
  return <nav class="experience-rail" aria-label={t("Nexa workspace")}>
    <a class="experience-brand" href={admin ? "#home" : "#/overview"} aria-label={t(admin ? "Nexa home" : "Overview")}><img src="styles/images/nexa.svg" width="28" height="28" alt=""/></a>
    {links.map(item => <a key={item.page} href={`#/${item.page}`} title={t(item.label)} aria-label={t(item.label)} aria-current={page === item.page ? "page" : undefined}><BankingIcon name={item.icon}/></a>)}
    <div class="experience-rail-bottom">
      {!admin && !onLogout && <a href="#/settings" title={t("Profile & settings")} aria-label={t("Profile & settings")} aria-current={page === "settings" ? "page" : undefined}><BankingIcon name="profile"/></a>}
      {onLogout ? <details class="experience-profile-menu">
        <summary class="experience-avatar" title={name} aria-label={t("Account menu")}>{initials(name)}</summary>
        <div class="experience-profile-popover" role="menu">
          <span>{email || name}</span>
          <button type="button" role="menuitem" onClick={() => void onLogout()}><BankingIcon name="logout"/>{t("Sign out")}</button>
        </div>
      </details> : <span class="experience-avatar" title={name} aria-label={name}>{initials(name)}</span>}
    </div>
  </nav>;
}
