# Modern Heritage usability refinement

The existing Preact / Oracle JET application, banking routes and service contracts are retained. The final shared layer is `apps/web/src/styles/usability.css`; existing layout styles use its semantic palette rather than introducing another component library.

## Changes

- Ivory surfaces, indigo structure, saffron primary actions and explicit success/error labels. Shared 8/12/16/20px rounding, 48px controls, focus outlines and reduced motion.
- Larger banking labels and mobile menus; flexible button text, stacked mobile details and wrapping pagination/actions.
- Compact sign-in layout, visible field borders, inline email/password guidance, a correctly associated password label and descriptive progress/error copy. Failed sign-in retains entries.
- Text labels on chat navigation and Send, visible message label, simpler welcome copy and shorter voice guidance. Existing Hindi speech review remains available.
- Shared offline notice with listener cleanup and no automatic replay. Startup loading/recovery content appears while scripts load. Server failures do not display internal error details.
- Existing payment guards and review-only payment limitations remain intact. Confirmation labels describe the operation; closing payment review preserves the form.

## Validation

- `npm run build`: passed. Existing optional Sass and Node deprecation warnings remain; no new packages were added.
- `npm run typecheck`: passed.
- `npm run lint`: passed, zero errors.
- `npm test`: 41 passed. Coverage includes money precision, navigation guards, request deduplication, uncertain transactions, workflow confirmation, offline event lifecycle and sanitized server failures.
- Browser checks with `tests/messenger-fixture.cjs`: 360px chat and overview; 320px payment form/review dialog, menu, empty bills and sign-in; 1280px chat/balance response. Document width did not exceed viewport in measured screens. Login/chat controls measured at least 44px; shared controls target 48px.
- Payment review returned the explicit no-money-moved outcome. Closing it retained payee and amount; completing review cleared the form as before.
- Failed sign-in retained the typed email and password (visually verified) with plain recovery text.
- Chat failure showed a visible retry. Retry completed the same message successfully. Loading controls were disabled while waiting.
- Browser viewport override was reset after verification.

## Text contrast

| Foreground / background | Ratio |
| --- | ---: |
| Indigo / ivory | 12.42:1 |
| Indigo / saffron | 6.30:1 |
| Muted text / ivory | 5.16:1 |
| Dark text / white | 14.76:1 |
| Success green / success surface | 4.64:1 |
| Error text / error surface | 6.30:1 |
| Warning text / warning surface | 6.30:1 |

All listed pairs exceed WCAG AA's 4.5:1 threshold for normal text. These checks cover the shared semantic pairs, not a certification of every rendered pixel.

Browser validation used synthetic banking data, not live financial transactions. Real-device sunlight, physical Android keyboards, actual intermittent cellular service and human screen-reader/local-language usability testing remain deployment validation work. Offline UI is advisory and does not cache financial data or promise offline banking.

## Contemporary theme follow-up

The later visual refinement supersedes the ivory/saffron palette above with cool off-white (#F5F7FB), white cards, navy (#203454), pale blue selection surfaces and soft amber (#FFBC57). Typography uses a local system sans-serif stack; cards have restrained shadows and the balance panel has a subtle navy gradient.

Custom native scrollbars cover the page, chat, history, inputs and overflow collections. Chromium/WebKit receive rounded handles, transparent tracks and no arrow buttons; Firefox receives the themed standard scrollbar fallback. Dark navigation has lighter handles. High-contrast system mode remains supported, with no JavaScript scrolling replacement.

Follow-up verification: build and lint passed; desktop overview and 360px chat inspected with no horizontal page overflow. Computed styles confirm custom 12px scrollbars on the document, chat feed, history and textarea. Updated text contrast: navy/background 11.65:1, navy/amber 7.49:1, muted/background 5.53:1. Native scrollbar appearance can still vary with browser and operating-system support.
