-- Typed domain projections populated by seed data or future banking adapters.
-- Ownership is relational, independent of the display-only JSON payload.
ALTER TABLE bank_accounts ADD CONSTRAINT uk_accounts_id_owner UNIQUE (id, user_id);
ALTER TABLE beneficiaries ADD bank_name VARCHAR2(160);
ALTER TABLE transactions ADD payment_method VARCHAR2(32);
CREATE TABLE banking_products (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    kind VARCHAR2(24) NOT NULL,
    account_id VARCHAR2(26) NOT NULL,
    payload CLOB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_products_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_products_owned_account FOREIGN KEY (account_id,user_id) REFERENCES bank_accounts(id,user_id),
    CONSTRAINT ck_products_kind CHECK (kind IN ('MANDATE','BILL','CARD','SCHEDULED_PAYMENT','LOAN')),
    CONSTRAINT ck_products_json CHECK (payload IS JSON)
);
CREATE INDEX ix_products_owner_kind ON banking_products(user_id,kind,id);
