import { LandingPage } from "../../components/landing/LandingPage";
import { WorkspaceRail } from "../../components/design/WorkspaceRail";
import { AdminApp } from "./AdminApp";
import { SidebarBrand, SidebarNavigation, SidebarFooter, primaryNavigation, secondaryNavigation } from "../../components/SidebarNavigation";
import { BankingIcon } from "../../components/BankingIcon";
import { LanguageSelect } from "../../components/LanguageSelect";
import { getLocale, t } from "../../services/locale";
import { MoneyTransfer } from "./MoneyTransfer";
import { ConnectionNotice } from "../../components/ConnectionNotice";
import { confirmNavigation, hasUnsavedWork } from "../../hooks/useNavigationGuard";
import { useEffect, useRef, useState } from "preact/hooks";
import { AuthSession, restoreSession, logout } from "../../services/auth";
import { AuthPage } from "../../components/auth/AuthPage";
import { AccountSettings } from "../../components/chat/AccountSettings";
import { ConversationWorkspace } from "../../components/chat/ConversationWorkspace";
import { bankApi, ProductKind } from "./api";
import { Overview, AccountsPage, TransactionsPage } from "./Accounts";
import { ProductsPage, productNames } from "./Products";
import { PaymentsPage, OperationsPage } from "./Payments";
import { PageHeading, Panel, State, Modal, Detail, useLoad } from "./ui";
import { go, initials, parseRoute } from "./utils";
const landingHashes = ["", "#home", "#the-nexa-way", "#possibilities", "#whats-next", "#main"];

export function BankingApp({onReady}: {onReady?: () => void} = {}) {
    const [locale, updateLocale] = useState(getLocale);
    useEffect(() => { document.documentElement.lang = locale; const update = () => updateLocale(getLocale()); window.addEventListener("nexa-language-change", update); return () => window.removeEventListener("nexa-language-change", update); }, [locale]);
    const [session, setSession] = useState<AuthSession | null>(null);
    const [starting, setStarting] = useState(true);
    useEffect(() => { if (!starting) onReady?.(); }, [starting, onReady]);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [route, setRoute] = useState(() => parseRoute(window.location.hash));
    const generation = useRef(0);
    const acceptedHash = useRef(window.location.hash);
    async function restore() { const epoch = ++generation.current; setStarting(true); setError(""); try {
        const s = await restoreSession();
        if (epoch === generation.current)
            setSession(s);
    }
    catch (e) {
        if (epoch === generation.current)
            setError(e instanceof Error ? e.message : "Your session couldn’t be restored.");
    }
    finally {
        if (epoch === generation.current)
            setStarting(false);
    } }
    useEffect(() => { const unload = (event: BeforeUnloadEvent) => { if (hasUnsavedWork()) { event.preventDefault(); event.returnValue = ""; } }; window.addEventListener("beforeunload", unload); restore(); const change = () => {
        if (!confirmNavigation()) { window.history.replaceState(null, "", window.location.pathname + window.location.search + acceptedHash.current); return; }
        acceptedHash.current = window.location.hash; setRoute(parseRoute(window.location.hash));
    }; const expired = () => { const destination = landingHashes.includes(acceptedHash.current) ? "#home" : "#/login"; generation.current++; setSession(null); setNotice("Your session has expired. Sign in again to continue."); window.history.replaceState(null, "", destination); acceptedHash.current = destination; setRoute(parseRoute(destination)); }; window.addEventListener("hashchange", change); window.addEventListener("nexa-session-expired", expired); return () => { window.removeEventListener("beforeunload", unload); generation.current++; window.removeEventListener("hashchange", change); window.removeEventListener("nexa-session-expired", expired); }; }, []);
    async function signOut() { if (!confirmNavigation()) return; const destination = landingHashes.includes(acceptedHash.current) ? "#home" : "#/login"; generation.current++; setSession(null); window.history.replaceState(null, "", destination); acceptedHash.current = destination; setRoute(parseRoute(destination)); try {
        await logout();
        setNotice("You have been signed out.");
    }
    catch {
        setNotice("Signed out on this device. We couldn’t reach the bank to finish signing out. Close this page if you are using a shared device.");
    } }
    if (starting || error)
        return <div class="bank-app bank-start"><ConnectionNotice/><span class="bank-wordmark">nexa<span>®</span></span><State loading={starting} error={error} retry={restore}/></div>;
    if (landingHashes.includes(acceptedHash.current)) return <LandingPage session={session} onSignOut={signOut} notice={notice}/>;
    if (!session)
        return <div class="bank-auth"><ConnectionNotice/>{notice && <div class="bank-session-notice" role="status">{notice}</div>}<AuthPage mode={route.page === "register" ? "register" : "login"} onBack={() => { window.location.hash = "home"; }} onSwitch={mode => go(mode)} onAuthenticated={s => { setSession(s); setNotice(""); if (["login", "register", "not-found"].includes(route.page))
            go(s.user.role === "ADMIN" ? "admin" : "assistant"); }}/></div>;
    if (session.user.role === "ADMIN") return <AdminApp key={session.user.id} session={session} signOut={signOut} route={route}/>;
    return <Workspace key={session.user.id} session={session} setSession={setSession} signOut={signOut} route={route}/>;
}
function Workspace({ session, setSession, signOut, route }: {
    session: AuthSession;
    setSession: (s: AuthSession) => void;
    signOut: () => Promise<void>;
    route: ReturnType<typeof parseRoute>;
}) {
    const [menu, setMenu] = useState(false);
    const [signingOut, setSigningOut] = useState(false);
    const heading = useRef<HTMLElement>(null);
    const data = useLoad(() => bankApi.accounts(session.accessToken), [session.user.id, route.page]);
    const accounts = data.data || [];
    const page = route.page === "login" || route.page === "register" ? "assistant" : route.page;
    const pageTitle = [...primaryNavigation, ...secondaryNavigation].find(n => n.page === page)?.label || ({settings: "Profile & settings", security: "Security & session", assistant: "Chat", operations: "Banking operations"} as Record<string, string>)[page] || "Page not found";
    useEffect(() => { setMenu(false); window.scrollTo(0, 0); document.title = pageTitle + " · Nexa"; heading.current?.querySelector<HTMLElement>("h1")?.focus(); }, [page, route.id, data.loading]);
    const endSession = async () => { if (signingOut)
        return; setSigningOut(true); try { await signOut(); } finally { setSigningOut(false); } };
    if (page === "assistant")
        return <ConversationWorkspace session={session} onClose={() => go("overview")} onLogout={endSession} accounts={accounts} accountsLoading={data.loading} accountsError={data.error} onRefreshAccounts={data.reload}/>;
    const accountPage = ["overview", "accounts", "transactions", "payments"].includes(page);
    function content() {
        if (accountPage && (data.loading || data.error))
            return <State loading={data.loading} error={data.error} retry={data.reload}/>;
        if (page === "overview")
            return <Overview token={session.accessToken} name={session.profile.fullName} accounts={accounts} reload={data.reload}/>;
        if (page === "accounts")
            return <AccountsPage token={session.accessToken} id={route.id} accounts={accounts} reload={data.reload}/>;
        if (page === "transactions")
            return <TransactionsPage key={route.id || route.account || "history"} token={session.accessToken} id={route.id} accounts={accounts} initialAccount={route.account}/>;
        if (page === "send-money")
            return <MoneyTransfer token={session.accessToken} userId={session.user.id}/>;
        if (page === "payments")
            return <PaymentsPage token={session.accessToken} accounts={accounts}/>;
        if (page in productNames)
            return <ProductsPage key={page} token={session.accessToken} kind={page as ProductKind} id={route.id}/>;
        if (page === "settings")
            return <><PageHeading title={t("Profile & settings")} description={t("Keep your personal banking details up to date.")}/><AccountSettings session={session} account={accounts[0]} onBack={() => go("accounts")} onLogout={endSession} onProfileUpdated={profile => setSession({ ...session, profile })}/></>;
        if (page === "security")
            return <><PageHeading title={t("Security & session")} description={t("Understand and manage your current sign-in.")}/><div class="bank-detail-grid"><Panel title={t("Your current session")}><div class="bank-form"><span class="bank-tile-symbol">⊡</span><h3>{t("Signed in to Nexa")}</h3><dl><Detail label={t("Email")}>{session.profile.email}</Detail><Detail label={t("Account status")}>{session.profile.status}</Detail><Detail label={t("Access")}>{session.user.role.replace(/_/g, " ").toLowerCase()}</Detail></dl><p>{t("Sign out when you finish banking, especially on a shared phone or computer.")}</p><button class="bank-button" disabled={signingOut} onClick={endSession}>{t("Sign out of this session")}</button></div></Panel><Panel title={t("Keep your banking private")}><div class="bank-form"><p>{t("Never share your password or sign-in details. Always sign out on a shared device.")}</p><p>{t("Online password changes, multi-factor authentication controls, device management and security alerts are not available in this application. Contact your bank for help with these services.")}</p></div></Panel></div></>;
        if (page === "operations" && session.user.role === "ADMIN")
            return <OperationsPage token={session.accessToken}/>;
        return <><PageHeading title={page === "operations" ? "Administrator access required" : "Page not found"} description={t("This page is not available for your session.")}/><a class="bank-button" href="#/overview">{t("Back to overview")}</a></>;
    }
    const nav = <><SidebarBrand/><SidebarNavigation page={page} admin={session.user.role === "ADMIN"}/><SidebarFooter page={page} onLogout={endSession} disabled={signingOut}/></>;
    return <div class="bank-app experience-workspace"><WorkspaceRail page={page} name={session.profile.fullName}/><a class="bank-skip" href="#bank-main" onClick={e => { e.preventDefault(); heading.current?.focus(); }}>{t("Skip to main content")}</a><aside class="nexa-sidebar bank-sidebar" aria-label={t("Banking navigation")}>{nav}</aside>{menu && <Modal title={t("Navigation")} onClose={() => setMenu(false)}><div class="nexa-sidebar bank-mobile-nav">{nav}</div></Modal>}<div class="bank-workspace"><header class="bank-topbar"><div><button class="bank-icon-button bank-menu-toggle" aria-label={t("Open navigation")} aria-expanded={menu} onClick={() => setMenu(true)}>{t("☰ Menu")}</button><span class="experience-section-title">{t(pageTitle)}</span></div><div><LanguageSelect/><a class="bank-assistant-link" href="#/assistant" aria-label={t("Ask Nexa — type or speak for help")}>{t("✧ Ask Nexa")}</a><a class="bank-profile-link" href="#/settings"><span class="bank-avatar">{initials(session.profile.fullName)}</span><span>{session.profile.fullName}<small>{session.user.role === "ADMIN" ? "Administrator" : "Personal account"}</small></span></a></div></header><main id="bank-main" ref={heading} tabIndex={-1} class="bank-main"><ConnectionNotice/>{content()}<footer class="bank-footer"><span>nexa <span>·</span>{t("Your everyday banking, connected.")}</span><a href="#/security">{t("Security & session ↗")}</a></footer></main><nav class="bank-mobile-tabs" aria-label={t("Quick navigation")}>{primaryNavigation.filter(n => n.page !== "cards").map(n => <a href={"#/" + n.page} aria-current={page === n.page ? "page" : undefined} class={page === n.page ? "active" : ""}><BankingIcon name={n.icon}/>{t(n.label)}</a>)}<button onClick={() => setMenu(true)}><span>☰</span>{t("More")}</button></nav></div></div>;
}
