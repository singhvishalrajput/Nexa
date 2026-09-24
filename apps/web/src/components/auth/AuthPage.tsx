import "ojs/ojformlayout";
import { Action } from "../design/Action";
import "ojs/ojinputtext";
import { t } from "../../services/locale";
import { LanguageSelect } from "../LanguageSelect";
import { useEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError, AuthSession, login, register } from "../../services/auth";

export type AuthMode = "login" | "register";

type AuthPageProps = {
  mode: AuthMode;
  onBack: () => void;
  onSwitch: (mode: AuthMode) => void;
  onAuthenticated: (session: AuthSession) => void;
};

function PasswordVisibilityIcon({ visible }: { visible: boolean }) {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
    <circle cx="12" cy="12" r="2.5" />
    {!visible && <path d="m4 4 16 16" />}
  </svg>;
}

export function AuthPage({ mode, onBack, onSwitch, onAuthenticated }: AuthPageProps) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");


  const submissionLock = useRef(false);


  useEffect(() => {
    setError("");
    document.title = t(mode === "login" ? "Welcome back" : "Create your account") + " · Nexa";
    document.querySelector<HTMLElement>(".na-shell")?.scrollTo({ top: 0, behavior: "auto" });
    document.getElementById("na-title")?.focus();
  }, [mode]);

  const submit = async (event?: Event) => {
    event?.preventDefault();
    if (submissionLock.current) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.length > 254) { setError("Enter a valid email address."); return; }
    if (password.length < 8 || password.length > 72) { setError("Enter your password using 8–72 characters."); return; }
    if (mode === "register" && (!/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password) || !/[^a-zA-Z0-9]/.test(password))) { setError("Include uppercase, lowercase, a number, and a special character in your password."); return; }
    if (mode === "register" && !fullName.trim()) { setError("Enter your full name."); return; }
    submissionLock.current = true;
    setSubmitting(true);
    setError("");
    try {
      const session = mode === "login"
        ? await login(email.trim(), password)
        : await register(fullName.trim(), email.trim(), password, phoneNumber);
      onAuthenticated(session);
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError && requestError.status === 401 ? "Your email or password doesn’t match. Check both and try again." : requestError instanceof ApiRequestError && requestError.status === 0 ? "We couldn’t connect to the bank. Check your internet connection and try again. Your entries are still here." : requestError instanceof Error ? requestError.message : "We couldn’t sign you in. Check your details and try again.");
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  return <main class={`na-shell na-${mode}`}>
    <header class="na-header">
      <a class="na-brand" href="#home" aria-label="Nexa home"><img src="styles/images/nexa.svg" width="28" height="28" alt=""/><span>Nexa</span></a>
      <div class="experience-auth-header"><LanguageSelect compact/><Action label={t("Back to home")} disabled={submitting} onAction={onBack}/></div>
    </header>
    <div class="na-layout">
      <section class="na-story" aria-label={t("Banking through conversation")}>
        <div class="na-story-heading"><h2>{t(mode === "register" ? "A new beginning." : "Your space.")}<br/><span>{t(mode === "register" ? "One hello away." : "Your conversation.")}</span></h2></div>
        <div class="na-artwork" aria-hidden="true"><img class="na-artwork-image" src="styles/images/nexa-conversation-portal.png" width="1122" height="1402" alt=""/></div>
      </section>
      <section class="na-form-panel" aria-labelledby="na-title"><div class="na-form-wrap">
        <h1 id="na-title" tabIndex={-1}>{t(mode === "login" ? "Welcome back" : "Create your account")}</h1>
        {mode === "login" && <p class="na-form-intro">{t("Log in to continue your conversation with Nexa.")}</p>}
        <form class="na-form" aria-busy={submitting} onSubmit={submit} noValidate onKeyDown={event => { if (event.key === "Enter" && (event.target as HTMLElement).tagName === "INPUT") { event.preventDefault(); void submit(); } }}>
          <oj-form-layout labelEdge="top" maxColumns={1}>
          {mode === "register" && <oj-input-text labelHint={t("Full name")} labelEdge="provided" userAssistanceDensity="compact" autocomplete="name" required disabled={submitting} value={fullName} length={{max:160}} onrawValueChanged={event => setFullName(event.detail.value || "")} placeholder={t("Your full name")}/>}
          <oj-input-text labelHint={t("Email address")} labelEdge="provided" userAssistanceDensity="compact" autocomplete="email" virtualKeyboard="email" required disabled={submitting} value={email} length={{max:254}} onrawValueChanged={event => setEmail(event.detail.value || "")} placeholder="you@example.com"/>
          {mode === "register" && <oj-input-text labelHint={t("Phone number (optional)")} labelEdge="provided" userAssistanceDensity="compact" autocomplete="tel" virtualKeyboard="tel" disabled={submitting} value={phoneNumber} length={{max:32}} onrawValueChanged={event => setPhoneNumber(event.detail.value || "")} placeholder="+91 98765 43210"/>}
          <div class="na-password-control">
            <label class="na-password-label" for="na-password">{t("Password")}</label>
            {showPassword ? <oj-input-text id="na-password" aria-label={t("Password")} labelEdge="none" userAssistanceDensity="compact" autocomplete={mode === "login" ? "current-password" : "new-password"} required disabled={submitting} value={password} length={{max:72}} onrawValueChanged={event => setPassword(event.detail.value || "")}/> : <oj-input-password id="na-password" aria-label={t("Password")} labelEdge="none" userAssistanceDensity="compact" autocomplete={mode === "login" ? "current-password" : "new-password"} required disabled={submitting} value={password} onrawValueChanged={event => setPassword(event.detail.value || "")}/>}
            <button type="button" class="na-password-eye" aria-label={t(showPassword ? "Hide password" : "Show password")} disabled={submitting} onClick={() => setShowPassword(value => !value)}><PasswordVisibilityIcon visible={showPassword}/></button>
          </div>
          </oj-form-layout>
          {mode === "register" && <p class="na-password-hint">{t("Use 8–72 characters. Include a capital letter (A), a small letter (a), a number (1) and a symbol (!).")}</p>}
          {error && <p class="na-notice" role="alert">{t(error)}</p>}
          <Action primary className="experience-auth-submit" disabled={submitting} onAction={() => void submit()} label={t(submitting ? (mode === "login" ? "Signing you in…" : "Creating your account…") : mode === "login" ? "Log in" : "Create account")}/>
        </form>
        <p class="experience-auth-switch">{t(mode === "login" ? "New to Nexa?" : "Already have an account?")} <Action disabled={submitting} label={t(mode === "login" ? "Create an account" : "Log in")} onAction={() => onSwitch(mode === "login" ? "register" : "login")}/></p>
      </div></section>
    </div>
  </main>;
}
