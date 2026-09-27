# Nexa database review — 25 September 2026

> Historical schema audit. References to `tools/demo` below describe retired utilities removed from `purvak` on 27 September 2026. See the [current repository layout](../REPOSITORY_LAYOUT.md) and [bank funding workflow](../BANK_FUNDING.md).

This reviews the schema defined in source through V23 and the application's persistence code. **The live Oracle catalog and data were not queried.** Counts below assume successful execution of the checked-in migration sequence and describe the optional retirement step separately.

See [the main audit](C:/Users/Purva/Project/BANK_APP/Nexa/docs/audit/PROJECT_AUDIT.md) for service/API defects and priorities.

## Current tables and relationships

| Table | Purpose | Assessment |
|---|---|---|
| CUSTOMERS | Personal profile, login user_id, status and role, including administrators | Active; authentication and bank-customer lifecycle are combined. No account-opening verification/application model. |
| CUSTOMER_CREDENTIALS | PASSWORD and REFRESH credential records | Active; shared typed storage works, but session-family/revocation management needs expansion. |
| ACCOUNTS | Savings/current, loan/card, system cash and clearing | Active; one balance column has different accounting meanings depending on type. Several optional loan/card fields apply to only some rows. |
| TRANSACTIONS | Posted PAYMENT plus instructions, beneficiaries, mandates/events, bills, schedules, transfer reviews, simulations, legacy transfers, product history, admin events and installments | Active; an overly broad table with nullable fields and polymorphic references. |
| JOURNAL_ENTRIES | Financial journals associated with transactions | Active; preserve authoritative, immutable posting history. |
| LEDGER_ENTRIES | Per-account debits and credits | Active; reconcile account balances using their normal debit/credit side and a known opening boundary. |
| LOAN_SALARY_SLIPS | Private document BLOBs, hashes and verification metadata | Active since V23; omitted from older six-table exports and demo backups. |
| CONVERSATIONS | User-owned conversation headers | Active. |
| CONVERSATION_TURNS | Ordered messages, structured banking/workflow snapshots | Active; keep ownership and retention policy distinct from financial audit retention. |
| CONVERSATION_WORKFLOWS | Banking action collection/review state | Active. |
| CONVERSATION_ACTION_EVENTS | Persistent action audit | Active; distinct from user-deletable chat text. |
| flyway_schema_history | Migration versions/checksums | Infrastructure; never treat as an unused application table. |

Core relationship: customer → accounts → financial transaction → journal → ledger entries → accounts. Loans/documents and conversation workflows have additional links. Some links in TRANSACTIONS are only text identifiers rather than foreign keys.

There are **11 active application tables and one migration-history table** after optional legacy retirement. “Six core banking tables” is a subset, not the complete schema.

## Legacy tables left by a fresh migration sequence

Sequential CREATE/DROP/RENAME analysis gives **29 application tables**, or **30 with Flyway**. The extra 18 are retained for historical compatibility, migration and optional retirement:

| Group | Tables |
|---|---|
| Old identity | USERS, AUTH_REFRESH_TOKENS |
| Old product/payee storage | BANKING_PRODUCTS, BENEFICIARIES, BENEFICIARY_VERIFICATIONS |
| Old payment platform | TRANSFERS, TRANSFER_APPROVALS, IDEMPOTENCY_RECORDS, AUDIT_EVENTS, OUTBOX_EVENTS |
| Old action/review storage | MONEY_TRANSFER_REQUESTS, SHOWCASE_ACTIONS |
| Old core migration data | BANKING_ACCOUNT_MIGRATION, LEGACY_BANK_ACCOUNTS, LEGACY_TRANSACTIONS, LEGACY_JOURNAL_ENTRIES, LEGACY_LEDGER_ACCOUNTS, LEGACY_LEDGER_POSTINGS |

The [retirement utility](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/SixTableCutover.java:14) lists those tables. They are no longer the normal request-handling persistence targets, but they can contain indispensable historical data. A past document saying they were retired does not prove retirement on this installation. Do not drop them simply because runtime SQL no longer reads them.

## Detailed schema and migration findings

**D01 — Domain consolidation weakens ordinary database guarantees.**  
[V16__six_table_structure.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V16__six_table_structure.sql:43) makes payment type/amount nullable and adds unrelated instruction/product fields; [V20__loan_amortization.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V20__loan_amortization.sql:15) extends the kinds again. target_id and transaction_reference have no clear relational type or foreign key. A non-null completed-review reference need not resolve to a payment. Before consolidation, the old transfer-request reference had an actual FK ([V14__money_transfer_requests.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V14__money_transfer_requests.sql:14)). A funding-account FK verifies existence but not ownership/type. Services often enforce these rules, so this is not a claim that all customer endpoints lack ownership protection. Dedicated entities make both enforcement and query intent clearer.

**D02 — CHECK constraints allow invalid NULL fields.**  
[V20__loan_amortization.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V20__loan_amortization.sql:19) tests installment number ranges and amount=principal+interest but omits explicit NOT NULL conditions for some fields. SQL CHECK accepts UNKNOWN, so a null amount/number can pass. Add explicit per-kind required fields or a proper installment table with NOT NULL columns, unique loan/ordinal and enforced financial components.

**D03 — Calendar dates are arbitrary strings.**  
[V16__six_table_structure.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V16__six_table_structure.sql:36) and line 51 use VARCHAR2 for due/effective/end dates. [JdbcBankingProductRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/JdbcBankingProductRepository.java:41) compares due dates lexically. Application ISO formatting makes ordinary writes work but the schema permits invalid values. Use DATE for calendar dates, a consistent UTC timestamp representation for events, and start/end constraints.

**D04 — Full journal reconstruction of imported history is not available.**  
[V11__integrate_authoritative_banking_core.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V11__integrate_authoritative_banking_core.sql:109) copies balances directly; its history import at line 117 does not migrate corresponding historical journals. It derives deposit/withdrawal from signed amount, reducing old type/reference detail in the active representation. Preserve original records and a signed-off opening-balance snapshot/provenance mapping. Reconciliation must state the cutover date and starting balances; do not pretend every imported history item has a modern journal.

**D05 — V17 drops useful old attributes from live projections.**  
[V17__migrate_banking_products.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/db/migration/V17__migrate_banking_products.java:37) omits old fee_amount, purpose, authorized_at, failure_code and version when importing transfers. After old tables are retired, these remain only in archives. History with no occurredAt gets migration-time Instant.now at line 145. Preserve needed historical fields and an explicit imported_at/unknown-occurred-at distinction.

**D06 — Retirement checks are incomplete accounting reconciliation.**  
[SixTableCutover.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/SixTableCutover.java:97) checks balanced journals, selected ownership/password invariants, and successful TX-prefixed payments missing journals. It does not prove all account balances match entries plus opening balances, all funding/control totals are correct, or all historical transfer/session semantics survived. Old-table archive checksums validate the archive; they do not validate the new representation's completeness. Expand checks and perform restore rehearsal before retirement.

**D07 — V23 invalidates the demo tool's backup/cleanup assumptions.**  
[DemoDataMaintenance.java](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/tools/demo/DemoDataMaintenance.java#L13) excludes LOAN_SALARY_SLIPS from its hardcoded backup membership. Its restore script deletes accounts (line 96), but [V23__loan_salary_slips.sql](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/resources/db/migration/V23__loan_salary_slips.sql:4) has a non-cascading document→account FK. Restore/cleanup can fail after document-backed loans exist, and backups lack documents. Rollback exists at line 311: this audit observed no deletion or data loss. Include documents, restore/delete dependency order, binary data handling and verification together.

**D08 — The demo client is behind the API contract.**  
[seed-demo.cjs](https://github.com/singhvishalrajput/Nexa/blob/9ab8a6d91fbf3a6a2d42d9a8f5b732d1816b2c0a/tools/demo/seed-demo.cjs#L50) creates loans with JSON; [LoanController.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/controller/LoanController.java:48) now rejects JSON-only creation. Multipart documents and verified document IDs are required. Update fixture generation through the current documented flow, and never run it against real users.

**D09 — Generated H2 schemas do not validate the full Oracle migration chain.**  
[SixTableBankingIntegrationTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/SixTableBankingIntegrationTest.java:26) disables Flyway and normally uses H2 create-drop. [MigrationVersionTest.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/test/java/com/nexa/api/banking/MigrationVersionTest.java:16) resolves migration metadata without applying Oracle SQL. Some loan migrations are executed in adapted H2 tests, which is useful but narrower. Add disposable Oracle fresh-install and upgrade tests; optional existing-schema tests are not a substitute and can create fixtures/temporary failure constraints.

**D10 — Paginated product lists can load unbounded history.**  
[JdbcBankingProductRepository.java](C:/Users/Purva/Project/BANK_APP/Nexa/apps/api/src/main/java/com/nexa/api/repository/JdbcBankingProductRepository.java:83) fetches per-loan EMI/history, and cards/bills similarly fetch nested history. Queries at line 176 have no limit. Use summary-only list DTOs, batch lookups, explicit history endpoints and bounded pagination. Add/query indexes based on measured Oracle plans, not speculative index proliferation.

**D11 — Schema export and old runbooks are not a complete current schema.**  
[SixTableCutover.java](C:/Users/Purva/Project/BANK_APP/Nexa/scripts/java/SixTableCutover.java:217) exports six tables, omitting loan documents and intentionally separate conversation tables. SIX_TABLE_SCHEMA.sql and older SIX_TABLE_BANKING.md sections are snapshots. Generate/version a current complete schema reference and separate historical cutover notes from supported setup instructions.

**D12 — Application validation and DB validation must agree.**  
Current mixed JDBC/JPA code means a service may write columns absent from another entity's view; handwritten SQL can bypass entity validation. The invalid bill INSERT is a concrete example, and incomplete per-kind constraints permit errors to persist if another write path is added. Test actual create/list/update contracts against migrated schemas, not only mocked DTOs. Keep constraints in forward migrations; do not rewrite already-applied files.

## Recommended schema direction

Do not set a target table count. Keep the present frontend-facing DTOs while introducing explicit tables in stages:

| Domain | Suggested responsibility |
|---|---|
| Identity | User identities, credentials, sessions and roles; customer profile separately where it has a different lifecycle |
| Onboarding | Account applications, verification checks/document references, decisions and consent versions |
| Accounting | Accounts with normal balance side, immutable financial transactions/journals/postings, reconciliation/opening boundaries |
| Payments | Payment/transfer orders, durable review/authorization state, idempotency records and provider references |
| Payees and recurring instructions | Beneficiaries, mandates, mandate events, scheduled instructions/executions |
| Lending | Loan applications, approved terms, loan accounts, installments, paid/due interest, documents and decisions |
| Billing/cards | Explicit obligations and provider state; separate customer tracking and simulation data |
| Operations | Append-only audit events, adjustment requests/approvals, reconciliation runs; outbox when asynchronous provider/notification work is introduced |
| Conversation | Keep conversational messages/state separate from durable financial facts and audit retention |

Use expand → copy/backfill → reconcile → switch readers/writers → retire. Preserve stable external IDs and old references, enforce FKs/NOT NULL/status checks/typed dates, and create tested rollback/restore procedures. Keep the current UI contract through compatibility projections while migrating.

Do not run setup, cutover, retirement, cleanup or seed scripts as part of merely reading this audit. No such operations were performed here.
