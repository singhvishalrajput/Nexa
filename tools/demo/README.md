# Three-customer demonstration environment

The demo keeps Vishal Singh (`vishal@example.com`) and adds Asha Rao (`asha@example.com`) and Rahul Sharma (`rahul@example.com`). These are three customer identities, each with login credentials and linked banking products; loan/card accounts and bank system accounts are separate rows in the existing `ACCOUNTS` table. The dedicated administrator remains `admin@nexa.local`.

Credentials are stored only in ignored `.tools/demo-access.json` and `.tools/admin-access.json`. Never commit them or the backups, which include password hashes, refresh tokens and private chat content.

## Offline cleanup and recovery

`DemoDataMaintenance.java` uses the existing ignored `apps/api/application-local.properties` and `BANKING_DB_URL`, `BANKING_DB_USERNAME`, `BANKING_DB_PASSWORD`, `BANKING_DB_SCHEMA` environment overrides. Run from the repository root with the existing JDBC runtime classpath (for example, Maven's dependency classpath). It supports:

- `inventory`: identify only exact validation-email patterns. Unknown customer identities stop the operation.
- `cleanup`: stop the API first. Acquire exclusive locks, export all six banking tables and the separate chat tables to `.tools/demo-backups/<timestamp>/restore.sql`, then remove validation identities and their dependent records in one database transaction. Reverse only their ledger effects on retained account balances. Verify remaining journals, rehearse the complete restore under a savepoint, roll the rehearsal back, then commit cleanup. An error explicitly rolls back all data changes.
- `fund [target-INR]`: after migration V21, post one idempotent, balanced capital deposit to bring the bank funding reserve to INR 1 crore by default, or to the supplied target. Cash is debited and the funding reserve credited. Existing customer balances are untouched. A target may be as high as INR 99,999,999,999,999,999.99.

Each backup has a SHA-256 digest. Recovery is a deliberate offline operation: stop the application, verify the digest and target schema, then execute `restore.sql` in that same schema using SQLcl/SQL*Plus with `WHENEVER SQLERROR EXIT ROLLBACK`. It replaces the current table contents with the snapshot. It does not restore or downgrade Flyway schema history. A snapshot must be restored against a compatible schema. Existing public IDs and chat content are preserved; Oracle regenerates the internal `CONVERSATION_TURNS.SEQUENCE_ID` pagination sequence in the original order because it is an ALWAYS identity. Sequences may have harmless gaps after a rehearsal. No tables are created or dropped.

## Provision and demonstrate

1. Complete cleanup and deploy V21 through normal Flyway startup.
2. Run the `fund` mode once.
3. Set `NEXA_DEMO_VISHAL_PASSWORD` to Vishal's existing password for the first run, then run `node tools/demo/seed-demo.cjs`. Later runs use the saved local demo credentials. The script reuses the three identities, saved payees, opening-fund audits, application keys and installment receipts; it does not create disposable validation customers.
4. Sign in as a customer. Each main deposit account starts with at least INR 2.5 lakh before loan/demo movements. Asha and Rahul have approved, disbursed amortizing loans and a first repayment in their history. Vishal retains his existing products and has a INR 25,000 application waiting for review. Customers have real linked payees and an active mandate to another demo customer.
5. Sign in as admin, open **Loan requests**, select **Review request**, enter a reason, and confirm approval or rejection. The queue refreshes every 30 seconds and also has a Refresh button. Approval does not move funds. The customer can then accept the approved loan; both `/accept` and the compatible `/disburse` endpoint enforce approval.

Tests use isolated H2 databases by default. Do not run optional Oracle integration fixtures against this curated demo after cleanup; those explicitly create test identities. Live verification should use the three demo identities and retain the resulting demo transaction history.

## Accounting

V21 introduces no banking tables. Loans remain customer-owned `ACCOUNTS`; admin decisions are `ADMIN_EVENT` rows in `TRANSACTIONS` with actor, reason and before/after status. Application/review fields and the pending queue live on the loan account. Historical un-disbursed auto-approved requests are returned to review; disbursed loans are preserved.

`NEXA-BANK-FUNDING` is the bank's credit-normal funding reserve. `NEXA-LOAN-CONTROL` is its credit-normal contra account for allocated loan principal. Both are SYSTEM/CLEARING accounts. A disbursement debits the customer loan and credits the deposit, and allocates funding by debiting the bank reserve and crediting loan control in the same journal. A principal repayment reverses both pairs. The system reserve therefore pays out and receives principal without losing the individual customer's outstanding-loan asset. The funding control equals total outstanding principal. V21 records an explicit balanced allocation for historical outstanding loans rather than replaying their customer transactions.

An EMI also credits `NEXA-LOAN-INTEREST` with earned interest. The business transaction identifies the bank as disbursement source / repayment destination; `target_id` and ledger postings retain its link to the individual loan. All involved accounts are locked in ascending ID order. Insufficient reserve, blocked accounts, failed ledger inserts, balances, schedules and statuses commit or roll back together.

V21's historical allocation can make an unfunded reserve negative until capital is posted. New disbursements are blocked when the reserve is insufficient. The demo `fund` step explicitly supplies the reserve before any demo loans are accepted.
