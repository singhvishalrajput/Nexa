# Payments walkthrough

## One payment entry point

Open **Payments** and choose **Nexa account** or **Other bank** in the destination dropdown. Existing transfer links remain compatible. Bills, saved payees, scheduled payments and other payment products are available through payment shortcuts.

## Sending to a Nexa account

Save the recipient in **Payees** with a nickname and their full Nexa account number. Select a funded active account, the recipient and the amount in Payments. Review the actual account-holder details before confirming. An internal confirmed payment debits and credits the Nexa ledger; preparing a review does not move funds.

## Sending to another bank

1. In **Payees → Add payee**, choose **Other bank**.
2. Enter a nickname and the recipient's account-holder name. Select the bank from the dropdown, or choose **Another bank** and enter its name. Add the full account number, the account number again and the 11-character IFSC. The implemented provider contract accepts account numbers containing 9–18 letters/digits and account-holder names containing letters/spaces. Get the details from the recipient; do not use an invented real account.
3. Open **Payments → Other bank**, select the Nexa account context, saved payee and amount, then review and confirm.
4. Refresh the saved request's status if the outcome is pending. Do not create a replacement while the first request is unresolved.

This release connects only to **Cashfree Payouts sandbox**. No real bank settlement occurs and Nexa balances/bill statuses do not change. Removing "test" from navigation does not enable live transfers. A live release needs provider approval and funding plus ledger reservation, settlement, refund and reconciliation work.

Saving a payee validates the entered formats and stores the details encrypted. It does not verify account existence, the actual account-holder name or whether the selected bank matches the IFSC. The bank dropdown is a convenience list, not a verified provider-support list. Bank names can be checked against the Department of Financial Services' [public](https://www.financialservices.gov.in/public-sector-banks) and [private](https://www.financialservices.gov.in/private-sector-banks) bank directories.

A pending result means Nexa has not established a final provider outcome. It can reflect ongoing processing, a network/provider error or an unverified response; the generic message does not establish the cause. **Refresh provider status** checks Cashfree for the same request. **Refresh history** only reloads Nexa's saved records. Avoid submitting a replacement while a request is unresolved. Even a completed sandbox request leaves Nexa balances unchanged.

For sandbox access, obtain Payouts sandbox credentials under **Cashfree Dashboard → Payouts → Developers → API Keys**, as described in [Cashfree's setup documentation](https://www.cashfree.com/docs/api-reference/payouts/getting-started-with-payouts-apis). Put these settings in the existing ignored `apps/api/.env`, preserving its other values:

```dotenv
NEXA_PAYOUTS_MODE=SANDBOX
NEXA_PAYOUTS_CLIENT_ID=your_payouts_sandbox_client_id
NEXA_PAYOUTS_CLIENT_SECRET=your_payouts_sandbox_client_secret
```

Restart the backend. These are Payouts keys, not Payment Gateway keys; keep them local and do not commit them. Use Cashfree-supported sandbox beneficiary details for the provider walkthrough. See [external-bank setup](EXTERNAL_BANK_TRANSFERS.md) for encryption and deployment details. Setting `LIVE` does not make this implementation production-ready.

## Paying a bill

1. Save the bill recipient as a **Nexa payee** using their existing account number.
2. Open **Bills → Add bill** and select that saved recipient. Correct the biller name if its saved nickname differs from the invoice.
3. Enter the **consumer/customer number printed on the bill**, category, invoice amount, minimum amount if applicable, and due date. The consumer number is not the bank account number. Past due dates are allowed for unpaid overdue bills.
4. Open the saved bill, select **Pay now**, choose the funding account and review the linked recipient and amount. Confirm to post the internal payment.
5. Successful payments reduce the outstanding amount; full payment changes the status to Paid. An unpaid bill becomes overdue after its due date. A failed payment is recorded as a failed attempt and does not erase the amount due. Automatic debit is off.

The current dropdown reuses your saved Nexa payees; no sample accounts are inserted. It is not a utility-company directory. This project tracks manually entered invoices and transfers to the linked Nexa recipient; it does not fetch or reconcile bills with external water/electricity companies. Cashfree sandbox transfers cannot settle those bills. A real utility-payment experience requires a separate supported bill-payment provider integration.

## Account application feedback

Saving an incomplete application now focuses an error summary next to Save. Select an issue to jump to its field; entered values remain intact. The same feedback applies when updating a draft. Required phone number, date of birth, deposit, identity and consent rules remain in force.

## Verification — 26 September 2026

The isolated backend Maven build passed: 561 tests, zero failures/errors, one optional model check skipped. All 255 compared API inputs matched the working source. Frontend checks passed: 371 tests, TypeScript checking, lint and production release build. All 116 release-build inputs matched the working source; only existing optional Sass and Node deprecation warnings were reported.

Browser checks against synthetic local data confirmed that incomplete account applications focus a visible error summary and its field links, the single Payments page switches between transfer types, bill recipient selection fills the name, and a saved bill opens Pay now with the linked recipient and invoice amount. The desktop pages had no horizontal overflow. No live Oracle changes or Cashfree requests were made during verification.
