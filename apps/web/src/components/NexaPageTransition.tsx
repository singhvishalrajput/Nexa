import { t } from "../services/locale";
import { useState } from "preact/hooks";

export function NexaPageTransition({ ai = false }: { ai?: boolean }) {
  const [stars] = useState(() => Array.from({ length: 12 }, () => ({
    left: `${5 + Math.random() * 90}%`,
    top: `${6 + Math.random() * 88}%`,
    fontSize: `${10 + Math.random() * 19}px`,
    animationDelay: `${Math.random() * 300}ms`,
    animationDuration: `${2000 + Math.random() * 450}ms`,
    "--star-opacity": .12 + Math.random() * .16
  })));
  return <div class={"nexa-transition nexa-route-transition" + (ai ? " nexa-transition-ai" : "")} aria-hidden="true">
    {ai && <div class="nexa-transition-ai-stars">{stars.map((style, index) => <span key={index} style={style}>✦</span>)}</div>}
    <div class="nexa-transition-lockup">
      <div class="nexa-transition-halo" />
      <svg class="nexa-transition-mark" viewBox="0 0 48 48">
        <g class="nexa-transition-turn">
          {[0, 72, 144, 216, 288].map((rotation, index) => <g key={rotation} transform={`rotate(${rotation} 24 24)`}>
            <g class="nexa-transition-facet" style={{animationDelay: `${index * 22}ms`}}>
              <path d="M21.5 17.5 17 7A20 20 0 0 1 29 4.6v11.8l-4.5 3.2Z" />
            </g>
          </g>)}
        </g>
      </svg>
      <span class="nexa-transition-wordmark">Nexa</span>
      {ai && <span class="nexa-transition-ai-label">{t("Powered By AI")}</span>}
    </div>
  </div>;
}
