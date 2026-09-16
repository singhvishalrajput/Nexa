CREATE TABLE showcase_actions (
  id VARCHAR2(36) PRIMARY KEY,
  user_id VARCHAR2(26) NOT NULL REFERENCES users(id),
  operation VARCHAR2(40) NOT NULL,
  account_id VARCHAR2(80),
  target_id VARCHAR2(80) NOT NULL,
  amount VARCHAR2(32),
  currency_code VARCHAR2(3),
  status VARCHAR2(24) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP
);
CREATE INDEX ix_showcase_owner ON showcase_actions(user_id, expires_at);
