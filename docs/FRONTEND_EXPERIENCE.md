# Frontend experience migration

`apps/web` is the sole frontend application and owns the migrated visual design, authentication, APIs, permissions and workflows. The former `apps/frontend` design prototype was removed after confirming that it contained only local banking simulations and no backend integrations.

## Capability map

| Capability retained from web | Presentation |
| --- | --- |
| Login, registration, refresh, expiry, logout, English/Hindi | Split artwork/authentication screen and workspace language controls |
| Persisted conversations, pagination, deletion, retry, workflow expiry | Conversation sidebar and chat canvas |
| Voice capture, transcript review, cancellation, spoken replies | Composer and conversation options |
| Account context and balance privacy | On-demand account dialog |
| Account opening/editing, details, statement download | Banking workspace |
| Transaction filters, pagination, detail navigation | Activity workspace |
| Own-account/payee transfers, review, confirmation, cancellation, receipts, uncertain-result retry | Send-money workspace |
| Payee lookup, creation, activation and management | Payees in banking navigation |
| Cards, bills, mandates, scheduled payments, loans and product operations | Banking navigation and shared panels/dialogs |
| Profile editing and session/security information | Workspace settings |
| Admin account search, details, adjustments, audit, related accounts | Administration workspace |
| Admin loan approval/rejection and bank funding | Loan request queue |

Service modules, request IDs, review/confirm boundaries, navigation guards and role checks remain the functional baseline. No preview balances or simulated authentication are substituted for API responses. Static reference illustrations are presentation assets only.

## Design

The reference's inset rounded frame, narrow icon rail, separate sidebar, white conversation canvas, charcoal actions, coral accents, DM Sans/Manrope typography and split authentication artwork apply across the application. Additional banking and administrative capabilities use the same design tokens and workspace shell.

OJET VComponent/Preact is the existing Oracle JET runtime, not React. New interactive design components use Oracle JET controls; no third-party UI framework is added.

## Verification (18 September 2026)

- TypeScript checking and strict frontend lint pass.
- All 79 frontend regression tests pass. The suite covers authentication, roles, routing, navigation guards, transfer review/confirmation, duplicate suppression, uncertain-result recovery, conversation deletion, voice review, product operations and admin loan/account actions. New tests exercise OJET auth value events and prevent programmatic select updates from clearing transfer drafts.
- Live tests passed against the existing local API using the documented demo login: authentication/ownership, account and product reads, filtered transaction history, and non-executing payment preparation. No live payment or administrative approval was executed for this redesign.
- Browser checks used the UI fixture server: desktop conversation/account navigation, account dialog, sign-out and authentication artwork, landing page, transfer review/edit and retained values, and phone navigation/chat.
- A normal OJET build initially passed. Subsequent default staging builds encountered Windows `EBUSY` locks in generated JET theme images. Final verification used an isolated staging directory under ignored `.tools`, the Oracle JET build pipeline, and explicit TypeScript emission; browser checks ran against that output. No build configuration or dependency versions were changed.

The design blueprint was incorporated into `apps/web`, including its illustrative landing assets. Asset provenance is retained in `apps/web/docs/ASSET_PROVENANCE.md`. The redundant prototype and its simulated banking/session implementation have been removed; the original source remains in Git history. Existing native controls in retained business screens remain framework-free; the new shared actions, authentication fields, language selector, transfer controls, progress indicator and dialogs use Oracle JET.

## Loading and contrast follow-up

The initial HTML now presents the reference five-facet Nexa logo animation while JET and the session load. The cover waits for session restoration, preserves slow-loading recovery, and skips motion for reduced-motion preferences. App render errors reveal the recovery screen.

Removed inherited inverse text rules from the peach balance panel, strengthened muted text across light surfaces, and assigned explicit foregrounds to coral panels, JET action states, landing previews and footer text. Browser contrast checks cover the landing/auth screens, chat, overview, accounts, transfer, transaction history, payments, cards and settings; phone landing/auth checks also pass. Regression tests cover loader timing, slow sessions, reduced motion, cleanup, recovery, and the new palette pairs.

Final follow-up verification: standard `npm run build`, `npm run typecheck`, `npm run lint` and all 83 frontend tests pass. The standard build completed without the earlier Windows staging lock. No backend/service behavior changed in this follow-up.

## Session-aware landing page

The root page and landing-section anchors are available to guests and signed-in users. Workspace branding opens `#home` without signing out. Customer actions return to conversation, accounts and profile; administrator actions return to administration and loan requests. Guest actions offer sign-in and registration. Landing sign-out/expiry changes the page to its guest state, while expiry on protected screens still returns to login. Navigation guards continue to protect drafts. Mobile menus expose the same session actions.
Verification: standard OJET build, TypeScript, strict lint and all 88 tests pass. Browser checks confirm desktop workspace-to-landing navigation retains the session, mobile session menus adapt after sign-out, and guest registration remains accessible.
## Clearer activity and transaction history

Recent activity and history now share an Oracle JET ListView with a consistent merchant/category/date column and a right-aligned amount/outcome column. Pending and failed amounts are presented as requested amounts, without a completed debit/credit sign. Detail links, account selection, search, filtering and pagination retain their existing backend integrations. Additional history filters use an OJET collapsible to keep the mobile list visible sooner.

Activity at a glance is a compact account snapshot: money in, money out and up to four spending-category rows rendered with OJET Core Pack meter bars. It uses the latest returned page (up to 15 transactions), explicitly labels that scope, excludes non-completed records, preserves exact paise arithmetic and refuses invalid or mixed-currency totals. More than four categories are combined into a labelled Other categories row without dropping amounts. Empty accounts/spending and loading/error states remain explicit.
Verification: the standard OJET build, TypeScript, strict lint and all 93 frontend tests pass. Browser checks covered desktop/mobile row layout, category meters, pending transaction details, category/direction filters, pagination, and switching between populated and empty account summaries. The UI fixture now respects transaction filters and pagination so those checks exercise realistic responses.

## Copy and composer spacing

Removed repeated instructional copy and decorative taglines from the conversation, sidebar brand and authentication footer. The message field, microphone and send action now share one compact row with 44px action targets. Multiline text still expands and returns to the compact height after sending; editable voice review and its explicit send action remain available. Composer metadata and welcome spacing are reduced.

Verification: standard OJET build, TypeScript, lint and all 93 tests pass. UI fixture browser checks confirm desktop and phone layout, multiline expansion, successful submission/reset, and the retained voice review. No backend behavior changed.
