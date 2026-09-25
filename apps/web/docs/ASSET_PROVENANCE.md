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

