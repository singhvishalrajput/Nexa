import { h } from "preact";

export function LandingFooter() {
  return <footer class="nexa-footer" id="pricing">
    <div class="nexa-footer__main">
      <div class="nexa-footer__brand">
        <a href="#top" aria-label="Nexa home">Nexa</a>
        <p>Banking that moves<br />at the speed of a conversation.</p>
      </div>

      <div class="nexa-footer__links">
        <div>
          <span>Explore</span>
          <nav aria-label="Explore Nexa">
            <a href="#platform">Platform</a>
            <a href="#intelligence">Intelligence</a>
            <a href="#security">Security</a>
          </nav>
        </div>
        <div>
          <span>Follow</span>
          <nav aria-label="Nexa social links">
            <a href="#linkedin">LinkedIn <b aria-hidden="true">↗</b></a>
            <a href="#twitter">X / Twitter <b aria-hidden="true">↗</b></a>
          </nav>
        </div>
        <div class="nexa-footer__legal">
          <span>Legal</span>
          <nav aria-label="Legal information">
            <a href="#privacy">Privacy</a>
            <a href="#terms">Terms</a>
          </nav>
        </div>
      </div>
    </div>
  </footer>;
}
