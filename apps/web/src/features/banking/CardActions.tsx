import { useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { bankApi, Product } from "./api";
import { DemoAction } from "./Showcase";
import { State, useLoad } from "./ui";
import { DigitalCard } from "./DigitalCard";

export function CardSupport({ token }: { token: string }) {
    const support = useLoad(() => authenticatedRequest<{ phone: string; email: string; message: string }>("/cards/support", token), [token]);
    return <div class="bank-card-support"><State loading={support.loading} error={support.error} retry={support.reload}>
        <p>For card upgrades, adjustments or credit-card issuance, contact card support.</p>
        {support.data?.phone ? <a class="bank-inline-action" href={"tel:" + support.data.phone.replace(/[^+\d]/g, "")}>{support.data.phone}</a> : <p>Use your bank’s official support channel.</p>}
        {support.data?.email && <p><a class="bank-inline-action" href={"mailto:" + support.data.email}>{support.data.email}</a></p>}
    </State></div>;
}

export function DigitalDebitCreate({ token, onCreated }: { token: string; onCreated?: () => void }) {
    const accounts = useLoad(() => bankApi.accounts(token), [token]);
    const [account, setAccount] = useState("");
    const [review, setReview] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [created, setCreated] = useState<Product>();
    async function create() {
        if (busy || !review || !account) return;
        setBusy(true); setError("");
        try {
            setCreated(await authenticatedRequest<Product>("/cards/digital-debit", token, { method: "POST", body: JSON.stringify({ accountId: account }) }));
            onCreated?.();
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to create a card."); }
        finally { setBusy(false); }
    }
    if (created) return <div><p role="status">Your digital debit card is ready.</p><DigitalCard product={created}/><a href={"#/cards/" + encodeURIComponent(created.id)}>View card</a></div>;
    return <div class="bank-form"><h3>Create a digital debit card</h3><p>Link your new card to an active account.</p>
        <State loading={accounts.loading} error={accounts.error} retry={accounts.reload}>
            <label>Linked account<select value={account} disabled={busy || review} onChange={e => setAccount(e.currentTarget.value)}><option value="">Choose an account</option>
                {accounts.data?.filter(a => a.status === "ACTIVE" && ["SAVINGS", "CURRENT"].includes(a.accountType)).map(a => <option key={a.id} value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}
            </select></label>
            {review ? <><p>Create a digital debit card for this account?</p><button class="bank-button" disabled={busy} onClick={create}>{busy ? "Creating…" : "Confirm card creation"}</button><button disabled={busy} onClick={() => setReview(false)}>Back</button></>
                : <button class="bank-button" disabled={!account || accounts.loading} onClick={() => setReview(true)}>Create digital debit card</button>}
        </State>{error && <p role="alert">{error}</p>}
    </div>;
}

export function ProductPayment({ token, product, operation, reload }: { token: string; product: Product; operation: "PAY_CARD" | "PAY_BILL"; reload?: () => void }) {
    const accounts = useLoad(() => operation === "PAY_BILL" ? authenticatedRequest<import("./api").BankAccount[]>("/cards/bill-funding-accounts", token) : bankApi.accounts(token), [token, operation]);
    const [account, setAccount] = useState("");
    const [amount, setAmount] = useState("");
    const maximum = Number(operation === "PAY_CARD" ? product.outstanding : product.amount);
    const minimum = operation === "PAY_BILL" ? Number(product.minimumAmount || 0.01) : 0.01;
    const valid = /^\d+(\.\d{1,2})?$/.test(amount) && Number(amount) > 0 && Number(amount) >= minimum && Number(amount) <= maximum;
    return <div class="bank-form bank-product-payment"><h3>{operation === "PAY_CARD" ? "Pay credit-card bill" : "Pay bill"}</h3>
        <State loading={accounts.loading} error={accounts.error} retry={accounts.reload}>
            <label>Pay from<select value={account} onChange={e => setAccount(e.currentTarget.value)}><option value="">Choose an account or card</option>{accounts.data?.filter(a => a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.displayName} · {a.accountNumberMasked}</option>)}</select></label>
            <label>Amount (INR)<input type="number" min={minimum} max={maximum} step="0.01" value={amount} onInput={e => setAmount(e.currentTarget.value)}/></label>
            <button type="button" class="bank-inline-action" onClick={() => setAmount(maximum.toFixed(2))}>Use full amount</button>
            {account && valid && <DemoAction token={token} request={{ operation, accountId: account, targetId: product.id, amount }} label="Review payment" onDone={() => { setAmount(""); reload?.(); }}/>} 
        </State>
    </div>;
}
