import "ojs/ojbutton";
import "ojs/ojinputtext";
import "ojs/ojselectcombobox";
import "ojs/ojlabel";
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
  async function review(event?: Event) {
    event?.preventDefault(); if (lock.current || !valid) return;
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
  return <section class="bank-service-page bank-transfer-page"><PageHeading title={t("Send money")} description={t("Transfer Indian rupees to another Nexa account.")}/>
    {restoring ? <Panel title={t("Checking your previous transfer")}><div class="bank-form" aria-busy={busy}><p>{t("We’ll check the saved request before you send money again.")}</p>{error && <p role="alert" class="bank-error">{error}</p>}<oj-button chroming="callToAction" class="nexa-action transfer-action" disabled={busy} onojAction={restore}>{busy ? "Checking transfer…" : "Check previous transfer"}</oj-button></div></Panel>
    : receipt ? <div ref={title} class="bank-narrow transfer-workflow"><Panel><div class="bank-form" aria-busy={busy}>
      <h2 tabIndex={-1}>{receipt.status === "COMPLETED" ? t("✓ Money sent") : receipt.status === "EXPIRED" ? t("This review has expired") : t("Check before you send")}</h2>
      <dl><Detail label={t("From")}>{receipt.sourceName} · {receipt.sourceMasked}</Detail><Detail label={t("Recipient")}>{receipt.recipientName}</Detail><Detail label={t("To Nexa account")}>{receipt.destinationMasked}</Detail><Detail label={t("Amount")}>{formatMoney(receipt.amount, receipt.currencyCode)}</Detail>{receipt.reference && <Detail label={t("Reference")}>{receipt.reference}</Detail>}</dl>
      {receipt.status === "COMPLETED" ? <><p role="status">{t("Your transfer is complete. The money has been added to the recipient’s Nexa account.")}</p><a class="bank-button" href={"#/transactions/" + encodeURIComponent(receipt.reference!)}>{t("View transaction")}</a><oj-button chroming="outlined" class="nexa-action transfer-action transfer-action-secondary" onojAction={reset}>{t("Make another transfer")}</oj-button></>
      : receipt.status === "EXPIRED" ? <><p>{t("No money was sent by this request. Review the details again to create a new transfer.")}</p><oj-button chroming="callToAction" class="nexa-action transfer-action" onojAction={reset}>{t("Start a new review")}</oj-button></>
      : <><p>{t("Check the recipient and amount carefully. Sending moves money immediately. This review is valid for 5 minutes.")}</p>{error && <p role="alert" class="bank-error">{error}</p>}
        {uncertain && <oj-button chroming="outlined" class="nexa-action transfer-action transfer-action-secondary" disabled={busy} onojAction={checkStatus}>{t("Check transfer status")}</oj-button>}
        <oj-button chroming="callToAction" class="nexa-action transfer-action" disabled={busy} onojAction={send}>{busy ? t("Please wait…") : uncertain ? "Send this transfer again" : "Send " + formatMoney(receipt.amount, receipt.currencyCode)}</oj-button>
        {!uncertain && <oj-button chroming="outlined" class="nexa-action transfer-action transfer-action-secondary" disabled={busy} onojAction={() => { remember(null); setReceipt(undefined); setError(""); }}>{t("Edit details")}</oj-button>}</>}
    </div></Panel></div>
    : <div class="bank-narrow transfer-workflow transfer-entry"><Panel title={t("Transfer details")}><State loading={accounts.loading} error={accounts.error} retry={accounts.reload} empty={!accounts.loading && !accounts.error && !active.length ? "You need an active INR account to send money" : undefined}>
      <form class="bank-form experience-transfer-form" onSubmit={review} aria-busy={busy} onKeyDown={event => { if (event.key === "Enter" && (event.target as HTMLElement).tagName === "INPUT") { event.preventDefault(); void review(); } }}><fieldset disabled={busy} class="bank-transfer-fields">
        <div class="bank-field">
          <oj-label for="transfer-source">{t("From account")}</oj-label>
          <oj-select-one id="transfer-source" labelHint={t("From account")} labelEdge="provided" userAssistanceDensity="compact" required disabled={busy} value={source} onvalueChanged={event => { if (event.detail.updatedFrom !== "internal" || event.detail.value === source) return; setSource(event.detail.value || ""); if (own) setDestination(""); }} options={active.map(a => ({value:a.id,label:a.displayName + " · " + a.accountNumberMasked}))}/>
          {from && <p class="transfer-available">{t("Available:")} <strong>{formatMoney(from.availableBalance, "INR")}</strong></p>}
        </div>
        <div class="bank-field">
          <oj-label for="transfer-recipient-type">{t("Who are you sending to?")}</oj-label>
          <oj-select-one id="transfer-recipient-type" labelHint={t("Who are you sending to?")} labelEdge="provided" userAssistanceDensity="compact" disabled={busy} value={own ? "own" : "other"} onvalueChanged={event => { if (event.detail.updatedFrom !== "internal" || (event.detail.value === "own") === own) return; setOwn(event.detail.value === "own"); setDestination(""); }} options={[{value:"other",label:t("Another Nexa account")},{value:"own",label:t("One of my Nexa accounts")}]}/>
        </div>
        {own ? <div class="bank-field"><oj-label for="transfer-destination">{t("To account")}</oj-label><oj-select-one id="transfer-destination" labelHint={t("To account")} labelEdge="provided" userAssistanceDensity="compact" required disabled={busy} value={destination} placeholder={t("Choose another account")} onvalueChanged={event => { if (event.detail.updatedFrom === "internal") setDestination(event.detail.value || ""); }} options={active.filter(a => a.id !== source).map(a => ({value:a.id,label:a.displayName + " · " + a.accountNumberMasked}))}/>{active.length < 2 && <small>{t("You need two active INR accounts for this option.")}</small>}</div>
        : <div class="bank-field"><oj-label for="transfer-number">{t("Recipient’s Nexa account number")}</oj-label><oj-input-text id="transfer-number" labelHint={t("Recipient’s Nexa account number")} labelEdge="provided" userAssistanceDensity="compact" required disabled={busy} virtualKeyboard="number" autocomplete="off" length={{max:30}} value={destination} onrawValueChanged={event => setDestination((event.detail.value || "").replace(/\s/g, ""))}/></div>}
        <div class="bank-field"><oj-label for="transfer-amount">{t("Amount (₹)")}</oj-label><oj-input-text id="transfer-amount" labelHint={t("Amount (₹)")} labelEdge="provided" userAssistanceDensity="compact" required disabled={busy} virtualKeyboard="number" value={amount} length={{max:16}} placeholder={t("For example, 500")} describedBy={tooMuch ? "transfer-amount-help" : undefined} onrawValueChanged={event => setAmount(event.detail.value || "")}/>{tooMuch && <small id="transfer-amount-help" role="alert">{t("This is more than your available balance.")}</small>}</div>
      </fieldset>
        {error && <p role="alert" class="bank-error">{error}</p>}
        <div class="transfer-form-footer">
          <p class="bank-form-note">{t("Transfers are currently available between Nexa accounts. Other-bank and UPI transfers are not supported.")}</p>
          <oj-button chroming="callToAction" class="nexa-action transfer-action" disabled={busy || !valid} onojAction={() => void review()}>{busy ? t("Checking recipient…") : t("Review transfer")}</oj-button>
        </div>
      </form>
    </State></Panel><a href="#/payments">{t("Review bills and other payments →")}</a></div>}
  </section>;
}
