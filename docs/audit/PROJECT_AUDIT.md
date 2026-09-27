# Nexa project audit — 25 September 2026

> Folder cleanup update: `apps/frontend` and `tools/demo` were removed from `purvak` on 27 September 2026. This audit retains its original baseline and findings; see the [current repository layout](../REPOSITORY_LAYOUT.md).

Repository baseline: branch `rb-fe`, commit `2d5ce83`. Scope: tracked source, API routes and services, persistence/entities and migrations, configuration, both frontend applications, tests, CI, documentation and operational/demo scripts. The baseline contains **443 tracked files**: API 217, integrated web 129, prototype frontend 60, docs 24, scripts 6, demo tools 3, root/CI 4.

This audit is a historical snapshot taken before the account-opening implementation. Its findings and line references describe the baseline above. Subsequent local changes add reviewed account opening, generated receipts, receipt-funded activation, demo retirement and teammate setup; see [the upgrade notes](../ACCOUNT_OPENING_UPGRADE.md). Other audit findings remain open unless explicitly addressed there. No running Oracle schema, live customer data, external deployment configuration, production traffic, or installed Ollama model was inspected during the audit.

Related notes: [Database and table review](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/DATABASE_REVIEW.md); [Separate unused/legacy inventory](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/UNUSED_AND_LEGACY.md); [All 443 tracked files classified by role](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/FILE_INVENTORY.md).

## Overall assessment

Nexa is a substantial learning/showcase application with working authenticated banking flows. The repository explicitly describes it as a showcase. It has meaningful financial safeguards, but it is not ready to hold real customer money. The main architectural issue is inconsistent policy across older and newer APIs, combined with an overloaded database model and incomplete external banking workflows. Preserve the current frontend design while fixing the backend contracts.

The two concerns raised by the owner are valid with these qualifications:

- **Account opening has basic validation**, including required/length-limited name and address, past DOB, allowed account type, INR, customer role and unique account number. What is missing is verified identity/contact information, eligibility policy, and an application/review stage before activation.
- **Opening creates zero balance**. Admin credits do use transactional journals/ledger entries and a system cash counter-account. They are not a bare `UPDATE balance`. However, a ledger entry does not prove cash was actually received. There is no required external receipt/settlement evidence or second-person approval. Older admin routes additionally bypass the newer reason/actor/idempotency controls.

Severity below distinguishes **confirmed defects**, **design/policy weaknesses**, and **production gaps**. “High” identifies money integrity, access control or critical workflow problems; it is not a claim that an unauthenticated attacker can exploit every item.

## Findings and remedies

### Account opening, identity and access

**A01 — High production gap: unverified customers receive active accounts immediately.**  
[OpenAccountRequest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/OpenAccountRequest.java:7) validates shape, not identity. [AccountOpeningService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountOpeningService.java:42) creates the account without an application/verification decision; [Account.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/Account.java:95) defaults status to ACTIVE. Registration issues a session immediately ([AuthenticationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticationService.java:67)). There is no verified email/phone, identity-document workflow, eligibility/minor-account handling, or account-opening approval. A past date alone accepts a very recent birth date; it is not an age policy.  
**Improve:** separate login registration, contact verification, account application, identity review, approval/rejection, and activation. Define the product's age/guardian rules instead of assuming a universal minimum. Only activate after the required checks. Retain zero opening balance.

**A02 — Medium defect/design gap: account-opening retries can open additional accounts.**  
[AccountOpeningService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountOpeningService.java:69) allocates a fresh number on every call; no application/request key or duplicate-submission guard exists. A successful request followed by a lost response and retry can create a second account. There is also no explicit multiple-account policy. Account-number collision checking happens before insertion; the DB unique constraint protects uniqueness but a concurrent collision has no retry around insertion.  
**Improve:** durable application ID/idempotency key; explicit allowed account counts/types; retry generated-number collisions safely. Multiple accounts may be intentional, so do not simply ban them.

**A03 — Medium defect: partially completed customer profiles are handled inconsistently.**  
Registration already inserts a customer with no DOB/address. [CustomerQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerQueryService.java:40) routes profile editing through [CustomerServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CustomerServiceImpl.java:53), which requires an address even when a request only edits a name/phone. This can reject an otherwise valid update before account opening. [AccountOpeningService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AccountOpeningService.java:64) overwrites both DOB and address if either is missing; when both exist, newly submitted values are silently ignored.  
**Improve:** distinguish optional profile edits from verified identity fields; PATCH only supplied fields; make DOB/address verification and change policy explicit.

**A04 — High production gap: unilateral account funding has no evidence or approval workflow.**  
[AdminAccountService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AdminAccountService.java:208) has useful controls: reason, role lookup, UUID request, matching retry payload, and posted deposit/withdrawal. It still permits one administrator to credit/debit a deposit account without documented cash receipt, incoming-settlement reference, adjustment category, limit or independent approval.  
**Improve:** separate cash deposits, confirmed incoming transfers, reversals and exceptional adjustments. Each needs an accountable source and reference. Large/exceptional corrections should require a second approver. Keep fictional demo funding isolated and labelled.

**A05 — High defect: older admin money routes bypass newer audit and retry controls.**  
[TransactionController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/TransactionController.java:19) directly exposes deposit/withdraw/transfer without request ID or reason. [TransactionServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionServiceImpl.java:62) generates a new transaction per request. Retrying a deposit after a lost response can post another deposit. These routes are ADMIN-only, but that does not make duplicate postings safe.  
**Improve:** all routes must call the same idempotent, actor-audited command service; retire unsupported aliases only after checking consumers.

**A06 — High conditional deployment risk: default startup uses a published development signing key and demo profile.**  
[application.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/application.properties:2) defaults to local; [application-local.properties](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/application-local.properties:6) supplies a fixed fallback signing secret and local seed migrations. If this configuration is exposed publicly, knowledge of the repository key undermines JWT authenticity. This audit did not establish that any public deployment uses it.  
**Improve:** require explicit local opt-in; fail deployment startup without a separate strong secret and allowed origins; prevent demo seeds/credentials from appearing in nonlocal environments.

**A07 — High design gap: user suspension, role changes and logout do not consistently invalidate access tokens.**  
Login/refresh check ACTIVE ([AuthenticationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticationService.java:99)); logout only revokes its refresh token ([AuthenticationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticationService.java:119)). [SecurityConfiguration.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/security/SecurityConfiguration.java:115) validates signature/issuer/time, and [AuthenticatedCurrentUserProvider.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/AuthenticatedCurrentUserProvider.java:13) returns the JWT subject without checking current customer status. Most customer paths and older ADMIN paths rely on those token claims. New admin services perform an extra active-role lookup, so behavior differs across routes. An already issued access token remains usable until expiry on affected paths.  
**Improve:** central active-user/session checks, session/token version or revocation policy, consistent live role checks for privileged operations, and session-family revocation on compromise. A documented short logout grace period is a policy choice, but must be explicit.

**A08 — High production gap: no abuse controls or stronger authentication for sensitive operations.**  
No application-level login throttling/lockout, verified contact challenge, MFA/step-up approval, password recovery/change workflow, transaction velocity limits, or trusted-session management was found in the inspected routes/configuration. Hosting-layer controls were not inspected. Refresh rotation does correctly use a row lock and hashed tokens; however, reuse of a revoked token rejects that token without invalidating its whole descendant session family.  
**Improve:** rate limits and monitoring, secure recovery/change flows, MFA for admins and sensitive operations, and clear stolen-session response. Do not build pretend OTP verification that accepts arbitrary values.

**A09 — Medium hardening gap: browser-readable refresh tokens.**  
[auth.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/auth.ts:142) persists the full session in sessionStorage. This limits persistence to the tab but allows same-origin JavaScript to read tokens. No actual XSS exploit was demonstrated.  
**Improve:** consider an HttpOnly Secure refresh/session cookie architecture with its required CSRF controls, short-lived access tokens and deployed CSP. Preserve the current login appearance.

### Money, loans and payment states

**A10 — High defect: generic transfer accounting allows incompatible system account types.**  
[TransactionServiceImpl.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionServiceImpl.java:130) accepts active accounts other than LOAN/CARD, including SYSTEM/CASH. It subtracts from the source and records a DEBIT. Yet the same service's cash deposit logic treats a cash DEBIT as increasing cash. CASH-to-deposit transfers can therefore leave stored cash balance inconsistent with ledger-derived cash balance even while the journal balances.  
**Improve:** restrict ordinary transfers to approved deposit-account types and centralize debit/credit effects by account normal side. Reconcile each balance to its entries, not just journal debit totals versus credit totals.

**A11 — High defect: generic mandate status editing bypasses the real lifecycle.**  
[MandateController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/MandateController.java:56) calls [PaymentItemService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/PaymentItemService.java:46), whose direct update accepts any listed state. A cancelled mandate can be changed back to ACTIVE without the dedicated activation/revocation guards or mandate event. The customer UI exposes these options.  
**Improve:** one locked transition service for activate/pause/resume/revoke; enforce terminal cancellation and audit every change. Explain removal of the free-form status selector before changing the UI.

**A12 — Medium confirmed defect: bill creation fails, and its write/read models disagree.**  
[PaymentItemService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/PaymentItemService.java:37) inserts 14 columns with 13 SQL values and binds 10 arguments to 9 placeholders. Valid create requests fail. Independently, no source account is stored, while [JdbcBankingProductRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/JdbcBankingProductRepository.java:56) inner-joins bills through source_account_id; fixing only the SQL still leaves new bills absent from list/detail.  
**Improve:** define whether bills are customer-owned trackers or account-linked obligations, align both contracts, and cover create → list → detail in a DB-backed test.

**A13 — Medium product distinction: a customer-entered PAID status is not settlement evidence.**  
[PaymentItemService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/PaymentItemService.java:18) allows owners to mark bills PAID/FAILED without a payment. The UI describes bill tracking, so this is not proof of unauthorized money movement. It becomes misleading if the same status is presented as bank-confirmed settlement.  
**Improve:** distinguish “marked paid by you” from “payment settled”, and link settled status to a posted transaction/provider confirmation.

**A14 — High business-policy weakness: new legacy loan applications still allow customer-supplied rates and principal-only servicing.**  
Multipart creation accepts a legacy shape through [LoanDocumentsController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/LoanDocumentsController.java:23) and [CreditMandateService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CreditMandateService.java:263). Omitting scheduled-loan fields permits customer-supplied principal/rate under different limits; the legacy repayment branch at line 383 reduces principal without scheduled interest. This bypasses the modern bank-defined rate, amount and tenure contract. Salary slips and admin approval are still required. Compatibility is explicitly documented; it is an unsuitable policy split for new real loans, not automatic unauthorized disbursement.  
**Improve:** stop new legacy applications; keep deliberate migration/servicing support for existing records; use one bank-owned loan policy.

**A15 — High servicing flaw: full payoff drops other overdue installment interest.**  
[CreditMandateService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/CreditMandateService.java:638) calculates payoff from principal plus only the earliest unpaid installment interest. A full payoff then removes remaining unpaid projections at line 771. Interest attached to other already-overdue installments disappears without settlement or an explicit waiver. The partial-prepayment overdue guard does not cover this full-payoff path. The documented lack of daily accrual/late fees is a separate limitation.  
**Improve:** record due/accrued interest separately from future projections; settle all due amounts or record an authorized waiver before closure. Test multiple-overdue-installment payoff and reconciliation.

**A16 — High user-flow risk: loan/mandate payment recovery is weaker than transfer recovery.**  
[ProductOperations.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/ProductOperations.tsx:186) changes request IDs when amounts are edited, and again for prepayment choice changes at line 273. After a network/5xx error, outcome is uncertain; edits/reloads can lose the original ID and permit a second payment. Keeping the unchanged form does reuse the ID, which is useful but incomplete.  
**Improve:** persist the pending operation ID, prevent resubmission with a new key until status is resolved, and offer receipt/status recovery. Reuse the existing MoneyTransfer recovery pattern.

**A17 — Medium defect: generic loan-account activity can omit real postings.**  
[LoanSettlementService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/LoanSettlementService.java:63) maps public transaction source/destination to the bank settlement account, retaining loan linkage in target_id/ledger. [TransactionQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java:186) filters generic account history only by source/destination. Dedicated loan history and admin activity work differently.  
**Improve:** restrict generic history to deposit accounts explicitly, or derive product-account activity through ledger relationships.

**A18 — Medium product gap: simulations and real operations share surfaces with inconsistent outcomes.**  
[ShowcaseService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ShowcaseService.java:68) now posts real internal payee transfers, but bills/card payments/card controls/showcase mandate cancellation remain simulations. Direct mandate revoke changes the actual mandate, whereas chat/showcase cancellation only records a simulated receipt. Scheduled-payment APIs list records; no automatic execution worker was found.  
**Improve:** explicit server execution modes and receipt types, consistent cancellation behavior, isolated simulation configuration, and truthful feature labels. External payment providers, reconciliation, reversal/refund workflows and automatic scheduling are additional product work; do not imply they already exist.

### Reporting, chat and frontend correctness

**A19 — High reporting defect: spending totals use only the latest 30 matching rows.**  
[TransactionQuery.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/beans/TransactionQuery.java:41) sets spending limit to 30. [TransactionQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java:93) fetches that page; [ConversationInsights.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ConversationInsights.java:81) sums only returned rows into category totals without warning of truncation. Thirty-one ₹100 withdrawals can display ₹3,000 instead of ₹3,100. The separate aggregate SpendingQueryService currently has no method caller.  
**Improve:** aggregate the entire authorized filtered set in the database; paginate only the detail list. Test more than 30 and more than 100 matching transactions.

**A20 — Medium reporting defect: “exclude own transfers” excludes every transfer.**  
[TransactionQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java:85) removes all transaction_type=TRANSFER, including payments to another customer's Nexa account. This disagrees with the message that only transfers between your own accounts are excluded.  
**Improve:** compare source and destination owners. Decide explicitly which outgoing payment categories belong in spending, and use the same rule in chat and activity.

**A21 — Medium defect: identical calendar filters mean different time intervals.**  
Chat uses business-timezone boundaries converted to UTC; direct account history uses raw midnight dates ([TransactionQueryService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/TransactionQueryService.java:201)). A payment at 01:00 IST can fall on different requested days in the two views. Salary insights also choose the first owned account and UTC month, while the main business calendar defaults to Asia/Kolkata. The response does disclose the first-account scope.  
**Improve:** use BusinessDateResolver everywhere, allow clear salary account scope, and test month/day boundaries. JVM UTC configuration exists; LocalDateTime.now alone is not a host-offset defect.

**A22 — Medium defect: chat labels/routes real repayment receipts as generic simulated receipts.**  
[WorkflowCard.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/components/chat/WorkflowCard.tsx:16) infers simulation from two operation names. Real REPAY_LOAN and START_TRANSFER fall through; repayment also gets a Transfer heading, and its receipt goes to generic Payments.  
**Improve:** explicit backend execution/receipt metadata and an operation presentation map. This is a small behavior/copy correction; no layout redesign is needed.

**A23 — Medium performance flaw: slow model calls hold DB locks and transactions.**  
[ConversationService.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/ConversationService.java:140) locks a conversation inside a transaction, then may invoke [OllamaInterpreter.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/service/OllamaInterpreter.java:142) synchronously for up to the configured 45-second default. Many slow requests can occupy database connections and block subsequent writes/deletion.  
**Improve:** separate bounded model interpretation from the short state-validation/commit transaction; recheck version/action state before posting. Bound concurrency and preserve explicit confirmation. Privacy filtering is a useful heuristic, not complete secret detection; keep raw messages out of diagnostic logs and define retention/access policies.

**A24 — Medium UX correctness: list search searches only loaded records.**  
[Products.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/Products.tsx:28) fetches a 12-row page and filters it locally. Conversation history search likewise filters loaded conversations. Existing matches on later pages can appear absent.  
**Improve:** server-side search/pagination or label the loaded-record scope. Preserve the existing controls.

**A25 — Medium scalability/UX gap: client pagination and incomplete navigation protection.**  
[AdminApp.tsx](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/features/banking/AdminApp.tsx:44) downloads all accounts and slices locally; admin services return unbounded lists. Product projections also load histories per product (see database review). Several product creation/payment/payee/upload forms lack the navigation guard already used by transfers/account settings.  
**Improve:** real server pagination and summary DTOs; visibility-aware polling; reuse existing dirty/pending-operation guards. Pending financial outcome recovery takes priority over ordinary unsaved-field warnings.

### Database, tooling and delivery

**A26 — High integrity/maintainability weakness: the table-count goal has overridden domain boundaries.**  
TRANSACTIONS stores payments plus many unrelated instructions, products and audit events. ACCOUNTS combines deposits, cash, clearing, loans and cards with different balance semantics. Some relationships lost foreign keys; per-kind required/status checks are incomplete; installment CHECK constraints allow important NULL values; several dates are strings. See [the detailed schema findings and proposed structure](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/DATABASE_REVIEW.md).  
**Improve:** model domain records separately, with typed dates, explicit FKs and state rules. Keep existing UI/API response shapes through adapters.

**A27 — High migration/auditability gap: history and balance reconciliation have an incomplete boundary.**  
V11 imports balances and transaction history without the corresponding old journals. V17 omits some old transfer attributes from the live projection. Retirement verification checks selected invariants but not complete balance reconstruction or all migrated semantics. These behaviors are documented in part; archived data is not the same as queryable provenance.  
**Improve:** reviewed opening balances/cutover reconciliation, retained source references, explicit unknown historical timestamps and full migration reports. Never invent missing historical journal entries.

**A28 — Medium confirmed tooling failures: demo seed and backup/restore are stale.**  
The seed script still posts JSON-only loan requests and approvals without document IDs. The current API requires multipart salary slips and verified IDs. DemoDataMaintenance omits LOAN_SALARY_SLIPS from backup/cleanup/restore; dependent rows can block account deletion. Rollback exists, so this is not observed data loss. Details and evidence are in the database review.  
**Improve:** update all utilities to the current schema/API and rehearse restore in an isolated database before trusting backups.

**A29 — Medium deployment gaps: localhost API default, development build, and narrow CI.**  
[auth.ts](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/src/services/auth.ts:1) defaults to localhost:8088; no checked-in production injection was found. [package.json](C:/Users/Purva/Project/BANK_APP/Nexa/apps/web/package.json:6) uses `ojet build`, while CI labels it production; Vercel also uses that command and `npm install`. Both workflows trigger only for rb-fe and omit standalone setup-script changes. Node metadata says >=16, CI selects 20, and a resolved Selenium tool dependency requires >=22; this is a compatibility mismatch, not proof all builds fail.  
**Improve:** validated deployment API configuration/same-origin routing, release builds, lockfile installs, consistent runtime versions, and CI paths/branches reflecting the supported delivery workflow.

**A30 — Medium verification/operations gaps: passing tests do not cover the current integration boundary.**  
Most backend integration tests use generated H2 schemas with Flyway disabled. Some loan DDL is applied separately, but the complete Oracle migration/cutover path is not tested by normal verify. Frontend tests rely heavily on transpiled modules and JET/DOM stubs; live API checks are optional. No automated end-to-end browser job was found. The general exception handler converts DB errors to generic 503 and unexpected errors to 500 without logging the underlying exception ([ApiExceptionHandler.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/exep/ApiExceptionHandler.java:96)). This hides actionable diagnostics and treats integrity errors like temporary outages.  
**Improve:** isolated Oracle fresh-install/upgrade tests, meaningful cross-layer browser journeys, recovery/concurrency regressions, correct 4xx/5xx mapping and sanitized server-side exception logs. The custom /api/v1/health is a static liveness response; use dependency-aware health for readiness. Actuator support already exists.

**A31 — Low/medium maintenance defects: dead branches, stale docs and an actual style-test failure.**  
The separate prototype is deliberately retained; active web source directories are referenced. One OperationsPage branch is unreachable, starter prototype files are orphaned, build hooks include a dangling path, and historical docs describe removed files/old flows. Newer product/admin copy is only partly localized. The palette test rejects raw colors in experience.css; this is a rule mismatch, not evidence the appearance is poor.  
**Improve:** follow [the separate inventory](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/UNUSED_AND_LEGACY.md); archive or remove only confirmed unused material. Keep identical visual values if converting them to tokens, after explaining that frontend edit first.

## Database counts: do not mistake “six core tables” for the entire schema

The source defines **7 current banking/document tables + 4 conversation tables**, plus Flyway history. The six core tables remain a subset; V23 adds salary slips.

Running all migrations from scratch leaves 18 legacy tables too: **29 application tables + Flyway = 30**. The optional retirement tool removes those 18, leaving **11 application tables + Flyway = 12**. These are repository-derived counts, not a live Oracle inspection. See the table-by-table database note.

## Existing protections to preserve

- BCrypt passwords, signed expiring JWTs, hashed refresh tokens and locked refresh rotation.
- Customer ownership checks and administrator route restrictions.
- BigDecimal money, amount precision checks, insufficient-funds checks and zero opening balances.
- Transactional real postings, ordered account locks, journal/ledger entries and tested rollback.
- Durable transfer reviews, expiry, destination snapshots, confirmation revalidation and same-ID retry recovery.
- Admin adjustment reasons/idempotency and loan approval/document verification.
- Loan EMI rounding, prepayment preview binding, stale-preview rejection and disbursement retry protection.
- Explicit chat confirmation commands: casual “yes” or model output alone does not authorize a posting.
- Knowledge answers grounded in a versioned local resource and useful deterministic fallback routing.
- Private salary-slip downloads, size/type-signature checks, sanitized filenames, duplicate checks and immutable submissions.

Salary-slip signature checks are not malware scanning or verification of payroll authenticity. The current docs acknowledge that; production needs a real document review/retention/security process.

## Proposed order of work, preserving the frontend

1. **Correctness first:** unify legacy posting controls, close mandate state bypass, restrict incompatible transfer types, fix bills, spending aggregates/date filters and loan arrears payoff. Add focused regressions.
2. **Account onboarding and funding:** agree identity/eligibility requirements; add application/review/activation states; implement verified funding evidence and adjustment approval.
3. **Security:** eliminate unsafe deployment defaults, centralize active-session/role checks, add abuse controls and sensitive-operation authentication.
4. **Database evolution:** design explicit domain tables and reconciliation boundary; migrate incrementally with compatibility DTOs and tested backups.
5. **Delivery and recovery:** fix release configuration, isolated Oracle/browser coverage, pending-payment recovery, diagnostics and restore tooling.
6. **Cleanup last:** archive the reference frontend if desired, remove confirmed orphan code/assets, reconcile documentation and dependency declarations.

Potential frontend changes to explain and approve before implementation: verification/application status in account opening; a funding receipt/approval workflow for admins; action-specific mandate controls; tracker-versus-settlement labels; correct repayment receipts; uncertain-result recovery. The current layout, theme, typography and navigation can be preserved.

## Verification performed

- Backend: `mvnw.cmd -o -Dmaven.repo.local=C:\Users\Purva\.m2\repository verify` — **BUILD SUCCESS; 197 tests, 0 failures, 0 errors, 1 skipped** (196 passed). Uses isolated H2; no Oracle migration was run. The initial sandbox invocation failed because Maven resolved its repository to C:\.m2; the successful run used the existing user cache.
- Frontend: `npm test` attempted; missing installed dependencies, notably TypeScript, prevent much of the suite from starting. No dependency installation or frontend build was performed.
- Independently executable palette suite: **3 passed, 1 failed**. Raw values in experience.css violate its token-only rule.
- Static entrypoint/import/reference tracing: all 48 integrated web TS/TSX modules reachable; all 16 CSS files and five image assets referenced. Unreachable branches and prototype orphans are listed separately.
- All SQL migrations plus V17 Java, repository classes and setup/cutover/demo utilities reviewed. Tests and comments are evidence of intended behavior, not substitutes for the current source.
- Not performed: live Oracle catalog/data verification, full Oracle migration rehearsal, deployed security/header inspection, browser visual QA, real payment-provider testing or dependency-vulnerability scanning.
