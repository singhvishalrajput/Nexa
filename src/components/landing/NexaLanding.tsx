import { h } from "preact";
import { useState } from "preact/hooks";
import { Navbar } from "./Navbar";
import { HeroDemo as Hero } from "./HeroDemo";
import { ExperienceSection } from "./ExperienceSection";
import { FinalCta } from "./FinalCta";
import { LandingFooter } from "./LandingFooter";
import { ChatWorkspace } from "../chat/ChatWorkspace";

export function NexaLanding() {
  const [chatOpen, setChatOpen] = useState(false);

  if (chatOpen) {
    return <ChatWorkspace onClose={() => setChatOpen(false)} />;
  }

  return (
    <div class="nexa-landing">
      <Navbar onGetStarted={() => setChatOpen(true)} />

      <main>
        <Hero />
        <ExperienceSection />
        <FinalCta onGetStarted={() => setChatOpen(true)} />
      </main>

      <LandingFooter />
    </div>
  );
}
