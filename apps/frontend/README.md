# Nexa — Your money. In your words.

A landing page for conversational banking, built with Oracle JET 21, Preact, and TypeScript.

## Run

```sh
npm install
npm start
```

Open **http://localhost:8005/** (the configured project port).

```sh
npm run typecheck
npm run build
npm run format
```

The release build is generated in `web/`.

## Page structure

1. Editorial hero with a stepped portrait, animated voice commands, payment status, and a balanced text-and-portrait layout.
2. The Nexa way: type it, say it, sorted.
3. Everyday possibilities: payments, spending insights, and savings goals.
4. Closing call to action.
5. Footer.

Explore Nexa, Meet Nexa, and Try Nexa open the conversation workspace at `#chat`. Section navigation continues to navigate the landing page. Banking illustrations and chat operations use sample data.

## Conversation workspace

- A responsive chat workspace with Nexa's coral mark, charcoal rail, conversation search/history, new chat, deletion, transcript export, and settings.
- Text prompts return structured balance, spending, savings, and transaction cards. A local intent handler supports these demo operations; no language-model API or banking backend is connected.
- Transfers to Alex, Sam, Priya, or the travel fund require explicit review and confirmation. Confirmation checks the current balance and prevents duplicate debits. Cancel leaves balances unchanged.
- The demo account and conversations are saved to session storage for the current browser tab. Reset demo account clears both after a second confirmation. Do not enter real financial credentials.
- Voice dictation shows an animated waveform and live transcript inside the composer. Silence restarts recognition; Stop ends listening without sending. Permission or service errors end the session with an actionable message. Browser support and microphone permission are required, and some browsers use an external transcription service. [Speech recognition documentation](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition).
- Attach photos or documents with the paperclip: up to five files per message, 10 MB each, with image thumbnails, removal, and download controls. Files remain local to this mounted workspace; only their metadata persists across reloads. No file analysis or upload backend is connected.
- Read-aloud responses are opt-in, with stop controls. [Speech synthesis documentation](https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis).
- Test the demo's balance, payment approval, duplicate protection, savings, and validation rules, plus voice silence recovery, transcript accumulation, Stop, and permission-error cleanup with `npm test`.

Implementation: `src/components/chat-workspace.tsx`, `src/components/banking-demo.ts`, and `src/styles/chat-workspace.css`.

## Login and registration

- `#login` and `#register` use original conversation-portal artwork, distinct welcome headlines, a restrained entrance animation, and compact forms in the same neutral and coral palette as the chat. The layout sizes to the viewport without a fixed minimum page height. Both routes use the branded page transition and custom cursor.
- Registration collects `fullName`, `email`, and `password` only. Phone, date of birth, and address are deferred; profile completion is not implemented yet.
- Inline validation, password visibility, browser autofill, and a password-reset form are available. Forms are accessible by keyboard and adapt to mobile screens.
- These are frontend previews. No authentication API is connected, no account is created, and credentials are never saved to browser storage or logged. Valid submissions show an honest availability message and clear the password. Password reset does not send email.
- Implementation: `src/components/auth-page.tsx` and `src/styles/auth-page.css`. Enter from Log in in the landing navigation or chat header.

## Design and accessibility

- A 1.7-second branded transition on launch and between the landing page and chat: the five original SVG facets assemble, rotate, and settle above the Nexa wordmark. In-page section links stay immediate. Reduced-motion mode uses a 240ms static-mark fade. The destination is prepared beneath the overlay and chat focus returns when it clears.
- White, charcoal, and coral (`#ff6248`) palette based on the supplied reference.
- Manrope headings and DM Sans body typography.
- Outlined arrow cursor with a soft coral glow on fine-pointer devices; native touch behavior is preserved.
- Motion pause/play control, selectable hero scenes, system reduced-motion support, and scroll reveals.
- Four synchronized savings conversations in a compact charcoal panel, with a fanned card stack, continuous transform/opacity transitions, rolling values, and coral progress effects. Scenes advance every 4.2 seconds while visible, with manual scene selection. Page-wide pause and reduced-motion preferences are respected.
- Keyboard focus rings, a skip link, semantic sections, and mobile navigation with Escape dismissal.
- Local hero image and SVG/CSS artwork. Google Fonts require network access; system fonts provide fallback.

## Source

- `src/components/app.tsx`: landing page, visual scenes, motion controls, and custom cursor.
- `src/components/goals-sequence.tsx` and `src/styles/goals-sequence.css`: savings conversation sequence, illustrative answer cards, and responsive motion design.
- `src/styles/app.css`: design system, stepped hero composition, animations, and responsive layouts.
- `src/styles/images/nexa-voice-portrait.png`: generated portrait used by the hero.
- `src/styles/images/nexa.svg`: shared abstract coral-orange brand mark and favicon. Five curved geometric facets surround an open center, paired with the capitalized black Nexa wordmark in the header and footer.

## Generated asset provenance

The portrait was produced using the **built-in image-generation tool**, then copied into `src/styles/images/nexa-voice-portrait.png`. It depicts a fictional person. The supplied website image was used as visual direction for the layout, not copied into the site.

Final generation prompt:

> Use case: photorealistic-natural. Create a premium editorial studio photograph for a conversational banking website hero. Portrait 4:5 composition. A friendly young adult Indian woman with natural wavy dark shoulder-length hair wearing a muted soft coral knit sweater and dark charcoal jeans, cropped just below the waist, standing in a relaxed pose, smiling naturally while holding a slim graphite smartphone slightly in front of her mouth as if speaking a voice command. Modern approachable banking campaign, authentic candid expression, natural hands and skin texture. Subject positioned on the right two-thirds of the frame, generous pale cool gray empty space on the left for interface overlays. Seamless pale gray studio backdrop #e8e8e8, soft diffuse daylight from upper left, subtle shadows. Refined neutral palette with coral clothing, no scenery, no props other than phone, no text, no logos, no UI, no watermarks, no bank cards. Photograph only, not a website mockup.

Banking cards, waveforms, icons, cursor, and other motion effects are local SVG/CSS/TypeScript artwork.

### Closing CTA portrait

- Asset: `src/styles/images/nexa-cta-portrait.png`.
- Created with the **built-in image-generation tool**, copied into this project with its transparent background preserved. The model is fictional.
- The Try Nexa button opens the chat workspace.
- Desktop uses `src/styles/images/nexa-cta-portrait-desktop.png`, an identity-preserving edit made with the built-in image-generation tool. The model points horizontally toward the button beneath the left-side copy. Mobile keeps the original downward-pointing portrait with the button beneath her hand.

Desktop edit prompt:

> Use case: identity-preserve. Asset type: transparent photographic cutout for Nexa's desktop call-to-action. Input image 1 is the edit target. Preserve this exact adult woman's face, identity, smile, dark wavy hair, ivory blazer and top, professional studio lighting, realistic skin texture, right-side torso placement, square canvas and waist-up crop. Change ONLY her extended right arm and hand gesture: she points to HER RIGHT, which is the VIEWER'S LEFT, with one index finger extended straight HORIZONTALLY toward the left edge. Her fingertip should be at approximately 68 percent of the canvas height, aligned with a website button placed outside the image on the left. Natural elbow, wrist and hand anatomy; relaxed professional gesture. Keep the entire hand and full head within the canvas. Keep her gaze directed slightly left toward her pointing direction. Genuinely transparent background with alpha. No downward pointing, no text, no button, no props, no graphics, no watermark. Preserve the original photograph's premium restrained character.

Final generation prompt:

> Use case: photorealistic-natural. Asset type: transparent cutout photograph for the right side of Nexa, a premium conversational banking website call-to-action. Create one adult Indian woman in her late twenties with natural shoulder-length dark wavy hair, wearing a refined ivory blazer over a simple ivory top, minimal accessories. Friendly confident natural smile, head and gaze turned slightly toward the viewer's LEFT. Waist-up editorial studio photograph. Her torso is on the RIGHT side of the composition; her right arm extends naturally across her body toward the viewer's LEFT, with ONE index finger pointing slightly downward toward a button that will sit outside the image to the lower left. Her fingertip should be near the left edge at about 65 percent of image height. The gesture must be unmistakable, relaxed and anatomically correct, with a natural wrist and hand. Keep the full head, hair, arm, pointing hand and torso inside the canvas; clean waist crop at the bottom. Soft premium studio lighting, authentic skin texture, approachable professional banking campaign, restrained styling. Square composition with generous space around the extended arm. GENUINELY TRANSPARENT background with alpha, no backdrop, no ground shadow, no colored outline. The subject will be placed over a coral-orange panel. No text, no words, no button, no phone, no props, no graphics, no logos, no watermark. Photograph only.

### Authentication artwork

- Asset: `src/styles/images/nexa-conversation-portal.png`. Original coral speech-bubble sculpture and charcoal banking card, generated with the built-in image-generation tool.
- Full prompt and provenance: `src/styles/images/nexa-conversation-portal.md`.
- Both authentication routes share the artwork; login and signup use different HTML headlines. Reduced-motion preferences disable the entrance animation.
