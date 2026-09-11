# Banking frontend

The application entry point now renders the authenticated banking workspace. It uses the existing Oracle JET / Preact runtime, REST services, identity model and banking core. No runtime dependencies, backend endpoints, migrations or synthetic banking data were introduced for this frontend.

## Screens and API contracts

All customer paths below are relative to the configured `/api/v1` base URL.

| Screen / route | Existing services |
| --- | --- |
| Login, registration and session restoration | `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/me` |
| Overview and accounts (`#/overview`, `#/accounts`, `#/accounts/:id`) | `/accounts`, `/accounts/:id`; zero-balance account opening through the existing account service |
| History and details (`#/transactions`, `#/transactions/:id`) | `/transactions` with account, search, category, direction, date range and pagination; `/transactions/:id` |
| Cards, bills, payees, direct debits, loans and scheduled payments | Corresponding `/cards`, `/bills`, `/beneficiaries`, `/mandates`, `/loans`, `/scheduled-payments` list/detail endpoints |
| Payment review (`#/payments`) | `/actions/prepare` for transfers, bill/card payments and mandate cancellation eligibility |
| Profile and security | `/me` GET/PATCH; existing logout/revocation |
| Ask Nexa | Existing conversation workspace and conversation APIs |
| Administrator operations (`#/operations`) | Authorized `/api/accounts` and `/api/transactions/deposit`, `/withdraw`, `/transfer` |

Product search is explicitly limited to the displayed page. Supported product lists have status filters and pagination. The beneficiary API returns a capped collection and does not support pagination. Dashboard movement totals describe completed transactions in the displayed recent activity, not an invented monthly total.

## Structure and safeguards

- `apps/web/src/features/banking/BankingApp.tsx`: session gate, hash routes, responsive shell and role-aware navigation.
- `Accounts.tsx`, `Products.tsx`, `Payments.tsx`: feature screens, account opening, details and confirmations.
- `api.ts`: typed adapters around existing authenticated transport and domain models.
- `ui.tsx`, `utils.ts`: asynchronous loading, retries, accessible modal dialogs, status labels, route parsing and amount utilities.
- `apps/web/src/styles/banking.css`: scoped banking design, responsive navigation/forms/cards and reduced-motion support.
- Existing authentication, profile editor and conversation components remain integrated. The previous landing/demo shell is no longer the application entry point; existing unrelated source files were preserved.

The client follows the existing session-storage token architecture. Concurrent authentication failures share a single refresh attempt; refresh results cannot restore a session after logout. Temporary restoration failures provide a retry without discarding credentials. Mutations are not automatically replayed after network failures. Account opening and administrator posting require review, lock repeated submissions, and show confirmed receipts or reconciliation guidance for uncertain responses. Backend authorization and validation remain authoritative. Account/card numbers use the backend masks or explicit masking in administrator selectors.

## Verification

Run from `apps/web`:

```text
npm run lint
npm run typecheck
npm test
npm run build
```

The dependency-free lint command uses TypeScript diagnostics plus rules against unused declarations, debug logging, debugger statements, dynamic evaluation and unsafe HTML insertion in the new banking modules and authentication transport. It is a focused project check, not a full accessibility or ESLint audit. Installing ESLint was blocked by the environment's certificate verification; certificate checks were not disabled.

Final verification passed: build, type checking, focused lint (eight files, zero errors), 21 frontend tests, 65 backend tests and the live API contract check. The build reports the existing optional Sass compiler is absent; this frontend uses CSS and builds successfully without it. Frontend tests include route parsing, precise amount validation, concurrent token refresh, failed mutation behavior, core API errors, restoration and logout races.

For the separate live API contract check, set `NEXA_TEST_EMAIL` and `NEXA_TEST_PASSWORD` to a local test customer's credentials, optionally set `NEXA_TEST_API_URL` (default `http://localhost:8088/api/v1`), then run `npm run test:live`. It verifies login/profile/accounts/balance/history/search, product lists/details, unauthenticated and administrator access rejection, and a non-executing payment preparation with unchanged balance. It logs out its own test session afterward. Credentials are not embedded in the test.

Browser verification covered desktop, tablet and mobile layouts, account activity and empty states, product screens, profile/security, the preserved conversation workspace, session restoration, logout, protected deep links and payment preparation. The account-opening dialog and required-field gating were also checked; browser error logs were empty. Live posting and account creation were intentionally not exercised against the existing database; backend automated tests cover these mutations. No funds were moved during frontend verification.

## Backend limitations

- Customer action preparation returns `executionAvailable: false`. The frontend never presents it as a completed transfer, bill/card payment or cancelled mandate.
- Payee creation/edit/deletion, card freeze/replacement/limit changes, customer schedule/mandate mutations and loan applications have no supported customer write endpoints. Available records are shown read-only.
- There are no notification, password-change, MFA or device-management APIs. Security displays current session information and logout; unsupported controls are not fabricated.
- The backend has no transaction idempotency key contract. The frontend prevents concurrent duplicate submissions and warns after an uncertain response, but durable protection across reloads/devices requires backend idempotency support.
- This work does not constitute a deployment security audit or exhaustive assistive-technology audit. Administrator posting needs a dedicated administrator test account for end-to-end browser validation before production deployment.
