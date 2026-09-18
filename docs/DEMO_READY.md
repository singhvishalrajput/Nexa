# Demo setup: 17 September 2026

Only three customer identities remain, plus the existing administrator. Loan/card products and system accounts are separate rows, not additional customer logins.

| Customer | Login | Main account balance | Active loan outstanding |
|---|---|---:|---:|
| Vishal Singh | vishal@example.com | INR 250,000.00 | INR 185,000.00 |
| Asha Rao | asha@example.com | INR 304,598.65 | INR 55,323.65 |
| Rahul Sharma | rahul@example.com | INR 286,399.10 | INR 36,882.43 |

Passwords are in the ignored local `.tools/demo-access.json`. Vishal's existing password was preserved. Asha and Rahul have 12-month loans, one paid installment each, and their remaining schedules. All three customers have verified links to both other demo customers for payments and an active household mandate. Vishal retains his original demonstration products and chat history.

The bank reserve `NEXA-BANK-FUNDING` has INR 9,907,793.92 after disbursements and repayments. The loan control balance is INR 277,206.08, matching all outstanding principal. The interest account holds INR 1,208.33. System reserve, control and interest balances reconcile exactly to their ledger entries; every retained journal balances.

Open `http://localhost:8000/#/admin/loans`. Vishal's INR 25,000 **Home office upgrade** application is awaiting review. An administrator must enter a reason and confirm approval/rejection. After approval the customer accepts the loan to receive funds. Pending or rejected loans cannot be disbursed through either API path. The admin queue refreshes automatically and each decision appears in the loan account audit.

79 validation identities, 91 associated account rows, and 163 related transaction/instruction records were removed. The complete original backup is under `.tools/demo-backups/2026-09-17T14-03-11.788842200/compatible/`; its restore script was successfully rehearsed in a savepoint and rolled back. The original chat content is present: 1 conversation, 15 turns, 3 workflows and 69 action events. All six banking tables and the separate chat schema remain in place.

Verification: the full backend suite passed (138 tests, one optional model test skipped); the subsequent saved-payee regression suite passed all 13 scenarios. All 74 frontend tests, type checking, lint and build passed. Live Oracle-backed verification covered all three logins, funding, admin approval, disbursement, first EMI repayment, schedules, customer/admin isolation, refusal to disburse a pending application, balanced journals, and reserve/control reconciliation. A repeated seed run retained the same identities, balances, loans and pending application.

The administrator password was updated on 2026-09-17 and verified through the normal login endpoint and the administrator loan queue. The current credential is stored in the ignored local `.tools/admin-access.json`.

See [setup, recovery and accounting](../tools/demo/README.md), [V21 approval/funding migration](../apps/api/src/main/resources/db/migration/V21__admin_loan_approval_and_bank_funding.sql), [V22 payee repair](../apps/api/src/main/resources/db/migration/V22__linked_payee_destination_hash.sql) and the [current six-table schema](SIX_TABLE_SCHEMA.sql).
