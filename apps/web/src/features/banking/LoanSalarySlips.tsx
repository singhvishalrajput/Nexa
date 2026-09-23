import { useRef, useState } from "preact/hooks";
import { authenticatedBlobRequest, authenticatedRequest } from "../../services/auth";
import { Panel, State, useLoad } from "./ui";

export type SalarySlip = {
    id: string; month: string; fileName: string; mediaType: string; size: number;
    uploadedAt: string; verifiedBy: string | null; verifiedAt: string | null;
};
export type SalarySlipBundle = { requiredMonths: string[]; documents: SalarySlip[] };
export const salaryMonth = (month: string) => new Intl.DateTimeFormat("en-IN", {
    month: "long", year: "numeric", timeZone: "UTC"
}).format(new Date(month + "-01T00:00:00Z"));
const path = (id: string) => "/loans/" + encodeURIComponent(id) + "/salary-slips";

export function SalarySlipFields({ months, disabled = false }: { months: string[]; disabled?: boolean }) {
    return <fieldset class="loan-slip-fields" disabled={disabled}>
        <legend>Salary slips · last 3 completed months</legend>
        <p>Upload one separate slip per month. An administrator will cross-check all three before approving your application.</p>
        <div class="loan-slip-inputs">{months.map(month => <label key={month}>{salaryMonth(month)}
            <input name={"slip-" + month} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" required/>
        </label>)}</div>
        <small>PDF, JPG or PNG · up to 5 MB per file. Only you and authorized administrators can access these documents.</small>
    </fieldset>;
}

export function appendSalarySlips(target: FormData, form: FormData, months: string[]) {
    if (months.length !== 3) throw new Error("Salary-slip requirements are unavailable. Please retry.");
    for (const month of months) {
        const file = form.get("slip-" + month) as File | null;
        if (!file || typeof file === "string" || !file.size)
            throw new Error("Upload your salary slip for " + salaryMonth(month) + ".");
        if (file.size > 5 * 1024 * 1024 || !["application/pdf", "image/png", "image/jpeg"].includes(file.type))
            throw new Error("Each salary slip must be a PDF, JPG or PNG of at most 5 MB.");
        target.append("months", month);
        target.append("files", file);
    }
}

export function completeSalarySlips(bundle?: SalarySlipBundle): boolean {
    return !!bundle && bundle.requiredMonths.length === 3 && bundle.documents.length === 3
        && new Set(bundle.documents.map(d => d.month)).size === 3
        && bundle.requiredMonths.every(month => bundle.documents.some(d => d.month === month));
}

export function SalarySlipDocuments({ token, loanId, bundle, verified, onVerify, disabled = false }: {
    token: string; loanId: string; bundle: SalarySlipBundle; verified?: string[];
    onVerify?: (ids: string[]) => void; disabled?: boolean;
}) {
    const [opening, setOpening] = useState(""), [error, setError] = useState("");
    const [opened, setOpened] = useState<string[]>([]);
    const lock = useRef(false);
    async function download(slip: SalarySlip) {
        if (lock.current) return;
        lock.current = true; setOpening(slip.id); setError("");
        try {
            const blob = await authenticatedBlobRequest(path(loanId) + "/" + encodeURIComponent(slip.id), token);
            const url = URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = url; anchor.download = slip.fileName; document.body.appendChild(anchor);
            anchor.click(); anchor.remove();
            window.setTimeout(() => URL.revokeObjectURL(url), 60000);
            setOpened(previous => [...previous, slip.id]);
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to download salary slip."); }
        finally { lock.current = false; setOpening(""); }
    }
    return <div class="loan-slip-documents">
        {bundle.documents.map(slip => <article class="loan-slip-document" key={slip.id}>
            <div><strong>{salaryMonth(slip.month)}</strong><span>{slip.fileName}</span><small>{Math.ceil(slip.size / 1024)} KB{slip.verifiedAt ? " · Verified by administrator" : " · Awaiting verification"}</small></div>
            <button type="button" class="bank-button secondary" disabled={disabled || !!opening} onClick={() => download(slip)}>{opening === slip.id ? "Downloading…" : "Download slip"}</button>
            {onVerify && <label class="loan-slip-check"><input type="checkbox" checked={verified?.includes(slip.id) || false}
                disabled={disabled || !opened.includes(slip.id)}
                onChange={e => onVerify(e.currentTarget.checked ? [...(verified || []), slip.id] : (verified || []).filter(id => id !== slip.id))}/>
                <span>I cross-checked this slip’s month, employee details and salary.</span></label>}
        </article>)}
        {onVerify && <small>Download and inspect each document, then confirm verification. Approval records your administrator identity and verification time.</small>}
        {error && <p role="alert">{error}</p>}
    </div>;
}

export function LoanSalarySlipPanel({ token, loanId, pending }: { token: string; loanId: string; pending: boolean }) {
    const data = useLoad(() => authenticatedRequest<SalarySlipBundle>(path(loanId), token), [token, loanId]);
    const [busy, setBusy] = useState(false), [error, setError] = useState("");
    const lock = useRef(false);
    async function submit(event: Event) {
        event.preventDefault();
        if (lock.current || !data.data) return;
        lock.current = true; setBusy(true); setError("");
        try {
            const body = new FormData();
            appendSalarySlips(body, new FormData(event.currentTarget as HTMLFormElement), data.data.requiredMonths);
            await authenticatedRequest(path(loanId), token, { method: "POST", body });
            data.reload();
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to upload salary slips."); }
        finally { lock.current = false; setBusy(false); }
    }
    return <Panel title="Salary-slip documents" className="loan-slip-panel">
        <div class="loan-slip-body"><State loading={data.loading} error={data.error} retry={data.reload}>
            {data.data && <SalarySlipDocuments token={token} loanId={loanId} bundle={data.data}/>}
            {data.data && !data.data.documents.length && (pending ? <form onSubmit={submit}>
                <SalarySlipFields months={data.data.requiredMonths} disabled={busy}/>
                <button class="bank-button" disabled={busy}>{busy ? "Uploading…" : "Submit salary slips"}</button>
            </form> : <p>No salary slips were recorded for this older application.</p>)}
        </State>{error && <p role="alert">{error}</p>}</div>
    </Panel>;
}
