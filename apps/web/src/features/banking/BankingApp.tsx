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
import { go, initials, parseRoute, Route } from "./utils";
const navigation: Array<{
    label: string;
    page: Route;
    icon: string;
    group?: string;
}> = [
    { label: "Chat", page: "assistant", icon: "✧", group: "WORKSPACE" }, { label: "Overview", page: "overview", icon: "◫", group: "WORKSPACE" }, { label: "Accounts", page: "accounts", icon: "▤" }, { label: "Transactions", page: "transactions", icon: "⇄" }, { label: "Payments", page: "payments", icon: "↗" }, { label: "Payees", page: "beneficiaries", icon: "♧" },
    { label: "Cards", page: "cards", icon: "▰", group: "YOUR FINANCES" }, { label: "Bills", page: "bills", icon: "▥" }, { label: "Direct debits", page: "mandates", icon: "⟳" }, { label: "Scheduled payments", page: "scheduled-payments", icon: "◷" }, { label: "Loans", page: "loans", icon: "◇" }
];
export function BankingApp() {
    const [session, setSession] = useState<AuthSession | null>(null);
    const [starting, setStarting] = useState(true);
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
    }; const expired = () => { generation.current++; setSession(null); setNotice("Your session has expired. Sign in again to continue."); window.history.replaceState(null, "", "#/login"); acceptedHash.current = "#/login"; setRoute(parseRoute("#/login")); }; window.addEventListener("hashchange", change); window.addEventListener("nexa-session-expired", expired); return () => { window.removeEventListener("beforeunload", unload); generation.current++; window.removeEventListener("hashchange", change); window.removeEventListener("nexa-session-expired", expired); }; }, []);
    async function signOut() { if (!confirmNavigation()) return; generation.current++; setSession(null); window.history.replaceState(null, "", "#/login"); acceptedHash.current = "#/login"; setRoute(parseRoute("#/login")); try {
        await logout();
        setNotice("You have been signed out.");
    }
    catch {
        setNotice("Signed out on this device. The server could not confirm session revocation.");
    } }
    if (starting || error)
        return <div class="bank-app bank-start"><span class="bank-wordmark">nexa<span>®</span></span><State loading={starting} error={error} retry={restore}/></div>;
    if (!session)
        return <div class="bank-app bank-auth">{notice && <div class="bank-session-notice" role="status">{notice}</div>}<AuthPage mode={route.page === "register" ? "register" : "login"} onBack={() => go("login")} onSwitch={mode => go(mode)} onAuthenticated={s => { setSession(s); setNotice(""); if (["login", "register", "not-found"].includes(route.page))
            go("assistant"); }}/></div>;
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
    const pageTitle = navigation.find(n => n.page === page)?.label || ({settings: "Profile & settings", security: "Security & session", assistant: "Chat", operations: "Banking operations"} as Record<string, string>)[page] || "Page not found";
    useEffect(() => { setMenu(false); window.scrollTo(0, 0); document.title = pageTitle + " · Nexa"; heading.current?.querySelector<HTMLElement>("h1")?.focus(); }, [page, route.id, data.loading]);
    const endSession = async () => { if (signingOut)
        return; setSigningOut(true); try { await signOut(); } finally { setSigningOut(false); } };
    if (page === "assistant")
        return <ConversationWorkspace session={session} onClose={() => go("overview")} onLogout={endSession}/>;
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
        if (page === "payments")
            return <PaymentsPage token={session.accessToken} accounts={accounts}/>;
        if (page in productNames)
            return <ProductsPage key={page} token={session.accessToken} kind={page as ProductKind} id={route.id}/>;
        if (page === "settings")
            return <><PageHeading title="Profile & settings" description="Keep your personal banking details up to date."/><AccountSettings session={session} account={accounts[0]} onBack={() => go("accounts")} onLogout={endSession} onProfileUpdated={profile => setSession({ ...session, profile })}/></>;
        if (page === "security")
            return <><PageHeading title="Security & session" description="Understand and manage your current sign-in."/><div class="bank-detail-grid"><Panel title="Your current session"><div class="bank-form"><span class="bank-tile-symbol">⊡</span><h3>Signed in to Nexa</h3><dl><Detail label="Email">{session.profile.email}</Detail><Detail label="Account status">{session.profile.status}</Detail><Detail label="Access">{session.user.role.replace(/_/g, " ").toLowerCase()}</Detail></dl><p>Signing out clears this device’s session and requests revocation of its refresh token.</p><button class="bank-button" disabled={signingOut} onClick={endSession}>Sign out of this session</button></div></Panel><Panel title="Keep your banking private"><div class="bank-form"><p>Never share your password or session tokens. Always sign out on a shared device.</p><p>Online password changes, multi-factor authentication controls, device management and security alerts are not available in this application. Contact your bank for help with these services.</p></div></Panel></div></>;
        if (page === "operations" && session.user.role === "ADMIN")
            return <OperationsPage token={session.accessToken}/>;
        return <><PageHeading title={page === "operations" ? "Administrator access required" : "Page not found"} description="This page is not available for your session."/><a class="bank-button" href="#/overview">Back to overview</a></>;
    }
    const nav = <><a class="bank-wordmark" href="#/assistant" aria-label="Nexa chat">nexa<span>®</span></a><span class="bank-workspace-label">PERSONAL BANKING</span><nav aria-label="Main navigation">{navigation.map(n => <div key={n.page}>{n.group && <p class="bank-nav-group">{n.group}</p>}<a class={page === n.page ? "active" : ""} aria-current={page === n.page ? "page" : undefined} href={"#/" + n.page} onClick={() => setMenu(false)}><span aria-hidden="true">{n.icon}</span>{n.label}{page === n.page && <i />}</a></div>)}{session.user.role === "ADMIN" && <a class={page === "operations" ? "active" : ""} href="#/operations"><span>↔</span>Banking operations</a>}</nav><div class="bank-sidebar-bottom"><a href="#/assistant"><span>✧</span> Ask Nexa <span>↗</span></a><a href="#/settings">Profile & settings</a><a href="#/security">Security & session</a><button onClick={endSession} disabled={signingOut}>Sign out <span>↗</span></button></div></>;
    return <div class="bank-app"><a class="bank-skip" href="#bank-main" onClick={e => { e.preventDefault(); heading.current?.focus(); }}>Skip to main content</a><aside class="bank-sidebar">{nav}</aside>{menu && <Modal title="Navigation" onClose={() => setMenu(false)}><div class="bank-mobile-nav">{nav}</div></Modal>}<div class="bank-workspace"><header class="bank-topbar"><div><button class="bank-icon-button bank-menu-toggle" aria-label="Open navigation" onClick={() => setMenu(true)}>☰</button><span>Personal banking <b>/</b> <strong>{pageTitle}</strong></span></div><div><a class="bank-assistant-link" href="#/assistant" aria-label="Ask Nexa — type or speak for help">✧ Ask Nexa</a><a class="bank-profile-link" href="#/settings"><span class="bank-avatar">{initials(session.profile.fullName)}</span><span>{session.profile.fullName}<small>{session.user.role === "ADMIN" ? "Administrator" : "Personal account"}</small></span></a></div></header><main id="bank-main" ref={heading} tabIndex={-1} class="bank-main">{content()}<footer class="bank-footer"><span>nexa <span>·</span> Your everyday banking, connected.</span><a href="#/security">Security & session ↗</a></footer></main><nav class="bank-mobile-tabs" aria-label="Quick navigation">{navigation.slice(0, 4).map(n => <a href={"#/" + n.page} aria-current={page === n.page ? "page" : undefined} class={page === n.page ? "active" : ""}><span aria-hidden="true">{n.icon}</span>{n.page === "transactions" ? "History" : n.label}</a>)}<button onClick={() => setMenu(true)}><span>☰</span>More</button></nav></div></div>;
}
