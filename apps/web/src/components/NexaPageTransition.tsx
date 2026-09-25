export function NexaPageTransition() {
  return <div class="nexa-transition nexa-route-transition" aria-hidden="true">
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
    </div>
  </div>;
}
