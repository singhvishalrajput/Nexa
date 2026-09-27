# External bank transfers — Cashfree sandbox

This release adds an actual Cashfree Payouts v2 sandbox adapter, encrypted external-bank payees, explicit transfer review/confirmation and durable status recovery. It does not fabricate success locally. A configured provider account is required to submit test requests.

**This is a provider sandbox integration. No real money moves. Nexa account balances, journals and bill statuses remain unchanged.** A selected Nexa account provides the owned account context and an amount limit for the test; it is not a source of funds at Cashfree. `LIVE` execution is deliberately unavailable in this release. A future live release needs approved provider access, actual funding, ledger reservation/settlement/refund integration and operational reconciliation.

## Local setup

1. Create or access a Cashfree **Payouts** sandbox account. Obtain the client ID and client secret from its Payouts dashboard under **Developers → API Keys**. Use Payouts sandbox credentials, not Payment Gateway or production credentials. Follow [Cashfree's setup instructions](https://www.cashfree.com/docs/api-reference/payouts/getting-started-with-payouts-apis).
2. Add the following settings to your existing ignored `apps/api/.env`, replacing the placeholders locally. Keep the other database, admin and identity settings. Do not put secret values in source control, chat or screenshots.

```dotenv
NEXA_PAYOUTS_MODE=SANDBOX
NEXA_PAYOUTS_CLIENT_ID=your_payouts_sandbox_client_id
NEXA_PAYOUTS_CLIENT_SECRET=your_payouts_sandbox_client_secret
NEXA_PAYOUTS_TIMEOUT_SECONDS=15
```

3. External account numbers are encrypted with a domain-separated key derived from your existing `NEXA_ONBOARDING_IDENTITY_KEY`. If a separate storage key is needed, configure `NEXA_PAYOUTS_ACCOUNT_KEY` as a base64-encoded 32-byte random key. Preserve the chosen key with database backups. Changing the key after storing payees makes those encrypted details unreadable; do not regenerate it on each startup.
4. Restart the API from `apps/api` using the project's existing database settings. Flyway applies additive migrations V28 and V29. Do not delete tables or repair unrelated migration history. Restart/refresh the frontend.

Missing credentials disable transfer preparation and submission with an explanation. Readiness means configuration is present; it does not certify that credentials have been accepted or that a provider request succeeded. The backend only calls the fixed Cashfree sandbox host, with redirects disabled. Setting mode `LIVE` cannot enable live payments.

## Customer flow

- In **Payees**, select the other-bank option and enter a payee nickname, account-holder name, bank name, account number twice and IFSC. External payees remain separate from verified internal Nexa recipients. Saving details does not independently verify the account-holder's identity or register a successful payment.
- Open **Payments**, then select **Other bank** in the destination dropdown, or follow **Make a transfer** from an external payee's details. Select an owned active INR deposit account for sandbox context and an external payee. Enter at least INR 1, no more than the displayed available balance, with at most two decimal places. Nexa transfers use the same Payments page with **Nexa account** selected. Old `#/send-money` and `#/external-transfers` bookmarks still open the corresponding flow.
- Review the recipient name, masked account, IFSC and amount. These are saved details supplied by the customer, not an account-name verification response from the bank. Select **Confirm transfer** to submit after reading the sandbox disclosure.
- **Pending** means the result is not yet established. Use the status-refresh action to check the same request. Completed, failed and reversed outcomes come from correlated provider results. The screen and history identify sandbox outcomes; neutral navigation and button labels do not imply live settlement.
- Cancelling or expiring an unsubmitted review does not send a provider request. After submission, the request cannot be locally cancelled or replaced while its result is unknown. Request history remains available after refresh or sign-out.

The browser retains a request ID for recovery, not full external account details. The backend commits its submission claim before the network call. Repeated confirmation cannot issue a second provider POST for the same review. A timeout, duplicate response or unknown result remains unresolved and is reconciled through the provider status API. A provider not-found response after submission does not prove that no transfer was accepted; investigate it in the sandbox dashboard before creating a replacement.

## Scope and compatibility

Internal Nexa transfers and internal bill payments keep their current behavior. External payees use their own endpoints and storage; old masked imports are not silently treated as verified external destinations. Existing chat transfers remain internal; use the external-transfer screen for sandbox requests.

A bank transfer to a biller's account is different from a utility bill-payment integration. This feature does not connect to a biller directory or confirm settlement of an electricity/water consumer account. Therefore it never marks a bill paid. The bill's customer/consumer number remains a reference printed on that bill, while external transfers use account number and IFSC.

## API and storage

`POST /api/v1/external-payees` accepts `displayName`, `recipientName`, `bankName`, `accountNumber`, `accountNumberConfirmation` and `ifsc`. External payees are immutable in this release. List/detail responses expose masked accounts only. Full account numbers use AES-GCM with owner/payee-bound authenticated data; changing or copying ciphertext fails verification. Destination fingerprints use an owner-bound HMAC with a separately derived key, so a database copy does not enable account-number guessing through unkeyed hashes. Account number and IFSC together identify an external destination; numbers may coincide at different banks.

`/api/v1/external-transfers` exposes authenticated readiness, preparation, details, history, confirmation, cancellation and explicit refresh endpoints. Each review has a UUID request key, immutable provider request ID and reviewed destination fingerprint. API roles and ownership checks apply to every review. The V29 table contains sandbox receipt state and masked snapshots; no payment transaction or ledger entry is written.

The adapter uses [Cashfree Standard Transfer v2](https://www.cashfree.com/docs/api-reference/payouts/v2/transfers-v2/standard-transfer-v2) and [Get Transfer Status v2](https://www.cashfree.com/docs/api-reference/payouts/v2/transfers-v2/get-transfer-status-v2). It treats an accepted or sent request as pending until the provider reports completion. Unknown, malformed or mismatched responses cannot mark a test completed. No webhook URL or unauthenticated callback is enabled; status refresh queries the authenticated provider API directly.

## Verification limits

Automated checks use local HTTP provider fixtures and isolated H2 databases; they send no requests to Cashfree and do not use live database credentials. A real sandbox walkthrough still requires your configured Payouts sandbox keys and provider-supported test details. Production bank settlement is outside this sandbox release.

Verified on 26 September 2026: backend Maven `verify` ran 547 tests with zero failures/errors and one optional model test skipped, then built the executable JAR. The verification copy was rebuilt from scratch before the focused checks; all 251 source/resource files and the POM matched the working source. Frontend typecheck, lint, all 356 tests and the isolated Oracle JET production release build passed; all 113 build inputs matched the working source. Existing optional Sass and Node deprecation warnings remain. No live Oracle migration, Cashfree sandbox request, real customer transaction, commit or push was performed during verification.
