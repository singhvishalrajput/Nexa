# Nexa

Banking now uses six domain tables. See [the final schema, mandate/loan workflows, migration and validation](docs/SIX_TABLE_BANKING.md).

This is a learning showcase. See [showcase flows and simulation boundaries](docs/SHOWCASE.md) for confirmed demo payments, card controls, saved receipts and microphone-free voice demonstrations.

One banking application with an Oracle JET web client and a Java 17 / Spring Boot 4.1.1 API.

- `apps/api/src/main/java/com/nexa/api/{beans,repository,service,controller}`: authoritative customers, accounts, transactions, journals, ledger entries and management APIs.
- `apps/api`: authentication, customer-facing APIs, conversations, product views, migrations and backend tests in the same application.
- `apps/web`: the integrated web client.

Start the API from `apps/api` with `./mvnw.cmd spring-boot:run` (or `mvn spring-boot:run`) after configuring Oracle. It listens on port **8088**. Start the frontend from `apps/web` with `npm ci` and `npm run dev`.

Validate with `mvn verify` in `apps/api` and `npm run lint`, `npm run typecheck`, `npm test`, `npm run build` in `apps/web`. Optional live API checks use `npm run test:live` with test credentials supplied through environment variables.

See [API setup](apps/api/README.md) and [banking integration and database cutover](docs/BANKING_INTEGRATION.md).

See [frontend implementation and verification](docs/FRONTEND_IMPLEMENTATION.md) for banking screens, API integration, and backend limitations.

See the [frontend audit results](docs/FRONTEND_AUDIT.md) for the subsequent cleanup, regression checks and release-build verification.

See [chat-first banking architecture and verification](docs/CHAT_FIRST_BANKING.md) for conversational workflows, confirmed own-account transfers, safety guarantees and integration boundaries.

See [conversation context and follow-up handling](docs/CONVERSATION_CONTEXT.md) for Hindi/Hinglish requests, corrections, cancellations, read follow-ups and payment confirmation behavior.

New teammate? Run `.\scripts\setup-db.ps1` from PowerShell 7 to provision the local Oracle schema and apply migrations. See [database setup prerequisites and commands](apps/api/docs/local-oracle-setup.md).
