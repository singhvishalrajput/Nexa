# Frontend cleanup — 16 September 2026

This audit starts from the existing working tree, including its previous design and banking changes. No backend source, API contract, authentication implementation, financial execution logic, or package manifest was changed.

## Usage evidence and decisions

| Area | Evidence | Cleanup / retention |
| --- | --- | --- |
| Entry and routes | `index.ts` → registered `App` → `BankingApp`; hash routes dispatch to the conversation, accounts, transactions, payments, money transfers, products, settings, security and administrator screens. | All active screens retained. Customer denial of administrator routes and unknown-route recovery remain. |
| Status and detail components | `BankingResponse` duplicated the markup already owned by `features/banking/ui.tsx`. Searches found no other consumers of its `StatusBadge` or `DetailRow` exports. | Migrated every conversation consumer to `Status` and `Detail`, then removed both implementations. Conversation CSS keeps its compact presentation; accessible labels and definition-list semantics remain. |
| Profile heading | `AccountSettings` has one consumer in `BankingApp`, immediately after `PageHeading`; its internal heading was always hidden by banking CSS. | Removed the hidden duplicate heading and its styles. The visible route heading and focus management remain. |
| Overview decoration | `bank-hero-art` appeared only in the overview and was always hidden by the current stylesheet. | Removed markup and both the original and hiding rules. |
| Navigation | `WORKSPACE` was repeated on consecutive Chat and Overview items. | Kept the group label once. Kept every navigation destination and mobile navigation implementation. |
| Stylesheet ownership | `usability.css` was loaded immediately after `banking.css`. | Moved its conventional banking refinements into `banking.css` at the same cascade position and removed its link/file. Removed 80 superseded declarations, using identical selectors or verified auth/profile consumers beneath the banking root. Conditional layout rules remain. |
| Private palettes | Banking and conversation aliases only pointed at existing shared tokens. | Replaced consumers with shared tokens, removed private alias declarations and `nexa-text-soft`, and removed three unreferenced spacing/type tokens. Dynamic visual-viewport properties remain. |
| Starter styles/assets | Full source/config searches found no consumers of the old navbar/loading styles, Oracle logo, avatar images or app icon font. The JET favicon had one HTML reference. | Removed unused assets and styles; migrated the favicon reference to a small Nexa SVG. Kept Oracle JET build injection markers and required framework runtime. |
| Responsive collections | The former `horizontal` label now drove a wrapping comparison grid, with no carousel behavior. | Renamed the layout/class to `comparison` and updated its test. Preserved single-item and row layouts. |
| Empty states | `EmptySummary` looked up an English message using an already-translated heading. | Look up by the stable copy key and translate the selected message. Added a Hindi empty-bills regression test. |
| Forms | Profile field border styling overrode the shared control-boundary token; auth still added a legacy focus shadow. | Use the shared boundary token and shared focus treatment. Validation, labels, native constraints and submission locks remain. |
| Code and dependencies | TypeScript lint checks unused imports/locals/parameters. A source-wide exported-name scan found no uniquely unreferenced named declarations. | No speculative feature, API, utility or dependency removal. Dependency manifests and lockfile unchanged. |

The cleanup removes five starter assets and adds one branded favicon, removes one stylesheet, and reduces source CSS by approximately 4 KB. This is targeted consolidation, not a new component framework or a wholesale CSS rewrite.

## Verification

- Baseline: 61 frontend tests passed. Final: 62 tests passed.
- Lint: 33 source files, zero errors. TypeScript check passed.
- Optimized release build passed, including Terser and RequireJS. Existing optional-Sass and Node deprecation notices remain.
- CSS parsing, custom-property reference checks and `git diff --check` passed.
- Local fixture browser checks: conversation balances, transaction list/detail navigation, explicit transfer review and completion receipt, profile save, sign-out, and sign-in failure recovery.
- Route smoke checks: accounts, transactions, payments, beneficiaries, cards, bills, mandates, scheduled payments, loans, settings, security, customer access denial for operations, and unknown-route recovery. No alerts or horizontal page overflow in these 390px checks.
- Visual checks at desktop, 768px tablet, 390px phone and 320px sign-in widths. Final profile fields resolve to the shared font and control-border tokens.
- Keyboard checks: modal heading focus, visible Tab focus, Escape dismissal, and focus restored to the triggering control. Accessibility-tree checks retain labelled controls, status announcements, transaction definitions and confirmation details. Browser console reported no errors.
- Existing regression tests cover session refresh/revocation, authorization errors, draft navigation, stale responses, duplicate confirmations, expired reviews, uncertain financial responses and voice-input recovery.
- `npm run test:live` was attempted and failed its prerequisite assertion: `NEXA_TEST_EMAIL` and `NEXA_TEST_PASSWORD` are not configured. No live-bank validation or real money movement was performed. Browser mutations used the existing local fixture server only.

## Deliberately retained and follow-up work

Keep the conventional banking routes alongside the conversational experience: they still provide active account, product, payment and administration workflows. Keep typed API adapters, versioned/historical response handling, unsupported-content fallbacks, refresh coalescing, retry IDs, draft guards, pending locks, validation, native dialogs, live regions, reduced-motion and forced-colors support. These have active consumers or protect recovery and accessibility.

Keep distinct asynchronous states where they represent different scopes: session bootstrap, account context, paged transaction requests and financial confirmation do not have interchangeable state lifecycles. No stale feature flag or abandoned API flow was proven removable.

The banking stylesheet still contains older base rules with more-specific responsive and presentation refinements. Further flattening should use visual regression coverage across every product/detail screen rather than assume equal-looking selectors are equivalent. Remaining partial localization, conventional Unicode navigation icons versus conversation SVG icons, and broad cross-browser/assistive-technology coverage should be addressed separately. Run the live suite with designated test credentials and verify administrator mutations in the configured backend environment before release; fixture checks do not certify those integrations.
