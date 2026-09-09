import { h } from "preact";

type NavbarProps = {
  authenticated: boolean;
  onLogin: () => void;
  onRegister: () => void;
  onOpenChat: () => void;
  onLogout: () => void;
};

export function Navbar({ authenticated, onLogin, onRegister, onOpenChat, onLogout }: NavbarProps) {
  return (
    <header class="nexa-navbar">
      <a class="nexa-logo" href="#top">Nexa</a>

      <nav class="nexa-nav-links" aria-label="Main navigation">
        <a href="#platform">Platform</a>
        <a href="#intelligence">Intelligence</a>
        <a href="#security">Security</a>
      </nav>

      <div class="nexa-navbar-actions">
        {authenticated ? <>
          <button class="nexa-navbar-link-button" type="button" onClick={onLogout}>Sign out</button>
          <button class="nexa-primary-button" type="button" onClick={onOpenChat}>Open chat</button>
        </> : <>
          <button class="nexa-navbar-link-button" type="button" onClick={onLogin}>Log in</button>
          <button class="nexa-primary-button" type="button" onClick={onRegister}>Sign up</button>
        </>}
      </div>
    </header>
  );
}
