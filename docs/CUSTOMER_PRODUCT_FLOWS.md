# Scheduled payments, cards and customer forms

## Local upgrade

Keep your existing database and apply the additive Flyway migrations using the normal API startup. V30 adds durable scheduled-payment instructions; V31 adds local card-application fields to accounts. Neither creates presentation customers, credits balances, or activates historical schedules. Do not drop tables or use Flyway repair to bypass migration failures. Teammates need the same source and migrations with their own ignored environment configuration.

Restart the API after updating and refresh the frontend. The `local` profile enables the scheduled-payment worker by default; `NEXA_SCHEDULED_PAYMENTS_ENABLED=false` pauses it and prevents new authorizations. Other deployment profiles must explicitly enable `nexa.scheduled-payments.enabled` after applying migrations. No Cashfree credentials are needed for these internal flows.

## Scheduled payments

Open **Scheduled payments**, choose **Schedule a payment**, select your active INR Nexa account and an active saved Nexa payee, enter the amount and a date, then review and explicitly authorize the instruction. Dates use Asia/Kolkata and must be tomorrow through the next 365 days. This release supports one-time internal transfers.

Creating a review or authorizing it does not debit or reserve money. Keep sufficient funds available. The API checks due instructions approximately every 30 seconds while running, from the start of the selected date. If the API was stopped, due instructions are processed after it restarts. Ownership, recipient, account status and funds are checked again before execution. Successful execution saves one balanced ledger transfer and marks the instruction completed in the same transaction. A failed eligibility or funds check marks it failed without moving money; a technical rollback leaves it available for retry without a partial posting.

Open the instruction to refresh its status or cancel before execution. A cancellation racing execution returns the saved outcome. Older schedule records remain visible as historical records and never run automatically. Other-bank schedules, recurring series and automatic utility-bill collection are not provided by this flow.

**Check schedule status** reads the saved instruction and shows when it was checked, even when its status is unchanged. It does not authorize a draft, run a payment early or retry a failed payment. A review stays `READY` until explicitly authorized; `SCHEDULED` means the bank is waiting to execute it on or after the selected date. `COMPLETED`, `FAILED`, `CANCELLED` and `EXPIRED` are final outcomes for that instruction.

## Loans and direct debits

The loan request uses a required type dropdown: Personal, Car, Home, Education, Business or Other. This selection records the application purpose; it does not introduce separate mortgage/collateral products or automatically change eligibility, rates or terms. The existing document review, bank approval and customer acceptance still apply.

Loan disbursement also requires available funds in this app's `NEXA-BANK-FUNDING` system reserve. The migration creates it without adding opening capital. Customer opening deposits fund customer accounts and do not fund this reserve. Approval alone does not create funds: a ₹7,000 approved loan requires at least ₹7,000 available in the reserve when the customer accepts it. If funds are insufficient, disbursement stops without posting and the loan remains approved for a later retry.

Administrators can record received bank-owned cash through **Bank funding**, after applying V32. Enter its source, amount, unique receipt/evidence reference and reason, review the details and acknowledge actual receipt. The workflow records the administrator and time, posts a balanced journal and protects against duplicate requests/references. It does not initiate an external transfer or reassign customer deposits. See [Bank funding and loan disbursement](BANK_FUNDING.md) for setup, receipt recovery and the ₹7,000 example. An approved loan remains awaiting funding until enough reserve is available.

For a mandate, choose **Saved Nexa payee** to reuse an existing verified internal recipient, or **Enter a Nexa account** for manual entry. The server resolves the saved payee under the signed-in customer; the browser does not need its full account number. Saving a mandate creates a pending authorization. Open it to activate and review payments. Mandates are not an external-bank collection service, and activation alone does not create a timed recurring-payment series.

## Cards

Under **Cards → Request a card**, choose an active INR savings/current account and a card type. A debit card record becomes active immediately. A credit card request stays **Pending approval**, with no available limit, until an administrator uses **Card applications** to approve it with a limit or reject it with a reason. Applying or approving does not add money to a deposit account.

For an approved credit card, the summary labels available credit separately from the outstanding amount owed. A ₹50,000 credit limit with no spending means ₹50,000 available and ₹0 outstanding. Debit cards do not display credit-limit metrics.

Duplicate usable/pending cards of the same type for the same account are prevented. Requests can be retried using the same request ID. Customer **Block card** and **Unblock card** persist the local status, and customer unblocking cannot remove a bank restriction. Old simulated card-control requests are rejected.

These are internal Nexa card records with local references. Nexa has no card-network issuing, PAN/CVV, ATM/POS purchases, credit-card billing cycle, dispatch or replacement integration. Do not present the records as network-issued cards.

## Current accounts

Customer onboarding currently supports personal savings accounts only. The current-account option stays disabled, and the API rejects personal current-account applications. Direct administrator account creation is also disabled.

A business need not be an incorporated company: a sole proprietor also needs business and proprietor due diligence. A future business flow should capture legal form and business identity, applicable registration/tax details and supporting documents, authorised signatories and their authority, and ownership/beneficial-owner details, followed by recorded bank review. Organisation documents should have private storage, controlled access, retention rules and auditable review rather than a name-only form. This is a product-design outline, not implemented government verification. See the [RBI supervisory handbook's customer due-diligence overview](https://website.rbi.org.in/documents/d/rbi/handbookg27022025d0f3f53f5d3c4310a6bb2f8ac2175d3a).

The transaction-history form now keeps the account and search controls aligned; search guidance occupies its own row without pushing either input down.
