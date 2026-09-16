# Nexa learning showcase

Nexa demonstrates banking architecture and user journeys. Use fictional identities and a dedicated local database. The interface uses concise request and status labels. Simulation boundaries are documented here rather than displayed as application banners.

## Demonstration walkthrough

Start the API and web client using the root README. Flyway applies `V15__showcase_actions.sql` on API startup. The existing local profile supplies fictional products and the demo login documented in the API README. Use an account with sufficient recorded balance for payment validation; an administrator can post a local deposit through Banking operations.

1. **Payments:** choose a payee, bill, credit card or direct debit, select the linked source account and enter a valid amount when needed. Check payment details, choose **Continue**, then **Confirm request**. A receipt is saved and displayed with a `REQ-…` reference. Direct-debit cancellation requires its linked account.
2. **Chat:** request a payee transfer or bill payment, select the target and source, and use **Confirm request**. Typing “yes” does not execute it. Receipts link to demo history. Own-account transfers retain their existing ledger-backed behavior.
3. **Cards:** open a card and choose freeze, unfreeze or replacement. Review and confirm to see the simulated outcome and saved receipt. No PIN, full card number or identity document is requested.
4. **Voice:** choose English or Hindi and select **Use suggested message**. A suggested message opens for editing, sending or cancellation without accessing the microphone. Normal speech input remains available.
5. **Request history:** Payments and card details show the latest 30 demo reviews and receipts. Refresh to retrieve new results, reopen a pending request, or inspect a completed receipt after reloading or signing in again.

## Behavior and boundaries

- Demo actions only write `showcase_actions`. They never post a ledger transaction, contact a payment provider, change a product projection, or send notifications. Card and mandate state changes are described in the receipt; the recorded product remains unchanged so demonstrations can be repeated.
- Existing login, account opening, profile editing, conversations, internal transfers and administrator operations still persist to the application's configured database. This refactor does not turn that database into an in-memory sandbox. Use a dedicated fictional-data database.
- Demo API routes require customer/admin authentication. Product and source ownership, active status, supported currency, amount precision, bill/card limits and available balance remain validated. Financial validation runs again at confirmation.
- Reviews expire after ten minutes. Confirmation is bound to the saved review, locks its row and returns the same receipt on retries. Cancelled and expired reviews cannot be confirmed. Demo history is scoped to the signed-in user.
- New simulation endpoints are `/api/v1/demo/actions/prepare`, `/{id}/confirm`, `/{id}/cancel`, `/{id}` and the collection GET for history. They cannot execute a real-provider action, even outside the local profile.
- Oracle remains required to run the full application. H2 is used for backend regression tests. No migration was applied to your running Oracle database during this refactor.

Earlier architecture documents describe provider actions as review-only or unavailable. The simulated provider journeys above supersede those limitations; the existing real-provider integration boundaries remain.

## Verification

Run `mvn verify` from `apps/api`; run `npm run lint`, `npm run typecheck`, `npm test` and `npm run build` from `apps/web`. Backend regression coverage includes receipts, repeat confirmations, unchanged balances/products, ownership, cancellation, expiry and changed balances. Frontend coverage includes chat confirmation labels and microphone-free voice review.

Verified for this refactor: 94 backend tests passed, one optional live-model test skipped; 53 frontend tests passed; lint, type checking and build passed. A browser walkthrough using the local fixture verified voice review, card confirmation and receipt, payee payment confirmation and receipt, and saved demo history. These browser fixtures supplement the database-backed backend tests.

On this Windows machine the local-model HTTP tests needed a shorter Java temporary path. The successful Maven run used `-DargLine="-Djava.io.tmpdir=D:/Nexa/.tools/tmp -Djdk.net.unixdomain.tmpdir=D:/Nexa/.tools/tmp"` with the existing bundled JDK 17 and offline Maven cache. This is a test-runtime setting, not an application security change.
