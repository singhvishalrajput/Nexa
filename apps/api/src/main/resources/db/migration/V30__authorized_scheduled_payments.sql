-- Only newly reviewed and explicitly authorized rows in this table may execute.
-- Legacy SCHEDULED_PAYMENT transaction records are retained and never enrolled.
CREATE TABLE authorized_scheduled_payments (
 id VARCHAR2(40) PRIMARY KEY,
 request_key VARCHAR2(36) NOT NULL,
 request_fingerprint VARCHAR2(64) NOT NULL,
 user_id VARCHAR2(26) NOT NULL REFERENCES customers(user_id),
 source_account_id NUMBER NOT NULL REFERENCES accounts(id),
 source_name VARCHAR2(120) NOT NULL,
 source_masked VARCHAR2(40) NOT NULL,
 payee_id VARCHAR2(40) NOT NULL REFERENCES transactions(id),
 destination_account_id NUMBER NOT NULL REFERENCES accounts(id),
 recipient_name VARCHAR2(160) NOT NULL,
 destination_masked VARCHAR2(40) NOT NULL,
 amount NUMBER NOT NULL,
 currency_code VARCHAR2(3) DEFAULT 'INR' NOT NULL,
 due_date VARCHAR2(10) NOT NULL,
 run_at TIMESTAMP NOT NULL,
 status VARCHAR2(16) DEFAULT 'READY' NOT NULL,
 authorization_version VARCHAR2(40),
 authorized_at TIMESTAMP,
 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 completed_at TIMESTAMP,
 transaction_reference VARCHAR2(40) REFERENCES transactions(id),
 failure_reason VARCHAR2(500),
 CONSTRAINT uk_schedule_request UNIQUE(user_id,request_key),
 CONSTRAINT uk_schedule_posting UNIQUE(transaction_reference),
 CONSTRAINT ck_schedule_amount CHECK(amount BETWEEN 0.01 AND 9999999999999.99 AND amount=ROUND(amount,2)),
 CONSTRAINT ck_schedule_route CHECK(source_account_id<>destination_account_id AND currency_code='INR'),
 CONSTRAINT ck_schedule_status CHECK(status IN ('READY','SCHEDULED','COMPLETED','FAILED','CANCELLED','EXPIRED')),
 CONSTRAINT ck_schedule_authorization CHECK (
   (authorized_at IS NULL AND authorization_version IS NULL AND status NOT IN ('SCHEDULED','COMPLETED')) OR
   (authorized_at IS NOT NULL AND authorization_version='one-time-internal-v1' AND status NOT IN ('READY','EXPIRED'))),
 CONSTRAINT ck_schedule_result CHECK (
   (status='COMPLETED' AND transaction_reference IS NOT NULL AND completed_at IS NOT NULL AND failure_reason IS NULL) OR
   (status IN ('READY','SCHEDULED') AND transaction_reference IS NULL AND completed_at IS NULL AND failure_reason IS NULL) OR
   (status IN ('FAILED','CANCELLED','EXPIRED') AND transaction_reference IS NULL AND completed_at IS NULL AND failure_reason IS NOT NULL)),
 CONSTRAINT ck_schedule_dates CHECK (run_at>created_at AND expires_at>created_at
   AND (authorized_at IS NULL OR authorized_at>=created_at))
);
CREATE INDEX ix_schedule_due ON authorized_scheduled_payments(status,run_at);
CREATE INDEX ix_schedule_owner ON authorized_scheduled_payments(user_id,created_at);
