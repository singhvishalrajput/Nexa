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

export function WorkspaceRail({ page, name, admin = false }: { page: string; name: string; admin?: boolean }) {
  const links = admin ? [{page: "admin", label: "Administration", icon: "shield" as BankingIconName}] : destinations;
  return <nav class="experience-rail" aria-label={t("Nexa workspace")}>
    <a class="experience-brand" href="#home" aria-label="Nexa home"><img src="styles/images/nexa.svg" width="28" height="28" alt=""/></a>
    {links.map(item => <a key={item.page} href={`#/${item.page}`} title={t(item.label)} aria-label={t(item.label)} aria-current={page === item.page ? "page" : undefined}><BankingIcon name={item.icon}/></a>)}
    <div class="experience-rail-bottom">
      {!admin && <a href="#/settings" title={t("Profile & settings")} aria-label={t("Profile & settings")} aria-current={page === "settings" ? "page" : undefined}><BankingIcon name="profile"/></a>}
      <span class="experience-avatar" title={name} aria-label={name}>{initials(name)}</span>
    </div>
  </nav>;
}
