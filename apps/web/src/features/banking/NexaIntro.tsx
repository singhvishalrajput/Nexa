import { useEffect, useState } from "preact/hooks";
import { t } from "../../services/locale";
import { Modal } from "./ui";

const chapters = [
    { label: "Start chatting", title: "Your bank is a conversation.", description: "From accounts and spending to payments, bills, cards and loans — start with what you need, right here in chat.", message: "Help me with my banking.", reply: "Of course. We can explore your money, review payments or find the banking details you need.", action: "Try a reply", result: "You started a conversation. No commands to memorise." },
    { label: "Explore together", title: "Ask. Understand. Keep going.", description: "Explore account details and transactions, then ask a follow-up in the same conversation.", message: "Help me understand my spending.", reply: "We can look through your transactions together. Would you like to explore recent activity?", action: "See recent spending", result: "Groceries ₹250 · travel ₹80. Keep asking in your own words." },
    { label: "Make a payment", title: "Tell Nexa what you want to do.", description: "Start transfers and payments through chat. Nexa guides you through the details needed for review.", message: "Send ₹1 to Asha.", reply: "Asha · ₹1. Ready to review the payment?", action: "Review payment", result: "To Asha · ₹1. Check the details, then choose whether to confirm." },
    { label: "You confirm", title: "Conversation leads. You decide.", description: "Review the recipient and amount in chat before confirming. You stay in control of the payment.", message: "Show me the payment review.", reply: "To Asha · ₹1. Check the recipient and amount before you continue.", action: "Confirm ₹1 to Asha", result: "Practice complete! No money moved. Real payments need your confirmation." },
    { label: "Stay informed", title: "Everyday banking, one chat away.", description: "Bring bills, recurring payments, beneficiaries, cards and loan details into the same conversation.", message: "What banking details can we explore?", reply: "Your bills, mandates, scheduled payments, saved recipients, cards and loans — choose what matters to you.", action: "View electricity bill", result: "Electricity · ₹450 upcoming. Ask a follow-up for more detail." },
    { label: "Your words", title: "Chat your way.", description: "Type or speak in English or Hindi. Describe what you need naturally, and continue from Nexa’s reply.", message: "क्या हम हिन्दी में बात कर सकते हैं?", reply: "हाँ, बिल्कुल। अपनी बैंकिंग के बारे में अपने शब्दों में पूछें।", action: "Try the Hindi reply", result: "You’re ready to chat with Nexa. Your words, your pace." }
];

export function NexaIntro({ onClose }: { onClose: () => void }) {
    const [step, setStep] = useState(0);
    const [phase, setPhase] = useState(0);
    const [automatic, setAutomatic] = useState(true);
    const [interaction, setInteraction] = useState(0);
    const [completed, setCompleted] = useState<number[]>([]);
    const chapter = chapters[step];
    const selectStep = (next: number) => { setStep(next); setPhase(0); };
    useEffect(() => {
        const timer = window.setTimeout(() => {
            if (!automatic) { setAutomatic(true); return; }
            if (phase < 2) setPhase(current => current + 1);
            else { setStep(current => (current + 1) % chapters.length); setPhase(0); }
        }, automatic ? (phase === 0 ? 1800 : phase === 1 ? 2800 : 3400) : 5000);
        return () => window.clearTimeout(timer);
    }, [step, phase, automatic, interaction]);
    const takeControl = () => { setAutomatic(false); setInteraction(current => current + 1); };
    const practice = () => {
        if (phase === 0) setPhase(1);
        else if (phase === 1) {
            setPhase(2);
            setCompleted(current => current.includes(step) ? current : [...current, step]);
        } else selectStep((step + 1) % chapters.length);
    };
    return <Modal title={t("Know Nexa")} onClose={onClose} className="nexa-intro-dialog">
        <div class="nexa-intro" onClick={takeControl} onKeyDown={takeControl}>
            <header class="nexa-intro-heading">
                <div><p class="bank-eyebrow">{t("MEET NEXA CHAT")}</p><h2>{t("Banking starts with a conversation.")}</h2></div>
                <span class="nexa-intro-score" aria-label={`${completed.length} / ${chapters.length} ${t("tried")}`}>✧ {completed.length}/{chapters.length} <span>{t("tried")}</span></span>
            </header>
            <nav class="nexa-intro-chapters" aria-label={t("Explore Nexa capabilities")}>
                {chapters.map((item, index) => <button type="button" key={item.label} aria-label={t(item.label)} title={t(item.label)} aria-current={step === index ? "step" : undefined} onClick={() => selectStep(index)}>
                    <span aria-hidden="true">{completed.includes(index) ? "✓" : String(index + 1).padStart(2, "0")}</span><span class="nexa-intro-chapter-label">{t(item.label)}</span>
                    <i style={{ transform: step === index ? `scaleX(${(phase + 1) / 3})` : "scaleX(0)" }} aria-hidden="true"/>
                </button>)}
            </nav>
            <section class="nexa-intro-stage" aria-label={t("Interactive chat tour")}>
                <div class="nexa-intro-story" key={step}>
                    <span class="nexa-intro-count">{String(step + 1).padStart(2, "0")} / 06</span>
                    <h3>{t(chapter.title)}</h3><p>{t(chapter.description)}</p>
                    <span class="nexa-intro-status">{t(automatic ? "Watch it happen, or tap to try." : "Your turn · auto resumes after 5s idle")}</span>
                </div>
                <div class="nexa-intro-chat">
                    <header><span>✧ {t("Nexa chat")}</span><span class="nexa-intro-demo">{t("Practice · no real money")}</span></header>
                    <div class="nexa-intro-messages" key={`${step}-${phase}`} aria-live={automatic ? "off" : "polite"} aria-atomic="true">
                        <div class="nexa-intro-bubble user">{t(chapter.message)}</div>
                        {phase === 0 ? <div class="nexa-intro-hint">{t("Tap send to begin. Nexa will guide you.")}</div> : <div class="nexa-intro-bubble assistant"><span aria-hidden="true">✧</span><p>{t(phase === 2 ? chapter.result : chapter.reply)}</p></div>}
                    </div>
                    <button type="button" class={`nexa-intro-try${phase === 2 ? " is-complete" : ""}`} onClick={practice}>
                        <span>{t(phase === 0 ? "Send message" : phase === 1 ? chapter.action : "Next chapter")}</span><span aria-hidden="true">{phase === 2 ? "✓ →" : "↑"}</span>
                    </button>
                </div>
            </section>
            <footer class="nexa-intro-controls">
                <p>{t("Try the chat. Discover what you can do.")}</p>
                <div class="nexa-intro-actions">
                    <button type="button" class="bank-button secondary" aria-label={t("Previous capability")} onClick={() => selectStep((step + chapters.length - 1) % chapters.length)}>←</button>
                    <button type="button" class="bank-button secondary" aria-label={t("Next capability")} onClick={() => selectStep((step + 1) % chapters.length)}>→</button>
                    <button type="button" class="bank-button" onClick={() => { onClose(); window.location.hash = "#/assistant"; }}>{t("Start chatting")}</button>
                </div>
            </footer>
        </div>
    </Modal>;
}
