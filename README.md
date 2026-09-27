# Nexa

Start with [how Nexa works and the high-level architecture diagram](docs/PROJECT_ARCHITECTURE.md), then see the [current ER diagrams and table relationships](docs/ER_DIAGRAM.md).

Banking uses six core domain tables plus supporting security, conversation, audit and account-opening tables. See [the banking core](docs/SIX_TABLE_BANKING.md) and [the account-opening upgrade and teammate setup](docs/ACCOUNT_OPENING_UPGRADE.md).

This is a learning showcase. See [supported workflows and integration boundaries](docs/SHOWCASE.md) for persisted card controls, payment workflows and the remaining provider limitations.

Bill payments to linked Nexa payees use actual internal balance transfers; see [bill payments](docs/BILL_PAYMENTS.md). Other-bank transfers now have a [Cashfree Payouts sandbox integration and setup guide](docs/EXTERNAL_BANK_TRANSFERS.md). Sandbox tests leave Nexa balances and bill statuses unchanged; live external payouts are unavailable.

Customer transfers now share one **Payments** page with a Nexa/Other bank destination selector. Bills can link an existing saved Nexa recipient when created. Follow the [payment and bill walkthrough](docs/PAYMENTS_WALKTHROUGH.md) for the required details and local provider settings.

The [admin analytics dashboard](docs/ADMIN_ANALYTICS.md) shows customer/account totals, active deposit balances, posted payment trends and application queues using real database aggregates.

See [scheduled payments, card applications and customer forms](docs/CUSTOMER_PRODUCT_FLOWS.md) for one-time internal scheduled transfers, debit/credit applications, saved-payee mandates, loan types and the current-account onboarding boundary.

One banking application with an Oracle JET web client and a Java 17 / Spring Boot 4.1.1 API.

See [the Knowledge Base](docs/KNOWLEDGE_BASE.md) for grounded banking explanations, product terms, multilingual content and the separation between knowledge, authenticated data and confirmed actions.

- `apps/api/src/main/java/com/nexa/api/{beans,repository,service,controller}`: authoritative customers, accounts, transactions, journals, ledger entries and management APIs.
- `apps/api`: authentication, customer-facing APIs, conversations, product views, migrations and backend tests in the same application.
- `apps/web`: the integrated web client.

See [repository layout and completed folder cleanup](docs/REPOSITORY_LAYOUT.md) for the retained directories and the retired prototype/demo tools.

Start the API from `apps/api` with `./mvnw.cmd spring-boot:run` (or `mvn spring-boot:run`) after configuring Oracle. It listens on port **8088**. Start the frontend from `apps/web` with `npm ci` and `npm run dev`.

Validate with `mvn verify` in `apps/api` and `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` in `apps/web`. Optional live API checks use `npm run test:live` with test credentials supplied through environment variables.

See [API setup](apps/api/README.md) and [banking integration and database cutover](docs/BANKING_INTEGRATION.md).

See [frontend implementation and verification](docs/FRONTEND_IMPLEMENTATION.md) for banking screens, API integration, and backend limitations.

See the [frontend audit results](docs/FRONTEND_AUDIT.md) for the subsequent cleanup, regression checks and release-build verification.

See [chat-first banking architecture and verification](docs/CHAT_FIRST_BANKING.md) for conversational workflows, confirmed own-account transfers, safety guarantees and integration boundaries.

See [conversation context and follow-up handling](docs/CONVERSATION_CONTEXT.md) for Hindi/Hinglish requests, corrections, cancellations, read follow-ups and payment confirmation behavior.

New teammate? Run `.\scripts\setup-db.ps1 -Schema NEXA_BANK_APP` from PowerShell 7 to provision a separate local Oracle schema and apply migrations. Keep any reference project's `NEXA_APP` intact. See [database setup prerequisites and commands](apps/api/docs/local-oracle-setup.md).
