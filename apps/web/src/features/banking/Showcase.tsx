import { useRef, useState } from "preact/hooks";
import { demoApi, DemoReceipt, DemoRequest } from "./demo-api";
import { Detail, Modal, Panel, State, useLoad } from "./ui";
import { formatMoney, humanize } from "../../services/banking-content";

const requestStatus = (status: string) => status === "SIMULATED" ? "Simulation — no money moved" : humanize(status);
const referenceLabel = (reference: string) => reference;

export function DemoConfirmation({token, receipt, onClose}: {token: string; receipt: DemoReceipt; onClose: () => void}) {
  const [current, setCurrent] = useState(receipt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const lock = useRef(false);
  async function act(cancel: boolean) {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError("");
    try { setCurrent(await (cancel ? demoApi.cancel(token, current.id) : demoApi.confirm(token, current.id))); }
    catch (e) { setError((e as Error).message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <Modal title={current.status === "COMPLETED" ? "Money sent" : current.status === "SIMULATED" ? "Simulation saved" : "Request · " + requestStatus(current.status)} locked={busy} onClose={onClose}><div class="bank-form">
    <dl><Detail label="Action">{humanize(current.operation)}</Detail><Detail label="Record">{current.targetId}</Detail>{current.amount && <Detail label="Amount">{formatMoney(current.amount, current.currencyCode)}</Detail>}<Detail label="Status">{requestStatus(current.status)}</Detail>{current.reference && <Detail label="Reference">{referenceLabel(current.reference)}</Detail>}</dl>
    {current.status === "SIMULATED" && <p role="status">Simulation recorded. No money moved.</p>}
    {current.status === "COMPLETED" && <p role="status">Money sent. Both account balances and the ledger have been updated.</p>}
    {current.status === "REVIEW" && <p>{current.simulated ? "This is a provider simulation. Confirming will not move money." : "Confirming sends money immediately to the reviewed Nexa payee."}</p>}
    {error && <p class="bank-error" role="alert">{error}</p>}
    {current.status === "REVIEW" ? <><small>Review expires {new Date(current.expiresAt).toLocaleTimeString()}.</small><div class="bank-form-actions"><button disabled={busy} onClick={() => act(true)}>Cancel</button><button class="bank-button" disabled={busy || Date.parse(current.expiresAt) <= Date.now()} onClick={() => act(false)}>{busy ? "Processing…" : "Confirm request"}</button></div></> : <button class="bank-button" onClick={onClose}>Done</button>}
  </div></Modal>;
}

export function DemoAction({token, request, label, onDone}: {token: string; request: DemoRequest; label: string; onDone?: () => void}) {
  const [receipt, setReceipt] = useState<DemoReceipt>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const lock = useRef(false);
  async function prepare() {
    if (lock.current) return;
    lock.current = true; setBusy(true); setError("");
    try { setReceipt(await demoApi.prepare(token, request)); }
    catch (e) { setError((e as Error).message); }
    finally { lock.current = false; setBusy(false); }
  }
  return <><button type="button" class="bank-button secondary" disabled={busy} onClick={prepare}>{busy ? "Preparing…" : label}</button>{error && <p role="alert">{error}</p>}{receipt && <DemoConfirmation token={token} receipt={receipt} onClose={() => {setReceipt(undefined); onDone?.();}}/>}</>;
}

export function DemoHistory({token}: {token: string}) {
  const data = useLoad(() => demoApi.history(token), [token]);
  const [selected, setSelected] = useState<DemoReceipt>();
  return <Panel className="bank-request-history" title="Request history" action={<button class="bank-button secondary" type="button" onClick={data.reload}>Refresh</button>}><State loading={data.loading} error={data.error} retry={data.reload} empty={!data.loading && !data.data?.length ? "No requests yet." : undefined}>{data.data?.map(r => <div class="bank-record" key={r.id}><div><strong>{humanize(r.operation)}</strong><small>{requestStatus(r.status)} · {referenceLabel(r.reference || r.id)}</small></div><button class="bank-button secondary" type="button" onClick={() => setSelected(r)}>View request</button></div>)}</State>{selected && <DemoConfirmation key={selected.id} token={token} receipt={selected} onClose={() => {setSelected(undefined); data.reload();}}/>}</Panel>;
}
