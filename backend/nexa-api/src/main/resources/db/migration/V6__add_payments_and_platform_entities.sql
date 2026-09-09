-- Service ownership: identity owns USERS; accounts owns BANK_ACCOUNTS;
-- payments owns BENEFICIARIES, TRANSFERS and their workflow records.
-- Shared operational records are append-only and may later be projected by
-- separately deployed audit and notification services.

ALTER TABLE bank_accounts DROP CONSTRAINT uk_bank_accounts_user;

CREATE TABLE beneficiaries (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    display_name VARCHAR2(160) NOT NULL,
    beneficiary_type VARCHAR2(24) NOT NULL,
    account_holder_name VARCHAR2(160) NOT NULL,
    destination_account_masked VARCHAR2(40) NOT NULL,
    destination_account_hash CHAR(64) NOT NULL,
    routing_code VARCHAR2(32),
    status VARCHAR2(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    version NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT fk_beneficiaries_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_beneficiaries_destination UNIQUE (user_id, destination_account_hash),
    CONSTRAINT ck_beneficiaries_type CHECK (beneficiary_type IN ('BANK_ACCOUNT', 'UPI')),
    CONSTRAINT ck_beneficiaries_status CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'REMOVED'))
);

CREATE TABLE beneficiary_verifications (
    id VARCHAR2(26) PRIMARY KEY,
    beneficiary_id VARCHAR2(26) NOT NULL,
    verification_method VARCHAR2(32) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_beneficiary_verifications_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id),
    CONSTRAINT ck_beneficiary_verification_status CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'EXPIRED'))
);

CREATE TABLE transfers (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    source_account_id VARCHAR2(26) NOT NULL,
    beneficiary_id VARCHAR2(26) NOT NULL,
    transfer_reference VARCHAR2(80) NOT NULL,
    amount NUMBER(19, 4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    fee_amount NUMBER(19, 4) DEFAULT 0 NOT NULL,
    status VARCHAR2(32) NOT NULL,
    purpose VARCHAR2(280),
    scheduled_for TIMESTAMP WITH TIME ZONE,
    authorized_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR2(80),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    version NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT uk_transfers_reference UNIQUE (transfer_reference),
    CONSTRAINT fk_transfers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transfers_source_account FOREIGN KEY (source_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transfers_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id),
    CONSTRAINT ck_transfers_amount CHECK (amount > 0),
    CONSTRAINT ck_transfers_fee CHECK (fee_amount >= 0),
    CONSTRAINT ck_transfers_status CHECK (status IN ('DRAFT', 'READY_FOR_APPROVAL', 'AUTHORIZED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'EXPIRED'))
);

CREATE TABLE transfer_approvals (
    id VARCHAR2(26) PRIMARY KEY,
    transfer_id VARCHAR2(26) NOT NULL,
    user_id VARCHAR2(26) NOT NULL,
    challenge_hash CHAR(64) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_transfer_approvals_challenge UNIQUE (challenge_hash),
    CONSTRAINT fk_transfer_approvals_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (id),
    CONSTRAINT fk_transfer_approvals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_transfer_approvals_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'))
);

CREATE TABLE idempotency_records (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    operation VARCHAR2(80) NOT NULL,
    idempotency_key VARCHAR2(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id VARCHAR2(26),
    status VARCHAR2(24) NOT NULL,
    response_code NUMBER(3),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_idempotency_operation UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE TABLE audit_events (
    id VARCHAR2(26) PRIMARY KEY,
    actor_user_id VARCHAR2(26),
    aggregate_type VARCHAR2(80) NOT NULL,
    aggregate_id VARCHAR2(26) NOT NULL,
    event_type VARCHAR2(120) NOT NULL,
    correlation_id VARCHAR2(100),
    payload CLOB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE TABLE outbox_events (
    id VARCHAR2(26) PRIMARY KEY,
    aggregate_type VARCHAR2(80) NOT NULL,
    aggregate_id VARCHAR2(26) NOT NULL,
    event_type VARCHAR2(120) NOT NULL,
    payload CLOB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts NUMBER(10) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0)
);

CREATE INDEX ix_bank_accounts_user ON bank_accounts (user_id);
CREATE INDEX ix_beneficiaries_user_status ON beneficiaries (user_id, status);
CREATE INDEX ix_transfers_user_created ON transfers (user_id, created_at DESC);
CREATE INDEX ix_transfers_source_status ON transfers (source_account_id, status);
CREATE INDEX ix_transfer_approvals_transfer ON transfer_approvals (transfer_id);
CREATE INDEX ix_outbox_events_unpublished ON outbox_events (published_at, occurred_at);
