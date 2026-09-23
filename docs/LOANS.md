# Loan applications and EMI payments

Loans use the existing customer-owned `ACCOUNTS` model and authenticated `/api/v1/loans` API. Migration V20 adds application terms to accounts and amortization components to transactions. Installments are non-posting `LOAN_INSTALLMENT` transaction records. V23 adds a separate private supporting-document table, `LOAN_SALARY_SLIPS`; the six core accounting tables remain unchanged.

## API

Ownership is derived from the current user. IDs use the existing string product and transaction IDs.

| Endpoint | Contract |
| --- | --- |
| `POST /api/v1/loans/quote` | `{ "amount": 12000, "tenureMonths": 3 }`. Returns annual rate, regular/final EMI, total interest and total repayment. |
| `POST /api/v1/loans` | Multipart: JSON `application` part containing `accountId, applicationKey, purpose, amount, tenureMonths`; three `files` parts and three matching `months` fields (`YYYY-MM`). Returns 201, `status: PENDING_APPROVAL` and persisted `terms`. All documents and the application commit atomically. JSON-only requests are rejected. |
| `GET /api/v1/loans/salary-slip-requirements` | The last three completed months in the business timezone, for a new application. |
| `GET /api/v1/loans/{id}/salary-slips` | Required months and document metadata. Owner or active administrator only. |
| `POST /api/v1/loans/{id}/salary-slips` | Multipart `files` and `months` to complete an older pending application with no documents. |
| `GET /api/v1/loans/{id}/salary-slips/{documentId}` | Authorized, non-cacheable attachment download; no public URLs. |
| `GET /api/v1/loans` | Existing pagination and status filtering, including derived `OVERDUE`. |
| `GET /api/v1/loans/{id}` | Existing fields (`outstanding`, `nextEmi`, `dueAt`, etc.) plus `terms`: application key, purpose, original amount, annual rate, tenure, regular EMI, approval and closure timestamps. |
| `POST /api/v1/loans/{id}/accept` | Disburses an administrator-approved principal and creates the monthly schedule atomically. Replays return the current loan without posting again, including after closure. |
| `GET /api/v1/loans/{id}/schedule` | Installment IDs, ordinal, due date, principal, interest, total, status and payment timestamp. |
| `GET /api/v1/loans/{id}/repayment-options` | Current minimum payment, maximum payoff, interest allocation, minimum extra principal, regular EMI and projected final due date. |
| `POST /api/v1/loans/{id}/repayment-preview` | `{ "amount": 5000 }`. Read-only comparison of keep-EMI/reduce-tenure and reduce-EMI/keep-dates, including savings, full unpaid schedules and a snapshot token. |
| `POST /api/v1/loans/{id}/repay` | `amount` and UUID `requestId`; partial prepayments additionally require `prepaymentOption` (`REDUCE_TENURE` or `REDUCE_EMI`) and `previewToken`. |
| `POST /api/v1/loans/{id}/installments/{installmentId}/pay` | Pays the earliest unpaid installment from the linked account. The installment is the idempotency key; retries return the original payment. |
| `GET /api/v1/loans/{id}/payments` | Scheduled loans expose disbursement and EMI receipts with principal/interest components. Legacy loans retain their previous payment-history format. |

Every new application requires an active administrator to approve or reject it with a reason. The admin queue is `GET /api/v1/admin/loans`; decisions use `POST /api/v1/admin/loans/{id}/approve` or `/reject` with `{ "reason": "..." }`. There is no automated eligibility approval. Supported principal is INR 1,000–10,00,000 with at most two decimal places and tenure is 1–60 months. `app.loans.annual-interest-rate` defaults to 14.50% (range 0–50%, at most two decimal places). The rate is persisted at application time and cannot be overridden by an application request.

Application keys accept 1–80 letters, digits, underscores or hyphens and are scoped to the customer across all their accounts. Identical retries reuse the original application; changed details return 409. Funding requires an owned active INR savings/current account and borrower login credentials. Ownership failures return 404; invalid amounts, payment order and insufficient funds return 400 through the shared error handler.

## Salary-slip verification

Applications require a separate salary slip for each of the three completed calendar months preceding the application, using the business timezone. For September 2026 applications, these are June, July and August 2026. The server supplies the months; an older application's required months remain anchored to its original application date.

Accepted types are PDF, PNG and JPEG, at most 5 MiB each (16 MiB total multipart request limit). The server checks content signatures and declared MIME types, rejects empty/duplicate files and incorrect month sets, and sanitizes filenames. These checks do not authenticate payroll contents or replace malware scanning; the administrator must manually inspect the documents. Production deployments should add malware scanning and a defined document-retention policy before handling real salary data.

V23 stores bytes and metadata in private database BLOBs. Only the borrower or an active administrator can list/download them, with attachment, no-store and nosniff response headers. Uploaded documents are immutable; identical retries are accepted, but different files for the same submitted application return 409. Older pending applications without documents can upload the three files from their details page; previously disbursed loans are unaffected.

Approval requests now require `{ "reason": "...", "verifiedSalarySlipIds": ["D-...", "D-...", "D-..."] }`. The server checks all three required documents and their exact IDs under the loan lock. Each document records the approving administrator and verification timestamp in the same transaction as the approval and audit event. In the admin screen, download each slip, inspect it and tick its verification checkbox before reviewing/confirming approval. Rejection still requires only a reason.

## Accounting and dates

Quotes use decimal reducing-balance amortization with monthly interest rounded to paise. The final installment clears the exact remaining principal. Due dates are anchored to the disbursement day, so January 31 returns to March 31 after February. Customer dates use `nexa.business-timezone` (default Asia/Kolkata); persisted timestamps use UTC. Pending installments become overdue after their due date. No late fees are applied.

Disbursement debits the loan asset and credits the funding deposit. Each EMI debits the deposit by the full EMI, credits the loan by principal, and credits the system loan-interest account by interest. V20 creates that credit-normal `CLEARING` account (`NEXA-LOAN-INTEREST`). Zero-rate loans omit the zero-value interest posting. All balances, journals, ledger entries, installments and loan state commit or roll back together. Account locks follow ascending numeric ID order, including the interest account, to coordinate with transfers.

Outstanding means principal only. The final EMI closes the loan. Projections expose the actual next installment amount, including the adjusted final EMI. `terms.emiAmount` is the current regular EMI: it changes only when the customer chooses REDUCE_EMI after an extra principal payment. Original tenure remains the application tenure; the projected finish is the last unpaid installment date.

## Fixed-rate prepayment choices

The annual rate stored with the loan does not change. The customer can compare:

- REDUCE_TENURE: keep the current regular EMI, recalculate principal/interest, and remove unnecessary final installments.
- REDUCE_EMI: recalculate EMI using the reduced balance, fixed rate and existing unpaid installment count. Preserve every unpaid due date and its ID; never restore previously removed installments or extend the current maturity.

Minimum extra principal is one current regular EMI, unless paying off the loan in full. For a combined EMI-plus-prepayment, this minimum applies to the extra portion in addition to the next EMI. Once the current calendar-month coverage condition passes, principal-only payments require at least one regular EMI or the smaller payoff. Early explicit installment payments remain supported separately.

The comparison subtracts any EMI being settled from the baseline, then compares future scheduled interest with and without extra principal. The interest charged in the current payment is excluded from both sides. Figures assume remaining installments are paid on their scheduled dates, use monthly interest rounded to paise, and do not introduce daily accrual or prepayment fees. A tiny balance that cannot be spread over all remaining dates in positive paise installments makes REDUCE_EMI unavailable; full closure requires no preference.

Previews do not post money. A token binds confirmation to the loan, payment amount, business date, rate, current EMI, balance and schedule. Execution rechecks under the existing ordered account locks; stale or altered previews return 409. The strategy and token are audited with the payment, and retries cannot change that strategy. Changes to projections, regular EMI, balances and journal entries are atomic.

## Existing clients and migration

Creation is now multipart-only, including for the legacy principal-only contract (`accountId`, `displayName`, `principal`, `interestRate`), which retains its 200 response when supplied as the application part with three salary slips. Update API clients and demo scripts that previously posted JSON-only applications. Existing loans are not given invented schedules. `/account`, `/disburse` and `/repay` remain supported. Ordinary scheduled EMI and payoff payments require a UUID `requestId`; partial prepayments additionally require the explicit preference and preview token described above. Retries return the original transaction, including after closure. Chat repayment supports normal payments and directs extra-principal requests to loan details for a preference instead of silently selecting one.

No new migration is required for the prepayment-choice feature. Existing fixed-rate scheduled loans use the new comparison without modifying already-paid records or their original interest rate.

Deploy migrations through V23 using the existing Flyway process before running the updated API against an existing database. V22 remains the linked-payee repair; V23 adds salary-slip storage. Restart the backend and rebuild/restart the frontend to use the document workflow. Earlier migrations remain unchanged. Oracle conditional unique indexes exclude ordinary accounts and unrelated transaction records without loan keys. The H2 integration test executes V20 with equivalent `NULLS DISTINCT` constraints for those two Oracle-specific indexes, and executes the V23 document migration.

## Verification

`LoanCalculationServiceTest` covers reference amounts, boundaries, zero rates, final rounding, month ends, leap years and invalid inputs. `LoanIntegrationTest` covers authenticated HTTP application-to-closure flows, migration DDL, balanced accounting, ownership, overdue filtering, concurrent retries, repayment compatibility and injected posting failures. It uses isolated H2 by default; optional Oracle verification requires an already migrated schema.

```powershell
cd apps/api
./mvnw.cmd '-Dtest=LoanCalculationServiceTest,LoanIntegrationTest' test
./mvnw.cmd verify
```

## Curated demo and bank settlement

V21 adds persisted administrator review and the bank funding reserve/control accounts. Loan requests now wait for administrator approval, including the legacy creation endpoint. Pending or rejected loans cannot be disbursed. See [the demo setup and accounting guide](../tools/demo/README.md) for cleanup, recovery, funding, credentials and the three-customer demonstration.
