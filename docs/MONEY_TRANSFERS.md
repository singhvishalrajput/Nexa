# Sending money between Nexa accounts

## Available flow

Open **Send money** in navigation, the Payments page, the overview balance panel, or the chat's Send Money button. Select the source account, enter a recipient's full Nexa account number (or select another account you own), and enter an INR amount. Review shows the recipient's name, masked accounts and exact amount. Only the final Send button posts a transfer.

The existing core transaction service changes both balances and creates one transaction, one journal entry and two equal debit/credit ledger entries. These records and the transfer receipt commit atomically. A completed receipt links to the existing transaction-history detail page.

Other-bank, UPI, bill and card payments still require their respective payment-network integrations. Saved-payee review remains unchanged. No external payment is simulated as successful by the production service.

## Backend setup

Restart the API with Flyway enabled to apply `V14__money_transfer_requests.sql`, after V13. This is an additive migration; it does not drop existing tables or reset balances. The running database was not modified during implementation verification.

New table: MONEY_TRANSFER_REQUESTS

Fields: ID, USER_ID, SOURCE_ACCOUNT_ID, DESTINATION_ACCOUNT_ID, SOURCE_NAME, SOURCE_MASKED, RECIPIENT_NAME, DESTINATION_MASKED, AMOUNT, CURRENCY_CODE, STATUS, TRANSACTION_REFERENCE, CREATED_AT, EXPIRES_AT, COMPLETED_AT.

## API contract

All endpoints require an authenticated CUSTOMER or ADMIN. Ownership is checked independently of the role.

- `POST /api/v1/money-transfers/prepare`: body has `sourceAccountId`, `amount`, and exactly one of `destinationAccountNumber` or `destinationAccountId`. The latter is restricted to another account owned by the caller. No balances change during preparation. Returns a durable review ID valid for five minutes.
- `POST /api/v1/money-transfers/{id}/confirm`: uses only the stored review; the caller cannot substitute an amount or recipient. Repeating the same ID returns the same completed receipt.
- `GET /api/v1/money-transfers/{id}`: returns the caller's review/receipt. Status is READY, EXPIRED or COMPLETED. It waits for an in-flight confirmation's row lock before reporting the result.

No destination balance or full account number is returned. Both accounts must be active customer accounts in INR. Amounts must be positive, with at most two fractional digits and thirteen integer digits. Ownership, balance, currency and status are rechecked under account locks at confirmation. Locks follow account-ID order to avoid reciprocal-transfer deadlocks.

## Connection recovery

The browser stores only the review ID in session storage, keyed by signed-in user. Reloading checks that request before allowing another transfer. After an uncertain response, the UI keeps the same ID, blocks editing into a new request, and provides status checking or an explicit retry of that same request. The server's row lock and atomic transaction prevent that retry from posting twice. There is no automatic financial retry.

## Verification

- Frontend typecheck, lint and build pass; 46 frontend tests pass, including review-only behavior, rapid duplicate clicks, invalid amounts, retained form entries, uncertain-response recovery and saved-review recovery.
- Full backend suite passed with 82 tests before the final concurrency refinement. All nine dedicated transfer integration tests pass after that refinement, covering cross-customer and own-account transfers, confirmation replay, concurrent confirmations, separate requests competing for one balance, authorization, invalid recipients/amounts, changed balances/status, expiry, unsupported currencies/system accounts and rollback after a ledger failure.
- 360px browser fixture: source/recipient/amount entry, review, explicit simulated confirmation, receipt and recovery of the same receipt after reload. No horizontal page overflow. No live financial transaction was executed during verification.
