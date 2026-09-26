import { useEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError } from "../services/auth";
import { AccountApplication } from "../services/account-applications";
import { useNavigationGuard } from "./useNavigationGuard";

type Attempt = { label: string; key: string; sent: boolean; send: (key: string) => Promise<AccountApplication> };
export type ApplicationActionAvailability = boolean | ((actionLabel: string) => boolean);

/** Keep the exact action closure, payload/version and request key after an uncertain response. */
export function useApplicationAction(onResult: (application: AccountApplication) => void, availability: ApplicationActionAvailability = false) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const [label, setLabel] = useState("");
  const inFlight = useRef(false);
  const pending = useRef<Attempt | null>(null);
  const mounted = useRef(true);
  const latestAvailability = useRef<ApplicationActionAvailability>(availability);
  latestAvailability.current = availability;
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  // Changing page while the outcome is unknown would discard the safe retry key.
  useNavigationGuard(uncertain, busy || uncertain);

  function available(actionLabel: string): boolean {
    const current = latestAvailability.current;
    // A missing/malformed readiness response or a failed predicate must fail closed.
    try { return (typeof current === "function" ? current(actionLabel) : current) === true; }
    catch { return false; }
  }

  async function execute(attempt: Attempt) {
    if (!mounted.current || inFlight.current) return;
    if (!available(attempt.label)) {
      if (!attempt.sent && pending.current === attempt) pending.current = null;
      setError("This action is unavailable according to the bank's latest readiness check. Refresh availability before continuing.");
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError("");
    setLabel(attempt.label);
    try {
      attempt.sent = true;
      const result = await attempt.send(attempt.key);
      if (!mounted.current) return;
      pending.current = null;
      setUncertain(false);
      try { onResult(result); }
      catch { setError("The request completed. Reload the application to view its recorded result."); }
    } catch (cause) {
      if (!mounted.current) return;
      const unknown = !(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500;
      setUncertain(unknown);
      if (!unknown) pending.current = null;
      setError(unknown
        ? "The outcome could not be confirmed. Do not submit another request or collect/return cash again. Retry this same request to recover its recorded result."
        : cause instanceof Error ? cause.message : "The request could not be completed.");
    } finally {
      inFlight.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  async function run(actionLabel: string, send: (requestKey: string) => Promise<AccountApplication>) {
    if (!mounted.current || inFlight.current || pending.current) return;
    if (!available(actionLabel)) {
      setError("This action is unavailable according to the bank's latest readiness check. Refresh availability before continuing.");
      return;
    }
    if (typeof crypto === "undefined" || typeof crypto.randomUUID !== "function") {
      setError("Secure request identifiers are unavailable. Use a supported browser on HTTPS or localhost.");
      return;
    }
    const attempt = {label: actionLabel, key: crypto.randomUUID(), sent: false, send};
    pending.current = attempt;
    await execute(attempt);
  }

  async function retry() {
    if (pending.current) await execute(pending.current);
  }
  function clearError() { if (!inFlight.current && !pending.current) setError(""); }
  return {busy, error, uncertain, label, run, retry, clearError};
}
