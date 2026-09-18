import { registerCustomElement } from "ojs/ojvcomponent";
import { h } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { GoalsSequence } from "./goals-sequence";
import { ChatWorkspace } from "./chat-workspace";
import { BrandTransition } from "./brand-transition";
import { AuthPage } from "./auth-page";

type Page = "landing" | "chat" | "login" | "register";
const readPage = (): Page => {
  const hash = window.location.hash;
  return hash === "#chat"
    ? "chat"
    : hash === "#login"
      ? "login"
      : hash === "#register"
        ? "register"
        : "landing";
};

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

function CustomCursor() {
  const cursor = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const pointer = window.matchMedia("(hover: hover) and (pointer: fine)");
    const hide = () => {
      document.documentElement.classList.remove("custom-cursor-active");
      cursor.current?.classList.remove("visible");
    };
    const move = (event: PointerEvent) => {
      if (
        !pointer.matches ||
        event.pointerType === "touch" ||
        !cursor.current
      ) {
        hide();
        return;
      }
      const target = (event.target as Element)?.closest(
        "a[href], button, [role='button']",
      );
      const interactive =
        !!target && !target.matches(":disabled, [aria-disabled='true']");
      cursor.current.style.transform = `translate3d(${event.clientX - (interactive ? 14 : 7)}px, ${event.clientY - 5}px, 0)`;
      cursor.current.classList.add("visible");
      cursor.current.classList.toggle("over-link", interactive);
      document.documentElement.classList.add("custom-cursor-active");
    };
    window.addEventListener("pointermove", move, { passive: true });
    document.documentElement.addEventListener("pointerleave", hide);
    window.addEventListener("blur", hide);
    pointer.addEventListener("change", hide);
    return () => {
      hide();
      window.removeEventListener("pointermove", move);
      document.documentElement.removeEventListener("pointerleave", hide);
      window.removeEventListener("blur", hide);
      pointer.removeEventListener("change", hide);
    };
  }, []);
  return (
    <div class="custom-cursor" ref={cursor} aria-hidden="true">
      <svg width="35" height="38" viewBox="0 0 35 38">
        <path
          class="cursor-arrow"
          d="M7 5.5 27.4 13.5c2.4.9 2.2 3.7-.3 4.4l-8.2 2.2-3.1 9c-.8 2.2-3.5 2.3-4.3 0L3.6 9.2C2.7 6.7 4.5 4.5 7 5.5Z"
          fill="#242424"
          stroke="#fff"
          stroke-width="2.2"
          stroke-linejoin="round"
        />
        <path
          class="cursor-hand"
          d="M11.5 19.5V7.2a2.5 2.5 0 0 1 5 0v7.1c1.6-1.3 4-.2 4 1.8v.8c1.7-1.1 4 .1 4 2v.6c1.8-.9 4 .3 4 2.3v3.4c0 2.7-.9 5.1-2.6 7.2a2.2 2.2 0 0 1-1.7.8h-8.4a2.6 2.6 0 0 1-2.2-1.2L5.8 21.4a2.4 2.4 0 0 1 3.6-3.1l2.1 1.2Z"
          fill="#242424"
          stroke="#fff"
          stroke-width="2.2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
    </div>
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

export const App = registerCustomElement("app-root", () => {
  const [page, setPage] = useState<Page>(readPage);
  const chatOpen = page === "chat";
  const [transition, setTransition] = useState<number | null>(0);
  const transitionId = useRef(0);
  const requestedPage = useRef<Page>(readPage());
  const [menuOpen, setMenuOpen] = useState(false);
  const [paused, setPaused] = useState(false);
  const [moment, setMoment] = useState(0);
  const menuButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    document.documentElement.classList.toggle(
      "nexa-chat-open",
      page !== "landing",
    );
    return () => document.documentElement.classList.remove("nexa-chat-open");
  }, [page]);
  useEffect(() => {
    const route = () => {
      const nextPage = readPage();
      if (nextPage !== requestedPage.current) {
        requestedPage.current = nextPage;
        setTransition(++transitionId.current);
      }
      setMenuOpen(false);
    };
    window.addEventListener("hashchange", route);
    return () => window.removeEventListener("hashchange", route);
  }, []);
  useEffect(() => {
    Context.getPageContext().getBusyContext().applicationBootstrapComplete();
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const updateMotion = () => setPaused(media.matches);
    updateMotion();
    media.addEventListener("change", updateMotion);
    const observer = new IntersectionObserver(
      (entries) =>
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("revealed");
            observer.unobserve(entry.target);
          }
        }),
      { threshold: 0.12 },
    );
    document.querySelectorAll("[data-reveal]").forEach((element) => {
      element.classList.add("will-reveal");
      observer.observe(element);
    });
    return () => {
      observer.disconnect();
      media.removeEventListener("change", updateMotion);
    };
  }, []);
  useEffect(() => {
    if (paused || page !== "landing") return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setMoment((index) => (index + 1) % moments.length);
    }, 5200);
    return () => window.clearInterval(timer);
  }, [paused, page]);
  useEffect(() => {
    if (!menuOpen) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenuOpen(false);
        menuButton.current?.focus();
      }
    };
    document.addEventListener("keydown", close);
    return () => document.removeEventListener("keydown", close);
  }, [menuOpen]);
  const nav = (
    <>
      <a href="#the-nexa-way" onClick={() => setMenuOpen(false)}>
        The Nexa way <Icon name="down" size={14} />
      </a>
      <a href="#possibilities" onClick={() => setMenuOpen(false)}>
        Possibilities <Icon name="down" size={14} />
      </a>
      <a href="#whats-next" onClick={() => setMenuOpen(false)}>
        What’s next <Icon name="down" size={14} />
      </a>
    </>
  );
  const scene = moments[moment];
  return (
    <>
      <CustomCursor />
      {transition !== null && (
        <BrandTransition
          key={transition}
          onCovered={() => setPage(requestedPage.current)}
          onComplete={() => {
            setTransition(null);
            window.requestAnimationFrame(() => {
              if (requestedPage.current === "chat") {
                document
                  .getElementById("nc-message-input")
                  ?.focus({ preventScroll: true });
              } else if (
                requestedPage.current === "login" ||
                requestedPage.current === "register"
              ) {
                document
                  .getElementById("na-title")
                  ?.focus({ preventScroll: true });
              } else {
                document
                  .getElementById(window.location.hash.slice(1) || "home")
                  ?.scrollIntoView({ behavior: "auto" });
              }
            });
          }}
        />
      )}
      <div
        class="nexa-page-content"
        inert={transition !== null}
        aria-hidden={transition !== null ? "true" : undefined}
      >
        <div
          class="site-shell"
          style={page !== "landing" ? { display: "none" } : undefined}
          data-motion={paused ? "paused" : "playing"}
        >
          <a class="skip-link" href="#main">
            Skip to content
          </a>
          <header class="site-header">
            <a href="#home" aria-label="Nexa home">
              <Logo />
            </a>
            <nav class="desktop-nav" aria-label="Main navigation">
              {nav}
            </nav>
            <div class="nav-actions">
              <a class="nav-story" href="#login">
                Log in
              </a>
              <ArrowLink href="#chat">Explore Nexa</ArrowLink>
              <button
                ref={menuButton}
                class="menu-toggle"
                aria-label={menuOpen ? "Close navigation" : "Open navigation"}
                aria-expanded={menuOpen}
                aria-controls="mobile-navigation"
                onClick={() => setMenuOpen(!menuOpen)}
              >
                <Icon name={menuOpen ? "close" : "menu"} />
              </button>
            </div>
            {menuOpen && (
              <nav
                id="mobile-navigation"
                class="mobile-nav"
                aria-label="Mobile navigation"
              >
                {nav}
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
                  <ArrowLink href="#chat">Meet Nexa</ArrowLink>
                  <span class="hero-small">
                    A little less banking. <strong>A lot more living.</strong>
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
                      <button
                        key={item.label}
                        aria-label={`Show preview: ${item.label}`}
                        aria-pressed={moment === i}
                        onClick={() => {
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
              <button
                class="motion-control"
                aria-pressed={paused}
                onClick={() => setPaused(!paused)}
              >
                <Icon name={paused ? "play" : "pause"} size={12} />
                {paused ? "Play animations" : "Pause animations"}
              </button>
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
              <a class="closing-try" href="#chat">
                Try Nexa{" "}
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
              <span>A concept experience.</span>
              <span>
                <Icon name="globe" size={14} /> English · India
              </span>
            </div>
          </footer>
        </div>
        {chatOpen && <ChatWorkspace />}
        {(page === "login" || page === "register") && (
          <AuthPage key={page} mode={page} />
        )}
      </div>
    </>
  );
});
