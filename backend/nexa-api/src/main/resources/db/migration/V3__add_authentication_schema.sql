ALTER TABLE users ADD password_hash VARCHAR2(100);
ALTER TABLE users ADD role VARCHAR2(24) DEFAULT 'CUSTOMER' NOT NULL;

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'ADMIN'));

CREATE TABLE auth_refresh_tokens (
    id VARCHAR2(26) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_auth_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_auth_refresh_tokens_user ON auth_refresh_tokens (user_id);
CREATE INDEX ix_auth_refresh_tokens_expiry ON auth_refresh_tokens (expires_at);
