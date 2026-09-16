CREATE TABLE money_transfer_requests (
    id VARCHAR2(36) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL REFERENCES users(id),
    source_account_id NUMBER NOT NULL REFERENCES accounts(id),
    destination_account_id NUMBER NOT NULL REFERENCES accounts(id),
    source_name VARCHAR2(120) NOT NULL,
    source_masked VARCHAR2(40) NOT NULL,
    recipient_name VARCHAR2(160) NOT NULL,
    destination_masked VARCHAR2(40) NOT NULL,
    amount NUMBER(19,2) NOT NULL,
    currency_code VARCHAR2(3) NOT NULL,
    status VARCHAR2(16) NOT NULL,
    transaction_reference VARCHAR2(40) UNIQUE REFERENCES transactions(id),
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT ck_money_transfer_amount CHECK (amount > 0),
    CONSTRAINT ck_money_transfer_accounts CHECK (source_account_id <> destination_account_id),
    CONSTRAINT ck_money_transfer_state CHECK (
      (status = 'READY' AND transaction_reference IS NULL AND completed_at IS NULL)
      OR (status = 'COMPLETED' AND transaction_reference IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE INDEX ix_money_transfer_owner ON money_transfer_requests(user_id, created_at);
