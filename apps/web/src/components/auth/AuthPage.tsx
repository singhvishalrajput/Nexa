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

export function AuthPage({ mode, onBack, onSwitch, onAuthenticated }: AuthPageProps) {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [emailIssue, setEmailIssue] = useState("");
  const [passwordIssue, setPasswordIssue] = useState("");
  const submissionLock = useRef(false);
  const emailRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    setError("");
    emailRef.current?.focus();
  }, [mode]);

  const submit = async (event: Event) => {
    event.preventDefault();
    if (submissionLock.current) return;
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

  return (
    <div class="nexa-auth-page">
      <header class="nexa-auth-header"><LanguageSelect/>
        <button class="nexa-auth-logo" type="button" disabled={submitting} onClick={onBack}>Nexa</button>
        {mode === "register" && <button class="nexa-auth-back" type="button" disabled={submitting} onClick={onBack}>{t("Back to sign in")}</button>}
      </header>

      <main class="nexa-auth-main">
        <section class="nexa-auth-intro">
          <p>{t("PERSONAL BANKING")}</p>
          <h1>{mode === "login" ? <>{t("Welcome to Nexa")}</> : <>{t("Join Nexa")}</>}</h1>
          <span>{mode === "login"
            ? t("Check your balance, view payments or ask for help. You can type or speak.")
            : t("Enter your details to get started. You can open a bank account after signing up.")}</span>
        </section>

        <section class="nexa-auth-panel" aria-labelledby="nexa-auth-title">
          <div class="nexa-auth-tabs" role="group" aria-label={t("Authentication options")}>
            <button class={mode === "login" ? "is-active" : ""} type="button" aria-pressed={mode === "login"} disabled={submitting} onClick={() => onSwitch("login")}>{t("Log in")}</button>
            <button class={mode === "register" ? "is-active" : ""} type="button" aria-pressed={mode === "register"} disabled={submitting} onClick={() => onSwitch("register")}>{t("Create account")}</button>
          </div>

          <form class="nexa-auth-form" aria-busy={submitting} onSubmit={submit}><fieldset disabled={submitting}>
            <div><p>{mode === "login" ? t("Existing customer") : t("New customer")}</p><h2 id="nexa-auth-title">{mode === "login" ? t("Log in to Nexa") : t("Create your account")}</h2></div>

            {mode === "register" && <label>{t("Full name")}<input value={fullName} onInput={(event) => setFullName(event.currentTarget.value)} autocomplete="name" maxlength={160} required placeholder={t("Your full name")} /></label>}
            <label>{t("Email address")}<input ref={emailRef} type="email" aria-invalid={!!emailIssue} aria-describedby={emailIssue ? "email-issue" : undefined} onBlur={event => setEmailIssue(event.currentTarget.validity.valid ? "" : "Enter your email address, for example name@example.com.")} value={email} onInput={(event) => { setEmail(event.currentTarget.value); setEmailIssue(""); }} autocomplete="email" maxlength={254} required placeholder="you@example.com" />{emailIssue && <span id="email-issue" class="nexa-field-error">{t(emailIssue)}</span>}</label>
            {mode === "register" && <label>{t("Phone number")}<small>{t("Optional")}</small><input type="tel" value={phoneNumber} onInput={(event) => setPhoneNumber(event.currentTarget.value)} autocomplete="tel" maxlength={32} placeholder="+91 98765 43210" /></label>}
            <label for="auth-password">{t("Password")}</label><div class="nexa-password-field"><input id="auth-password" aria-invalid={!!passwordIssue} aria-describedby={passwordIssue ? "password-issue" : mode === "register" ? "password-help" : undefined} onBlur={event => setPasswordIssue(event.currentTarget.validity.valid ? "" : "Enter your password using 8–72 characters.")} aria-label={t("Password")} type={showPassword ? "text" : "password"} value={password} onInput={(event) => { setPassword(event.currentTarget.value); setPasswordIssue(""); }} autocomplete={mode === "login" ? "current-password" : "new-password"} minlength={8} maxlength={72} required placeholder={t("Enter your password")} /><button type="button" aria-label={showPassword ? t("Hide password") : t("Show password")} aria-pressed={showPassword} onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? t("Hide") : t("Show")}</button></div>

            {passwordIssue && <span id="password-issue" class="nexa-field-error">{t(passwordIssue)}</span>}
            {mode === "register" && <span id="password-help" class="nexa-password-help">{t("Use 8–72 characters. Include a capital letter (A), a small letter (a), a number (1) and a symbol (!).")}</span>}
            {error && <div class="nexa-auth-error" role="alert">{t(error)}</div>}

            <button class="nexa-auth-submit" type="submit" disabled={submitting}>{submitting ? (mode === "login" ? t("Signing you in…") : t("Creating your account…")) : mode === "login" ? t("Sign in to Nexa") : t("Create account")}<b aria-hidden="true">↗</b></button>
          </fieldset></form>
        </section>
      </main>
    </div>
  );
}
