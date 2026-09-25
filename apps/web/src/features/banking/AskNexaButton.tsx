import { useEffect, useState } from "preact/hooks";
import { BankingIcon } from "../../components/BankingIcon";
import { t } from "../../services/locale";

const labels = [
    { text: "Ask Nexa", lang: "en" },
    { text: "Nexa ko puche", lang: "hi-Latn" },
    { text: "Nexa से पूछें", lang: "hi" }
];

export function AskNexaButton({ onClick }: { onClick: () => void }) {
    const [index, setIndex] = useState(0);
    const [leaving, setLeaving] = useState(false);
    useEffect(() => {
        const motion = window.matchMedia("(prefers-reduced-motion: reduce)");
        let timer: number | undefined;
        const schedule = () => {
            timer = window.setTimeout(() => {
                setLeaving(true);
                // Keep the outgoing label visible until its exit completes.
                timer = window.setTimeout(() => {
                    setIndex(value => (value + 1) % labels.length);
                    setLeaving(false);
                    schedule();
                }, 360);
            }, 4600);
        };
        const update = () => {
            window.clearTimeout(timer);
            setLeaving(false);
            if (!motion.matches) schedule();
        };
        update();
        motion.addEventListener("change", update);
        return () => {
            window.clearTimeout(timer);
            motion.removeEventListener("change", update);
        };
    }, []);

    return <button type="button" class="bank-button bank-ask-nexa" onClick={onClick} aria-label={t("Ask Nexa")}>
        <span class="bank-ask-nexa-label" aria-hidden="true" translate={false}>
            {labels.map((label, position) => <span key={label.lang} lang={label.lang} class={position === index ? "is-visible" + (leaving ? " is-leaving" : "") : ""}>{label.text}</span>)}
        </span>
        <span class="bank-ask-nexa-icon" aria-hidden="true"><BankingIcon name="messages"/></span>
    </button>;
}
