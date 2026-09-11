# Nexa Backend Handoff

> For the merged banking core, current setup and schema cutover, see [Banking integration](BANKING_INTEGRATION.md). Pre-merge implementation and activation notes below are historical.

**Status:** Frontend prototype complete; backend not started  
**Last updated:** 4 September 2026  
**Purpose:** Give a new backend task or developer enough product, architecture, security, and integration context to begin without relying on the original frontend conversation.

---

## 1. Product summary

Nexa is a conversational personal-banking application. A user can type or speak a request in natural language, receive an explanation or a prepared banking action, review the exact effect, and explicitly approve any state-changing operation.

The product is not intended to be a generic chatbot. The conversation is the navigation and assistance layer; deterministic banking services remain the authority for balances, permissions, limits, transfers, and records.

### Core product principle

> Nexa may understand, explain, and prepare an action. It must never move money or change a sensitive banking setting without an explicit, verified user instruction.

### MVP goals

- Provide secure password-based login and session management.
- Show accounts, balances, transactions, and a consolidated dashboard.
- Manage beneficiaries and prepare immediate or scheduled transfers.
- Require stronger authentication for transfers and high-risk decisions.
- Support cards, bills, subscriptions, budgets, savings goals, alerts, disputes, reports, and forecasting.
- Accept conversational requests and return structured actions that the frontend can open.
- Use Oracle AI Database for relational data and, later, vector search.
- Use Ollama locally for development-time embeddings and response generation.

### Current exclusions

- Investments, trading, cheque services, rewards, and offers are not part of the planned product.
- The prototype is not connected to real bank rails, UPI, card networks, SMS, or email providers.
- Do not represent mocked transactions or approvals as real financial operations.

---

## 2. Current frontend

### Location and stack

- Frontend: `D:\Nexa\apps\web`
- Framework: Oracle JET 20.1 with Preact and TypeScript
- Local application: `http://localhost:8000/`
- Current data: component state and browser `localStorage`
- Current intent recognition: frontend regular expressions and simple parsing
- Current status: all banking data, assistant replies, confirmations, downloads, and outcomes are demonstrations only

### Important source files

| File | Responsibility |
|---|---|
| `src/components/chat/ChatWorkspace.tsx` | Chat history, speech input, attachments, intent routing, action cards, profile view, and workspace navigation |
| `src/components/chat/FinancialDashboard.tsx` | Financial overview |
| `src/components/chat/FinancialExperiences.tsx` | Transactions, immediate transfers, subscriptions, and card management |
| `src/components/chat/AccountBankingWorkspaces.tsx` | Connected accounts and beneficiaries |
| `src/components/chat/PlanningWorkspaces.tsx` | Scheduled/recurring transfers and budgets |
| `src/components/chat/GoalsWorkspace.tsx` | Savings goals and contribution planning |
| `src/components/chat/BillsWorkspace.tsx` | Bills, bill payments, scheduling, AutoPay, and bill creation |
| `src/components/chat/InsightsSecurityWorkspaces.tsx` | Notifications, fraud/disputes, statements/reports, and cash-flow forecasting |

### Implemented frontend experiences

| Area | Frontend capabilities |
|---|---|
| Conversation | Multiple conversations, local history, deletion confirmation, typed input, browser speech input, image/document attachment selection |
| Profile | Personal details, masked banking details, local profile updates |
| Dashboard | Balances, spending, connected accounts, goals, budgets, bills, subscriptions, insights, and quick actions |
| Transactions | Transaction list, filters, category analysis, and last-month summary |
| Transfers | Prepared immediate transfer, review, confirmation, and success state |
| Scheduled transfers | Future-dated and recurring instructions, review, creation, pause/resume |
| Beneficiaries | Search, add, verify, edit, remove, and prepare transfer |
| Connected accounts | View, connect, select, and remove external account connections |
| Cards | Multiple cards, add card, freeze/unfreeze, limits, and usage controls |
| Subscriptions | Active subscriptions, low-use signals, and management actions |
| Bills | Upcoming bills, add bill, pay now, schedule, reminders, and AutoPay controls |
| Budgets | Category limits, monthly usage, creation, and adjustment |
| Savings goals | Create/edit/pause goals, contribution options, and budget impact |
| Notifications | Financial alerts and notification preferences |
| Fraud and disputes | Suspicious activity review, dispute submission, and case tracking |
| Statements and reports | Date/type filters and prototype downloads |
| Forecasting | Cash-flow horizon and scenario-based projection |

### Existing chat action contract

The frontend currently understands these action identifiers. Preserve them initially to minimize integration changes:

```text
open-dashboard
open-transactions
open-transfer
open-scheduled-transfers
open-subscriptions
open-cards
open-goals
open-budgets
open-bills
open-accounts
open-beneficiaries
open-notifications
open-disputes
open-reports
open-forecast
```

An assistant API response should eventually return an action identifier plus typed prefill data, rather than asking the frontend to infer behavior from prose.

Example:

```json
{
  "messageId": "msg_01J...",
  "conversationId": "conv_01J...",
  "text": "I prepared a transfer of ₹5,000 to Rahul. Review the details before sending.",
  "action": {
    "type": "open-transfer",
    "prefill": {
      "amount": 5000.00,
      "currency": "INR",
      "beneficiaryId": "ben_01J...",
      "beneficiaryDisplayName": "Rahul"
    }
  },
  "requiresConfirmation": true
}
```

The frontend must treat all assistant output as untrusted presentation data. It must fetch the authoritative review from the appropriate banking endpoint before approval.

---

## 3. Architecture decision

### Start with a modular monolith

Create one Spring Boot application named `nexa-api`, organized into strict business modules. This provides simple deployment and reliable database transactions while the domain is still evolving.

Suggested location:

```text
C:\Nexa\
├── apps\
│   ├── api\
│   └── web\
└── docs\
```

Save `D:\Nexa` as the common project so future frontend and backend tasks can see both applications and the shared documentation.

### Initial runtime topology

```text
Oracle JET frontend
        |
        | HTTPS / JSON
        v
Spring Boot nexa-api
        |
        +---- Oracle AI Database (system of record)
        |
        +---- Ollama (development AI runtime; introduced after core APIs)
```

Kafka is **not required for the initial features**. Introduce it after the login-to-transfer journey is reliable and tested.

### Future service boundaries

If independent scaling or team ownership later justifies microservices, use approximately these coarse-grained boundaries:

1. Identity and Customer
2. Accounts and Ledger
3. Payments
4. Financial Management
5. Risk and Disputes
6. Notifications and Documents
7. AI Assistant

Do not create these as separate deployments now. First enforce the same boundaries as packages/modules inside `nexa-api`.

---

## 4. Spring Boot project setup

Use Spring Initializr to create the backend.

```text
Project: Maven
Language: Java
Spring Boot: current stable release supported by the selected dependencies
Group: com.nexa
Artifact: nexa-api
Name: nexa-api
Package: com.nexa.api
Packaging: Jar
Java: 21
```

### Initial dependencies

- Spring Web
- Spring Security
- OAuth2 Resource Server
- Spring Data JPA
- Bean Validation
- Oracle JDBC Driver
- Flyway Migration
- Spring Boot Actuator
- Spring Boot Test
- Spring Security Test
- Testcontainers for Oracle-compatible integration testing when feasible
- OpenAPI/Swagger support after the first endpoints

Add later, rather than blocking the initial vertical slice:

- WebAuthn/passkey support
- Spring AI Ollama integration
- Oracle vector-store integration
- Apache Kafka client and Spring for Apache Kafka
- Object storage integration for uploaded files and generated statements

### Suggested module/package structure

```text
com.nexa.api
├── shared
│   ├── configuration
│   ├── errors
│   ├── money
│   ├── security
│   └── audit
├── identity
├── customer
├── accounts
├── ledger
├── transactions
├── beneficiaries
├── transfers
├── cards
├── bills
├── subscriptions
├── budgets
├── goals
├── notifications
├── disputes
├── reports
├── forecasting
└── assistant
```

Each module should own its domain model and persistence access. One module must not directly edit another module's tables. Cross-module operations should go through explicit application services or domain events.

---

## 5. Backend build order

### Phase 0 — foundation

- Generate the Spring Boot application.
- Add environment-specific configuration with no secrets committed.
- Connect to a local/development Oracle database.
- Configure Flyway and create the first migration.
- Add a health endpoint and consistent API error format.
- Add request correlation IDs and structured logs.
- Configure CORS specifically for the local frontend origin during development.

### Phase 1 — first secure vertical slice

Build and verify this complete journey before secondary features:

```text
Register/login
→ view profile
→ view owned account and balance
→ view transactions
→ add/verify beneficiary
→ prepare transfer
→ review authoritative amount, recipient, fee, and resulting balance
→ perform strong approval
→ atomically post ledger entries
→ show transfer receipt
→ record audit event and notification
```

Recommended implementation sequence:

1. Identity and session management
2. Customer profile
3. Accounts, ledger, and transactions
4. Beneficiaries
5. Transfer preparation and review
6. Transfer authorization and ledger posting
7. Passkeys/WebAuthn for strong approval
8. Audit records and database-backed notifications

### Phase 2 — remaining frontend-backed domains

1. Cards
2. Scheduled and recurring transfers
3. Bills and AutoPay instructions
4. Subscriptions
5. Budgets
6. Savings goals
7. Financial notifications
8. Fraud/dispute cases
9. Statements/reports
10. Cash-flow forecasting
11. Connected-account provider abstraction

### Phase 3 — assistant and document intelligence

- Store conversations and messages in the backend.
- Replace frontend regular-expression routing with a server-side assistant orchestrator.
- Use structured outputs for intents and fields.
- Add secure document upload, malware scanning, content extraction, and summarization.
- Add embeddings and Oracle vector retrieval where semantic retrieval adds value.
- Keep banking execution in deterministic services.

### Phase 4 — Kafka and selective extraction

- Add a transactional outbox.
- Publish versioned domain events to Kafka.
- Begin with notification and audit consumers.
- Later connect fraud monitoring, budgets, forecasting, and report projections.
- Extract a module into a microservice only after measurements or team ownership justify it.

---

## 6. Authentication and authorization

### Password login

- Hash passwords with Argon2id, or bcrypt when Argon2id is unavailable.
- Never store or log plaintext passwords.
- Prefer secure, `HttpOnly`, `SameSite` cookies for browser sessions.
- Add CSRF protection when cookie authentication is used.
- Rotate refresh/session credentials after login and sensitive security changes.
- Rate-limit login, passkey challenges, beneficiary verification, and transfer attempts.
- Support session revocation, lockout/backoff, and security audit history.

### Passkeys and face confirmation

Use WebAuthn/passkeys for strong approval. Windows Hello, Face ID, fingerprint, or device PIN can satisfy the challenge while the biometric remains on the user's device.

Do **not**:

- Build custom webcam facial recognition for payment authorization.
- Store facial images or biometric templates.
- Treat a frontend success flag as proof of authentication.

A strong-approval challenge must be bound to:

- User ID
- Transfer/operation ID
- Amount and currency
- Recipient/beneficiary
- Random nonce
- Expiration time
- One-time-use state

If the amount, funding account, or recipient changes, invalidate the approval and create a new challenge.

### Authorization layers

Authentication has one authority: the Identity module, and later possibly an Identity service. Authorization remains enforced at multiple layers:

- Gateway/security filter: valid session/token, basic scopes, coarse rate limits
- Controller/application layer: required permission and validated request
- Domain service: account ownership, beneficiary state, transfer limit, account status, available funds, and operation-specific rules
- Database: constraints and safe transaction isolation

Never trust an `accountId`, `userId`, balance, fee, or approval status supplied by the frontend. Derive the current user from the verified session and re-read authoritative state during execution.

### Initial roles and permissions

Keep the MVP simple:

- `CUSTOMER`: access only their own accounts and banking operations
- `SUPPORT_AGENT`: limited case visibility; no direct money movement
- `FRAUD_ANALYST`: risk/dispute workflows with audited access
- `ADMIN`: system administration, not unrestricted transaction execution

Use ownership- and context-based rules in addition to roles.

---

## 7. Financial correctness requirements

These are non-negotiable even for the educational MVP architecture.

### Money and ledger

- Represent money using `BigDecimal`, never `float` or `double`.
- Store an ISO 4217 currency code, initially `INR`.
- Use an append-only double-entry ledger for posted money movements.
- Do not update a balance without corresponding ledger entries.
- Do not delete posted financial entries; use reversals/corrections.
- Calculate dashboard balances and transfer reviews from authoritative backend data.

### Transfers

Use a lifecycle such as:

```text
DRAFT → READY_FOR_APPROVAL → AUTHORIZED → PROCESSING → COMPLETED
                                   └────→ FAILED
                         DRAFT/READY → CANCELLED/EXPIRED
```

- Preparing a transfer must not move money.
- Review and execution should be separate endpoints.
- Revalidate ownership, balance, limits, recipient, and approval at execution time.
- Require an idempotency key for state-changing payment requests.
- Repeated requests with the same idempotency key must return the original result rather than create a second payment.
- Use database locking or an equivalent concurrency strategy to prevent double spending.
- Create an immutable audit trail for important state transitions.

### Connected accounts

- Do not collect another bank's username or password directly.
- Put external connections behind a provider interface so a mock provider can be replaced by an approved account-aggregation/open-banking provider later.
- Encrypt provider tokens and sensitive identifiers at rest.
- Mask account numbers in all normal API responses and logs.

---

## 8. Initial domain model

The exact schema should be finalized through Flyway migrations, but these are the expected MVP entities.

| Module | Principal entities |
|---|---|
| Identity | `users`, `password_credentials`, `sessions`, `passkey_credentials`, `auth_challenges`, `roles`, `user_roles` |
| Customer | `customer_profiles`, `addresses`, `preferences`, `consents` |
| Accounts | `bank_accounts`, `external_account_connections`, `account_balance_snapshots` |
| Ledger | `ledger_accounts`, `journal_entries`, `ledger_postings` |
| Transactions | `transactions`, `transaction_categories`, `merchants` |
| Beneficiaries | `beneficiaries`, `beneficiary_destinations`, `beneficiary_verifications` |
| Transfers | `transfers`, `transfer_approvals`, `scheduled_transfer_instructions`, `transfer_executions`, `idempotency_records` |
| Cards | `cards`, `card_controls`, `card_limits`, `card_status_history` |
| Bills | `billers`, `bills`, `bill_payment_instructions`, `autopay_mandates` |
| Subscriptions | `subscriptions`, `subscription_activity` |
| Budgets | `budgets`, `budget_category_limits`, `budget_periods` |
| Goals | `savings_goals`, `goal_contributions`, `goal_schedules` |
| Notifications | `notifications`, `notification_preferences`, `delivery_attempts` |
| Disputes | `fraud_alerts`, `dispute_cases`, `dispute_evidence`, `case_status_history` |
| Reports | `statement_requests`, `generated_documents` |
| Forecasting | `forecast_runs`, `forecast_items`, `forecast_scenarios` |
| Assistant | `conversations`, `messages`, `message_attachments`, `assistant_actions`, `document_chunks` |
| Shared | `audit_events`, `outbox_events` |

Every persistent entity should use a non-guessable identifier, created/updated timestamps, and explicit status fields where lifecycle matters. Add optimistic versioning where concurrent edits are possible.

---

## 9. Initial API plan

Use `/api/v1` and publish an OpenAPI specification. This list is a starting contract, not permission to implement every endpoint at once.

### Identity and profile

```text
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/me
PATCH  /api/v1/me
GET    /api/v1/me/sessions
DELETE /api/v1/me/sessions/{sessionId}
POST   /api/v1/webauthn/registration/options
POST   /api/v1/webauthn/registration/verify
POST   /api/v1/webauthn/approval/options
POST   /api/v1/webauthn/approval/verify
```

### Accounts and transactions

```text
GET /api/v1/accounts
GET /api/v1/accounts/{accountId}
GET /api/v1/accounts/{accountId}/balance
GET /api/v1/accounts/{accountId}/transactions
GET /api/v1/transactions/{transactionId}
GET /api/v1/transactions/summary?from=&to=
```

### Beneficiaries

```text
GET    /api/v1/beneficiaries
POST   /api/v1/beneficiaries
GET    /api/v1/beneficiaries/{beneficiaryId}
PATCH  /api/v1/beneficiaries/{beneficiaryId}
DELETE /api/v1/beneficiaries/{beneficiaryId}
POST   /api/v1/beneficiaries/{beneficiaryId}/verification
```

### Transfers

```text
POST /api/v1/transfers/drafts
GET  /api/v1/transfers/{transferId}/review
POST /api/v1/transfers/{transferId}/authorization-challenge
POST /api/v1/transfers/{transferId}/authorize
POST /api/v1/transfers/{transferId}/execute
GET  /api/v1/transfers/{transferId}
GET  /api/v1/transfers
```

State-changing transfer endpoints must accept an idempotency key. Return normalized statuses and safe, user-facing failure reasons without leaking security details.

### Assistant

```text
GET    /api/v1/conversations
POST   /api/v1/conversations
GET    /api/v1/conversations/{conversationId}/messages
POST   /api/v1/conversations/{conversationId}/messages
DELETE /api/v1/conversations/{conversationId}
POST   /api/v1/attachments
```

The assistant message endpoint should return typed action metadata. A later OpenAPI definition should define each action payload with a discriminated schema.

### Standard error shape

```json
{
  "type": "https://api.nexa/errors/insufficient-funds",
  "title": "Insufficient available balance",
  "status": 422,
  "code": "TRANSFER_INSUFFICIENT_FUNDS",
  "detail": "The selected account does not have enough available funds.",
  "traceId": "01J...",
  "fieldErrors": []
}
```

Do not expose stack traces, SQL details, secrets, full account numbers, or model prompts in API errors.

---

## 10. AI architecture

### Available local model

The development machine currently has:

```text
nomic-embed-text:latest
```

This is an embedding model. It can convert text into vectors for semantic retrieval, but it cannot generate conversational answers.

Before choosing a local generative model, check the machine's RAM, GPU model, and VRAM. Possible development starting points discussed previously were a smaller Qwen instruct model for modest hardware or a stronger local model when resources allow.

### Responsibilities

| Component | Responsibility |
|---|---|
| Embedding model | Convert approved text/document chunks into vectors |
| Oracle vector search | Retrieve semantically relevant content |
| Generative model | Interpret language and produce explanations/structured intent |
| Assistant orchestrator | Validate structured model output and call allowed application services |
| Banking services | Authoritative permissions, calculations, state changes, and records |

### Safety boundary

The language model must never:

- Execute arbitrary SQL.
- Calculate or declare an authoritative balance.
- Directly post ledger entries.
- Decide that authentication can be skipped.
- Approve a transfer, dispute, beneficiary, or card change.
- Receive unnecessary secrets or full payment credentials.

Use an explicit allowlist of assistant tools/actions. Validate all model-generated fields with normal DTO validation and domain rules. State-changing tools should normally create or update a draft; the user-facing review and approval endpoint completes the action.

### What vector search is suitable for

- Product help and policy explanations
- User-approved uploaded-document retrieval
- Merchant/category similarity assistance
- Searching a user's own permitted conversation or statement content

Do not use approximate vector results as the source of truth for balances, transactions, ownership, limits, or payment status.

---

## 11. Kafka adoption plan

Kafka is intentionally deferred until the initial banking journey is working.

### Prepare now

- Keep module boundaries explicit.
- Represent meaningful state changes as internal domain events.
- Create an `outbox_events` table design.
- Include a stable event ID, aggregate ID, event type, version, timestamp, correlation ID, and payload.
- Make future consumers idempotent.

### Introduce later

Start with events that do not determine whether money is posted:

```text
transfer.completed
transfer.failed
beneficiary.verified
card.status-changed
bill.payment-due
low-balance.detected
suspicious-activity.detected
goal.progress-updated
```

Example consumers:

```text
transfer.completed
├── Notifications creates confirmation
├── Fraud performs post-transaction monitoring
├── Budget updates spending projections
├── Forecasting recalculates outlook
└── Reporting updates its read model
```

Oracle remains the financial system of record. Publish through the transactional outbox pattern so a committed business operation cannot silently lose its event. Do not use Kafka alone as proof that a transfer succeeded.

---

## 12. Frontend integration rules

- Replace hard-coded frontend arrays incrementally, one workspace at a time.
- Keep the existing visual components and connect them to typed API clients.
- Centralize API base URL, authentication handling, error mapping, and retry rules.
- Never retry a payment mutation automatically without the same idempotency key.
- Display loading, empty, partial, stale, success, and error states.
- Mask sensitive values returned to the browser.
- Use backend conversation IDs and message IDs instead of timestamps/local-only IDs.
- Store uploaded file bytes in approved object storage, not in Oracle table columns unless deliberately justified; keep metadata and ownership in Oracle.
- Preserve explicit confirmation dialogs, but make the backend review response authoritative.

Recommended first frontend conversion:

1. Login/session
2. `GET /me`
3. Account list and balance
4. Transactions
5. Beneficiaries
6. Transfer draft, review, approval, execution, and receipt

---

## 13. Testing and operational expectations

### Minimum tests

- Unit tests for domain rules and calculations
- Repository integration tests against an Oracle-compatible test database
- Security tests for ownership and roles
- Transfer concurrency and duplicate-idempotency tests
- API contract tests
- End-to-end test for the first secure vertical slice
- Failure tests for expired approval, changed transfer details, insufficient funds, disabled account, invalid beneficiary, and repeated execution

### Observability

- Structured logs with correlation and user-safe identifiers
- Metrics for authentication failures, transfer outcomes, latency, and downstream failures
- Audit events separate from ordinary diagnostic logs
- No passwords, tokens, passkey responses, full account numbers, document contents, or model-sensitive data in logs

### Configuration and secrets

- Use environment variables or a secrets manager.
- Commit safe example configuration only.
- Maintain separate local, test, and production profiles.
- Use least-privilege database users.
- Encrypt traffic and sensitive data appropriately.

---

## 14. Decisions still required

The backend task should record decisions for the following before production-like implementation:

- Local Oracle development option and cloud Oracle provisioning plan
- Exact Oracle AI Database version available to the project
- Session-cookie versus access/refresh-token implementation details
- WebAuthn library and supported authenticators
- Whether Nexa represents an actual bank or aggregates external banks in the demonstration
- Mock provider interfaces for UPI/bank transfers, billers, connected accounts, notifications, and cards
- File storage and malware-scanning approach
- Data-retention and deletion rules for conversations and attachments
- Hardware details for choosing the Ollama generation model
- Kafka hosting option when the Kafka phase begins

---

## 15. First backend milestone definition of done

The first milestone is complete only when:

- A user can register, log in, log out, and access only their own data.
- Flyway can build the schema from an empty development database.
- Seed/demo data creates an account, balance, transactions, and beneficiary safely.
- The user can prepare a transfer without moving money.
- The backend returns an authoritative transfer review.
- The transfer requires valid strong approval or a clearly documented temporary development substitute.
- Execution creates balanced ledger postings atomically.
- Repeating the execution request cannot create a duplicate payment.
- A receipt, audit event, and notification record are created.
- Automated tests cover the success path and critical failures.
- The frontend completes this journey using backend data rather than component mocks.

---

## 16. Prompt for the new backend task

Copy this into the dedicated backend task after opening the common `ojet` project:

```text
We are starting the Nexa backend. Nexa is a conversational banking application whose Oracle JET/Preact frontend is located at D:\Nexa\apps\web.

Read docs\BACKEND_HANDOFF.md completely before making changes. Treat the existing frontend as the visual and interaction reference, but remember that all current banking data and outcomes are mocked.

Create the backend at D:\Nexa\apps\api as a Java 17 Maven Spring Boot modular monolith. Begin only with the foundation and first secure vertical slice defined in the handoff. Use Oracle, Flyway, Spring Security, explicit module boundaries, BigDecimal money values, an append-only double-entry ledger, idempotent transfer execution, and an auditable transfer lifecycle.

Do not add Kafka yet; design domain events and a transactional-outbox boundary so it can be introduced after the core journey works. Do not add AI before the deterministic banking APIs are reliable. Use WebAuthn/passkeys for eventual face/fingerprint-backed approvals and never store biometric data.

Before generating the project, inspect the available Java, Maven, Docker, and Oracle environment, then explain any missing prerequisite. Keep an OpenAPI contract and automated tests current as the implementation grows.
```

---

## 17. Handoff rule

This document records product intent and architectural decisions. The current frontend code is the authority for existing UI behavior; the future OpenAPI document and Flyway migrations will become the authorities for network and database contracts. Update this handoff whenever a major boundary, security rule, or delivery phase changes.
