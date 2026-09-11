import { h } from "preact";
import { useEffect, useState } from "preact/hooks";
import { Navbar } from "./Navbar";
import { HeroDemo as Hero } from "./HeroDemo";
import { ExperienceSection } from "./ExperienceSection";
import { FinalCta } from "./FinalCta";
import { LandingFooter } from "./LandingFooter";
import { ConversationWorkspace as ChatWorkspace } from "../chat/ConversationWorkspace";
import { AuthMode, AuthPage } from "../auth/AuthPage";
import { AuthSession, logout, restoreSession } from "../../services/auth";

type Page = "landing" | AuthMode | "chat";

export function NexaLanding() {
  const [page, setPage] = useState<Page>("landing");
  const [session, setSession] = useState<AuthSession | null>(null);
  const [restoringSession, setRestoringSession] = useState(true);

  useEffect(() => {
    restoreSession().then((restoredSession) => {
      setSession(restoredSession);
      if (restoredSession) setPage("chat");
    }).finally(() => setRestoringSession(false));
  }, []);

  const openChat = () => setPage(session ? "chat" : "login");

  const signOut = async () => {
    try {
      await logout();
    } finally {
      setSession(null);
      setPage("landing");
    }
  };

  if (restoringSession) {
    return <div class="nexa-session-loading"><span>Nexa</span><p>Restoring your private session…</p></div>;
  }

  if (page === "chat" && session) {
    return <ChatWorkspace session={session} onClose={() => setPage("landing")} onLogout={signOut} />;
  }

  if (page === "login" || page === "register") {
    return <AuthPage
      key={page}
      mode={page}
      onBack={() => setPage("landing")}
      onSwitch={(mode) => setPage(mode)}
      onAuthenticated={(authenticatedSession) => {
        setSession(authenticatedSession);
        setPage("chat");
      }}
    />;
  }

  return (
    <div class="nexa-landing">
      <Navbar
        authenticated={!!session}
        onLogin={() => setPage("login")}
        onRegister={() => setPage("register")}
        onOpenChat={openChat}
        onLogout={signOut}
      />

      <main>
        <Hero />
        <ExperienceSection />
        <FinalCta onGetStarted={openChat} />
      </main>

      <LandingFooter />
    </div>
  );
}
