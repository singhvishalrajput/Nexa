import { useEffect, useRef, useState } from "preact/hooks";
import { ApiRequestError } from "../../services/auth";
import { CardApplicationRequest, cardApplications, localCardNotice } from "../../services/card-applications";
import { formatMoney, humanize } from "../../services/banking-content";
import { useNavigationGuard } from "../../hooks/useNavigationGuard";
import { bankApi, Product } from "./api";
import { Detail, Panel, State, Status, useLoad } from "./ui";

const unknownResult = (cause: unknown) => !(cause instanceof ApiRequestError) || cause.status === 0 || cause.status >= 500;

export function CardApplications({ token, initiallyOpen = false, reload, onAccountsChanged }: {
  token: string; initiallyOpen?: boolean; reload: () => void; onAccountsChanged?: () => void;
}) {
  const [open, setOpen] = useState(initiallyOpen);
  const [cardType, setCardType] = useState<"DEBIT" | "CREDIT">("DEBIT");
  const [accountId, setAccountId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [busy, setBusy] = useState(false), [error, setError] = useState("");
  const [pending, setPending] = useState<CardApplicationRequest>();
  const [created, setCreated] = useState<Product>();
  const requestId = useRef(crypto.randomUUID()), lock = useRef(false);
  const accounts = useLoad(() => open ? bankApi.accounts(token) : Promise.resolve([]), [token, open]);
  const eligible = (accounts.data || []).filter(account => account.status === "ACTIVE" && account.currencyCode === "INR" && ["SAVINGS", "CURRENT"].includes(account.accountType));
  const validName = !displayName.trim() || (Array.from(displayName.trim()).length <= 100 && new TextEncoder().encode(displayName.trim()).length <= 120);
  const valid = !accounts.loading && !accounts.error && eligible.some(account => account.id === accountId) && Number.isSafeInteger(Number(accountId)) && Number(accountId) > 0 && validName;
  const locked = busy || !!pending;
  useNavigationGuard(open && !!(accountId || displayName), locked);
  useEffect(() => { if (!locked) setOpen(initiallyOpen); }, [initiallyOpen]);
  async function submit(event?: Event, retry?: CardApplicationRequest) {
    event?.preventDefault();
    if (lock.current || (!retry && !valid)) return;
    const request = retry || {accountId: Number(accountId), cardType, requestId: requestId.current, ...(displayName.trim() ? {displayName: displayName.trim()} : {})};
    lock.current = true; setBusy(true); setError("");
    try {
      const card = await cardApplications.apply(token, request);
      if (!card.id || !["ACTIVE", "PENDING_APPROVAL", "BLOCKED", "REJECTED", "CLOSED"].includes(card.status)) throw Error("The saved card request could not be confirmed. Retry the same request.");
      setCreated(card); setPending(undefined); setOpen(false); setAccountId(""); setDisplayName(""); requestId.current = crypto.randomUUID();
      reload(); onAccountsChanged?.();
    } catch (cause) {
      if (unknownResult(cause)) setPending(request);
      else { setPending(undefined); requestId.current = crypto.randomUUID(); }
      setError(cause instanceof Error ? cause.message : "The card request could not be saved.");
    } finally { lock.current = false; setBusy(false); }
  }
  return <Panel className="bank-create-panel" title="Request a card" action={<button class="bank-button secondary" type="button" disabled={locked} aria-expanded={open} aria-controls="card-application-form" onClick={() => { setOpen(value => !value); setError(""); }}>{open ? "Close" : "Request a card"}</button>}>
    <p class="bank-create-description">Link a debit card to an active Nexa account, or apply for a credit card for bank review.</p>
    <p class="bank-create-description">{localCardNotice}</p>
    {created && <div class="bank-form" role="status"><Status value={created.status}/><p>{created.status === "PENDING_APPROVAL" ? "Your credit card application is awaiting bank review. No credit limit is available yet." : created.status === "ACTIVE" ? "Your Nexa card record is active. No account balance was changed." : `The bank has recorded this request as ${humanize(created.status).toLowerCase()}.`}</p><a class="bank-button secondary" href={"#/cards/" + encodeURIComponent(created.id)}>View card request</a></div>}
    {open && <div class="bank-form bank-editor-form"><State loading={accounts.loading} error={accounts.error} retry={accounts.reload}>
      {!eligible.length ? <p>You need an active Nexa INR savings or current account before requesting a card. <a href="#/accounts">View accounts</a>.</p> : <form id="card-application-form" onSubmit={event => void submit(event)}>
        <fieldset class="bank-fields-grid" disabled={locked}>
          <label>Card type<select value={cardType} onChange={event => setCardType(event.currentTarget.value as "DEBIT" | "CREDIT")}><option value="DEBIT">Debit card</option><option value="CREDIT">Credit card</option></select></label>
          <label>Linked Nexa account<select required value={accountId} onChange={event => setAccountId(event.currentTarget.value)}><option value="">Choose an account</option>{eligible.map(account => <option key={account.id} value={account.id}>{account.displayName} · {account.accountNumberMasked}</option>)}</select></label>
          <label class="bank-field-wide">Card name (optional)<input value={displayName} maxLength={100} onInput={event => setDisplayName(event.currentTarget.value)} placeholder={cardType === "DEBIT" ? "Everyday debit card" : "Nexa credit card"}/>{!validName && <small role="alert">Use a shorter card name.</small>}</label>
          <p class="bank-form-note bank-field-wide">{cardType === "DEBIT" ? "A debit card record links to this account and does not add credit or move money." : "The bank reviews the request and sets an approved credit limit. Applying does not provide credit or move money."}</p>
        </fieldset>
        {error && <p role="alert" class="bank-error">{error}</p>}
        {pending ? <><p role="alert">The result is not confirmed. Keep these details unchanged and retry the same request; it cannot create a second card.</p><button type="button" class="bank-button" disabled={busy} onClick={() => void submit(undefined, pending)}>{busy ? "Checking request…" : "Retry same card request"}</button></>
          : <div class="bank-editor-actions"><button class="bank-button" disabled={busy || !valid}>{busy ? "Submitting…" : cardType === "DEBIT" ? "Request debit card" : "Submit credit card application"}</button></div>}
      </form>}
    </State></div>}
  </Panel>;
}

export function CardStatusControl({ token, product, reload, onPosted }: {
  token: string; product: Product; reload: () => void; onPosted: (message: string) => void;
}) {
  const [review, setReview] = useState<"block" | "unblock">();
  const [busy, setBusy] = useState(false), [uncertain, setUncertain] = useState(false), [error, setError] = useState("");
  const lock = useRef(false);
  useNavigationGuard(!!review, busy || uncertain);
  function confirmed(card: Product) {
    if (card.id !== product.id || card.status !== (review === "block" ? "BLOCKED" : "ACTIVE")) return false;
    setReview(undefined); setUncertain(false); setError("");
    onPosted(card.status === "BLOCKED" ? "Card blocked in Nexa." : "Card unblocked in Nexa."); reload(); return true;
  }
  async function update(checkOnly = false) {
    if (lock.current || !review) return;
    lock.current = true; setBusy(true); setError("");
    try {
      const card = await (checkOnly ? cardApplications.card(token, product.id) : cardApplications.status(token, product.id, review));
      if (!confirmed(card)) { setUncertain(true); setError(`The card is currently ${humanize(card.status).toLowerCase()}. Retry the same action to confirm its result.`); }
    } catch (cause) {
      setUncertain(unknownResult(cause));
      setError(cause instanceof Error ? cause.message : "The card status could not be confirmed.");
    } finally { lock.current = false; setBusy(false); }
  }
  return <Panel title="Card controls"><div class="bank-form">
    <p class="bank-form-note">{localCardNotice}</p>
    {product.status === "PENDING_APPROVAL" ? <p>Your credit card application is awaiting review. The bank sets the limit when it approves the request.</p>
      : product.status === "REJECTED" ? <p>The bank declined this card request. Contact the bank for details before making another application.</p>
        : !["ACTIVE", "BLOCKED"].includes(product.status) ? <p>No card controls are available for this status.</p>
          : !review ? <button class="bank-button secondary" type="button" onClick={() => setReview(product.status === "ACTIVE" ? "block" : "unblock")}>{product.status === "ACTIVE" ? "Block card" : "Unblock card"}</button>
            : <div class="bank-confirm-summary"><h3>{review === "block" ? "Block this card?" : "Unblock this card?"}</h3><dl><Detail label="Card">{product.displayName || "Nexa card"}</Detail><Detail label="Current status"><Status value={product.status}/></Detail>{product.cardType === "CREDIT" && product.creditLimit != null && <Detail label="Approved credit limit">{formatMoney(product.creditLimit, product.currencyCode)}</Detail>}</dl><p>This updates the card status saved in Nexa.</p>
              {error && <p class="bank-error" role="alert">{error}</p>}{uncertain && <p role="alert">The last result is unconfirmed. Check the saved status or retry the same action.</p>}
              <div class="bank-form-actions"><button class="bank-button" type="button" disabled={busy} onClick={() => void update()}>{busy ? "Saving…" : uncertain ? "Retry same card action" : review === "block" ? "Confirm block" : "Confirm unblock"}</button>{uncertain ? <button class="bank-button secondary" type="button" disabled={busy} onClick={() => void update(true)}>Check card status</button> : <button class="bank-button secondary" type="button" disabled={busy} onClick={() => { setReview(undefined); setError(""); }}>Cancel</button>}</div>
            </div>}
  </div></Panel>;
}
