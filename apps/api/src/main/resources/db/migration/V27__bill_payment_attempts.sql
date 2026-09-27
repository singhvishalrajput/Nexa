-- Durable customer authorization for real, balanced internal bill transfers.
-- This migration introduces no users, bills, balances or automatic payments.
CREATE TABLE bill_payment_attempts (
    id VARCHAR2(40) PRIMARY KEY,
    request_key VARCHAR2(36) NOT NULL,
    request_fingerprint VARCHAR2(64) NOT NULL,
    user_id VARCHAR2(26) NOT NULL,
    bill_id VARCHAR2(40) NOT NULL,
    biller_name VARCHAR2(200) NOT NULL,
    source_account_id NUMBER NOT NULL,
    source_name VARCHAR2(120) NOT NULL,
    source_masked VARCHAR2(40) NOT NULL,
    payee_id VARCHAR2(40) NOT NULL,
    destination_account_id NUMBER NOT NULL,
    recipient_name VARCHAR2(160) NOT NULL,
    destination_masked VARCHAR2(40) NOT NULL,
    amount NUMBER(19,2) NOT NULL,
    original_remaining NUMBER(19,2) NOT NULL,
    currency_code VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    status VARCHAR2(16) DEFAULT 'READY' NOT NULL,
    transaction_reference VARCHAR2(40),
    failure_reason VARCHAR2(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT uk_bill_attempt_key UNIQUE (user_id,request_key),
    CONSTRAINT uk_bill_attempt_tx UNIQUE (transaction_reference),
    CONSTRAINT fk_bill_attempt_user FOREIGN KEY (user_id) REFERENCES customers(user_id),
    CONSTRAINT fk_bill_attempt_bill FOREIGN KEY (bill_id) REFERENCES transactions(id),
    CONSTRAINT fk_bill_attempt_payee FOREIGN KEY (payee_id) REFERENCES transactions(id),
    CONSTRAINT fk_bill_attempt_source FOREIGN KEY (source_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_bill_attempt_dest FOREIGN KEY (destination_account_id) REFERENCES accounts(id),
    CONSTRAINT fk_bill_attempt_tx FOREIGN KEY (transaction_reference) REFERENCES transactions(id),
    CONSTRAINT ck_bill_attempt_amount CHECK (amount>0 AND amount<=original_remaining),
    CONSTRAINT ck_bill_attempt_route CHECK (source_account_id<>destination_account_id AND currency_code='INR'),
    CONSTRAINT ck_bill_attempt_status CHECK (status IN ('READY','COMPLETED','FAILED','CANCELLED','EXPIRED')),
    CONSTRAINT ck_bill_attempt_shape CHECK (
        (status='COMPLETED' AND transaction_reference IS NOT NULL AND completed_at IS NOT NULL AND failure_reason IS NULL) OR
        (status='READY' AND transaction_reference IS NULL AND completed_at IS NULL AND failure_reason IS NULL) OR
        (status IN ('FAILED','CANCELLED','EXPIRED') AND transaction_reference IS NULL AND completed_at IS NULL AND failure_reason IS NOT NULL)
    )
);

CREATE INDEX idx_bill_attempt_bill ON bill_payment_attempts(bill_id,created_at);
CREATE INDEX idx_bill_attempt_user ON bill_payment_attempts(user_id,created_at);
