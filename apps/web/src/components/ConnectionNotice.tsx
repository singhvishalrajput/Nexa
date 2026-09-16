import { useEffect, useState } from "preact/hooks";

/** Connectivity is advisory: never replay a payment when the network returns. */
export function ConnectionNotice() {
  const [offline, setOffline] = useState(() => !navigator.onLine);
  useEffect(() => {
    const update = () => setOffline(!navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  return offline ? <div class="nexa-connection-notice" role="status"><strong>⚠ You are offline.</strong> Check your internet connection. Keep this page open to keep your entries. If you just sent money, check your history before trying again.</div> : null;
}
