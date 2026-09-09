ALTER TABLE users ADD (
    full_name VARCHAR2(160),
    phone_number VARCHAR2(32)
);

UPDATE users u
SET (full_name, phone_number) = (
    SELECT p.full_name, p.phone_number
    FROM customer_profiles p
    WHERE p.user_id = u.id
)
WHERE EXISTS (
    SELECT 1 FROM customer_profiles p WHERE p.user_id = u.id
);

UPDATE users
SET full_name = email
WHERE full_name IS NULL;

ALTER TABLE users MODIFY (full_name NOT NULL);

ALTER TABLE bank_accounts ADD (
    user_id VARCHAR2(26),
    account_number VARCHAR2(20)
);

UPDATE bank_accounts a
SET user_id = (
    SELECT p.user_id
    FROM customer_profiles p
    WHERE p.id = a.customer_id
);

UPDATE bank_accounts
SET account_number = '9' || LPAD(ROWNUM, 7, '0') || SUBSTR(account_number_masked, -4);

ALTER TABLE bank_accounts MODIFY (
    user_id NOT NULL,
    account_number NOT NULL
);

ALTER TABLE bank_accounts DROP CONSTRAINT fk_bank_accounts_customer;
DROP INDEX ix_bank_accounts_customer;
ALTER TABLE bank_accounts DROP COLUMN customer_id;

ALTER TABLE bank_accounts ADD CONSTRAINT fk_bank_accounts_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE bank_accounts ADD CONSTRAINT uk_bank_accounts_user UNIQUE (user_id);
ALTER TABLE bank_accounts ADD CONSTRAINT uk_bank_accounts_number UNIQUE (account_number);

DROP TABLE customer_profiles;

MERGE INTO ledger_accounts target
USING (
    SELECT
        'lda_demo_opening_fund' AS id,
        'EQUITY:DEMO_OPENING_FUND' AS account_code,
        'Nexa demo opening fund' AS account_name
    FROM dual
) source
ON (target.account_code = source.account_code)
WHEN NOT MATCHED THEN
    INSERT (id, bank_account_id, account_code, account_name, account_type, currency_code)
    VALUES (source.id, NULL, source.account_code, source.account_name, 'EQUITY', 'INR');
