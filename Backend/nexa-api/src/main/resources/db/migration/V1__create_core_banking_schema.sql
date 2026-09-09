CREATE TABLE users (
    id VARCHAR2(26) PRIMARY KEY,
    email VARCHAR2(254) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED'))
);

CREATE TABLE customer_profiles (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    full_name VARCHAR2(160) NOT NULL,
    phone_number VARCHAR2(32),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_customer_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE bank_accounts (
    id VARCHAR2(26) PRIMARY KEY,
    customer_id VARCHAR2(26) NOT NULL,
    account_number_masked VARCHAR2(32) NOT NULL,
    display_name VARCHAR2(120) NOT NULL,
    account_type VARCHAR2(24) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    available_balance NUMBER(19, 4) NOT NULL,
    ledger_balance NUMBER(19, 4) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    version NUMBER(19) DEFAULT 0 NOT NULL,
    CONSTRAINT fk_bank_accounts_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles (id),
    CONSTRAINT ck_bank_accounts_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CREDIT')),
    CONSTRAINT ck_bank_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_bank_accounts_balance CHECK (available_balance >= 0 AND ledger_balance >= 0)
);

CREATE TABLE ledger_accounts (
    id VARCHAR2(26) PRIMARY KEY,
    bank_account_id VARCHAR2(26),
    account_code VARCHAR2(80) NOT NULL,
    account_name VARCHAR2(160) NOT NULL,
    account_type VARCHAR2(24) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_ledger_accounts_code UNIQUE (account_code),
    CONSTRAINT fk_ledger_accounts_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT ck_ledger_accounts_type CHECK (account_type IN ('ASSET', 'LIABILITY', 'INCOME', 'EXPENSE', 'EQUITY'))
);

CREATE TABLE journal_entries (
    id VARCHAR2(26) PRIMARY KEY,
    entry_reference VARCHAR2(80) NOT NULL,
    entry_type VARCHAR2(40) NOT NULL,
    description VARCHAR2(500) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_journal_entries_reference UNIQUE (entry_reference)
);

CREATE TABLE ledger_postings (
    id VARCHAR2(26) PRIMARY KEY,
    journal_entry_id VARCHAR2(26) NOT NULL,
    ledger_account_id VARCHAR2(26) NOT NULL,
    direction CHAR(1) NOT NULL,
    amount NUMBER(19, 4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_postings_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id),
    CONSTRAINT fk_ledger_postings_account FOREIGN KEY (ledger_account_id) REFERENCES ledger_accounts (id),
    CONSTRAINT ck_ledger_postings_direction CHECK (direction IN ('D', 'C')),
    CONSTRAINT ck_ledger_postings_amount CHECK (amount > 0)
);

CREATE TABLE transactions (
    id VARCHAR2(26) PRIMARY KEY,
    bank_account_id VARCHAR2(26) NOT NULL,
    journal_entry_id VARCHAR2(26),
    transaction_reference VARCHAR2(80) NOT NULL,
    transaction_type VARCHAR2(32) NOT NULL,
    merchant_name VARCHAR2(200),
    category VARCHAR2(80),
    amount NUMBER(19, 4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR2(24) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_transactions_reference UNIQUE (transaction_reference),
    CONSTRAINT fk_transactions_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts (id),
    CONSTRAINT fk_transactions_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries (id),
    CONSTRAINT ck_transactions_status CHECK (status IN ('PENDING', 'POSTED', 'REVERSED', 'FAILED'))
);

CREATE INDEX ix_bank_accounts_customer ON bank_accounts (customer_id);
CREATE INDEX ix_ledger_postings_entry ON ledger_postings (journal_entry_id);
CREATE INDEX ix_transactions_account_occurred ON transactions (bank_account_id, occurred_at DESC);
