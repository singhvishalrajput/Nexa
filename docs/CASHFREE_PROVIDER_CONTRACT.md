# Cashfree sandbox provider contract

Verified against Cashfree's official documentation and SDK on 26 September 2026. This describes the isolated HTTP adapter; application review, ownership, receipt storage and state transitions belong to `ExternalTransferService`.

## Environment and credentials

The adapter is disabled by default. Its immutable startup settings are `nexa.payouts.mode` (`DISABLED`, `SANDBOX`, `LIVE`), `nexa.payouts.client-id`, `nexa.payouts.client-secret` and `nexa.payouts.timeout-seconds` (default 10, allowed 1–30). This release rejects `LIVE` even when credentials are supplied. It has no configurable remote URL and uses only `https://sandbox.cashfree.com/payout/transfers`. A package-private test constructor accepts IPv4 loopback HTTP only.

Cashfree documents separate sandbox and production hosts, and instructs clients to check transfer status following server errors. Production requires its own merchant setup and IP authentication; this release does not implement live activation or dynamic-IP signature authentication. See [v2 environments](https://www.cashfree.com/docs/api-reference/payouts/v2/payouts-api-v2-new) and [Payouts authentication](https://www.cashfree.com/docs/payouts/payouts/integrations/payouts-2fa).

## HTTP contract

Submission uses `POST /payout/transfers` with `x-client-id`, `x-client-secret` and `x-api-version: 2024-01-01`. The payload contains the persisted `transfer_id`, exact decimal `transfer_amount`, INR currency, IMPS mode, beneficiary name and nested bank account/IFSC details. No provider beneficiary creation call is needed for this direct-details request. The provider reference is limited to 40 letters, digits or underscores. See [Standard Transfer v2](https://www.cashfree.com/docs/api-reference/payouts/v2/transfers-v2/standard-transfer-v2).

Cashfree's [official SDK types](https://github.com/cashfree/cashfree-payout-sdk-nodejs/blob/main/api.ts) define the direct bank details and optional response fields. The request uses `beneficiary_instrument_details.bank_ifsc`; the response schema uses `beneficiary_instrument_details.ifsc`. The adapter accepts either response spelling and rejects contradictory values. Names accept Unicode letters, combining marks and ordinary spaces up to 100 code points; this interprets the documented alphabetic-name constraint without assuming Latin-only names. Provider-side validation remains authoritative.

Recovery uses `GET /payout/transfers?transfer_id=<persisted-reference>`. Optional amount/account/IFSC details are returned unchanged when supplied, and remain absent when omitted. Invalid supplied details produce an uncertain result; they are never silently treated as omitted. See [Get Transfer Status v2](https://www.cashfree.com/docs/api-reference/payouts/v2/transfers-v2/get-transfer-status-v2).

## State handling and replay

The adapter never automatically repeats a submission. The application commits its submission claim before network access, and uncertain submissions are recovered with GET requests using the original identifier. Cashfree documents identifier uniqueness; the application does not assume that repeating POST will replay an earlier response.

The application waits for `SUCCESS` plus `COMPLETED` before showing simulated completion. `SUCCESS` plus `SENT_TO_BENEFICIARY` still awaits beneficiary credit. Received, queued, pending, approval and validation states remain pending. A duplicate-transfer rejection remains uncertain because it can describe an already existing original payout. HTTP failures, malformed responses, redirect responses and unknown statuses never imply payment failure or recipient credit. See [v2 response codes](https://www.cashfree.com/docs/api-reference/payouts/v2/response-codes).

Cashfree allows a successful payout to be reversed later, so completed receipts may still be refreshed and changed to reversed. See [Payout lifecycle](https://www.cashfree.com/docs/payouts/payouts/make-payouts/lifecycle). A not-found observation is never permission to refund or resubmit. No webhooks are trusted or implemented by this adapter.

## Local verification

`CashfreePayoutProviderTest` uses a local HTTP server with synthetic credentials. It exercises request shape, exact decimal amounts, state mapping, later reversals, response correlation, uncertainty recovery, duplicate rejection, credential-safe errors, redirect refusal, a 64 KiB response limit, full-response timeout, disabled/live blocking and input validation. No test calls Cashfree, verifies merchant credentials, or proves that sandbox access has been activated.
