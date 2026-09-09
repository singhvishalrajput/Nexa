import { registerCustomElement } from "ojs/ojvcomponent";
import { h } from "preact";
import { useEffect } from "preact/hooks";
import Context = require("ojs/ojcontext");
import { NexaLanding } from "./landing/NexaLanding";

export const App = registerCustomElement(
  "app-root",
  () => {
    useEffect(() => {
      Context.getPageContext()
        .getBusyContext()
        .applicationBootstrapComplete();
    }, []);

    return <NexaLanding />;
  }
);