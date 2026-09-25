# Cards and payments

Cards use violet credit-card artwork and cyan debit-card artwork in Core Banking and chat. Chat card discovery returns all owned cards. Customers without cards can review and create a digital debit card for an active savings/current account. Existing card changes and credit-card issuance go to card support.

Bank support defaults to **88000 00067** and **nexa@help.bank.in**, configurable with `NEXA_CARD_SUPPORT_PHONE` and `NEXA_CARD_SUPPORT_EMAIL`. Core Banking and chat use the same support details.

Apply Flyway migration V24 when restarting the API. New real payment reviews are stored separately from legacy simulations; old simulated reviews cannot execute a real payment.

`PAY_CARD` now posts a credit-card balance repayment. `PAY_BILL` accepts a deposit account or active credit-card funding account. Both use the existing prepare/confirm endpoints, immutable review records, ownership checks, expiry, transactional posting and replay-safe confirmation. Credit-funded bills increase card outstanding and reduce available credit; repayments reduce outstanding. Partial bill payments retain the remaining amount due. Journals and payment histories are updated in the same transaction.

These are internal Nexa ledger postings. This change does not integrate an external card network or provision network credentials for the digital debit card. Freeze, unfreeze and replacement remain explicitly simulated controls.

New endpoints:
- `GET /api/v1/cards/support`
- `GET /api/v1/cards/bill-funding-accounts`
- `POST /api/v1/cards/digital-debit` with `accountId` after the UI's creation confirmation.

Migration V25 assigns persistent 16-digit internal Nexa numbers to existing cards, preserving their last four digits. New digital debit cards receive a full number at creation. Full numbers are available only through the authenticated reveal endpoint; list and chat snapshots remain masked. These are local card identifiers, not payment-network credentials.
