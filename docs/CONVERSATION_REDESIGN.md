# Conversation frontend redesign

## Audit before implementation — 16 September 2026

The existing application is Oracle JET 20.1 with Preact functional components, TypeScript and RequireJS/AMD. `BankingApp` restores the authenticated session and selects hash routes. The default route is the assistant; conventional account, transaction, transfer, payment, product, profile and administrator screens are already connected.

`services/auth.ts` owns authenticated HTTP, sessionStorage credentials, refresh coalescing, session expiry, timeouts and safe recovery. Banking and conversation services adapt existing endpoints. Component hooks own view state; there is no separate state library. Financial forms validate decimal amounts and use integer minor units for arithmetic. Navigation guards protect drafts and pending requests. Conversational workflow IDs, explicit confirmation, durable transfer review IDs and uncertain-result recovery must remain intact.

Voice uses browser SpeechRecognition and speechSynthesis. Microphone activation is explicit and recognized text is reviewed before sending. Existing phases are idle, listening, stopping and review; errors return users to typing. English/Hindi speech selection currently translates only a handful of labels. Formatting is primarily fixed to en-IN.

The component library is native semantic HTML over custom CSS: Panel, State, Modal, Detail, Status and structured banking responses. Dialogs already use native modal focus management. Forms use native constraints plus domain validation. Conversation log announcements and mobile viewport handling already exist.

Problems: conversation styling is overridden repeatedly across two files with conflicting paper/green/blue palettes; navigation is mixed into history; the composer is visually busy; the first screen offers little context; insights are plain definition lists; language changes do not localize most controls; account comparisons scroll sideways on phones. Shared status/detail presentation is duplicated. Backend narratives and proposal labels are server-owned and must not be translated by string guessing.

Baseline: all 54 existing frontend tests passed. The working tree already contained substantial frontend and backend edits. This implementation preserves those edits and does not edit backend sources, API endpoints, payloads, auth handling or financial execution rules.

## Implementation and verification

### Architecture and interaction changes

The assistant remains the default hash route. Its shell now has secondary banking navigation, a central conversation canvas and account context. Existing account reads in `Workspace` feed the context panel, avoiding a second account API client. A successful balance response or completed workflow refreshes that context. A hide/show control affects context-panel balances only; explicit balance requests remain visible in message history.

The welcome view contains four reusable quick actions. Accounts, transactions and insights open inside the conversation; contextual follow-up buttons use the same existing send pipeline. The conventional money-transfer form remains available through Send Money, and all existing banking routes remain available. No local intent classifier or replacement banking logic was added.

`locale.ts` centralizes English/Hindi UI copy and the language preference. Only the preference is stored in localStorage. Conversation, draft, pending action and speech state stay in their existing components. The application updates language in place; language switching does not remount the conversation. Currency/date helpers use the selected locale and retain the existing exact minor-unit arithmetic. Server narratives, merchant names, account names and workflow choices remain authoritative and are not translated by inference. Narrative language attributes distinguish Hindi from English for speech output.

### Added and refactored components

| Component/file | Change |
| --- | --- |
| `components/chat/ConversationTools.tsx` | Shared banking SVG icons, QuickAction and AccountContext with loading, empty, error and balance-visibility states. |
| `components/chat/SpendingSummary.tsx` | Category bars, exact spending totals and accessible text values. Different currencies are never summed together; invalid data is not displayed as a plausible total. |
| `components/LanguageSelect.tsx` | Shared language control used by sign-in and conventional banking. |
| `components/chat/ConversationWorkspace.tsx` | Redesigned shell, welcome canvas, compact composer, secondary navigation, context drawer, follow-ups and expiry refresh. Preserves conversation pagination, deletion, retry IDs, speech and draft guards. |
| `components/chat/WorkflowCard.tsx` | Localized explicit review; fees and timing state when bank data is unavailable. Confirmation rechecks expiry at click time; no action runs merely because a proposal is rendered. |
| `components/chat/BankingCollection.tsx` | Responsive comparison grid replaces the horizontal carousel and nested scrolling. |
| `components/chat/BankingResponse.tsx`, `MessageBubble.tsx`, `AssistantResponse.tsx`, `ConversationHistoryItem.tsx` | Localized labels, status/formatting reuse and structured spending dispatch. |
| `features/banking/ui.tsx` | Existing State, Panel, PageHeading, Detail and native Modal use shared localized copy. Modal is reused for account context. |
| Authentication, account, payment, product and profile screens | Existing forms and native constraints retained; common visible labels and controls connected to the language catalog. |
| `styles/tokens.css` | Single shared palette, spacing, typography, radii, shadows and motion tokens; documents layout breakpoints. |
| `styles/conversation-first.css`, `conversation-results.css`, `usability.css` | Conversation layout separated from structured-result styles and conventional banking refinements. Teal, warm neutral backgrounds and restrained surfaces replace competing palettes. |

Native buttons, inputs, selects, textareas and disclosure controls remain the primitives. Existing Modal/Panel/State/Status/Detail were adapted instead of adding an unused parallel component library. No new dependency or infrastructure was introduced.

### Cleanup

No previously existing source file was deleted. Removed the obsolete carousel ResizeObserver/scroll/arrow-key implementation, desktop messenger overrides embedded in result styles, and overlapping conversation rules in `usability.css`. Replaced the old conversation stylesheet and extracted its useful structured-response rules into `conversation-results.css`. Existing user changes and unrelated styles were retained.

### Accessibility and responsive behavior

- Semantic landmarks, labelled controls, conversation skip link, textual status labels, visible focus and live conversation/loading/error announcements.
- Native account-context dialog with focus containment, Escape dismissal and focus restoration. The existing history drawer retains its keyboard trap and inert background.
- Text is always available alongside opt-in voice. Recognition, transcript review, processing, microphone-denied and error/recovery experiences use the existing speech implementation.
- Shared controls target at least 44px, review actions become full width on phones, and reduced-motion rules disable transitions/animations. Charts expose the same values in text and do not rely on color alone.
- At 1280px and above: rail + conversation + account context. At 900–1279px: rail + conversation, with context in a dialog. Below 900px: history/navigation drawer. Below 600px: account comparisons stack, context becomes a bottom sheet, composer stays within the visual viewport.
- The conversation feed is the main scroll area; the welcome view starts at its top. Longer conversations, translated labels and response cards wrap without horizontal page scrolling.

This is an accessibility improvement toward WCAG 2.2 AA, not a certification. A complete assistive-technology audit remains a release gate.

### Checks completed

- Baseline: 54 frontend tests passed before refactoring. Final: 58 tests passed.
- `npm run lint`: 33 source files, zero errors.
- `npm run typecheck`: passed.
- `npm run build`: passed.
- `npm run build -- --release`: passed, including Terser and RequireJS optimization. Existing tooling reports optional Sass and Node deprecation notices, without failing the build.
- `git diff --check -- apps/web docs/CONVERSATION_REDESIGN.md`: passed.
- Added regression coverage for currency-separated exact spending totals, invalid spending data, Hindi formatting and a proposal that expires between rendering and clicking confirmation.
- Preserved existing regression checks for session refresh, sign-out, authorization, malformed/network responses, uncertain financial results, duplicate confirmation/deletion clicks, draft navigation, profile/resource loading and voice review/denied permissions.
- Browser fixture checks: desktop 1440×900, phone 390×844, Hindi review at 320×800, tablet 768×900; account summaries, spending bars, explicit transfer review, language switching with an unsent draft, balance visibility, dialog Escape/focus restoration and history navigation. At 320px, the page had no horizontal overflow, the composer bottom equalled the viewport height, and both review buttons measured 244px wide.

Browser tests use the existing local fixture server, enhanced for insight/workflow responses. Its action route now matches the existing conversation service contract. These fixtures are excluded from production and do not move real funds.

The fixture confirmation was also completed through the UI: the original proposal became read-only history and the completed response exposed its receipt reference with no confirm button. Reloading the optimized release retained that history and produced no browser console errors. Conventional overview navigation and Hindi switching were smoke-tested as well.

### Backend assumptions and unchanged boundaries

No backend files were changed by this redesign. API paths, payloads, action IDs, session storage, refresh behavior, authentication, authorization, business validation, transfer execution and durable retry handling are unchanged. The existing working-tree backend edits belong to earlier work and were not reverted or modified.

The current insight envelope supplies categories and a date range, but no previous-period comparison or related transaction subset. The UI therefore shows the supplied period and exact category totals, explains that comparison is unavailable, and links to existing transaction history. It does not invent comparison figures.

Conversational review envelopes do not provide a fee field or general payment-rail/ETA metadata. Missing values are labelled accordingly; own-account transfer uses the existing Nexa confirmation flow. Transaction rows retain existing type/category/status data; no unsupported payment method is invented. External-bank/UPI execution and named-recipient coverage remain governed by existing backend capabilities. Existing simulation boundaries are unchanged.

### Remaining release work

Run authenticated end-to-end checks against the configured bank API/Oracle environment with designated test accounts, including all product and administrator operations; this task used frontend contract tests and local fixtures. Have a native Hindi reviewer audit the catalog, remaining composed legacy notices and server-authored responses. Complete screen-reader, physical mobile-keyboard and cross-browser voice testing. Backend support is needed before adding genuine previous-period comparisons, rail/fee/ETA metadata, and server-localized narrative/choice labels. No real transfer, deployment or backend integration test was run in this task.
