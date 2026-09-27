# Nexa walkthrough and integration boundaries

This document replaces the earlier card and payment simulation walkthrough. Customer-facing transfers, bills, cards and scheduled payments now use their dedicated application workflows. The configured database contains the actual records used by Nexa; it is not reset or replaced by a browser fixture.

## Payments

Open **Payments** and choose **Nexa account** or **Other bank**. Nexa transfers use the existing review and confirmation flow and post balances and ledger entries. Own-account transfers are available inside the Nexa option. Bills, saved payees, scheduled payments, cards and direct debits remain available through payment shortcuts.

Other-bank transfers use Cashfree's sandbox. Their form and result disclose that no real money moves and Nexa balances and bills remain unchanged. They are separate from a live bank network or utility-bill settlement service. See [external bank transfers](EXTERNAL_BANK_TRANSFERS.md).

Bills use the dedicated bill-payment flow and derive status from recorded payments and due dates. They are not marked paid by a showcase receipt. See [bill payments](BILL_PAYMENTS.md).

The old `#/send-money` and `#/external-transfers` links still open the unified Payments workspace. New links use `#/payments`, with `?destination=other-bank` and an optional `payee` parameter when needed.

## Cards

**Cards → Request a card** offers debit and credit requests against an owned, active INR savings or current account.

- A debit request creates an active local Nexa card record linked to the selected account. It adds no credit and does not change the account balance.
- A credit request starts as **Pending approval** with no available credit limit. In **Administration → Card requests**, an administrator reviews it, approves a positive credit limit or rejects it with a reason. The persisted decision appears in the customer's card list and details.
- **Block card** and **Unblock card** update the saved Nexa card state after confirmation. Unblocking cannot override a bank restriction, and an unapproved, rejected or closed request cannot use these controls.
- The server prevents duplicate usable or pending cards of the same type for a funding account. Card creation uses a request UUID; uncertain submissions retry the same payload. Review decisions and status changes also support safe retries and status checks.

These are local card records, not physical cards or card-network credentials. Nexa does not issue a real PAN, CVV, Visa/RuPay card, merchant-payment capability or replacement card through this workflow. Displayed local references identify records in Nexa. The interface keeps this boundary visible and does not show simulated card-payment or replacement controls.

Card endpoints are:

- `POST /api/v1/cards/applications`
- `GET /api/v1/cards` and `GET /api/v1/cards/{id}`
- `POST /api/v1/cards/{id}/block` and `/{id}/unblock`
- `GET /api/v1/admin/card-applications` and `/{id}`
- `POST /api/v1/admin/card-applications/{id}/approve` and `/{id}/reject`

Card application metadata is introduced by `V31__local_card_applications.sql`. Normal backend startup applies pending Flyway migrations. Do not edit previously applied migrations or use Flyway repair to conceal a schema mismatch.

## Legacy showcase records

The `/api/v1/demo/actions` routes remain a compatibility boundary for older clients and saved history. They are not the current Cards or Payments screen. Old simulated card freeze, unfreeze and replacement requests cannot be newly prepared or confirmed; use the persisted card controls instead. Historical simulated receipts remain historical evidence of a demonstration, not proof that a card or payment changed.

The compatibility transfer flow can delegate to the real internal Nexa transfer service, so a route containing `demo` does not imply that every operation is harmless or balance-neutral. Bill simulations are rejected and redirect users to the dedicated bill flow. Existing simulation records never become live payment or card-network transactions.

Legacy `PAY_CARD` requests remain simulations in the compatibility API. The current customer card page exposes no payment action. Real credit-card billing, purchase settlement and repayment are separate work and are not implemented by the card application or block/unblock features.

## Verification

Use the repository's frontend and backend test commands, with isolated build copies when a local API or frontend is already running. Card regression tests cover owned active account eligibility, debit creation, credit review, duplicate requests, persisted block/unblock behavior, unchanged balances, authorization and uncertain-response recovery. Frontend tests cover the same customer and administrator interactions, including pending/rejected states and explicit confirmation.

Browser fixture data is synthetic and does not reach the banking database. Fixture walkthroughs supplement API tests; they do not verify Cashfree credentials, Oracle migrations or real card-network issuance. Current verification results belong in the completion report for the relevant change, rather than an outdated fixed test count in this document.
