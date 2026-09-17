-- Expand first. V17 copies data before the separately gated retirement step.
ALTER TABLE customers ADD (status VARCHAR2(24) DEFAULT 'ACTIVE' NOT NULL, role VARCHAR2(24) DEFAULT 'CUSTOMER' NOT NULL);
UPDATE customers c SET (status,role)=(SELECT status,role FROM users u WHERE u.id=c.user_id) WHERE user_id IS NOT NULL;
INSERT INTO customers(user_id,full_name,email,phone_number,status,role,created_at,updated_at)
SELECT id,full_name,email,phone_number,status,role,SYS_EXTRACT_UTC(created_at),SYS_EXTRACT_UTC(updated_at) FROM users u WHERE NOT EXISTS (SELECT 1 FROM customers c WHERE c.user_id=u.id);
CREATE UNIQUE INDEX uk_customer_email_ci ON customers(LOWER(email));
CREATE TABLE customer_credentials (
 id VARCHAR2(26) PRIMARY KEY,
 user_id VARCHAR2(26) NOT NULL REFERENCES customers(user_id),
 credential_type VARCHAR2(16) NOT NULL CHECK(credential_type IN ('PASSWORD','REFRESH')),
 password_hash VARCHAR2(100), token_hash CHAR(64) UNIQUE,
 expires_at TIMESTAMP WITH TIME ZONE, revoked_at TIMESTAMP WITH TIME ZONE,
 created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
 CONSTRAINT ck_credential_shape CHECK ((credential_type='PASSWORD' AND token_hash IS NULL) OR (credential_type='REFRESH' AND password_hash IS NULL AND token_hash IS NOT NULL AND expires_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_password_customer ON customer_credentials(CASE WHEN credential_type='PASSWORD' THEN user_id END);
CREATE INDEX idx_credentials_customer ON customer_credentials(user_id);
INSERT INTO customer_credentials(id,user_id,credential_type,password_hash,created_at) SELECT id,id,'PASSWORD',password_hash,created_at FROM users;
INSERT INTO customer_credentials(id,user_id,credential_type,token_hash,expires_at,revoked_at,created_at) SELECT id,user_id,'REFRESH',token_hash,expires_at,revoked_at,created_at FROM auth_refresh_tokens;
-- Replace only the identity FK on chat; chat data and structure remain unchanged.
BEGIN
 FOR f IN (SELECT constraint_name FROM all_constraints WHERE owner=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND table_name='CONVERSATIONS' AND constraint_type='R' AND r_constraint_name IN (SELECT constraint_name FROM all_constraints WHERE owner=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND table_name='USERS')) LOOP
  EXECUTE IMMEDIATE 'ALTER TABLE conversations DROP CONSTRAINT '||f.constraint_name;
 END LOOP;
 FOR f IN (SELECT constraint_name FROM all_constraints WHERE owner=SYS_CONTEXT('USERENV','CURRENT_SCHEMA') AND table_name='CUSTOMERS' AND constraint_type='R') LOOP
  EXECUTE IMMEDIATE 'ALTER TABLE customers DROP CONSTRAINT '||f.constraint_name;
 END LOOP;
END;
/
ALTER TABLE conversations ADD CONSTRAINT fk_chat_customer FOREIGN KEY(user_id) REFERENCES customers(user_id);
ALTER TABLE accounts DROP CONSTRAINT ck_accounts_type;
ALTER TABLE accounts ADD CONSTRAINT ck_accounts_type CHECK(account_type IN ('SAVINGS','CURRENT','CASH','CLEARING','LOAN','CARD'));
ALTER TABLE accounts ADD (
 product_id VARCHAR2(40) UNIQUE, funding_account_id NUMBER REFERENCES accounts(id),
 principal_amount NUMBER(19,2), interest_rate NUMBER(9,6), product_status VARCHAR2(32), product_type VARCHAR2(32),
 number_masked VARCHAR2(40), due_at VARCHAR2(40), periodic_payment NUMBER(19,2), credit_limit NUMBER(19,2), minimum_payment NUMBER(19,2)
);
ALTER TABLE accounts ADD CONSTRAINT ck_loan_values CHECK(account_type <> 'LOAN' OR (account_category='CUSTOMER' AND principal_amount>0 AND interest_rate>=0 AND balance>=0));
ALTER TABLE transactions DROP CONSTRAINT ck_tx_type;
ALTER TABLE transactions DROP CONSTRAINT ck_tx_amount;
ALTER TABLE transactions DROP CONSTRAINT ck_tx_status;
ALTER TABLE transactions DROP CONSTRAINT ck_tx_accounts;
ALTER TABLE transactions MODIFY (transaction_type NULL, amount NULL, status VARCHAR2(32), created_at DEFAULT CURRENT_TIMESTAMP);
ALTER TABLE transactions ADD (
 record_kind VARCHAR2(32) DEFAULT 'PAYMENT' NOT NULL,
 user_id VARCHAR2(26) REFERENCES customers(user_id), parent_id VARCHAR2(40) REFERENCES transactions(id),
 operation VARCHAR2(40), target_id VARCHAR2(80), transaction_reference VARCHAR2(80),
 minimum_amount NUMBER(19,2), currency_code VARCHAR2(3), display_name VARCHAR2(200),
 source_name VARCHAR2(120), source_masked VARCHAR2(40), recipient_name VARCHAR2(160), destination_masked VARCHAR2(40),
 bank_name VARCHAR2(160), destination_hash VARCHAR2(64), routing_code VARCHAR2(32), beneficiary_type VARCHAR2(24),
 frequency VARCHAR2(32), effective_date VARCHAR2(40), end_date VARCHAR2(40), due_at VARCHAR2(40),
 expires_at TIMESTAMP, updated_at TIMESTAMP
);
ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IN ('PAYMENT','MANDATE','MANDATE_EVENT','BILL','SCHEDULED_PAYMENT','BENEFICIARY','TRANSFER_REVIEW','SIMULATION','LEGACY_TRANSFER','PRODUCT_HISTORY'));
ALTER TABLE transactions ADD CONSTRAINT ck_payment_shape CHECK(record_kind <> 'PAYMENT' OR (amount IS NOT NULL AND amount>0 AND transaction_type IS NOT NULL AND transaction_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','LOAN_DISBURSEMENT','LOAN_REPAYMENT') AND status IN ('SUCCESS','FAILED','REVERSED') AND (source_account_id IS NOT NULL OR destination_account_id IS NOT NULL)));
ALTER TABLE transactions ADD CONSTRAINT ck_instruction_owner CHECK(record_kind='PAYMENT' OR user_id IS NOT NULL);
CREATE INDEX idx_tx_owner_kind ON transactions(user_id,record_kind,created_at);
CREATE INDEX idx_tx_parent ON transactions(parent_id);
CREATE UNIQUE INDEX uk_review_payment ON transactions(CASE WHEN record_kind='TRANSFER_REVIEW' THEN transaction_reference END);
