import { h } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";

const scenes = [
  {
    question: "How close am I to Japan?",
    title: "Japan, here I come.",
    kind: "travel",
  },
  {
    question: "What if I save ₹3,000 a month?",
    title: "Your goal. Within reach.",
    kind: "plan",
  },
  {
    question: "Move ₹3,000 to my travel fund.",
    title: "One step closer.",
    kind: "move",
  },
  {
    question: "How are all my goals doing?",
    title: "A little closer. All round.",
    kind: "overview",
  },
];

function Mark({ type = "arrow" }: { type?: string }) {
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
      {type === "check" ? (
        <path d="m5 12 4 4L19 6" />
      ) : (
        <path d="M6 18 18 6M6 6h12v12" />
      )}
    </svg>
  );
}

function Answer({ index }: { index: number }) {
  if (index === 0)
    return (
      <>
        <div class="dream-amount">
          <strong>
            <span>₹32,000</span>
          </strong>
          <span>of ₹50,000</span>
        </div>
        <div class="dream-segments" aria-label="64 percent saved">
          {Array.from({ length: 40 }, (_, i) => (
            <i key={i} class={i < 26 ? "filled" : ""} style={{ "--i": i }} />
          ))}
        </div>
        <div class="dream-foot">
          <span>64% there.</span>
          <span>Japan is getting closer.</span>
        </div>
      </>
    );
  if (index === 1)
    return (
      <>
        <div class="dream-plan-body">
          <div class="dream-amount">
            <strong>
              <span>6 months</span>
            </strong>
            <span>at ₹3,000 / month</span>
          </div>
          <div class="dream-steps" aria-hidden="true">
            {[25, 38, 51, 64, 77, 92].map((height, i) => (
              <i key={i} style={{ height: `${height}%`, "--i": i }} />
            ))}
          </div>
        </div>
        <div class="dream-foot">
          <span>₹32,000 today</span>
          <span>
            ₹50,000 projected <Mark />
          </span>
        </div>
      </>
    );
  if (index === 2)
    return (
      <>
        <div class="dream-move-body">
          <div class="dream-amount">
            <strong>
              <span>₹3,000</span>
            </strong>
            <span>Everyday → Travel fund</span>
          </div>
          <span class="dream-check">
            <Mark type="check" />
          </span>
        </div>
        <div class="dream-approval">
          <span class="dream-status-dot" />
          Ready for your approval
          <Mark />
        </div>
      </>
    );
  return (
    <div class="dream-goal-list">
      {[
        { name: "Japan", value: "₹32,000", percent: 64 },
        { name: "A place of my own", value: "₹85,000", percent: 42 },
        { name: "Just in case", value: "₹24,000", percent: 80 },
      ].map((goal, i) => (
        <div class="dream-goal-row" key={goal.name} style={{ "--i": i }}>
          <div>
            <span>{goal.name}</span>
            <strong>{goal.value}</strong>
          </div>
          <div class="dream-goal-track">
            <i style={{ width: `${goal.percent}%` }} />
          </div>
        </div>
      ))}
    </div>
  );
}

export function GoalsSequence({ paused }: { paused: boolean }) {
  const [active, setActive] = useState(0);
  const [visible, setVisible] = useState(false);
  const [pageVisible, setPageVisible] = useState(!document.hidden);
  const root = useRef<HTMLElement>(null);
  const stopped = paused || !visible || !pageVisible;
  const select = (index: number) => {
    if (index !== active) {
      setActive(index);
    }
  };
  useEffect(() => {
    const observer = new IntersectionObserver(
      ([entry]) => setVisible(entry.isIntersecting),
      { threshold: 0.25 },
    );
    if (root.current) observer.observe(root.current);
    const onVisibility = () => setPageVisible(!document.hidden);
    document.addEventListener("visibilitychange", onVisibility);
    return () => {
      observer.disconnect();
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, []);
  useEffect(() => {
    if (stopped) return;
    const timer = window.setTimeout(() => {
      setActive((active + 1) % scenes.length);
    }, 4200);
    return () => window.clearTimeout(timer);
  }, [active, stopped]);
  return (
    <article
      class="goals-story"
      id="saving-goals"
      ref={root}
      data-reveal
      data-stopped={stopped}
      aria-label="Explore saving goals with Nexa"
    >
      <div class="dream-copy">
        <h3>
          Turn “one day”
          <br />
          into <em>a little closer.</em>
        </h3>
        <div class="dream-question-stage" aria-live="off">
          <span class="dream-voice" aria-hidden="true">
            {[10, 18, 26, 16, 8].map((height, i) => (
              <i key={i} style={{ height, "--i": i }} />
            ))}
          </span>
          {scenes.map((scene, i) => (
            <blockquote
              key={scene.kind}
              class={`dream-question ${i === active ? "is-active" : ""}`}
              aria-hidden={i !== active}
            >
              “{scene.question}”
            </blockquote>
          ))}
        </div>
        <div class="dream-controls">
          <div
            class="dream-scenes"
            role="group"
            aria-label="Choose a savings conversation"
          >
            {scenes.map((scene, i) => (
              <button
                type="button"
                key={scene.kind}
                class={i === active ? "is-active" : ""}
                aria-label={`Show: ${scene.question}`}
                aria-pressed={i === active}
                onClick={() => select(i)}
              >
                <span>
                  <i key={`${active}-${stopped}`} />
                </span>
              </button>
            ))}
          </div>
        </div>
      </div>
      <div class="dream-visual">
        <svg
          class="dream-signal"
          viewBox="0 0 600 300"
          fill="none"
          aria-hidden="true"
        >
          <path
            key={active}
            d="M0 225H65C110 225 80 150 145 150H570"
            stroke="url(#dream-signal-color)"
            stroke-width="1.5"
            pathLength="1"
          />
          <defs>
            <linearGradient id="dream-signal-color">
              <stop stop-color="#ff6248" stop-opacity="0" />
              <stop offset=".45" stop-color="#ff967e" />
              <stop offset="1" stop-color="#ff6248" stop-opacity="0" />
            </linearGradient>
          </defs>
        </svg>
        <div class="dream-deck">
          {scenes.map((scene, i) => {
            const distance = (i - active + scenes.length) % scenes.length;
            const state =
              i === active
                ? "is-active"
                : distance === 3
                  ? "is-outgoing"
                  : distance === 1
                    ? "is-next"
                    : distance === 2
                      ? "is-next-two"
                      : "";
            return (
              <div
                key={scene.kind}
                class={`dream-answer dream-${scene.kind} ${state}`}
                aria-hidden={i !== active}
              >
                <div class="dream-card-top">
                  <h4>{scene.title}</h4>
                  <img
                    src="styles/images/nexa.svg"
                    width="24"
                    height="24"
                    alt=""
                  />
                </div>
                <Answer index={i} />
              </div>
            );
          })}
        </div>
      </div>
    </article>
  );
}
