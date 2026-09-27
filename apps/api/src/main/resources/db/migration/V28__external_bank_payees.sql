-- External bank details are separate from linked Nexa beneficiaries and encrypted at rest.
-- No provider registration, transfers, users or balances are created by this migration.
CREATE TABLE external_bank_payees (
    id VARCHAR2(40) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    display_name VARCHAR2(160) NOT NULL,
    recipient_name VARCHAR2(400) NOT NULL,
    bank_name VARCHAR2(100) NOT NULL,
    account_encrypted VARCHAR2(2000) NOT NULL,
    account_masked VARCHAR2(40) NOT NULL,
    ifsc VARCHAR2(11) NOT NULL,
    destination_hash VARCHAR2(64) NOT NULL,
    status VARCHAR2(16) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_external_payee_user FOREIGN KEY (user_id) REFERENCES customers(user_id),
    CONSTRAINT uk_external_payee_dest UNIQUE (user_id,destination_hash),
    CONSTRAINT ck_external_payee_status CHECK (status IN ('ACTIVE','SUSPENDED'))
);
CREATE INDEX idx_external_payee_owner ON external_bank_payees(user_id,created_at);
