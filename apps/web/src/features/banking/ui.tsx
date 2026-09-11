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
        return <div class="bank-state" role="status"><span class="bank-loader"/><strong>Loading your banking information</strong><span>Please wait a moment.</span></div>;
    if (error)
        return <div class="bank-state bank-error" role="alert"><strong>We couldn’t load this information</strong><p>{error}</p>{retry && <button class="bank-button" onClick={retry}>Try again</button>}</div>;
    if (empty)
        return <div class="bank-state"><span class="bank-empty-icon" aria-hidden="true">◇</span><strong>{empty}</strong><span>Information will appear here when it’s available.</span></div>;
    return <>{children}</>;
}
export function Status({ value }: {
    value: string;
}) { const {tone, label} = statusPresentation(value); return <span class={"bank-status " + tone}><i aria-hidden="true"/>{label}</span>; }
export function PageHeading({ eyebrow = "YOUR BANKING", title, description, action }: {
    eyebrow?: string;
    title: string;
    description?: string;
    action?: ComponentChildren;
}) { return <header class="bank-page-heading"><div><p class="bank-eyebrow">{eyebrow}</p><h1 tabIndex={-1}>{title}</h1>{description && <p>{description}</p>}</div>{action}</header>; }
export function Panel({ title, action, children, className = "" }: {
    title?: string;
    action?: ComponentChildren;
    children: ComponentChildren;
    className?: string;
}) { return <section class={"bank-panel " + className}>{title && <header class="bank-panel-heading"><h2>{title}</h2>{action}</header>}{children}</section>; }
export function Modal({ title, onClose, children, locked = false }: {
    title: string;
    onClose: () => void;
    children: ComponentChildren;
    locked?: boolean;
}) {
    const ref = useRef<HTMLDialogElement>(null);
    useEffect(() => { const previous = document.activeElement as HTMLElement; ref.current?.showModal(); return () => { ref.current?.close(); previous?.focus(); }; }, []);
    useEffect(() => { ref.current?.querySelector<HTMLElement>("h2")?.focus(); }, [title]);
    return <dialog ref={ref} class="bank-dialog" aria-label={title} onCancel={e => { e.preventDefault(); if (!locked)
        onClose(); }}><header><h2 tabIndex={-1}>{title}</h2><button class="bank-icon-button" aria-label="Close dialog" disabled={locked} onClick={onClose}>×</button></header>{children}</dialog>;
}
export function Detail({ label, children }: {
    label: string;
    children: ComponentChildren;
}) { return <div class="bank-detail"><dt>{label}</dt><dd>{children ?? "—"}</dd></div>; }
