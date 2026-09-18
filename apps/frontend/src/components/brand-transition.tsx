import { h } from "preact";
import { useEffect, useRef } from "preact/hooks";

type Props = { onCovered(): void; onComplete(): void };

export function BrandTransition(props: Props) {
  const callbacks = useRef(props);
  callbacks.current = props;
  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    const covered = window.setTimeout(() => callbacks.current.onCovered(), 40);
    const complete = window.setTimeout(
      () => callbacks.current.onComplete(),
      reduced.matches ? 240 : 1700,
    );
    return () => {
      window.clearTimeout(covered);
      window.clearTimeout(complete);
    };
  }, []);

  return (
    <div
      class="nexa-transition"
      role="status"
      aria-live="polite"
      aria-atomic="true"
    >
      <span class="nc-sr-only">Loading Nexa</span>
      <div class="nexa-transition-lockup" aria-hidden="true">
        <div class="nexa-transition-halo" />
        <svg class="nexa-transition-mark" viewBox="0 0 48 48" fill="none">
          <g class="nexa-transition-turn">
            {[0, 72, 144, 216, 288].map((angle, index) => (
              <g key={angle} transform={`rotate(${angle} 24 24)`}>
                <g
                  class="nexa-transition-facet"
                  style={{ animationDelay: `${index * 22}ms` }}
                >
                  <path
                    d="M21.5 17.5 17 7A20 20 0 0 1 29 4.6v11.8l-4.5 3.2Z"
                    fill="#ff6248"
                  />
                </g>
              </g>
            ))}
          </g>
        </svg>
        <span class="nexa-transition-wordmark">Nexa</span>
      </div>
    </div>
  );
}
