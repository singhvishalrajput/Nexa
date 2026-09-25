import "ojs/ojdialog";
import { ojDialog } from "ojs/ojdialog";
import Context = require("ojs/ojcontext");
import "ojs/ojprogress-circle";
import { getLocale, t } from "../../services/locale";
import { ComponentChildren } from "preact";
import { useEffect, useRef, useState } from "preact/hooks";
import { statusPresentation } from "../../services/banking-content";
export function useLoad<T>(load: () => Promise<T>, dependencies: unknown[]) {
    const [revision, setRevision] = useState(0);
    const key = JSON.stringify([...dependencies, revision]);
    const [result, setResult] = useState<{key: string; data?: T; error: string; loading: boolean}>({key, error: "", loading: true});
    useEffect(() => {
        let active = true;
        setResult({key, error: "", loading: true});
        Promise.resolve().then(load).then(data => { if (active) setResult({key, data, error: "", loading: false}); })
            .catch(error => { if (active) setResult({key, error: error instanceof Error ? error.message : "Something went wrong. Please try again.", loading: false}); });
        return () => { active = false; };
    }, [key]);
    return {...(result.key === key ? result : {data: undefined, error: "", loading: true}), reload: () => setRevision(n => n + 1)};
}
export function State({ loading, error, empty, retry, children }: {
    loading?: boolean;
    error?: string;
    empty?: string;
    retry?: () => void;
    children?: ComponentChildren;
}) {
    if (loading)
        return <div class="bank-state" role="status"><oj-progress-circle size="sm" value={-1} aria-label={t("Loading your banking information")}/><strong>{t("Loading your banking information")}</strong><span>{t("Please wait a moment.")}</span></div>;
    if (error)
        return <div class="bank-state bank-error" role="alert"><strong>{t("We couldn’t load this information")}</strong><p>{t(error)}</p>{retry && <button class="bank-button" onClick={retry}>{t("Try again")}</button>}</div>;
    if (empty)
        return <div class="bank-state"><span class="bank-empty-icon" aria-hidden="true">◇</span><strong>{t(empty)}</strong><span>{t("Information will appear here when it’s available.")}</span></div>;
    return <>{children}</>;
}
export function Status({ value }: {
    value: string;
}) {
    const {tone, label} = statusPresentation(value);
    // Replace the localized leaf rather than retaining translated text nodes.
    // Browser translation must not add a second translation to application copy.
    return <span key={getLocale()} class={"bank-status " + tone} lang={getLocale()} translate={false}><i aria-hidden="true"/><span class="bank-status-label">{label}</span></span>;
}
export function PageHeading({ eyebrow, title, description, action }: {
    eyebrow?: string;
    title: string;
    description?: string;
    action?: ComponentChildren;
}) { return <header class="bank-page-heading"><div>{eyebrow && <p class="bank-eyebrow">{t(eyebrow)}</p>}<h1 tabIndex={-1}>{t(title)}</h1>{description && <p>{t(description)}</p>}</div>{action}</header>; }
export function Panel({ title, action, children, className = "" }: {
    title?: string;
    action?: ComponentChildren;
    children: ComponentChildren;
    className?: string;
}) { return <section class={"bank-panel " + className}>{title && <header class="bank-panel-heading"><h2>{t(title)}</h2>{action}</header>}{children}</section>; }
export function Modal({ title, onClose, children, locked = false, className = "" }: {
    title: string;
    onClose: () => void;
    children: ComponentChildren;
    locked?: boolean;
    className?: string;
}) {
    const ref = useRef<ojDialog>(null);
    const active = useRef(true);
    useEffect(() => {
        let mounted = true;
        active.current = true;
        const previous = document.activeElement as HTMLElement;
        const dialog = ref.current;
        if (dialog) void Context.getContext(dialog).getBusyContext().whenReady().then(() => { if (mounted) dialog.open(); });
        return () => { mounted = false; active.current = false; dialog?.close(); previous?.focus(); };
    }, []);
    return <oj-dialog ref={ref} class={`bank-app bank-dialog ${className}`} dialogTitle={t(title)} modality="modal" cancelBehavior={locked ? "none" : "icon"} dragAffordance="none" resizeBehavior="none" onojBeforeClose={event => { if (locked && active.current) event.preventDefault(); }} onojClose={() => { if (active.current) onClose(); }}>
      <div slot="body">{children}</div>
    </oj-dialog>;
}
export function Detail({ label, children }: {
    label: string;
    children: ComponentChildren;
}) { return <div class="bank-detail"><dt>{t(label)}</dt><dd>{children ?? "—"}</dd></div>; }
