import { registerCustomElement } from "ojs/ojvcomponent";
import { Component, ComponentChildren } from "preact";
import { useCallback, useEffect, useState } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { BankingApp } from "../features/banking/BankingApp";

class AppRecovery extends Component<{children: ComponentChildren}, {failed: boolean}> {
  state = {failed: false};
  componentDidCatch() { document.getElementById("nexa-boot")?.remove(); }
  static getDerivedStateFromError() { return {failed: true}; }
  render() {
    if (!this.state.failed) return this.props.children;
    return <main class="bank-app bank-start"><div class="bank-state" role="alert">
      <h1>We couldn’t show this screen</h1>
      <p>Reload to try again. If you were making a payment, check your history before submitting it again.</p>
      <button class="bank-button" onClick={() => window.location.reload()}>Reload Nexa</button>
    </div></main>;
  }
}

export const App = registerCustomElement(
  "app-root",
  () => {
    const [ready, setReady] = useState(false);
    const [opening, setOpening] = useState(true);
    const onReady = useCallback(() => setReady(true), []);
    useEffect(() => {
      Context.getPageContext()
        .getBusyContext()
        .applicationBootstrapComplete();
    }, []);

    useEffect(() => {
      if (!ready) return;
      const boot = document.getElementById("nexa-boot");
      const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
      // Keep the reference logo sequence visible, but never dismiss a pending session.
      const remaining = reduced ? 0 : Math.max(0, 1700 - performance.now());
      let dismiss: number | undefined;
      const reveal = window.setTimeout(() => {
        boot?.classList.add("nexa-loading-ready");
        dismiss = window.setTimeout(() => {
          boot?.remove();
          setOpening(false);
        }, reduced ? 0 : 240);
      }, remaining);
      return () => { window.clearTimeout(reveal); window.clearTimeout(dismiss); };
    }, [ready]);

    return <AppRecovery><div class="nexa-page-content" inert={opening} aria-hidden={opening ? "true" : undefined}><BankingApp onReady={onReady}/></div></AppRecovery>;
  }
);
