import { useEffect, useRef, useState } from "preact/hooks";
import { AuthSession, login, register } from "../../services/auth";

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
      setError(requestError instanceof Error ? requestError.message : "Authentication failed. Please try again.");
    } finally {
      submissionLock.current = false;
      setSubmitting(false);
    }
  };

  return (
    <div class="nexa-auth-page">
      <header class="nexa-auth-header">
        <button class="nexa-auth-logo" type="button" disabled={submitting} onClick={onBack}>Nexa</button>
        {mode === "register" && <button class="nexa-auth-back" type="button" disabled={submitting} onClick={onBack}>Back to sign in</button>}
      </header>

      <main class="nexa-auth-main">
        <section class="nexa-auth-intro">
          <p>PERSONAL BANKING</p>
          <h1>{mode === "login" ? <>Welcome<br />back.</> : <>Start with<br />clarity.</>}</h1>
          <span>{mode === "login"
            ? "Start with a conversation. Your accounts, payments and everyday banking, together in one workspace."
            : "Create your customer profile, then enter your private Nexa workspace."}</span>
        </section>

        <section class="nexa-auth-panel" aria-labelledby="nexa-auth-title">
          <div class="nexa-auth-tabs" role="group" aria-label="Authentication options">
            <button class={mode === "login" ? "is-active" : ""} type="button" aria-pressed={mode === "login"} disabled={submitting} onClick={() => onSwitch("login")}>Log in</button>
            <button class={mode === "register" ? "is-active" : ""} type="button" aria-pressed={mode === "register"} disabled={submitting} onClick={() => onSwitch("register")}>Create account</button>
          </div>

          <form class="nexa-auth-form" onSubmit={submit}><fieldset disabled={submitting}>
            <div><p>{mode === "login" ? "Existing customer" : "New customer"}</p><h2 id="nexa-auth-title">{mode === "login" ? "Log in to Nexa" : "Create your account"}</h2></div>

            {mode === "register" && <label>Full name<input value={fullName} onInput={(event) => setFullName(event.currentTarget.value)} autocomplete="name" maxlength={160} required placeholder="Your full name" /></label>}
            <label>Email address<input ref={emailRef} type="email" value={email} onInput={(event) => setEmail(event.currentTarget.value)} autocomplete="email" maxlength={254} required placeholder="you@example.com" /></label>
            {mode === "register" && <label>Phone number <small>Optional</small><input type="tel" value={phoneNumber} onInput={(event) => setPhoneNumber(event.currentTarget.value)} autocomplete="tel" maxlength={32} placeholder="+91 98765 43210" /></label>}
            <label>Password<div class="nexa-password-field"><input aria-label="Password" type={showPassword ? "text" : "password"} value={password} onInput={(event) => setPassword(event.currentTarget.value)} autocomplete={mode === "login" ? "current-password" : "new-password"} minlength={8} maxlength={72} required placeholder="Enter your password" /><button type="button" aria-label={showPassword ? "Hide password" : "Show password"} aria-pressed={showPassword} onClick={() => setShowPassword((visible) => !visible)}>{showPassword ? "Hide" : "Show"}</button></div></label>

            {mode === "register" && <span class="nexa-password-help">Use 8–72 characters with uppercase, lowercase, a number, and a special character.</span>}
            {error && <div class="nexa-auth-error" role="alert">{error}</div>}

            <button class="nexa-auth-submit" type="submit" disabled={submitting}>{submitting ? "Please wait…" : mode === "login" ? "Sign in to Nexa" : "Create account"}<b aria-hidden="true">↗</b></button>
          </fieldset></form>
        </section>
      </main>
    </div>
  );
}
