# Internal Nexa bill payments

Bills can now be paid by transferring money from a customer's active INR savings or current account to an explicitly selected, saved Nexa payee. The recipient must be linked to an existing Nexa customer account. A biller name and customer number alone cannot identify a payment destination. No external bill-payment network or automatic debit is configured.

[External bank transfers](EXTERNAL_BANK_TRANSFERS.md) have a separate Cashfree sandbox flow. Those test transfers do not settle utility bills or change a bill's status.

## Customer flow

1. Save the recipient under **Payees**, using their Nexa account number. Use the real bill recipient's account; creating a bill does not create a recipient account.
2. Open **Bills** and add a bill. Optionally select **Saved Nexa bill recipient** to link an existing payee immediately; this also fills the biller name, which can be corrected to match the invoice. Choose the category, amount printed on the bill and due date. The form does not ask customers to set a minimum payment. **Biller customer / consumer number** identifies your service account with the biller; it is different from the recipient's bank account number. Nexa does not fetch or verify the invoice with a utility provider. A past due date is allowed for an existing unpaid bill. Before the due date its status is **Upcoming**; on that date it is **Due**; after that date an unpaid bill is **Overdue**. Status uses the configured business timezone (Asia/Kolkata by default). Overdue status does not block payment.
3. Select **Pay now**, a funding account and, if not already linked, a saved Nexa payee. Review the amount, actual recipient account-holder name and masked account number. Linking at creation or during the first review uses an owned, active, verified internal payee and an active INR deposit account. The recipient can be corrected before any completed payment; a review prepared for old details cannot subsequently pay a different recipient.
4. Select **Confirm payment** to transfer the displayed amount. This debits the source, credits the recipient and creates a balanced journal, ledger entries, transaction reference and durable payment receipt in one transaction. Merely creating a bill, opening its details or preparing a review does not move money.
5. A successful partial payment reduces the outstanding amount. A fully settled bill becomes **Paid**. The original billed amount is retained. Follow **View transaction** for the posted entry. Bill details show payment attempts and completed payment history.

Customers cannot manually set bill status. An unsuccessful confirmation records a **Failed** attempt while the bill remains unpaid, due or overdue as appropriate. Cancelled and expired reviews do not move money. A failed attempt does not erase earlier successful partial payments. Previously stored paid bills remain nonpayable and are identified as previously recorded paid when they have no verified payment posting.

The default payment amount is the current outstanding balance. New bills created through the form have no customer-set minimum; partial payments must still be positive and not exceed the remaining balance. Older records and compatible API clients can retain a saved minimum. A partial payment on those bills must meet that minimum, unless the full remaining balance is smaller. Reviews expire after five minutes. Confirmation rechecks ownership, account state, currency, available funds, recipient binding and outstanding amount. Changed details require a new review.

New bill amounts are bounded by the supported payment limit of INR 9,999,999,999,999.99. The compatible API applies the same bound to a minimum when one is supplied. Existing larger records remain readable. The saved-recipient dropdown uses the customer's existing Nexa payees; it does not insert sample bank accounts, change balances or designate arbitrary customers as official utility billers. Linking a recipient at creation requires no additional database migration and remains optional for older clients.

If a response is lost, **Check payment status** and **Retry same payment** recover the same saved request. Duplicate requests and concurrent confirmations cannot post that review twice. The browser retains the current request in session storage so a refresh can recover its outcome. Payment attempts remain on the server.

## Chat and compatibility

Chat can pay a bill after its recipient has been linked through the bill screen. Its review displays the actual recipient and amount; only the explicit confirmation button authorizes payment. An unlinked bill directs the customer to its details to choose a payee.

New and cached bill requests through `/api/v1/demo/actions` are rejected with directions to **Pay now**. A previously simulated bill review is never converted into a real debit. Other existing card or mandate simulation features are outside this bill-payment change.

## Teammate upgrade

Restart the backend on this repository's compatible schema so Flyway applies `V27__bill_payment_attempts.sql`. Keep existing database configuration and keys. Rebuild/restart the frontend and refresh it. No manual SQL, schema reset, shared password or seeded demo recipient is needed. The separate reference project's migration history is incompatible; follow [the upgrade guide](ACCOUNT_OPENING_UPGRADE.md).

The `bill_payment_attempts` table stores the reviewed source, verified recipient snapshot, amount, outstanding snapshot, expiry and terminal result. Request keys and posted transaction references are unique. Successful transactions carry `BILL_PAYMENT`, the bill ID, biller/category metadata and the receipt's transaction reference. Read models derive paid totals only from successful bill-payment transactions backed by posted journals. Existing migration files are preserved.

## Verification

Automated checks use isolated H2 databases and frontend component tests. They exercise actual authenticated endpoints, account balances, balanced ledger postings, partial payments, simultaneous confirmations, invalid ownership, changed recipients, expiry, cancellation, insufficient funds, rollback after a late failure, response recovery and blocked simulation endpoints. H2 does not certify an Oracle deployment.

For an intentional local walkthrough after upgrading, use existing test accounts and a verified saved payee: create an unpaid bill, review its recipient, confirm once, and check the debit, credit, receipt and remaining amount. Repeat confirmation of the same request and verify no second posting. This action moves funds in the selected local schema. No live customer transfer or schema migration is performed by the automated suite.

Verification on 26 September 2026: complete Maven `verify` ran 489 tests with zero failures/errors and one optional model test skipped; the executable JAR built successfully. All 238 API source/resource/test files matched the isolated verification copy. Frontend typecheck, lint and 343 tests passed. The isolated Oracle JET production release build passed with all 111 source/script/configuration inputs unchanged; only existing optional Sass and Node deprecation warnings remain. No live Oracle upgrade, authenticated browser walkthrough, commit or push was performed.

Frontend follow-up on 27 September 2026: removed the customer-set minimum from the new-bill form while retaining existing saved minimums, added readable utility category labels without changing filter keys, and labelled the transaction receipt's owned account as **Your Nexa account**. All 410 frontend tests, typecheck, lint and an isolated production build passed; all 169 source/script/test/configuration inputs matched the verified copy. Browser checks against synthetic fixtures confirmed the simplified bill form, **Water bill** receipt category, owned-account label and revised credit-card summary. No live database changes or payments were performed for these display changes.
