# Personal loans — banking-application

This implementation belongs to `conversational-banking-backend/banking-application`, with Java classes under `src/main/java/com/ofss` in the existing `beans`, `config`, `controller`, `repository`, and `service` packages. It targets this module's Java 17 / Spring Boot build and its numeric customer/account IDs. It does not use the separate `Backend/nexa-api` application.

Valid submissions are immediately approved. There is no credit-score, income, or eligibility check. Validation, authentication, ownership, and active-account checks still apply. Acceptance explicitly disburses the loan; approval alone moves no money.

## Database setup

Run `database/personal-loans.sql` once in the Oracle schema used by this application's datasource, using SQL Developer **Run Script / F5**, SQLcl, or SQL*Plus. Select the schema that already contains `CUSTOMERS.ID` and `ACCOUNTS.ID` as numeric primary keys. The script depends on those existing tables; it does not create or repair them. Do not rerun `database/schema.sql` on populated tables.

The file contains three plain `CREATE TABLE` statements followed by four `CREATE INDEX` statements, matching the style of the existing schema. Every statement ends with a semicolon. There is no PL/SQL wrapper, compatibility preflight, or final slash.

The deliverable named `create-personal-loan-tables.sql` contains the same SQL. This module does not use Flyway: there is no V6 migration or automatic Flyway setup involved. Restart the backend after creating the tables.

This is a one-time setup script, not a rerunnable migration. The three loan-table names and new index names must be unused. Running it again produces an object-already-exists error (`ORA-00955`). It does not drop, alter, overwrite, or migrate any existing table, including tables from the earlier `Backend/nexa-api` implementation. If loan tables already exist, check their structure before running new statements.

Oracle DDL commits implicitly. If execution fails partway through, earlier successful statements remain applied; inspect what was created and run only the missing statements after resolving the cause. This file change does not execute SQL against your database or resolve an existing database/schema error.

### What each table stores

| Table | One row represents | Example |
| --- | --- | --- |
| `LOANS` | One loan application and its approved terms, current principal balance, dates, and status | A customer's INR 2,00,000 loan for 24 months |
| `LOAN_INSTALLMENTS` | One scheduled monthly EMI, including its principal/interest split and whether it has been paid | Installment 1 of that 24-month loan |
| `LOAN_PAYMENTS` | One successful movement of loan money | The initial disbursement or payment of installment 1 |

Applying creates one `LOANS` row with status `APPROVED`. Acceptance creates the monthly installment rows and one `DISBURSEMENT` payment. Each successful EMI creates an `EMI_PAYMENT` row and marks its installment `PAID`. The existing `TRANSACTIONS` table is not used for this history.

The disbursement time is stored only in `LOAN_PAYMENTS.PAID_AT` on the `DISBURSEMENT` record. Loan details do not contain a separate disbursement-date field; retrieve that payment through the loan payment-history endpoint.

All new primary keys use Oracle numeric identity columns: Oracle generates the ID, and Java reads it as a `Long`. Money uses `NUMBER(19,2)` for two decimal places; due dates use `DATE`; event times such as `PAID_AT` use `TIMESTAMP`. `DEFAULT SYSTIMESTAMP` supplies the current database time when a required timestamp is omitted.

### Fields and rules that need explanation

| Field or rule | Why it exists |
| --- | --- |
| `CUSTOMER_ID`, `ACCOUNT_ID`, `LOAN_ID` | Link each record to its customer, receiving/repaying account, or parent loan. Foreign keys require those referenced records to exist. |
| `APPLICATION_KEY` | A client-generated identifier for one application. Reusing it for the same customer avoids creating another loan when the request is retried. |
| `VERSION` | A counter maintained by JPA when a loan changes. It detects attempts to save an older copy over a newer version; clients do not set it. |
| `AMOUNT`, `EMI_AMOUNT`, `OUTSTANDING_AMOUNT` on `LOANS` | Original principal, regular monthly payment, and remaining principal respectively. Remaining principal starts at zero and becomes the loan amount when disbursed. |
| `INSTALLMENT_NUMBER` | The position in the schedule (1, 2, 3...), separate from the database-generated installment ID. It is unique within its loan. |
| `PRINCIPAL_AMOUNT`, `INTEREST_AMOUNT`, `TOTAL_AMOUNT` / `AMOUNT` | Record the repayment split. Check constraints ensure the total equals principal plus interest. |
| `INSTALLMENT_ID` on `LOAN_PAYMENTS` | Identifies the EMI being paid. It is null for disbursement because disbursement does not pay an EMI. |
| `PAYMENT_REFERENCE` and `PAYMENT_TYPE` | A unique payment identifier and its direction: `DISBURSEMENT` credits the account; `EMI_PAYMENT` debits it. Stored amounts stay positive. |

`UNIQUE (CUSTOMER_ID, APPLICATION_KEY)` prevents duplicate applications for one customer. `UNIQUE (LOAN_ID, INSTALLMENT_NUMBER)` prevents duplicate months in a schedule. Unique payment references and installment links prevent duplicate successful payment records.

The payment table's composite foreign key `(INSTALLMENT_ID, LOAN_ID)` checks both values together. It prevents a payment for loan A from referencing an installment belonging to loan B. The corresponding `(ID, LOAN_ID)` unique constraint on installments provides the referenced pair.

`CHECK` constraints reject invalid amounts, rates, tenures, status values, or payment splits. These rules protect the data even if a statement comes from outside the Java application; they are not an eligibility check.

Three ordinary indexes speed up finding loans by account and payments by loan/account. The fourth, `UQ_LOAN_DISBURSEMENT`, is a unique conditional index: it permits only one `DISBURSEMENT` per loan while allowing that loan's many EMI payments.

## Behavior and defaults

- Loan amount: INR 1,000–10,00,000, at most two decimal places.
- Tenure: 1–60 months.
- Annual rate: 14.50% by default, configured through `app.loans.annual-interest-rate` or environment variable `APP_LOANS_ANNUAL_INTEREST_RATE`.
- Allowed annual rate: 0–50%, at most two decimal places.
- Outstanding principal is zero while approved, becomes the original amount on disbursement, and falls by each repaid principal portion.
- Rate and EMI are fixed when the application is created; changing configuration affects new loans and quotes.
- Interest is calculated monthly on reducing principal. All money calculations use `BigDecimal`, rounded to paise. The last EMI clears rounding differences and can differ slightly from the regular EMI.
- The first EMI is due one month after disbursement. Subsequent dates are based on the original disbursement date.
- The loan starts `APPROVED`, becomes `ACTIVE` after acceptance, and becomes `CLOSED` after full repayment.
- Installments store `PENDING` or `PAID`. Overdue status is derived when reading pending installments/active loans; no scheduled collection or late fee is implemented.
- Repayment is manual and pays the full earliest unpaid installment from the original account. Early payment is allowed at the scheduled amount; this does not recalculate interest as an early-settlement product would.
- Failed attempts roll back and do not create payment-history rows.

## API

Use this application's existing login flow and send `Authorization: Bearer <token>` on loan requests. All routes are customer-owned: the backend derives the customer from authentication and checks account/loan ownership. JSON requests need `Content-Type: application/json`.

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/loans/quote` | Estimate only; creates no records |
| POST | `/api/loans` | Submit and immediately approve a valid application |
| GET | `/api/loans` | Current customer's loans |
| GET | `/api/loans/{loanId}` | Loan details and remaining principal |
| POST | `/api/loans/{loanId}/accept` | Accept, credit the account, and generate installments |
| GET | `/api/loans/{loanId}/schedule` | Ordered installment schedule |
| GET | `/api/loans/{loanId}/payments` | Successful disbursement and repayment history |
| POST | `/api/loans/{loanId}/installments/{installmentId}/pay` | Pay the full earliest unpaid installment |

### Quote

`POST /api/loans/quote`

```json
{
  "amount": 200000,
  "tenureMonths": 24
}
```

The interest rate is supplied by backend configuration, not by the customer.

### Apply

`POST /api/loans`

```json
{
  "accountId": 1,
  "applicationKey": "personal-loan-2026-001",
  "purpose": "Home renovation",
  "amount": 200000,
  "tenureMonths": 24
}
```

Replace `accountId` with an actual numeric ID of an active account owned by the authenticated customer. Keep the returned numeric `id` as `loanId`.

`applicationKey` identifies one application for safe retries. Use 1–80 letters, digits, underscores, or hyphens. Reusing the same key with identical details returns that application; reusing it with different details is rejected. Generate a new key for a new application.

### Accept

`POST /api/loans/{loanId}/accept` — no request body.

Acceptance locks the loan/account, credits the principal to `ACCOUNTS.BALANCE`, writes a `DISBURSEMENT` payment, generates installments, and activates the loan in one database transaction. Failure rolls back every change. Retrying acceptance returns the accepted loan without a second credit.

Fetch the schedule after acceptance to obtain numeric installment IDs. No persisted installments exist before disbursement.

### Pay an EMI

`POST /api/loans/{loanId}/installments/{installmentId}/pay` — no request body.

The backend selects the installment amount and original account; the client cannot change them. It checks account status and funds, debits the full EMI, records its principal/interest split, marks the installment paid, reduces outstanding principal, and updates the next due date within one database transaction. The final EMI closes the loan.

Pay the earliest unpaid installment first. Retrying a paid installment returns its existing payment without another debit. Insufficient funds, an inactive account, or an invalid installment order leaves balances and loan records unchanged.

Payment amounts are positive. `DISBURSEMENT` credits the account, and `EMI_PAYMENT` debits it. Only `POSTED` successful records are stored. Disbursements have a null `installmentId`; repayments link to the paid installment.

## Account balance and history

The existing `TRANSACTIONS`, `JOURNAL_ENTRIES`, and `LEDGER_ENTRIES` tables are unchanged and do not receive loan rows. Existing journals require a transaction reference, and existing transaction types cover only deposits, withdrawals, and transfers. Loans therefore maintain their separate history in `LOAN_PAYMENTS`. View loan activity through `GET /api/loans/{loanId}/payments`; it will not appear in the existing transaction-history endpoint.

The existing account balance reflects disbursements and repayments. Shared account-row locks coordinate loan balance changes with existing banking operations, and loan-row locks serialize concurrent acceptance/repayment requests. Unique application keys, payment references, installment links, and the one-disbursement index prevent duplicate successful operations. Loan account changes and payment records commit or roll back together.

This is a separate loan subledger, not new entries in the existing double-entry ledger. Any reconciliation that compares the bank balance with only legacy ledger entries must include loan disbursements/repayments as a separate source.

The existing frontend is unchanged. Its conversational flow can call the loan endpoints and confirm acceptance or repayment before issuing those commands.

## Scope

Included: quote, automatic approval, explicit acceptance/disbursement, schedule, manual full EMI repayment, closure, ownership, duplicate protection, and derived overdue information.

Deferred: credit checks, automatic collections, fees, partial payments, interest-adjusted prepayment, loan restructuring, reversals, and persisted failed-attempt history.
