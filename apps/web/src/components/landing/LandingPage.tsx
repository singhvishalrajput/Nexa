import type { AuthSession } from "../../services/auth";
import { Action } from "../design/Action";
import { useEffect, useState } from "preact/hooks";
import "ojs/ojbutton";
import { GoalsSequence } from "./goals-sequence";
function Icon({ name = "arrow", size = 20 }: { name?: string; size?: number }) {
  const paths: Record<string, any> = {
    arrow: <path d="M5 12h14m-6-6 6 6-6 6" />,
    diagonal: <path d="M6 18 18 6M6 6h12v12" />,
    down: <path d="m8 10 4 4 4-4" />,
    mic: (
      <>
        <rect x="9" y="3" width="6" height="12" rx="3" />
        <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3" />
      </>
    ),
    check: <path d="m5 12 4 4L19 6" />,
    pause: <path d="M9 5v14M15 5v14" />,
    play: <path d="m8 5 11 7-11 7V5Z" />,
    menu: <path d="M4 8h16M4 16h16" />,
    close: <path d="m6 6 12 12M6 18 18 6" />,
    chat: <path d="M21 11a9 9 0 0 1-9 9H4l-2 2V11a9 9 0 0 1 19 0Z" />,
    shield: (
      <>
        <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    globe: (
      <>
        <circle cx="12" cy="12" r="9" />
        <ellipse cx="12" cy="12" rx="4" ry="9" />
        <path d="M3 12h18M5 7h14M5 17h14" />
      </>
    ),
    contactless: (
      <>
        <path d="M8 10a4 4 0 0 1 0 4M11 7a8 8 0 0 1 0 10M14 4a12 12 0 0 1 0 16" />
      </>
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.arrow}
    </svg>
  );
}

function Logo() {
  return (
    <span class="logo">
      <img
        class="logo-mark"
        src="styles/images/nexa.svg"
        width="38"
        height="38"
        alt=""
        aria-hidden="true"
      />
      <span>Nexa</span>
    </span>
  );
}
function Wave({ compact = false }: { compact?: boolean }) {
  return (
    <span class={`wave ${compact ? "compact" : ""}`} aria-hidden="true">
      {[12, 24, 16, 36, 46, 25, 52, 32, 19, 43, 29, 15, 37, 22, 10].map(
        (height, i) => (
          <i
            key={i}
            style={{ height: `${height}px`, animationDelay: `${i * -0.13}s` }}
          />
        ),
      )}
    </span>
  );
}
function ArrowLink({
  href,
  children,
  light = false,
}: {
  href: string;
  children: any;
  light?: boolean;
}) {
  return (
    <a class={`pill-link ${light ? "light" : ""}`} href={href}>
      <span class="arrow-circle">
        <Icon name="diagonal" size={19} />
      </span>
      {children}
    </a>
  );
}

const moments = [
  {
    request: "Send ₹500 to Alex.",
    label: "Payment confirmed",
    value: "₹500.00",
    detail: "You said it. You approved it.",
  },
  {
    request: "How’s my spending?",
    label: "Your month, made clear",
    value: "₹12,450",
    detail: "A little clarity. Just like that.",
  },
  {
    request: "Show my travel fund.",
    label: "Your next adventure",
    value: "64% there",
    detail: "Every little step adds up.",
  },
];


export function LandingPage({session, onSignOut, notice = ""}: {session: AuthSession | null; onSignOut: () => Promise<void>; notice?: string}) {
  const admin = session?.user.role === "ADMIN";
  const workspaceHref = admin ? "#/admin" : "#/assistant";
  const workspaceLabel = admin ? "Open administration" : "Continue conversation";
  const [signingOut, setSigningOut] = useState(false);
  async function signOut() {
    if (signingOut) return;
    setSigningOut(true);
    try { await onSignOut(); } finally { setSigningOut(false); }
  }
  const sessionLinks = session ? <>
    <a href={admin ? "#/admin/loans" : "#/accounts"}>{admin ? "Loan requests" : "My accounts"}</a>
    {!admin && <a href="#/settings">Profile &amp; settings</a>}
    <Action onAction={signOut} disabled={signingOut}>{signingOut ? "Signing out…" : "Sign out"}</Action>
  </> : <><a href="#/login">Log in</a><a href="#/register">Create an account</a></>;
  const [menuOpen, setMenuOpen] = useState(false);
  const [paused, setPaused] = useState(() => window.matchMedia("(prefers-reduced-motion: reduce)").matches);
  const [moment, setMoment] = useState(0);
  useEffect(() => {
    if (paused) return;
    const timer = window.setInterval(() => { if (!document.hidden) setMoment(index => (index + 1) % moments.length); }, 5200);
    return () => window.clearInterval(timer);
  }, [paused]);
  useEffect(() => {
    document.title = "Nexa · Your money. Your words. Your way.";
    const anchor = window.location.hash.slice(1);
    if (!anchor || anchor === "home") window.scrollTo(0, 0);
    else document.getElementById(anchor)?.scrollIntoView({behavior: "auto"});
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") setMenuOpen(false); };
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, []);
  const nav = <><a href="#the-nexa-way" onClick={() => setMenuOpen(false)}>The Nexa way <Icon name="down" size={14}/></a><a href="#possibilities" onClick={() => setMenuOpen(false)}>Possibilities <Icon name="down" size={14}/></a><a href="#whats-next" onClick={() => setMenuOpen(false)}>What’s next <Icon name="down" size={14}/></a></>;
  const scene = moments[moment];
  return (        <div
          class="site-shell"
          
          data-motion={paused ? "paused" : "playing"}
        >
          <a class="skip-link" href="#main">
            Skip to content
          </a>
          {notice && <p class="landing-session-notice" role="status">{notice}</p>}
          <header class="site-header">
            <a href="#home" aria-label="Nexa home">
              <Logo />
            </a>
            <nav class="desktop-nav" aria-label="Main navigation">
              {nav}
            </nav>
            <div class="nav-actions">
              <div class="landing-session-actions">{sessionLinks}</div>
              <ArrowLink href={workspaceHref}>{session ? (admin ? "Administration" : "Open banking") : "Explore Nexa"}</ArrowLink>
              <oj-button chroming="borderless"
                
                class="menu-toggle"
                aria-label={menuOpen ? "Close navigation" : "Open navigation"}
                aria-expanded={menuOpen}
                aria-controls="mobile-navigation"
                onojAction={() => setMenuOpen(!menuOpen)}
              >
                <Icon name={menuOpen ? "close" : "menu"} />
              </oj-button>
            </div>
            {menuOpen && (
              <nav
                id="mobile-navigation"
                class="mobile-nav"
                aria-label="Mobile navigation"
              >
                {nav}
                <div class="landing-mobile-session">{sessionLinks}</div>
              </nav>
            )}
          </header>
          <main id="main">
            <section class="hero" id="home" aria-labelledby="hero-title">
              <div class="hero-primary">
                <div class="hero-portrait">
                  <img
                    src="styles/images/nexa-voice-portrait.png"
                    alt="A woman smiling while speaking naturally to her smartphone"
                    fetchPriority="high"
                  />
                  <span class="portrait-grain" />
                </div>
                <svg width="0" height="0" class="clip-defs" aria-hidden="true">
                  <defs>
                    <clipPath
                      id="portrait-step"
                      clipPathUnits="objectBoundingBox"
                    >
                      <path d="M.24 0H.96Q1 0 1 .04V.96Q1 1 .96 1H.04Q0 1 0 .96V.36Q0 .32 .04 .32H.08Q.12 .32 .12 .28V.21Q.12 .17 .16 .17H.16Q.20 .17 .20 .13V.04Q.20 0 .24 0Z" />
                    </clipPath>
                  </defs>
                </svg>
                <div class="hero-title-block">
                  <h1
                    id="hero-title"
                    aria-label="Your money. Your words. Your way."
                  >
                    <span class="headline-lines" aria-hidden="true">
                      YOUR MONEY.
                      <br />
                      YOUR WORDS.
                      <br />
                      YOUR{" "}
                      <span class="headline-rotation">
                        {["WAY.", "VOICE.", "PACE.", "TERMS."].map(
                          (word, index) => (
                            <span
                              class="headline-word"
                              key={word}
                              style={{ animationDelay: `${index * 4 - 0.7}s` }}
                            >
                              {word}
                            </span>
                          ),
                        )}
                      </span>
                    </span>
                  </h1>
                </div>
                <div class="hero-copy">
                  <p>
                    Life moves fast.
                    <br />
                    Your bank should keep up.
                  </p>
                  <p>
                    Pay, save, and make sense of your money with a simple
                    conversation.
                  </p>
                  {session && <p class="landing-welcome">Welcome back{session.profile.fullName.trim() ? `, ${session.profile.fullName.trim().split(/\s+/)[0]}` : ""}.</p>}
                  <ArrowLink href={workspaceHref}>{session ? workspaceLabel : "Meet Nexa"}</ArrowLink>
                  <span class="hero-small">
                    {session ? <>You’re signed in. <strong>Your workspace is ready.</strong></> : <>A little less banking. <strong>A lot more living.</strong></>}
                  </span>
                </div>
                <div class="floating-status" key={`status-${moment}`}>
                  <span class="status-icon">
                    <Icon
                      name={
                        moment === 0
                          ? "check"
                          : moment === 1
                            ? "chat"
                            : "diagonal"
                      }
                      size={18}
                    />
                  </span>
                  <div>
                    <span>{scene.label}</span>
                    <strong>{scene.value}</strong>
                  </div>
                  <span class="status-spark">✳</span>
                </div>
                <div class="voice-command">
                  <span class="voice-icon">
                    <Icon name="mic" size={17} />
                  </span>
                  <div>
                    <small>YOU SAY IT. NEXA GETS IT.</small>
                    <span key={moment}>“{scene.request}”</span>
                  </div>
                  <Wave compact />
                </div>
                <div class="portrait-caption">
                  <span class="caption-mark">✳</span>
                  <span>
                    Less tapping.
                    <br />
                    <strong>More happening.</strong>
                  </span>
                  <div class="scene-dots" aria-label="Banking preview scenes">
                    {moments.map((item, i) => (
                      <oj-button chroming="borderless"
                        key={item.label}
                        aria-label={`Show preview: ${item.label}`}
                        aria-pressed={moment === i}
                        onojAction={() => {
                          setMoment(i);
                          setPaused(true);
                        }}
                      />
                    ))}
                  </div>
                </div>
              </div>
            </section>
            <div class="hero-bottom">
              <oj-button chroming="borderless"
                class="motion-control"
                aria-pressed={paused}
                onojAction={() => setPaused(!paused)}
              >
                <Icon name={paused ? "play" : "pause"} size={12} />
                {paused ? "Play animations" : "Pause animations"}
              </oj-button>
            </div>

            <section class="intro-section section-pad" id="the-nexa-way">
              <div class="section-heading" data-reveal>
                <h2>
                  Life doesn’t happen in menus.
                  <br />
                  Neither should <span>banking.</span>
                </h2>
              </div>
              <div class="ways-grid">
                <article class="way-card" data-reveal>
                  <div class="card-index">
                    <span>TYPE IT</span>
                    <Icon name="chat" />
                  </div>
                  <div class="type-art">
                    <span>Hey Nexa,</span>
                    <strong>
                      what’s my balance<span class="typing-cursor">?</span>
                    </strong>
                    <span class="art-send">
                      <Icon size={20} />
                    </span>
                  </div>
                  <h3>Your words are the shortcut.</h3>
                  <p>
                    No hunting through tabs. No learning the interface. Just ask
                    like you’d ask a friend.
                  </p>
                  <span class="card-footer">
                    Fewer taps. More possibility.
                    <Icon name="diagonal" size={17} />
                  </span>
                </article>
                <article class="way-card voice-card" data-reveal>
                  <div class="card-index">
                    <span>SAY IT</span>
                    <Icon name="mic" />
                  </div>
                  <div class="voice-art">
                    <Wave />
                    <span>“How’s my travel fund looking?”</span>
                  </div>
                  <h3>A voice that gets you.</h3>
                  <p>
                    Hands full? Mind busy? Speak naturally and let the
                    conversation take care of the rest.
                  </p>
                  <span class="card-footer">
                    As natural as saying hello.
                    <Icon name="diagonal" size={17} />
                  </span>
                </article>
                <article class="way-card" data-reveal>
                  <div class="card-index">
                    <span>SORTED</span>
                    <Icon name="check" />
                  </div>
                  <div class="done-art">
                    <span>
                      <Icon name="check" size={28} />
                    </span>
                    <div>
                      One conversation.<strong>Consider it done.</strong>
                    </div>
                    <i>✳</i>
                  </div>
                  <h3>From said to sorted.</h3>
                  <p>
                    Clear answers. Simple next steps. And a final say that
                    always belongs to you.
                  </p>
                  <span class="card-footer">
                    Always on your terms.
                    <Icon name="diagonal" size={17} />
                  </span>
                </article>
              </div>
            </section>

            <section
              class="possibilities-section section-pad"
              id="possibilities"
            >
              <div class="section-heading" data-reveal>
                <h2>
                  Big on possibilities.
                  <br />
                  <span>Light on effort.</span>
                </h2>
              </div>
              <div class="bento-grid">
                <article class="bento-card payment-card" data-reveal>
                  <span class="bento-icon">
                    <Icon name="diagonal" />
                  </span>
                  <h3>
                    Make their day.
                    <br />
                    In a few words.
                  </h3>
                  <p>
                    Send money without the runaround.
                    <br />
                    You say who and how much. You approve the rest.
                  </p>
                  <div class="payment-art">
                    <span class="person person-one">A</span>
                    <span class="person person-two">You</span>
                    <div class="payment-receipt">
                      <span class="receipt-check">
                        <Icon name="check" size={19} />
                      </span>
                      <small>Good things, sent.</small>
                      <strong>
                        ₹500<span>.00</span>
                      </strong>
                      <span>You → Alex</span>
                      <div class="receipt-divider" />
                      <small>One less thing on your list.</small>
                    </div>
                    <span class="payment-spark">✳</span>
                  </div>
                  <span class="feature-tag">“Send ₹500 to Alex.”</span>
                </article>
                <article class="bento-card insights-card" data-reveal>
                  <span class="bento-icon">
                    <Icon name="chat" />
                  </span>
                  <h3>
                    A little clarity.
                    <br />A lot of confidence.
                  </h3>
                  <p>
                    Understand where your money goes.
                    <br />
                    Without getting lost in the numbers.
                  </p>
                  <div class="spending-art">
                    <div>
                      <span>Your month, at a glance</span>
                      <strong>₹12,450</strong>
                      <small>spent this month</small>
                    </div>
                    <div
                      class="chart-bars"
                      aria-label="Illustrative weekly spending chart"
                    >
                      {[30, 52, 40, 72, 57, 95, 64].map((height, i) => (
                        <div key={i}>
                          <i
                            style={{
                              height: `${height}%`,
                              animationDelay: `${i * 0.15}s`,
                            }}
                          />
                          <span>{["M", "T", "W", "T", "F", "S", "S"][i]}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                  <span class="feature-tag">
                    “Where did my money go this month?”
                  </span>
                </article>
                <GoalsSequence paused={paused} />
              </div>
            </section>

            <section
              class="closing-section"
              id="whats-next"
              aria-labelledby="closing-title"
              data-reveal
            >
              <div class="closing-copy">
                <span class="closing-eyebrow">
                  YOUR NEXT CHAPTER STARTS WITH A HELLO.
                </span>
                <h2 id="closing-title">
                  Good things start
                  <br />
                  with a <em>conversation.</em>
                </h2>
                <p>Your money. Your voice. Your next move.</p>
              </div>
              <picture class="closing-portrait" aria-hidden="true">
                <source
                  media="(max-width: 600px)"
                  srcSet="styles/images/nexa-cta-portrait.png"
                />
                <img
                  src="styles/images/nexa-cta-portrait-desktop.png"
                  alt=""
                  loading="lazy"
                  decoding="async"
                />
              </picture>
              <a class="closing-try" href={workspaceHref}>
                {session ? workspaceLabel : "Try Nexa"}{" "}
                <span>
                  <Icon name="diagonal" size={19} />
                </span>
              </a>
            </section>
          </main>
          <footer class="site-footer">
            <div class="footer-top">
              <a href="#home" aria-label="Nexa home">
                <Logo />
              </a>
              <span>A more human way to money.</span>
              <a href="#home">
                Back to top <Icon name="diagonal" size={16} />
              </a>
            </div>
            <div class="footer-bottom">
              <span>
                © {new Date().getFullYear()} Nexa. Made for what’s next.
              </span>
              <span>Banking, in your words.</span>
              <span>
                <Icon name="globe" size={14} /> English · India
              </span>
            </div>
          </footer>
        </div>
);
}
