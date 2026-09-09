import { h } from "preact";
import { useEffect, useState } from "preact/hooks";
import { PromptExamples } from "./PromptExamples";
import { StatusCard } from "./StatusCard";

export function ExperienceSection() {
  const [activeIndex, setActiveIndex] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => setActiveIndex((current) => (current + 1) % 3), 3800);
    return () => window.clearTimeout(timer);
  }, [activeIndex]);

  return <section class="nexa-experience" id="intelligence"><div class="nexa-experience__content"><PromptExamples activeIndex={activeIndex} onSelect={setActiveIndex} /><div class="nexa-status-stage"><div class="nexa-orb" aria-hidden="true"><i /><i /><i /></div><StatusCard activeIndex={activeIndex} /></div></div></section>;
}
