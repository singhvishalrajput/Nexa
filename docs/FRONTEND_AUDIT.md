# Frontend audit and cleanup — 11 September 2026

The audit preserves the banking layout, navigation, branding, APIs and backend behavior. It covers the reachable application starting at `src/index.ts`, its shared services, authentication, banking pages and conversation components.

## Fixed

- A resource-level 403 after token refresh could clear a valid session. Refresh failures and resource permission failures now have separate handling.
- Late sign-in/refresh results could interfere with a newer or closed session. Session generation checks and ownership of the shared refresh promise now prevent that.
- Request timeouts stopped when headers arrived. They now cover response-body reading. A malformed successful financial response is treated as uncertain, never automatically replayed.
- Resource changes could briefly expose data for the previous account or record. The shared loader now keys its visible state to the requested resource and ignores late results.
- Routes accepted unsupported detail paths and extra segments. These now use the existing not-found state. Account-specific history links preserve the selected account.
- Profile balances used a separate formatter; chat did not consider `SUCCESS` completed. All banking surfaces now share decimal rounding, currency/date formatting and status presentation. Calendar-only dates retain their intended day outside India too.
- Navigation could silently discard drafts. Account opening, payment review, administrator posting, profile editing and chat now register unsaved work. Leaving asks for explicit discard; pending requests block in-app navigation. Refresh/close uses the browser's unsaved-work warning.
- Closing an uncertain administrator result allowed a fresh review. That form now remains blocked pending reconciliation. Confirmation handlers also validate required account selections and amounts independently of button state.
- Profile/auth fields remained editable while a request was pending. Disabled fieldsets now keep the submitted values stable. Profile success is announced, password visibility has a clear accessible name, and auth mode controls use button semantics rather than incomplete ARIA tabs.
- Account opening review now includes date of birth and address. Dialog step changes receive focus; Escape restores the invoking control.
- Unexpected render failures now show a recovery screen with a financial-reconciliation reminder instead of leaving a blank application.

## Cleanup and consistency

- Removed 24 unreachable legacy TypeScript/TSX files, including duplicated dashboard, account-opening, chat-shell and mock banking implementations. The live conversation workspace and Oracle JET entry point remain.
- Removed 13 obsolete stylesheets. Extracted the profile styles still used by the live app, and replaced the obsolete global landing-page styles with a small base reset. Six application stylesheets remain.
- Removed unused imports and the unused bulk transaction helper. Account collection requests now reuse the existing service.
- Lint now checks all 22 application TypeScript/TSX files, rather than a selected subset. It remains a dependency-free TypeScript/AST lint check, not a full ESLint or automated accessibility suite.
- Fixed dark-card text contrast, a primary-action color collision, sidebar label contrast, narrow navigation wrapping, profile typography, long amounts and dialog bounds. Shared money, status, loading and navigation-guard patterns are reused across screens.

## Validation

| Check | Result |
| --- | --- |
| `npm run build -- --release` | Passed, including minification and module optimization |
| `npm run typecheck` | Passed |
| `npm run lint` | Passed: 22 files, zero errors |
| `npm test` | Passed: 32 tests |
| `npm run test:live` | Passed against the existing local API |
| Browser release errors/warnings | None observed |
| Customer page viewport pass | Overview, accounts, transactions, payments, payees, cards, bills, direct debits, schedules, loans, profile and security at approximately 1200, 960 and 325 CSS pixels; no page-level horizontal overflow or API errors |
| Details and critical interactions | All six product detail types; empty account history; large-amount review; non-executing payment confirmation; real chat balances/history; login, logout and protected links; dialog focus/Escape |

Regression coverage includes stale loading responses, unsaved-work guards, session races, permission failures after refresh, body-read timeouts, uncertain mutation responses, route validation, financial rounding and local calendar dates. Live API checks also verify ownership/access boundaries, transaction search, account balances, product lists/details and unchanged balances after payment preparation.

No funds were moved. The existing local records were not modified by posting, profile updates or account opening; chat checks saved ordinary test requests through the existing conversation API. The backend was unchanged, so its existing automated mutation coverage was retained rather than replaced with changes to the live database.

## Remaining verification limits

- Customer payment execution is still unavailable in the backend; preparation is clearly labelled and does not submit money.
- Positive administrator posting needs a dedicated administrator test account. The customer authorization denial, client safeguards and existing backend tests do not substitute for that final deployment check.
- Physical microphone capture and a full screen-reader/device audit require appropriate hardware and assistive technology. Speech lifecycle/failure tests and browser keyboard, labels and layout checks passed.
- Hindi voice capture does not provide full Hindi banking understanding or application translation; existing backend language support is unchanged.
- The release build still reports that optional Sass compilation is skipped because Sass is not installed. The app uses CSS and builds successfully without it.
