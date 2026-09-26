-- Additive account-opening workflow for the existing V1-V23 schema.
-- Existing customers, accounts, balances and migration history are preserved.
-- Existing duplicate savings accounts are not removed or globally prohibited:
-- the workflow serializes new requests per customer and rejects new duplicates.
-- Applications retain encrypted PAN/passport references or Aadhaar last four only.
-- No document upload or government identity-verification capability is implied.
-- The only seeded row is an empty protected holding account, with no money posting.
-- Receipt numbers are generated once by the service and retained for safe retries.
-- No DELETE CASCADE: application, receipt and event history must remain explicit.
-- Supporting candidate key for an application's same-owner/type/currency FK.
-- ID is already unique, so this does not reject any otherwise valid existing row.
ALTER TABLE accounts ADD CONSTRAINT uk_acc_opening_owner
    UNIQUE (id, customer_id, account_type, currency_code);

CREATE TABLE account_applications (
    id VARCHAR2(36) PRIMARY KEY,
    customer_id NUMBER NOT NULL,
    request_key VARCHAR2(36) NOT NULL,
    account_type VARCHAR2(20) DEFAULT 'SAVINGS' NOT NULL,
    currency_code VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    full_name VARCHAR2(160) NOT NULL,
    email VARCHAR2(254) NOT NULL,
    phone_number VARCHAR2(32),
    date_of_birth DATE NOT NULL,
    identity_type VARCHAR2(16) NOT NULL,
    identity_ciphertext VARCHAR2(512),
    identity_key_id VARCHAR2(80),
    identity_last4 VARCHAR2(4) NOT NULL,
    business_date DATE NOT NULL,
    requested_amount NUMBER NOT NULL,
    consent_notice_version VARCHAR2(40) NOT NULL,
    consented_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consent_withdrawn_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR2(32) DEFAULT 'DRAFT' NOT NULL,
    review_decision VARCHAR2(16) DEFAULT 'PENDING' NOT NULL,
    reviewed_by VARCHAR2(26),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_reason VARCHAR2(500),
    submitted_at TIMESTAMP WITH TIME ZONE,
    account_id NUMBER,
    opened_at TIMESTAMP WITH TIME ZONE,
    ended_at TIMESTAMP WITH TIME ZONE,
    end_reason_code VARCHAR2(40),
    version NUMBER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_app_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_app_reviewer FOREIGN KEY (reviewed_by) REFERENCES customers(user_id),
    CONSTRAINT fk_app_opened_account FOREIGN KEY (account_id, customer_id, account_type, currency_code)
        REFERENCES accounts(id, customer_id, account_type, currency_code),
    CONSTRAINT uk_app_request UNIQUE (customer_id, request_key),
    CONSTRAINT uk_app_account UNIQUE (account_id),
    CONSTRAINT uk_app_cash_amount UNIQUE (id, currency_code, requested_amount),
    CONSTRAINT uk_app_account_link UNIQUE (id, account_id),
    CONSTRAINT ck_app_identity CHECK (
    (identity_type='AADHAAR' AND identity_ciphertext IS NULL AND identity_key_id IS NULL
        AND REGEXP_LIKE(identity_last4,'^[0-9]{4}$'))
    OR (identity_type IN ('PAN','PASSPORT') AND identity_ciphertext IS NOT NULL
        AND LENGTH(identity_ciphertext) BETWEEN 40 AND 512
        AND identity_key_id IS NOT NULL AND REGEXP_LIKE(identity_key_id,'^[A-Za-z0-9_-]{1,80}$')
        AND ((identity_type='PAN' AND REGEXP_LIKE(identity_last4,'^[0-9]{3}[A-Z]$'))
            OR (identity_type='PASSPORT' AND REGEXP_LIKE(identity_last4,'^[A-Z0-9]{4}$'))))
),
    CONSTRAINT ck_app_type CHECK (account_type='SAVINGS' AND currency_code='INR'),
    CONSTRAINT ck_app_text CHECK (TRIM(full_name) IS NOT NULL AND TRIM(email) IS NOT NULL
        AND TRIM(request_key) IS NOT NULL AND TRIM(consent_notice_version) IS NOT NULL),
    -- Unscaled NUMBER plus ROUND check rejects 1000.001 rather than rounding it.
    -- Opening cash is limited to INR 1,000 through INR 1 crore.
    CONSTRAINT ck_app_amount CHECK (requested_amount BETWEEN 1000 AND 10000000
        AND requested_amount=ROUND(requested_amount,2)),
    CONSTRAINT ck_app_adult CHECK (
        date_of_birth=TRUNC(date_of_birth) AND business_date=TRUNC(business_date)
        AND date_of_birth>=DATE '0001-01-01' AND business_date>=DATE '0001-01-01'
        AND date_of_birth<business_date
        AND (EXTRACT(YEAR FROM business_date)-EXTRACT(YEAR FROM date_of_birth)>18
          OR (EXTRACT(YEAR FROM business_date)-EXTRACT(YEAR FROM date_of_birth)=18
            AND (EXTRACT(MONTH FROM date_of_birth)<EXTRACT(MONTH FROM business_date)
              OR (EXTRACT(MONTH FROM date_of_birth)=EXTRACT(MONTH FROM business_date)
                AND EXTRACT(DAY FROM date_of_birth)<=EXTRACT(DAY FROM business_date)))))
    ),
    CONSTRAINT ck_app_version CHECK (version>=0 AND version=TRUNC(version)),
    CONSTRAINT ck_app_status CHECK (status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED',
        'APPROVED_AWAITING_CASH','CASH_RECEIVED','OPENED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED')),
    CONSTRAINT ck_app_review CHECK (
        (review_decision='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL)
        OR (review_decision IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL AND TRIM(review_reason) IS NOT NULL AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_app_stage_review CHECK (
        (status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED') AND review_decision='PENDING')
        OR (status IN ('APPROVED_AWAITING_CASH','CASH_RECEIVED','OPENED','REFUND_PENDING','REFUNDED') AND review_decision='APPROVED')
        OR (status='REJECTED' AND review_decision='REJECTED')
        OR (status='CANCELLED' AND review_decision IN ('PENDING','APPROVED'))
    ),
    CONSTRAINT ck_app_submission CHECK (
        (status='DRAFT' AND submitted_at IS NULL)
        OR status='CANCELLED'
        OR (status NOT IN ('DRAFT','CANCELLED') AND submitted_at IS NOT NULL)
    ),
    CONSTRAINT ck_app_opening CHECK (
        (status='OPENED' AND account_id IS NOT NULL AND opened_at IS NOT NULL)
        OR (status<>'OPENED' AND account_id IS NULL AND opened_at IS NULL)
    ),
    CONSTRAINT ck_app_terminal CHECK (
        (status IN ('REJECTED','CANCELLED','REFUNDED') AND ended_at IS NOT NULL
            AND end_reason_code IS NOT NULL
            AND end_reason_code IN ('REVIEW_REJECTED','CUSTOMER_CANCELLED','CONSENT_WITHDRAWN',
                'INELIGIBLE','EXPIRED','CASH_RETURNED','DUPLICATE_ACCOUNT'))
        OR (status NOT IN ('REJECTED','CANCELLED','REFUNDED') AND ended_at IS NULL AND end_reason_code IS NULL)
    ),
    CONSTRAINT ck_app_consent CHECK (consent_withdrawn_at IS NULL OR
        (consent_withdrawn_at>=consented_at AND status IN ('REJECTED','CANCELLED','REFUND_PENDING','REFUNDED','OPENED'))),
    CONSTRAINT ck_app_times CHECK (updated_at>=created_at AND consented_at<=created_at
        AND (submitted_at IS NULL OR submitted_at>=created_at)
        AND (reviewed_at IS NULL OR reviewed_at>=submitted_at)
        AND (opened_at IS NULL OR opened_at>=reviewed_at)
        AND (ended_at IS NULL OR ended_at>=created_at))
);

-- One live request per customer; an OPENED request is also retained in this index.
CREATE UNIQUE INDEX uk_app_open_customer ON account_applications (
    CASE WHEN status NOT IN ('REJECTED','CANCELLED','REFUNDED') THEN customer_id END
);
CREATE INDEX ix_app_review_queue ON account_applications(status, created_at);
CREATE INDEX ix_app_reviewer ON account_applications(reviewed_by);

CREATE TABLE application_documents (
    id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    document_type VARCHAR2(24) NOT NULL,
    storage_key VARCHAR2(80) NOT NULL,
    encryption_key_id VARCHAR2(80) NOT NULL,
    content_sha256 VARCHAR2(64) NOT NULL,
    media_type VARCHAR2(40) NOT NULL,
    byte_size NUMBER NOT NULL,
    storage_status VARCHAR2(16) DEFAULT 'QUARANTINED' NOT NULL,
    safety_status VARCHAR2(16) DEFAULT 'PENDING' NOT NULL,
    safety_checker_version VARCHAR2(80),
    safety_checked_at TIMESTAMP WITH TIME ZONE,
    review_status VARCHAR2(16) DEFAULT 'PENDING' NOT NULL,
    reviewed_by VARCHAR2(26),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_reason VARCHAR2(500),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    purged_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_doc_application FOREIGN KEY (application_id) REFERENCES account_applications(id),
    CONSTRAINT fk_doc_reviewer FOREIGN KEY (reviewed_by) REFERENCES customers(user_id),
    CONSTRAINT uk_doc_storage UNIQUE (storage_key),
    CONSTRAINT uk_doc_application_link UNIQUE (id, application_id),
    CONSTRAINT ck_doc_type CHECK (document_type IN ('AADHAAR','PAN','ADDRESS_PROOF')),
    CONSTRAINT ck_doc_private_ref CHECK (REGEXP_LIKE(storage_key,'^[A-Za-z0-9_-]{20,80}$')
        AND TRIM(encryption_key_id) IS NOT NULL AND REGEXP_LIKE(content_sha256,'^[a-f0-9]{64}$')),
    CONSTRAINT ck_doc_file CHECK (media_type IN ('application/pdf','image/jpeg','image/png')
        AND byte_size BETWEEN 1 AND 10485760 AND byte_size=TRUNC(byte_size)),
    CONSTRAINT ck_doc_storage CHECK (storage_status IN ('QUARANTINED','AVAILABLE','PURGED')
        AND ((storage_status='PURGED' AND purged_at IS NOT NULL)
          OR (storage_status<>'PURGED' AND purged_at IS NULL))
        AND (storage_status<>'AVAILABLE' OR safety_status='CLEAN')),
    CONSTRAINT ck_doc_safety CHECK (
        (safety_status='PENDING' AND safety_checker_version IS NULL AND safety_checked_at IS NULL)
        OR (safety_status IN ('CLEAN','REJECTED') AND TRIM(safety_checker_version) IS NOT NULL AND safety_checked_at IS NOT NULL)
    ),
    CONSTRAINT ck_doc_review CHECK (
        (review_status='PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL)
        OR (review_status IN ('ACCEPTED','REJECTED') AND reviewed_by IS NOT NULL
            AND reviewed_at IS NOT NULL AND TRIM(review_reason) IS NOT NULL)
    ),
    CONSTRAINT ck_doc_accept CHECK (review_status<>'ACCEPTED' OR
        (safety_status='CLEAN' AND storage_status IN ('AVAILABLE','PURGED'))),
    CONSTRAINT ck_doc_times CHECK ((safety_checked_at IS NULL OR safety_checked_at>=uploaded_at)
        AND (reviewed_at IS NULL OR reviewed_at>=uploaded_at)
        AND (purged_at IS NULL OR purged_at>=uploaded_at)
        AND (review_status<>'ACCEPTED' OR reviewed_at>=safety_checked_at)
        AND (review_status<>'ACCEPTED' OR purged_at IS NULL OR purged_at>=reviewed_at))
);
CREATE INDEX ix_doc_application ON application_documents(application_id, uploaded_at);
CREATE INDEX ix_doc_reviewer ON application_documents(reviewed_by);

-- Receipt rows acknowledge cash actually received, not intentions/screenshots.
-- Initial workflow: one full receipt and, if needed, one full refund per request.
CREATE TABLE opening_cash_receipts (
    id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    receipt_number VARCHAR2(64) NOT NULL,
    request_key VARCHAR2(36) NOT NULL,
    currency_code VARCHAR2(3) DEFAULT 'INR' NOT NULL,
    amount NUMBER NOT NULL,
    received_by VARCHAR2(26) NOT NULL,
    cash_received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    status VARCHAR2(20) DEFAULT 'RECEIVED' NOT NULL,
    receipt_transaction_id VARCHAR2(40) NOT NULL,
    allocation_transaction_id VARCHAR2(40),
    allocated_account_id NUMBER,
    allocated_at TIMESTAMP WITH TIME ZONE,
    refund_requested_by VARCHAR2(26),
    refund_requested_at TIMESTAMP WITH TIME ZONE,
    refund_reason VARCHAR2(500),
    refund_transaction_id VARCHAR2(40),
    refunded_by VARCHAR2(26),
    refunded_at TIMESTAMP WITH TIME ZONE,
    version NUMBER DEFAULT 0 NOT NULL,
    CONSTRAINT fk_receipt_app_amount FOREIGN KEY (application_id, currency_code, amount)
        REFERENCES account_applications(id, currency_code, requested_amount),
    CONSTRAINT fk_receipt_app_account FOREIGN KEY (application_id, allocated_account_id)
        REFERENCES account_applications(id, account_id),
    CONSTRAINT fk_receipt_receiver FOREIGN KEY (received_by) REFERENCES customers(user_id),
    CONSTRAINT fk_receipt_refund_request FOREIGN KEY (refund_requested_by) REFERENCES customers(user_id),
    CONSTRAINT fk_receipt_refunder FOREIGN KEY (refunded_by) REFERENCES customers(user_id),
    CONSTRAINT fk_receipt_payment FOREIGN KEY (receipt_transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_receipt_allocation FOREIGN KEY (allocation_transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_receipt_refund FOREIGN KEY (refund_transaction_id) REFERENCES transactions(id),
    CONSTRAINT uk_receipt_application UNIQUE (application_id),
    CONSTRAINT uk_receipt_number UNIQUE (receipt_number),
    CONSTRAINT uk_receipt_request UNIQUE (received_by, request_key),
    CONSTRAINT uk_receipt_payment UNIQUE (receipt_transaction_id),
    CONSTRAINT uk_receipt_allocation UNIQUE (allocation_transaction_id),
    CONSTRAINT uk_receipt_refund UNIQUE (refund_transaction_id),
    CONSTRAINT uk_receipt_app_link UNIQUE (id, application_id),
    CONSTRAINT ck_receipt_text CHECK (TRIM(receipt_number) IS NOT NULL AND TRIM(request_key) IS NOT NULL),
    CONSTRAINT ck_receipt_amount CHECK (currency_code='INR' AND amount BETWEEN 1000 AND 10000000 AND amount=ROUND(amount,2)),
    CONSTRAINT ck_receipt_version CHECK (version>=0 AND version=TRUNC(version)),
    CONSTRAINT ck_receipt_status CHECK (status IN ('RECEIVED','APPLIED','REFUND_PENDING','REFUNDED')),
    CONSTRAINT ck_receipt_alloc CHECK (
        (status='APPLIED' AND allocation_transaction_id IS NOT NULL AND allocated_account_id IS NOT NULL AND allocated_at IS NOT NULL)
        OR (status<>'APPLIED' AND allocation_transaction_id IS NULL AND allocated_account_id IS NULL AND allocated_at IS NULL)
    ),
    CONSTRAINT ck_receipt_refund_request CHECK (
        (status IN ('REFUND_PENDING','REFUNDED') AND refund_requested_by IS NOT NULL
            AND refund_requested_at IS NOT NULL AND TRIM(refund_reason) IS NOT NULL)
        OR (status NOT IN ('REFUND_PENDING','REFUNDED') AND refund_requested_by IS NULL AND refund_requested_at IS NULL AND refund_reason IS NULL)
    ),
    CONSTRAINT ck_receipt_refund CHECK (
        (status='REFUNDED' AND refund_transaction_id IS NOT NULL AND refunded_by IS NOT NULL AND refunded_at IS NOT NULL)
        OR (status<>'REFUNDED' AND refund_transaction_id IS NULL AND refunded_by IS NULL AND refunded_at IS NULL)
    ),
    CONSTRAINT ck_receipt_distinct_posts CHECK (
        (allocation_transaction_id IS NULL OR allocation_transaction_id<>receipt_transaction_id)
        AND (refund_transaction_id IS NULL OR refund_transaction_id<>receipt_transaction_id)
    ),
    CONSTRAINT ck_receipt_times CHECK (recorded_at>=cash_received_at
        AND (allocated_at IS NULL OR allocated_at>=recorded_at)
        AND (refund_requested_at IS NULL OR refund_requested_at>=recorded_at)
        AND (refunded_at IS NULL OR refunded_at>=refund_requested_at))
);
CREATE INDEX ix_receipt_receiver ON opening_cash_receipts(received_by);
CREATE INDEX ix_receipt_refund_request ON opening_cash_receipts(refund_requested_by);
CREATE INDEX ix_receipt_refunder ON opening_cash_receipts(refunded_by);

CREATE TABLE application_events (
    id VARCHAR2(36) PRIMARY KEY,
    application_id VARCHAR2(36) NOT NULL,
    event_key VARCHAR2(36) NOT NULL,
    application_version NUMBER NOT NULL,
    event_type VARCHAR2(32) NOT NULL,
    actor_user_id VARCHAR2(26) NOT NULL,
    actor_role VARCHAR2(16) NOT NULL,
    from_status VARCHAR2(32),
    to_status VARCHAR2(32) NOT NULL,
    reason_code VARCHAR2(40),
    document_id VARCHAR2(36),
    receipt_id VARCHAR2(36),
    correlation_id VARCHAR2(36) NOT NULL,
    request_fingerprint VARCHAR2(64) NOT NULL,
    review_reason VARCHAR2(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_event_application FOREIGN KEY (application_id) REFERENCES account_applications(id),
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_user_id) REFERENCES customers(user_id),
    CONSTRAINT fk_event_document FOREIGN KEY (document_id, application_id) REFERENCES application_documents(id, application_id),
    CONSTRAINT fk_event_receipt FOREIGN KEY (receipt_id, application_id) REFERENCES opening_cash_receipts(id, application_id),
    CONSTRAINT uk_event_key UNIQUE (application_id, event_key),
    CONSTRAINT uk_event_version UNIQUE (application_id, application_version),
    CONSTRAINT ck_event_request_hash CHECK (REGEXP_LIKE(request_fingerprint,'^[a-f0-9]{64}$')),
    CONSTRAINT ck_event_review_reason CHECK (
        event_type NOT IN ('DOCUMENT_REVIEWED','APPROVED','REJECTED','CHANGES_REQUESTED','REFUND_REQUESTED')
        OR TRIM(review_reason) IS NOT NULL),
    CONSTRAINT ck_event_identity_update CHECK (
        event_type<>'IDENTITY_UPDATED' OR (actor_role='CUSTOMER'
            AND from_status IN ('DRAFT','CHANGES_REQUESTED') AND to_status=from_status
            AND document_id IS NULL AND receipt_id IS NULL)),
    CONSTRAINT ck_event_version CHECK (application_version>=0 AND application_version=TRUNC(application_version)),
    CONSTRAINT ck_event_text CHECK (TRIM(event_key) IS NOT NULL AND TRIM(correlation_id) IS NOT NULL),
    CONSTRAINT ck_event_actor_role CHECK (actor_role IN ('CUSTOMER','ADMIN')),
    CONSTRAINT ck_event_type CHECK (event_type IN ('APPLICATION_CREATED','SUBMITTED','CHANGES_REQUESTED',
        'APPROVED','REJECTED','CANCELLED','DOCUMENT_ADDED','DOCUMENT_REVIEWED',
        'CASH_RECEIVED','ACCOUNT_OPENED','REFUND_REQUESTED','CASH_REFUNDED','IDENTITY_UPDATED')),
    CONSTRAINT ck_event_from CHECK (from_status IS NULL OR from_status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED',
        'APPROVED_AWAITING_CASH','CASH_RECEIVED','OPENED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED')),
    CONSTRAINT ck_event_to CHECK (to_status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED',
        'APPROVED_AWAITING_CASH','CASH_RECEIVED','OPENED','REJECTED','CANCELLED','REFUND_PENDING','REFUNDED')),
    CONSTRAINT ck_event_initial CHECK ((event_type='APPLICATION_CREATED' AND from_status IS NULL
        AND to_status='DRAFT' AND application_version=0 AND actor_role='CUSTOMER')
        OR (event_type<>'APPLICATION_CREATED' AND from_status IS NOT NULL AND application_version>0)),
    CONSTRAINT ck_event_admin CHECK (event_type NOT IN ('CHANGES_REQUESTED','APPROVED','REJECTED','DOCUMENT_REVIEWED',
        'CASH_RECEIVED','ACCOUNT_OPENED','REFUND_REQUESTED','CASH_REFUNDED') OR actor_role='ADMIN'),
    CONSTRAINT ck_event_reason CHECK (reason_code IS NULL OR reason_code IN ('DOCUMENT_UNREADABLE','DETAILS_MISMATCH',
        'INCOMPLETE_DOCUMENTS','CUSTOMER_INELIGIBLE','DUPLICATE_ACCOUNT','CUSTOMER_CANCELLED',
        'CONSENT_WITHDRAWN','EXPIRED','CASH_RETURNED','REVIEW_ACCEPTED','OTHER_REVIEW')),
    CONSTRAINT ck_event_reason_required CHECK (event_type NOT IN ('CHANGES_REQUESTED','REJECTED','CANCELLED','REFUND_REQUESTED') OR reason_code IS NOT NULL),
    CONSTRAINT ck_event_doc CHECK (event_type NOT IN ('DOCUMENT_ADDED','DOCUMENT_REVIEWED') OR document_id IS NOT NULL),
    CONSTRAINT ck_event_receipt CHECK (event_type NOT IN ('CASH_RECEIVED','ACCOUNT_OPENED','REFUND_REQUESTED','CASH_REFUNDED') OR receipt_id IS NOT NULL)
);
CREATE INDEX ix_event_actor ON application_events(actor_user_id);
CREATE INDEX ix_event_document ON application_events(document_id);
CREATE INDEX ix_event_receipt ON application_events(receipt_id);

-- Internal liability for acknowledged opening cash awaiting account creation.
-- Generic account/transfer endpoints must reject this protected account.
-- Reuse an existing reserved account without changing its status or balance.
MERGE INTO accounts target
USING (SELECT 'NEXA-OPENING-HOLD' account_number FROM dual) source
ON (target.account_number=source.account_number)
WHEN NOT MATCHED THEN INSERT (
    account_number, account_name, account_type, account_category,
    currency_code, balance, status, version, created_at, updated_at
) VALUES (
    source.account_number, 'Pending account opening cash', 'CLEARING', 'SYSTEM',
    'INR', 0, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
