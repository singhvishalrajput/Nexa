INSERT INTO users (id, email, status)
VALUES ('usr_01JDEMO000000000000001', 'vishal@example.com', 'ACTIVE');

INSERT INTO customer_profiles (id, user_id, full_name, phone_number)
VALUES ('cst_01JDEMO000000000000001', 'usr_01JDEMO000000000000001', 'Vishal Singh', '+91 90000 00482');

INSERT INTO bank_accounts (
    id, customer_id, account_number_masked, display_name, account_type, currency_code,
    available_balance, ledger_balance, status
) VALUES (
    'acc_01JDEMO000000000000001', 'cst_01JDEMO000000000000001', '•••• 4291', 'Primary account', 'SAVINGS', 'INR',
    96280.0000, 96280.0000, 'ACTIVE'
);

INSERT INTO ledger_accounts (id, bank_account_id, account_code, account_name, account_type, currency_code)
VALUES ('lda_01JDEMO000000000000001', 'acc_01JDEMO000000000000001', 'ASSET:CUSTOMER:4291', 'Primary account balance', 'ASSET', 'INR');

INSERT INTO ledger_accounts (id, bank_account_id, account_code, account_name, account_type, currency_code)
VALUES ('lda_01JDEMO000000000000002', NULL, 'EQUITY:OPENING', 'Opening balance equity', 'EQUITY', 'INR');

INSERT INTO journal_entries (id, entry_reference, entry_type, description, posted_at)
VALUES ('jen_01JDEMO000000000000001', 'OPENING-4291', 'OPENING_BALANCE', 'Opening balance for local demo account', SYSTIMESTAMP);

INSERT INTO ledger_postings (id, journal_entry_id, ledger_account_id, direction, amount, currency_code)
VALUES ('ldp_01JDEMO000000000000001', 'jen_01JDEMO000000000000001', 'lda_01JDEMO000000000000001', 'D', 96280.0000, 'INR');

INSERT INTO ledger_postings (id, journal_entry_id, ledger_account_id, direction, amount, currency_code)
VALUES ('ldp_01JDEMO000000000000002', 'jen_01JDEMO000000000000001', 'lda_01JDEMO000000000000002', 'C', 96280.0000, 'INR');

INSERT INTO transactions (
    id, bank_account_id, transaction_reference, transaction_type, merchant_name, category,
    amount, currency_code, status, occurred_at
) VALUES (
    'txn_01JDEMO000000000000001', 'acc_01JDEMO000000000000001', 'DEMO-SALARY-20260901', 'SALARY', 'Nexa Demo Employer', 'Income',
    82400.0000, 'INR', 'POSTED', SYSTIMESTAMP - INTERVAL '3' DAY
);

INSERT INTO transactions (
    id, bank_account_id, transaction_reference, transaction_type, merchant_name, category,
    amount, currency_code, status, occurred_at
) VALUES (
    'txn_01JDEMO000000000000002', 'acc_01JDEMO000000000000001', 'DEMO-BILLS-20260902', 'CARD_PURCHASE', 'BESCOM', 'Bills',
    -1840.0000, 'INR', 'POSTED', SYSTIMESTAMP - INTERVAL '2' DAY
);
