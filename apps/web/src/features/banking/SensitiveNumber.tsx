import { useEffect, useRef, useState } from "preact/hooks";
import { authenticatedRequest } from "../../services/auth";
import { safeMask } from "../../services/banking-content";

export function SensitiveNumber({ masked, kind = "accounts", id, fullValue }: { masked?: string | null; kind?: string; id?: string; fullValue?: string }) {
    const [number, setNumber] = useState("");
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    const generation = useRef(0);
    useEffect(() => {
        generation.current++;
        setNumber(""); setMessage(""); setBusy(false);
        return () => { generation.current++; };
    }, [id, kind, masked, fullValue]);
    async function toggle() {
        if (busy) return;
        if (number) { setNumber(""); return; }
        if (fullValue) { setNumber(fullValue); return; }
        if (!id) { setMessage("The bank has not provided the full number."); return; }
        const current = generation.current;
        setBusy(true); setMessage("");
        try {
            const result = await authenticatedRequest<{ number?: string; message?: string }>(
                `/sensitive-numbers/${encodeURIComponent(kind)}/${encodeURIComponent(id)}`, "", { cache: "no-store" });
            if (current === generation.current) {
                setNumber(result.number || "");
                setMessage(result.message || "");
            }
        } catch (_) {
            if (current === generation.current) setMessage("Unable to reveal this number. Please try again.");
        } finally { if (current === generation.current) setBusy(false); }
    }
    if (!masked) return <span>Number unavailable</span>;
    const hint = number ? "Click to hide the full number" : "Click to reveal the full number";
    return <span class="bank-sensitive-number"><button type="button" class="bank-number-toggle" title={hint}
        aria-label={hint} aria-pressed={!!number} aria-busy={busy}
        onClick={event => { event.preventDefault(); event.stopPropagation(); void toggle(); }}>
        {busy ? "Revealing…" : number || safeMask(masked)}
    </button>{message && <small class="bank-number-message" role="status">{message}</small>}</span>;
}

export function SensitiveLabel({ label, kind = "accounts", id }: { label: string; kind?: string; id?: string }) {
    const match = /[•*xX]{2,}\s*\d[\d -]*$/.exec(label);
    return match ? <>{label.slice(0, match.index)}<SensitiveNumber kind={kind} id={id} masked={match[0]}/></> : <>{label}</>;
}
