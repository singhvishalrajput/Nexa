import { registerCustomElement } from "ojs/ojvcomponent";
import { h } from "preact";
import { useEffect } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { BankingApp } from "../features/banking/BankingApp";

export const App = registerCustomElement(
  "app-root",
  () => {
    useEffect(() => {
      Context.getPageContext()
        .getBusyContext()
        .applicationBootstrapComplete();
    }, []);

    return <BankingApp />;
  }
);