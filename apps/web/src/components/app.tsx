import { registerCustomElement } from "ojs/ojvcomponent";
import { Component, ComponentChildren } from "preact";
import { useEffect } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { BankingApp } from "../features/banking/BankingApp";

class AppRecovery extends Component<{children: ComponentChildren}, {failed: boolean}> {
  state = {failed: false};
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
    useEffect(() => {
      document.getElementById("nexa-boot")?.remove();
      Context.getPageContext()
        .getBusyContext()
        .applicationBootstrapComplete();
    }, []);

    return <AppRecovery><BankingApp /></AppRecovery>;
  }
);
