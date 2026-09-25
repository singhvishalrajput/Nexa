import { flushSync } from "preact/compat";

type CardTransition = { finished: Promise<void>; skipTransition: () => void };
type TransitionDocument = Document & {
    startViewTransition?: (update: () => Promise<void>) => CardTransition;
};

let active: CardTransition | undefined;

/** Capture the selected card before replacing the route, then morph to its details. */
export function transitionToAccount(id: string | undefined, update: () => void) {
    active?.skipTransition();
    const source = id && Array.from(document.querySelectorAll<HTMLElement>("[data-account-card]"))
        .find(card => card.dataset.accountCard === id);
    const doc = document as TransitionDocument;
    if (!source || !doc.startViewTransition || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        update();
        return;
    }
    source.style.setProperty("view-transition-name", "account-expand");
    let destination: HTMLElement | undefined;
    const transition = doc.startViewTransition(async () => {
        // Rendering is paused during a view transition: never wait for animation frames here.
        flushSync(update);
        source.style.removeProperty("view-transition-name");
        window.scrollTo({ top: 0, left: 0, behavior: "instant" as ScrollBehavior });
        destination = Array.from(document.querySelectorAll<HTMLElement>("[data-account-card]"))
            .find(card => card.dataset.accountCard === id);
        destination?.style.setProperty("view-transition-name", "account-expand");
    });
    active = transition;
    void transition.finished.catch(() => undefined).finally(() => {
        source.style.removeProperty("view-transition-name");
        destination?.style.removeProperty("view-transition-name");
        if (active === transition) active = undefined;
    });
}
