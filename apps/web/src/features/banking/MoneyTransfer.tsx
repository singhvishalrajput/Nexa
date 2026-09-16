import { t } from "../../services/locale";
import { useEffect, useRef, useState } from "preact/hooks";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { ApiRequestError } from "../../services/auth";
import { formatMoney, moneyInMinorUnits } from "../../services/banking-content";
import { bankApi } from "./api";
import { moneyTransfers, TransferReceipt } from "./money-transfers";
import { validAmount } from "./utils";
import { Detail, PageHeading, Panel, State, useLoad } from "./ui";

export function MoneyTransfer({token, userId}: {token: string; userId: string}) {
  const storageKey = "nexa-transfer-review:" + userId;
  const initialId = useRef((() => { try { return window.sessionStorage.getItem(storageKey); } catch { return null; } })());
  const accounts = useLoad(() => bankApi.accounts(token), [token]);
  const [source, setSource] = useState("");
  const [own, setOwn] = useState(false);
  const [destination, setDestination] = useState("");
  const [amount, setAmount] = useState("");
  const [receipt, setReceipt] = useState<TransferReceipt>();
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(!!initialId.current);
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  const title = useRef<HTMLDivElement>(null);
  const active = (accounts.data || []).filter(a => a.status === "ACTIVE" && a.currencyCode === "INR");
  const from = active.find(a => a.id === source);
  const value = moneyInMinorUnits(amount);
  const balance = from ? moneyInMinorUnits(from.availableBalance) : null;
  const tooMuch = value !== null && balance !== null && value > balance;
  const valid = !!from && validAmount(amount) && !tooMuch && (own
    ? active.some(a => a.id === destination && a.id !== source)
    : /^[0-9]{6,30}$/.test(destination));
  useNavigationGuard(!receipt && !!(destination || amount), busy);
  useEffect(() => { if (!source && active.length) setSource(active[0].id); }, [accounts.data]);
  useEffect(() => { title.current?.querySelector<HTMLElement>("h2")?.focus(); }, [receipt?.status, receipt?.id]);
  useEffect(() => { if (initialId.current) void restore(); }, []);
  function remember(id: string | null) {
    try { if (id) window.sessionStorage.setItem(storageKey, id); else window.sessionStorage.removeItem(storageKey); } catch { /* Server receipt remains authoritative. */ }
  }
  async function restore() {
    if (lock.current || !initialId.current) return;
    lock.current = true; setBusy(true); setError("");
    try { const result = await moneyTransfers.status(token, initialId.current); setReceipt(result); setUncertain(result.status === "READY"); setRestoring(false); }
    catch (cause) {
      if (cause instanceof ApiRequestError && cause.status === 404) { remember(null); initialId.current = null; setRestoring(false); }
      else setError("We couldn’t check your previous transfer. Try again before starting another one.");
    } finally { lock.current = false; setBusy(false); }
  }
  async function review(event: Event) {
    event.preventDefault(); if (lock.current || !valid) return;
    lock.current = true; setBusy(true); setError("");
    try {
      const result = await moneyTransfers.prepare(token, {sourceAccountId: source, amount,
        ...(own ? {destinationAccountId: destination} : {destinationAccountNumber: destination})});
      remember(result.id); setReceipt(result);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "We couldn’t check these details. Please try again."); }
    finally { lock.current = false; setBusy(false); }
  }
  async function send() {
    if (lock.current || !receipt || receipt.status !== "READY") return;
    lock.current = true; setBusy(true); setError("");
    try { const result = await moneyTransfers.confirm(token, receipt.id); setReceipt(result); setUncertain(false); accounts.reload(); }
    catch (cause) {
      const unknown = !(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500;
      setUncertain(unknown);
      setError(unknown ? "We couldn’t confirm the result. Check this transfer’s status. Do not start another transfer for the same payment." : cause.message);
    } finally { lock.current = false; setBusy(false); }
  }
  async function checkStatus() {
    if (lock.current || !receipt) return;
    lock.current = true; setBusy(true); setError("");
    try {
      const result = await moneyTransfers.status(token, receipt.id); setReceipt(result);
      // Keep editing blocked after an uncertain submission. A retry uses the same durable ID.
      if (result.status !== "READY") setUncertain(false);
      else setError("No completed transfer is recorded yet. Use Send this transfer again to safely check and finish the same request.");
      if (result.status === "COMPLETED") accounts.reload();
    } catch { setError("We still couldn’t check the result. Keep this page open and try Check transfer status again."); }
    finally { lock.current = false; setBusy(false); }
  }
  function reset() { remember(null); setReceipt(undefined); setUncertain(false); setError(""); setAmount(""); setDestination(""); }
  return <><PageHeading title={t("Send money")} description={t("Transfer Indian rupees to another Nexa account.")}/>
    {restoring ? <Panel title={t("Checking your previous transfer")}><div class="bank-form" aria-busy={busy}><p>{t("We’ll check the saved request before you send money again.")}</p>{error && <p role="alert" class="bank-error">{error}</p>}<button class="bank-button" disabled={busy} onClick={restore}>{busy ? "Checking transfer…" : "Check previous transfer"}</button></div></Panel>
    : receipt ? <div ref={title} class="bank-narrow"><Panel><div class="bank-form" aria-busy={busy}>
      <h2 tabIndex={-1}>{receipt.status === "COMPLETED" ? t("✓ Money sent") : receipt.status === "EXPIRED" ? t("This review has expired") : t("Check before you send")}</h2>
      <dl><Detail label={t("From")}>{receipt.sourceName} · {receipt.sourceMasked}</Detail><Detail label={t("Recipient")}>{receipt.recipientName}</Detail><Detail label={t("To Nexa account")}>{receipt.destinationMasked}</Detail><Detail label={t("Amount")}>{formatMoney(receipt.amount, receipt.currencyCode)}</Detail>{receipt.reference && <Detail label={t("Reference")}>{receipt.reference}</Detail>}</dl>
      {receipt.status === "COMPLETED" ? <><p role="status">{t("Your transfer is complete. The money has been added to the recipient’s Nexa account.")}</p><a class="bank-button" href={"#/transactions/" + encodeURIComponent(receipt.reference!)}>{t("View transaction")}</a><button class="bank-button secondary" onClick={reset}>{t("Make another transfer")}</button></>
      : receipt.status === "EXPIRED" ? <><p>{t("No money was sent by this request. Review the details again to create a new transfer.")}</p><button class="bank-button" onClick={reset}>{t("Start a new review")}</button></>
      : <><p>{t("Check the recipient and amount carefully. Sending moves money immediately. This review is valid for 5 minutes.")}</p>{error && <p role="alert" class="bank-error">{error}</p>}
        {uncertain && <button class="bank-button secondary" disabled={busy} onClick={checkStatus}>{t("Check transfer status")}</button>}
        <button class="bank-button" disabled={busy} onClick={send}>{busy ? t("Please wait…") : uncertain ? "Send this transfer again" : "Send " + formatMoney(receipt.amount, receipt.currencyCode)}</button>
        {!uncertain && <button class="bank-button secondary" disabled={busy} onClick={() => { remember(null); setReceipt(undefined); setError(""); }}>{t("Edit details")}</button>}</>}
    </div></Panel></div>
    : <div class="bank-narrow"><Panel title={t("Transfer details")}><State loading={accounts.loading} error={accounts.error} retry={accounts.reload} empty={!accounts.loading && !accounts.error && !active.length ? "You need an active INR account to send money" : undefined}>
      <form class="bank-form" onSubmit={review} aria-busy={busy}><fieldset disabled={busy} class="bank-transfer-fields">
        <label>{t("From account")}<select required value={source} onChange={e => { setSource(e.currentTarget.value); if (own) setDestination(""); }}><option value="">{t("Choose an account")}</option>{active.map(a => <option key={a.id} value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label>
        {from && <p>{t("Available:")}<strong>{formatMoney(from.availableBalance, "INR")}</strong></p>}
        <label>{t("Who are you sending to?")}<select value={own ? "own" : "other"} onChange={e => { setOwn(e.currentTarget.value === "own"); setDestination(""); }}><option value="other">{t("Another Nexa account")}</option><option value="own">{t("One of my Nexa accounts")}</option></select></label>
        {own ? <label>{t("To account")}<select required value={destination} onChange={e => setDestination(e.currentTarget.value)}><option value="">{t("Choose another account")}</option>{active.filter(a => a.id !== source).map(a => <option key={a.id} value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select>{active.length < 2 && <small>{t("You need two active INR accounts for this option.")}</small>}</label>
        : <label>{t("Recipient’s Nexa account number")}<input required inputMode="numeric" autocomplete="off" pattern="[0-9]{6,30}" maxLength={30} value={destination} onInput={e => setDestination(e.currentTarget.value.replace(/\s/g, ""))} aria-describedby="recipient-help"/><small id="recipient-help">{t("Ask the recipient for their full account number. You’ll see their name before sending.")}</small></label>}
        <label>{t("Amount (₹)")}<input required inputMode="decimal" value={amount} maxLength={16} placeholder={t("For example, 500")} aria-invalid={tooMuch || (!!amount && !validAmount(amount))} aria-describedby="transfer-amount-help" onInput={e => setAmount(e.currentTarget.value)}/><small id="transfer-amount-help">{tooMuch ? t("This is more than your available balance.") : t("Enter an amount above ₹0, with up to two decimal places.")}</small></label>
        <p>{t("Transfers are currently available between Nexa accounts. Other-bank and UPI transfers are not supported.")}</p>
        {error && <p role="alert" class="bank-error">{error}</p>}<button class="bank-button" disabled={busy || !valid}>{busy ? t("Checking recipient…") : t("Review transfer")}</button>
      </fieldset></form>
    </State></Panel><a href="#/payments">{t("Review bills and other payments →")}</a></div>}
  </>;
}
