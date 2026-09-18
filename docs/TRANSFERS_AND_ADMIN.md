# Transfers and account administration

Saved-payee transfers in Chat and Payments previously confirmed through `ShowcaseService`, which only recorded simulations. They now prepare a `TRANSFER_REVIEW` and post through `MoneyTransferService`. Confirmation creates one business transaction, one journal and balanced ledger entries, and updates both accounts in the same database transaction. The durable review ID makes retries safe. Cancelling a review prevents subsequent confirmation. Historical simulations remain simulations and are never replayed as payments.

Payees must be linked to an actual Nexa deposit account. Use **Payees → Add payee**, or open an imported payee and select **Link account**. Enter the full account number supplied by the recipient. Customers can reveal their own number under **Accounts → Account details → Show full account number**. Masked account numbers and external-bank hashes cannot be used to guess a destination. External-bank, bill-provider and card-network simulations explicitly state that no money moved.

Loans can be repaid on the loan details page or in Chat, for example “repay 100 to Travel loan.” Both paths require confirmation and debit the linked deposit account while reducing loan outstanding. Active and overdue loans are eligible. Unknown historical principal does not prevent repayment of recorded outstanding. Repayment history includes the actual transaction reference and posting date. The product page retains its success receipt while balances refresh. Interest accrual remains outside this basic principal-repayment model.

## Dedicated administrator

The existing login mechanism supports `ADMIN`. Admin users enter a separate account-management interface automatically; customer users cannot access its APIs. Public registration always creates `CUSTOMER`, regardless of submitted role fields.

To provision a **new, dedicated** administrator, set `NEXA_ADMIN_EMAIL` and `NEXA_ADMIN_PASSWORD` in the API environment, then start the API. Use a unique email and a generated password of 16–72 characters. Bootstrap creates a `CUSTOMERS` identity plus a BCrypt password in `CUSTOMER_CREDENTIALS`. It never promotes an existing customer or resets an existing administrator password. Remove the bootstrap environment variables after creation. Existing local Oracle settings continue to load from the ignored external `apps/api/application-local.properties`.

The interface supports:

- Search across all customer and system accounts.
- Change account name and status, with a mandatory reason and stale-version check.
- Block or reactivate accounts; close only zero-balance accounts. Repaid loans cannot be reopened through a status edit.
- Credit/debit active customer deposit accounts through posted deposits/withdrawals, with a mandatory reason and idempotent request ID. Loan outstanding uses the loan workflow.
- Read administrative audit history, actor, reason, before/after values and financial transaction reference.

There is no direct balance overwrite. Cash and customer accounts are locked in ascending ID order, including concurrent administrator deposits and withdrawals.

## Schema and endpoints

[V19](../apps/api/src/main/resources/db/migration/V19__admin_audit_and_transfer_cancellation.sql) adds `ADMIN_EVENT` to the existing transaction discriminator, dedicated audit columns, and cancelled-transfer review support. It adds no tables, deletes no data and does not change the chat schema. Apply through normal Flyway startup before using the new application. [SIX_TABLE_SCHEMA.sql](SIX_TABLE_SCHEMA.sql) contains the current extracted schema.

| Endpoint | Function |
| --- | --- |
| `POST /api/v1/beneficiaries` | Create a linked Nexa payee (`displayName`, `accountNumber`). |
| `PUT /api/v1/beneficiaries/{id}` | Link/update an owned payee with verified account number. |
| `GET /api/v1/accounts/{id}/number` | Reveal only the authenticated customer's account number. |
| `GET /api/v1/admin/accounts` | All account summaries, owner identity and current version. |
| `PUT /api/v1/admin/accounts/{id}` | `name`, `status`, `version`, `reason`. |
| `POST /api/v1/admin/accounts/{id}/adjustments` | `direction` (`CREDIT`/`DEBIT`), `amount`, `reason`, UUID `requestId`. |
| `GET /api/v1/admin/accounts/{id}/audit` | Latest 100 administrative changes. |

Existing `/api/v1/demo/actions` transfer URLs remain compatible but return `simulated:false` for real internal transfers. Other provider demonstrations return `simulated:true`. Existing direct-transfer and loan endpoints retain their contracts.

Tests cover payee confirmation/retry/cancellation, real chat transfer and loan repayment, unlinked recipient handling, administrator login and authorization, audited edits and adjustments, stale versions, imported/overdue loan repayment, ledger balance, and rollback on posting failures. Run the backend verification and frontend tests/typecheck/lint/build; `SixTableBankingIntegrationTest` also runs against the migrated existing Oracle schema using `NEXA_VERIFY_ORACLE=true` and the existing database environment variables.

Verification on 17 September 2026: full backend `verify` discovered 126 tests (125 passed, one existing optional live-model test skipped); all 11 Oracle integration scenarios passed; all 70 frontend tests, type checking, lint and build passed. Live HTTP verification against the running Oracle-backed application posted a ₹25.50 payee transfer and a ₹10 loan repayment, checked both customer balances and loan outstanding, and verified idempotent retries and administrator access controls. Browser verification covered administrator sign-in, the separate account list, search, editor and audit display.

The local dedicated account is `admin@nexa.local`; its generated password is in the ignored local `.tools/admin-access.json`, not source control. Test customers and their posted accounting history are retained with explicit validation names; existing customer payments and historical simulations were not replayed or deleted.

## Account navigation

Administration now starts with a searchable directory at `#/admin`. Search supports account number, customer name, email and account name together; status/type filters and 20-row pages keep the list manageable. Click an account name to open its dedicated workspace. Returning to Account search retains the current search, filters and page for the session.

Each account has addressable Overview, Transactions, Related accounts & mandates, and Audit history sections (`#/admin/accounts/{id}/{section}`). Customer-owned loans/cards appear alongside that customer's other accounts; product terms and the funding-account link appear on their overview. Mandates show source/beneficiary links, status, limits and dates. Manage account opens the existing audited editor and balance-adjustment controls.

`GET /api/v1/admin/accounts/{id}` returns the account, product terms, other accounts belonging to its customer and mandates involving it. `GET /api/v1/admin/accounts/{id}/transactions?page=0&size=20` returns paginated payment records; it also includes system accounts linked through journal/ledger postings. Both require an active administrator. Unknown accounts return 404. No schema changes are needed for this navigation update.

Navigation verification: 72 frontend tests passed, along with type checking, lint and the production build. The 12-scenario H2 banking integration suite passed, including account scoping, mandate relationships, system-ledger activity, pagination, missing-account responses and rejection of customer access to admin views.

The same 12 integration scenarios also passed against the existing Oracle database. Browser verification confirmed customer/name and exact-number search, account overview, account-specific transactions, related account and loan navigation, loan terms, administrative audit history, and preservation of search results when returning to the directory.
