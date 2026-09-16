import { BankingIcon, BankingIconName } from "../BankingIcon";
import { AccountSnapshot, formatMoney, safeMask } from "../../services/banking-content";
import { t } from "../../services/locale";

export function QuickAction({ icon, title, description, disabled, onClick }: { icon: BankingIconName; title: string; description: string; disabled: boolean; onClick: () => void }) {
  return <button type="button" class="conversation-quick-action" disabled={disabled} onClick={onClick}>
    <span class="conversation-action-icon"><BankingIcon name={icon}/></span>
    <strong>{t(title)}</strong><span>{t(description)}</span><BankingIcon name="arrow"/>
  </button>;
}

export function AccountContext({ accounts, loading, error, hidden, onToggle, onRetry }: { accounts: AccountSnapshot[]; loading: boolean; error: string; hidden: boolean; onToggle: () => void; onRetry: () => void }) {
  return <div class="conversation-context-content">
    <div class="conversation-context-heading"><span>{t("At a glance")}</span><BankingIcon name="accounts"/></div>
    <h2>{t("Your accounts")}</h2>
    <button type="button" class="conversation-privacy" aria-pressed={hidden} onClick={onToggle}>{t(hidden ? "Show balances" : "Hide balances")}</button>
    {loading ? <div class="conversation-skeleton" role="status"><span/>{t("Loading your banking information")}<span/></div>
      : error ? <div role="alert"><p>{t("We couldn’t load this information")}</p><button type="button" onClick={onRetry}>{t("Try again")}</button></div>
      : !accounts.length ? <p>{t("No accounts to show.")}</p>
      : accounts.map(account => <section class="conversation-context-account" key={account.id}>
        <h3>{account.displayName}</h3><span>{safeMask(account.accountNumberMasked)}</span>
        <p>{t("Available balance")}</p><strong aria-label={hidden ? t("Balances hidden") : undefined}>{hidden ? "••••••" : formatMoney(account.availableBalance, account.currencyCode)}</strong>
        <a href={"#/accounts/" + encodeURIComponent(account.id)}>{t("Open accounts")} <BankingIcon name="arrow"/></a>
      </section>)}
    <div class="conversation-security"><BankingIcon name="shield"/><h3>{t("Review before you send")}</h3><p>{t("Check the recipient and amount before confirming a payment.")}</p></div>
  </div>;
}
