# Bank funding and loan disbursement

The administrator's **Bank funding** page records bank-owned capital received in cash. It increases the app's cash account and bank lending reserve through one recorded posting. It does not initiate an external transfer, verify a physical cash receipt, move customer deposits into the reserve, or fund accounts automatically on startup.

## Upgrade

1. Stop the backend and update the source. Keep your existing Oracle schema and private configuration.
2. Apply the additive `V32__bank_funding_receipts.sql` migration through normal Flyway startup or `scripts/setup-db.ps1 -ExistingSchema -Schema NEXA_BANK_APP`, using your own schema name if different. No accounts or balances are seeded by this migration.
3. Restart the backend and rebuild/restart the frontend. Sign in as an active administrator and open **Bank funding**.

Do not delete tables or change previous migrations. Resolve an existing migration failure before applying V32; [the V31 recovery guide](V31_ORACLE_RECOVERY.md) covers the known previous card migration issue.

## Record funding

Enter the amount actually received, its source/contributor, a unique receipt or evidence reference, and a reason. Amounts are positive INR with at most two decimals, up to ₹1,00,00,000 per receipt. The reference uses letters, digits, `/`, `.`, `_` or `-` (up to 80 characters); it is stored uppercase so changing its letter case cannot create another receipt for the same evidence.

The form calls this field **Source document reference**: enter the identifier from the original cash voucher or supporting receipt. Nexa separately generates its own **Receipt number** after confirmation. Generating a new source reference for each submission would prevent the application from recognising repeated entry of the same supporting receipt.

Review the displayed details, acknowledge that this bank-owned cash was actually received and confirm once. The server generates a receipt number, records the administrator and UTC recording time, creates a successful transaction and balanced journal, and updates both accounts atomically:

| Account | Balance effect | Ledger entry |
| --- | --- | --- |
| `SYSTEM-CASH` | Increases by receipt amount | Debit |
| `NEXA-BANK-FUNDING` | Increases by receipt amount | Credit |

Customer deposits, `NEXA-OPENING-HOLD` and `NEXA-LOAN-CONTROL` are unchanged by this receipt. The screen lists recent funding receipts with their source, reference and audit details. Recorded receipts are immutable; there is no delete or reversal action in this workflow.

For example, if the recorded cash balance is ₹59,000 and reserve is ₹0, a **new** bank-owned cash receipt of ₹7,000 makes those balances ₹66,000 and ₹7,000. It does not reuse the existing ₹59,000. The customer can then accept an approved ₹7,000 loan: the loan workflow consumes ₹7,000 of reserve, credits the customer's linked account and posts its loan accounting entries. Cash is not increased again during disbursement. Merely approving a loan never adds funds.

## Lost responses and duplicate protection

If confirmation loses its response, use the saved request's status check or retry the same request. A reload restores the pending request for the signed-in administrator. Do not enter a replacement receipt. The server uses a unique request UUID and a unique normalized evidence reference: retrying the same payload returns the recorded receipt, while reusing a key with changed details or a reference under another key is rejected. Original recorded balances are retained on the receipt even if later loans change the current reserve.

Only active administrators can read or post funding; the server rechecks the stored administrator role as well as API authentication. Posts lock cash and reserve accounts in the same numeric order used by other postings. A late database failure rolls back the receipt, audit, transaction, journal and balances together. Ordinary transfers and account management cannot change the reserved funding or loan-control accounts.

## API

- `GET /api/v1/admin/bank-funding`: current reserve/cash balances, infrastructure availability and recent receipts.
- `POST /api/v1/admin/bank-funding/receipts`: reviewed cash receipt with `requestId`, `amount`, `source`, `reference`, `reason` and `confirmed: true`.
- `GET /api/v1/admin/bank-funding/receipts/by-request/{requestId}`: recover a known request's recorded receipt without posting again.

This workflow records a manual cash receipt in Nexa's database. A bank-transfer funding option would require its own settlement account and evidence workflow; Cashfree sandbox payouts do not supply bank capital.

## Verification

On 27 September 2026, isolated backend `verify` passed 636 tests with one optional live-model test skipped and built the executable JAR. The funding tests exercise authenticated APIs against the actual V32 schema in H2, including a ₹7,000 loan blocked before funding, successful acceptance after funding, simultaneous funding/disbursement, duplicate requests/references, immutable receipt snapshots and rollback after an injected audit failure. The generic posting/edit guards and V31 recovery with V32 pending also passed.

All 429 frontend tests, typecheck, lint and an isolated production build passed. Browser verification used synthetic data to review and confirm a receipt, inspect the updated reserve/cash balances and view receipt audit details. The final checkbox layout adjustment was rechecked with the 16 funding tests and a rebuilt production bundle. These checks did not apply V32 to the local Oracle schema or record any real funding there; apply the migration during the upgrade above.
