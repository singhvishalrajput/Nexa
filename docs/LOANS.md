# Loan applications and EMI payments

Loans use the existing customer-owned `ACCOUNTS` model and authenticated `/api/v1/loans` API. Migration V20 adds application terms to accounts and amortization components to transactions. Installments are non-posting `LOAN_INSTALLMENT` transaction records. No additional banking tables are introduced.

## API

Ownership is derived from the current user. IDs use the existing string product and transaction IDs.

| Endpoint | Contract |
| --- | --- |
| `POST /api/v1/loans/quote` | `{ "amount": 12000, "tenureMonths": 3 }`. Returns annual rate, regular/final EMI, total interest and total repayment. |
| `POST /api/v1/loans` | `{ "accountId": 123, "applicationKey": "education-1", "purpose": "Education", "amount": 12000, "tenureMonths": 3 }`. Returns 201, `status: PENDING_APPROVAL` and persisted `terms`. No money moves yet. |
| `GET /api/v1/loans` | Existing pagination and status filtering, including derived `OVERDUE`. |
| `GET /api/v1/loans/{id}` | Existing fields (`outstanding`, `nextEmi`, `dueAt`, etc.) plus `terms`: application key, purpose, original amount, annual rate, tenure, regular EMI, approval and closure timestamps. |
| `POST /api/v1/loans/{id}/accept` | Disburses an administrator-approved principal and creates the monthly schedule atomically. Replays return the current loan without posting again, including after closure. |
| `GET /api/v1/loans/{id}/schedule` | Installment IDs, ordinal, due date, principal, interest, total, status and payment timestamp. |
| `POST /api/v1/loans/{id}/installments/{installmentId}/pay` | Pays the earliest unpaid installment from the linked account. The installment is the idempotency key; retries return the original payment. |
| `GET /api/v1/loans/{id}/payments` | Scheduled loans expose disbursement and EMI receipts with principal/interest components. Legacy loans retain their previous payment-history format. |

Every new application requires an active administrator to approve or reject it with a reason. The admin queue is `GET /api/v1/admin/loans`; decisions use `POST /api/v1/admin/loans/{id}/approve` or `/reject` with `{ "reason": "..." }`. There is no automated eligibility approval. Supported principal is INR 1,000–10,00,000 with at most two decimal places and tenure is 1–60 months. `app.loans.annual-interest-rate` defaults to 14.50% (range 0–50%, at most two decimal places). The rate is persisted at application time and cannot be overridden by an application request.

Application keys accept 1–80 letters, digits, underscores or hyphens and are scoped to the customer across all their accounts. Identical retries reuse the original application; changed details return 409. Funding requires an owned active INR savings/current account and borrower login credentials. Ownership failures return 404; invalid amounts, payment order and insufficient funds return 400 through the shared error handler.

## Accounting and dates

Quotes use decimal reducing-balance amortization with monthly interest rounded to paise. The final installment clears the exact remaining principal. Due dates are anchored to the disbursement day, so January 31 returns to March 31 after February. Customer dates use `nexa.business-timezone` (default Asia/Kolkata); persisted timestamps use UTC. Pending installments become overdue after their due date. No late fees are applied.

Disbursement debits the loan asset and credits the funding deposit. Each EMI debits the deposit by the full EMI, credits the loan by principal, and credits the system loan-interest account by interest. V20 creates that credit-normal `CLEARING` account (`NEXA-LOAN-INTEREST`). Zero-rate loans omit the zero-value interest posting. All balances, journals, ledger entries, installments and loan state commit or roll back together. Account locks follow ascending numeric ID order, including the interest account, to coordinate with transfers.

Outstanding means principal only. The final EMI closes the loan. Projections expose the actual next installment amount, including the adjusted final EMI, while `terms.emiAmount` retains the original regular EMI.

## Existing clients and migration

The previous creation contract (`accountId`, `displayName`, `principal`, `interestRate`) retains its original 200 response and principal-only behavior. Existing loans are not given invented schedules. `/account`, `/disburse` and `/repay` remain supported. For a scheduled loan, `/repay` requires the exact next EMI plus a UUID `requestId`; retries return the original transaction, including after closure. Chat repayment selects the next EMI automatically and uses the same posting service.

Deploy V20 and V21 through the existing Flyway process before running the updated API against an existing database. Earlier migrations remain unchanged. Oracle conditional unique indexes exclude ordinary accounts and unrelated transaction records without loan keys. The H2 integration test executes V20 with equivalent `NULLS DISTINCT` constraints for those two Oracle-specific indexes.

## Verification

`LoanCalculationServiceTest` covers reference amounts, boundaries, zero rates, final rounding, month ends, leap years and invalid inputs. `LoanIntegrationTest` covers authenticated HTTP application-to-closure flows, migration DDL, balanced accounting, ownership, overdue filtering, concurrent retries, repayment compatibility and injected posting failures. It uses isolated H2 by default; optional Oracle verification requires an already migrated schema.

```powershell
cd apps/api
./mvnw.cmd '-Dtest=LoanCalculationServiceTest,LoanIntegrationTest' test
./mvnw.cmd verify
```

## Curated demo and bank settlement

V21 adds persisted administrator review and the bank funding reserve/control accounts. Loan requests now wait for administrator approval, including the legacy creation endpoint. Pending or rejected loans cannot be disbursed. See [the demo setup and accounting guide](../tools/demo/README.md) for cleanup, recovery, funding, credentials and the three-customer demonstration.
