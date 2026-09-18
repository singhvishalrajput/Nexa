import { h } from "preact";
import { useState } from "preact/hooks";

type Mode = "login" | "register";
type Fields = { fullName: string; email: string; password: string };
type Errors = Partial<Record<keyof Fields, string>>;

function AuthIcon({ name }: { name: string }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      {name === "arrow" ? (
        <path d="M5 12h14m-5-5 5 5-5 5" />
      ) : name === "back" ? (
        <path d="M19 12H5m5-5-5 5 5 5" />
      ) : name === "email" ? (
        <>
          <rect x="3" y="5" width="18" height="14" rx="3" />
          <path d="m3 7 9 6 9-6" />
        </>
      ) : name === "person" ? (
        <>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
        </>
      ) : name === "lock" ? (
        <>
          <rect x="5" y="10" width="14" height="11" rx="3" />
          <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
        </>
      ) : (
        <>
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
          <circle cx="12" cy="12" r="3" />
          {name === "hide" && <path d="m3 3 18 18" />}
        </>
      )}
    </svg>
  );
}

function ConversationArtwork() {
  return (
    <div class="na-artwork" aria-hidden="true">
      <img
        class="na-artwork-image"
        src="styles/images/nexa-conversation-portal.png"
        alt=""
        width="1122"
        height="1402"
        decoding="async"
      />
    </div>
  );
}

export function AuthPage({ mode }: { mode: Mode }) {
  const register = mode === "register";
  const [fields, setFields] = useState<Fields>({
    fullName: "",
    email: "",
    password: "",
  });
  const [errors, setErrors] = useState<Errors>({});
  const [visible, setVisible] = useState(false);
  const [recovery, setRecovery] = useState(false);
  const [notice, setNotice] = useState("");
  const update = (name: keyof Fields, value: string) => {
    setFields((previous) => ({ ...previous, [name]: value }));
    setErrors((previous) => ({ ...previous, [name]: undefined }));
    setNotice("");
  };
  const submit = (event: Event) => {
    event.preventDefault();
    const next: Errors = {};
    if (register && !fields.fullName.trim())
      next.fullName = "Enter your full name.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(fields.email.trim()))
      next.email = "Enter a valid email address.";
    if (!recovery && !fields.password) next.password = "Enter your password.";
    else if (register && fields.password.length < 8)
      next.password = "Use at least 8 characters.";
    setErrors(next);
    const first = (Object.keys(next) as (keyof Fields)[])[0];
    if (first) {
      document.getElementById(`na-${first}`)?.focus();
      return;
    }
    // This frontend never persists, logs, or pretends to authenticate credentials.
    setNotice(
      recovery
        ? "Password reset isn’t available in this preview yet. You can still explore Nexa without signing in."
        : register
          ? "Account creation isn’t available in this preview yet. You can still explore Nexa without an account."
          : "Sign-in isn’t available in this preview yet. You can still explore Nexa without an account.",
    );
    setFields((previous) => ({ ...previous, password: "" }));
  };
  const passwordHint = !fields.password.length
    ? "Use 8 or more characters."
    : fields.password.length < 8
      ? "A few more characters to go."
      : "Length requirement met.";
  return (
    <main class="na-shell">
      <header class="na-header">
        <a class="na-brand" href="#home" aria-label="Nexa home">
          <img src="styles/images/nexa.svg" alt="" width="30" height="30" />
          <span>Nexa</span>
        </a>
        <a class="na-home" href="#home">
          <AuthIcon name="back" />
          <span>Back to home</span>
        </a>
      </header>
      <div class="na-layout">
        <section class="na-story" aria-label="Banking through conversation">
          <div class="na-story-heading">
            <h2>
              {register ? "A new beginning." : "Your space."}
              <br />
              <span>{register ? "One hello away." : "Your conversation."}</span>
            </h2>
          </div>
          <ConversationArtwork />
        </section>
        <section class="na-form-panel" aria-labelledby="na-title">
          <div class="na-form-wrap">
            <h1 id="na-title" tabIndex={-1}>
              {recovery
                ? "Reset your password"
                : register
                  ? "Create your account"
                  : "Welcome back"}
            </h1>
            <p class="na-form-intro">
              {recovery
                ? "Enter your email to request a reset link."
                : register
                  ? "Start with the basics. Add your profile details later."
                  : "Log in to continue your conversation with Nexa."}
            </p>
            <form class="na-form" onSubmit={submit} noValidate>
              {register && (
                <div class="na-field">
                  <label for="na-fullName">Full name</label>
                  <div
                    class={`na-input-wrap ${errors.fullName ? "has-error" : ""}`}
                  >
                    <AuthIcon name="person" />
                    <input
                      id="na-fullName"
                      name="fullName"
                      autoComplete="name"
                      placeholder="Your full name"
                      value={fields.fullName}
                      maxLength={100}
                      required
                      aria-invalid={!!errors.fullName}
                      aria-describedby={
                        errors.fullName ? "na-fullName-error" : undefined
                      }
                      onInput={(event) =>
                        update("fullName", event.currentTarget.value)
                      }
                    />
                  </div>
                  {errors.fullName && (
                    <p class="na-error" id="na-fullName-error">
                      {errors.fullName}
                    </p>
                  )}
                </div>
              )}
              <div class="na-field">
                <label for="na-email">Email address</label>
                <div class={`na-input-wrap ${errors.email ? "has-error" : ""}`}>
                  <AuthIcon name="email" />
                  <input
                    id="na-email"
                    name="email"
                    type="email"
                    autoComplete="email"
                    inputMode="email"
                    placeholder="you@example.com"
                    value={fields.email}
                    maxLength={254}
                    required
                    aria-invalid={!!errors.email}
                    aria-describedby={
                      errors.email ? "na-email-error" : undefined
                    }
                    onInput={(event) =>
                      update("email", event.currentTarget.value)
                    }
                  />
                </div>
                {errors.email && (
                  <p class="na-error" id="na-email-error">
                    {errors.email}
                  </p>
                )}
              </div>
              {!recovery && (
                <div class="na-field">
                  <div class="na-label-row">
                    <label for="na-password">Password</label>
                    {!register && (
                      <button
                        type="button"
                        class="na-text-button"
                        onClick={() => {
                          setRecovery(true);
                          setErrors({});
                          setNotice("");
                          setFields((previous) => ({
                            ...previous,
                            password: "",
                          }));
                        }}
                      >
                        Forgot password?
                      </button>
                    )}
                  </div>
                  <div
                    class={`na-input-wrap ${errors.password ? "has-error" : ""}`}
                  >
                    <AuthIcon name="lock" />
                    <input
                      id="na-password"
                      name="password"
                      type={visible ? "text" : "password"}
                      autoComplete={
                        register ? "new-password" : "current-password"
                      }
                      placeholder={
                        register ? "Create a password" : "Enter your password"
                      }
                      value={fields.password}
                      minLength={register ? 8 : undefined}
                      maxLength={128}
                      required
                      aria-invalid={!!errors.password}
                      aria-describedby={
                        errors.password
                          ? "na-password-error"
                          : register
                            ? "na-password-hint"
                            : undefined
                      }
                      onInput={(event) =>
                        update("password", event.currentTarget.value)
                      }
                    />
                    <button
                      type="button"
                      class="na-eye"
                      aria-label={visible ? "Hide password" : "Show password"}
                      aria-pressed={visible}
                      onClick={() => setVisible(!visible)}
                    >
                      <AuthIcon name={visible ? "hide" : "eye"} />
                    </button>
                  </div>
                  {errors.password ? (
                    <p class="na-error" id="na-password-error">
                      {errors.password}
                    </p>
                  ) : (
                    register && (
                      <div class="na-password-hint" id="na-password-hint">
                        <span
                          class={`na-length-meter ${fields.password.length >= 8 ? "is-ready" : ""}`}
                          aria-hidden="true"
                        >
                          <i
                            style={{
                              width: `${Math.min(100, (fields.password.length / 8) * 100)}%`,
                            }}
                          />
                        </span>
                        <span>{passwordHint}</span>
                      </div>
                    )
                  )}
                </div>
              )}
              <button class="na-submit" type="submit">
                <span>
                  {recovery
                    ? "Send reset link"
                    : register
                      ? "Create account"
                      : "Log in"}
                </span>
                <span class="na-submit-icon">
                  <AuthIcon name="arrow" />
                </span>
              </button>
              {notice && (
                <div class="na-notice" role="status">
                  <p>{notice}</p>
                  <a href="#chat">
                    Explore Nexa <AuthIcon name="arrow" />
                  </a>
                </div>
              )}
            </form>
            {recovery ? (
              <button
                class="na-return"
                onClick={() => {
                  setRecovery(false);
                  setErrors({});
                  setNotice("");
                }}
              >
                <AuthIcon name="back" />
                Back to log in
              </button>
            ) : (
              <p class="na-switch">
                {register ? "Already have an account?" : "New to Nexa?"}{" "}
                <a href={register ? "#login" : "#register"}>
                  {register ? "Log in" : "Create an account"}
                  <span>↗</span>
                </a>
              </p>
            )}
          </div>
          <div class="na-panel-footer">
            <a href="#chat">
              Explore Nexa without an account <span>↗</span>
            </a>
          </div>
        </section>
      </div>
    </main>
  );
}
