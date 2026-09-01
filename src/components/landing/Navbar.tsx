import { h } from "preact";

type NavbarProps = { onGetStarted: () => void };

export function Navbar({ onGetStarted }: NavbarProps) {
  return (
    <header class="nexa-navbar">
      <a class="nexa-logo" href="#top">Nexa</a>

      <nav class="nexa-nav-links" aria-label="Main navigation">
        <a href="#platform">Platform</a>
        <a href="#intelligence">Intelligence</a>
        <a href="#security">Security</a>
      </nav>

      <button class="nexa-primary-button" type="button" onClick={onGetStarted}>
        Get Started
      </button>
    </header>
  );
}
