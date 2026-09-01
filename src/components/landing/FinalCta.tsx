import { h } from "preact";
type FinalCtaProps = { onGetStarted: () => void };
export function FinalCta({ onGetStarted }: FinalCtaProps) { return <section class="nexa-final-cta" id="security"><p class="nexa-kicker">Make the next move</p><h2>Enough tapping<br />around.</h2><button class="nexa-enter-button" type="button" onClick={onGetStarted}>Start with Nexa <b aria-hidden="true">↗</b></button></section>; }
