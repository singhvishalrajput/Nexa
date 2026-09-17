# Six-table banking refactor

The Oracle application schema is `NEXA_APP` in the existing `FREEPDB1` database. The connection uses the supplied `SYSTEM` login; it does not create application objects in `SYSTEM`. Local connection settings are in the ignored external `apps/api/application-local.properties`. Start the API from `apps/api` so Spring loads that file. No database password is committed or embedded in Java/TypeScript/SQL.

The final banking schema is [SIX_TABLE_SCHEMA.sql](SIX_TABLE_SCHEMA.sql). It contains table definitions, primary/foreign keys, checks, unique constraints, and all standalone indexes. The database also retains `CONVERSATIONS`, `CONVERSATION_TURNS`, `CONVERSATION_WORKFLOWS`, `CONVERSATION_ACTION_EVENTS`, and the infrastructure table `flyway_schema_history`.

| Banking table | Responsibility |
| --- | --- |
| `CUSTOMERS` | Customer profile, stable login subject (`USER_ID`), access status and role. Existing numeric customer IDs and login subjects are retained. |
| `CUSTOMER_CREDENTIALS` | One password credential per login subject and any number of separately hashed, expiring, revocable refresh credentials. Password hashes are copied unchanged. Refresh rotation locks the credential to prevent reuse. |
| `ACCOUNTS` | Deposit and system accounts, plus loan/card accounts. Products retain stable public `PRODUCT_ID` values and link to the customer's funding account. Loan `BALANCE` is outstanding principal, not spendable funds. |
| `TRANSACTIONS` | Posted monetary movements, typed payment instructions, mandate authorizations/events, bill/schedule records, beneficiary instructions, review receipts, simulations and imported product history. `RECORD_KIND` distinguishes these explicitly; no arbitrary JSON product state remains. |
| `JOURNAL_ENTRIES` | One journal per financial transaction, with reference, status and timestamps. |
| `LEDGER_ENTRIES` | Debit/credit postings to accounts. |

Mandates are `TRANSACTIONS` rows with `RECORD_KIND='MANDATE'`. Their source, beneficiary, authorized operation, amount limit, effective dates, owner and status are relational columns. Creation produces `PENDING`; activation permits execution; cancellation is terminal. The limit is **per execution**, and effective dates are inclusive UTC dates. Each transition adds a `MANDATE_EVENT` row. Successful executions are separate `PAYMENT` rows with a foreign-key `PARENT_ID` pointing to the authorization. No money is moved by creation or activation.

Loans are customer-owned `ACCOUNTS` with `ACCOUNT_TYPE='LOAN'`, principal, annual interest rate, product status and funding account. Creation records terms with zero outstanding. Disbursement debits the loan asset and credits the customer's deposit liability, increasing both balances. Repayment debits the deposit and credits the loan, reducing both balances; paying all outstanding principal closes the loan. The basic implementation records the rate but does **not** accrue interest, amortize schedules, or charge fees automatically. Loan balances are excluded from spendable-account lists and remain available through loan details and the authenticated account/balance endpoints.

All new product workflows use authenticated existing customers and deposit accounts. A loan requires a password credential for its borrower. New identities use the existing registration and account-opening endpoints; no workflow creates an orphan customer, account, or fabricated login. Beneficiaries can be selected by an existing Nexa account number.

Monetary operations commit transaction, journal, two equal-and-opposite ledger entries, and account updates in one database transaction. Accounts are locked in ascending ID order. Mandate authorization rows serialize execution and revocation. Loan disbursement is idempotent; mandate execution and repayment require a UUID `requestId`, reject reuse with different details, and return the original transaction for retries. Deposit/withdrawal/transfer APIs reject loan/card accounts so product accounting cannot be bypassed. Accounts open at zero; deposits must be posted through the accounting service.

Existing product JSON response shapes and routes remain available as relational projections. New create and payment controls are on the Direct debits and Loans pages. Existing provider simulations remain explicitly simulated and do not create financial postings. Chat tables and stored conversations were retained; only the chat owner foreign key was repointed from `USERS(ID)` to `CUSTOMERS(USER_ID)`.

## Migration and retirement

Historical V1–V15 migrations and local seeds are unchanged, preserving Flyway checksums. The upgrade consists of:

1. [V16](../apps/api/src/main/resources/db/migration/V16__six_table_structure.sql): additive columns, credential migration, identity consolidation, chat owner FK and transaction discriminators.
2. [V17](../apps/api/src/main/java/db/migration/V17__migrate_banking_products.java): transactional data copy from product JSON and operational tables into typed account/transaction columns, including nested historical payments/card activity. Counts and product IDs are verified; no balances are replayed.
3. [V18](../apps/api/src/main/resources/db/migration/V18__six_table_constraints.sql): extended financial account-shape checks, mandate/review constraints and indexes.
4. [SixTableCutover.java](../scripts/java/SixTableCutover.java): archive, verification, schema extraction, and guarded retirement. Retirement compares every obsolete table with its complete archived rows before executing any `DROP TABLE`. It never uses `CASCADE CONSTRAINTS`, touches `SYSTEM` tables, or runs Flyway clean/repair.

The existing database has completed these steps. Eighteen obsolete tables were retired. Their complete rows and DDL are retained locally in `.tools/six-table-before.zip`, outside version control, including historical audit and authentication records. This archive was taken after the additive copy migrations, while **all original tables still existed**, and before any retirement. It is an archive of both the source data and the copied schema, not a claim of a pre-V16 snapshot. Protect it as database data because it contains credential hashes and customer information.

| Removed tables | Destination / disposition |
| --- | --- |
| `USERS`, `AUTH_REFRESH_TOKENS` | Identity/profile/status/role in `CUSTOMERS`; unchanged hashes and sessions in `CUSTOMER_CREDENTIALS`. |
| `BANKING_PRODUCTS` | Loans/cards in `ACCOUNTS`; mandates, bills and schedules in `TRANSACTIONS`; nested history in `PRODUCT_HISTORY` transaction rows. |
| `BENEFICIARIES` | Typed beneficiary instructions in `TRANSACTIONS`, including routing/hash/masked destination fields. |
| `TRANSFERS` | `LEGACY_TRANSFER` records preserve the existing status/detail API; original full workflow metadata remains in the archive. |
| `MONEY_TRANSFER_REQUESTS` | `TRANSFER_REVIEW` transaction records preserve review IDs, expiry, recipient snapshots and confirmation idempotency. |
| `SHOWCASE_ACTIONS` | `SIMULATION` transaction records preserve provider-demo receipts and history. |
| `TRANSFER_APPROVALS`, `BENEFICIARY_VERIFICATIONS`, `IDEMPOTENCY_RECORDS`, `AUDIT_EVENTS`, `OUTBOX_EVENTS` | No runtime consumers existed. Historical rows are retained in the archive. Current payment retry, transition audit and posting responsibilities use typed transaction records. |
| `BANKING_ACCOUNT_MIGRATION` | Historical ID map retained in the archive; current numeric account references were already migrated by V11. |
| `LEGACY_BANK_ACCOUNTS`, `LEGACY_TRANSACTIONS`, `LEGACY_JOURNAL_ENTRIES`, `LEGACY_LEDGER_ACCOUNTS`, `LEGACY_LEDGER_POSTINGS` | Earlier V11 cutover archives retained offline. Current balances and transaction history were already imported by V11. Missing historical journals are not invented or replayed. |

Legacy mandate projections did not store a verified destination account. Their status and display history are preserved, but execution requires creating a new authorization with a real beneficiary. Legacy loan projections did not record original principal: it remains null rather than inventing a principal or disbursement. Their outstanding balance, interest rate and recorded repayment history are retained.

For another installation, stop writers and take an archive **before migration**, then run each stage in order. Set `BANKING_DB_URL`, `BANKING_DB_USERNAME`, `BANKING_DB_PASSWORD` and `BANKING_DB_SCHEMA` through the terminal environment. Use the existing schema name, not the administrative connection username.

```powershell
./scripts/six-table-cutover.ps1 -Stage archive -Path D:/backups/nexa-before.zip
./scripts/six-table-cutover.ps1 -Stage migrate
./scripts/six-table-cutover.ps1 -Stage verify
# Run the Oracle integration suite and review the product projections before retirement.
./scripts/six-table-cutover.ps1 -Stage retire -Path D:/backups/nexa-before.zip
./scripts/six-table-cutover.ps1 -Stage inventory
```

Oracle DDL auto-commits: keep writers stopped until migration, verification and retirement finish. Do not roll back to the old application after new financial writes; restore/reconcile the database and application together. The archive contains each table's original DDL and every column/row as JSON, permitting historical recovery into an isolated schema. Retain a standard Oracle backup for a whole-database point-in-time recovery requirement. Retirement is deliberately a separate, validated operational stage, not an unconditional Flyway drop.

## API additions

All endpoints require authentication and enforce customer ownership.

| Endpoint | Request / result |
| --- | --- |
| `POST /api/v1/mandates` | `sourceAccountId`, either `beneficiaryAccountId` or `beneficiaryAccountNumber`, `payee`, decimal `limit`, `startDate`, optional `endDate`. Returns the authorization (ID key `ID`). |
| `GET /api/v1/mandates/{id}/authorization` | Relational terms, source/beneficiary and state. |
| `POST /api/v1/mandates/{id}/activate` | Activate a pending authorization. |
| `POST /api/v1/mandates/{id}/execute` | Decimal `amount` and UUID `requestId`; returns `transactionId`. |
| `POST /api/v1/mandates/{id}/revoke` or `/cancel` | Cancel authorization. |
| `GET /api/v1/mandates/{id}/history` | Lifecycle events and executed transactions. |
| `POST /api/v1/loans` | Owned deposit `accountId`, `displayName`, decimal `principal`, annual percentage `interestRate`. Returns loan account (`PRODUCT_ID` identifies the loan). |
| `GET /api/v1/loans/{id}/account` | Principal, outstanding balance, interest rate, loan state and linked account. |
| `POST /api/v1/loans/{id}/disburse` | Credit the linked deposit account exactly once. |
| `POST /api/v1/loans/{id}/repay` | Decimal `amount` and UUID `requestId`; repay principal from the linked deposit account. |

Existing list/detail/payment-history routes, account/transfer/login routes, and conversation APIs retain their contracts. Extended raw term endpoints currently use uppercase Oracle column names; existing product DTOs remain camelCase.

## Validation

[SixTableBankingIntegrationTest](../apps/api/src/test/java/com/nexa/api/banking/SixTableBankingIntegrationTest.java) runs against H2 by default, or the existing Oracle schema with `NEXA_VERIFY_ORACLE=true` and the four connection environment variables. It creates clearly prefixed test customers via registration, with valid credentials, and uses real API deposits to fund them. Oracle runs deliberately leave their identified test records and financial audit history intact; no existing customer data is deleted.

Coverage includes login/refresh rotation, account and balance retrieval, account transfers/history, mandate states/dates/limits/ownership, execution retries and audit, loan terms/disbursement/repayment/outstanding/history, borrower login/account access, balanced journals, concurrent requests, and injected ledger failures during mandate execution, disbursement and repayment. The schema-inventory test rejects extra banking tables.

```powershell
cd apps/api
./mvnw.cmd test
# After migrating and retiring obsolete tables:
$env:NEXA_VERIFY_ORACLE='true'
./mvnw.cmd -Dtest=SixTableBankingIntegrationTest test
cd ../web
npm test
npm run typecheck
npm run build
```

Existing transfer and chat integration fixtures now use the six tables. Historical local-demo seeds are preserved and transformed by V17. No new banking seed table is introduced.

## Verification results — 17 September 2026

- `mvn verify`: 121 tests discovered; **120 passed, 0 failures, 0 errors, 1 skipped**. The skip is the existing optional live-model test.
- Existing Oracle database: **8 end-to-end tests passed**, after obsolete-table retirement and V18 constraints, including the exact six-table inventory assertion.
- Frontend: **68 tests passed**, type checking, lint (36 files, zero errors), and production build passed.
- Setup launcher: **19 input-safety checks passed**. The existing setup launcher now compiles and includes application classes so it discovers Java migration V17 as well as SQL migrations.
- Database verification: no unbalanced/empty journals, no orphan customer accounts, and no successful `TX-` financial transactions missing journals. All four chat tables match the archived rows exactly.
- The backend executable JAR was rebuilt successfully.

On this Windows host the JDK's Unix-domain socket connection fails inside its selector implementation. The HTTP model-stub tests pass using the JDK's existing TCP fallback. The final full verification command was:

```powershell
./mvnw.cmd '-DargLine=-Djdk.net.unixdomain.tmpdir=D:\Nexa\.tools\no-unix-sockets' verify
```

That socket directory must remain nonexistent: binding there fails and the JDK falls back to TCP. This is a test-process workaround, not an application code change or skipped test. Other hosts can use ordinary `mvn verify`.
