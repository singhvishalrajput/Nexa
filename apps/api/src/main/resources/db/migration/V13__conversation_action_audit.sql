-- Deliberately independent of deletable chat history. No raw message text or credentials.
CREATE TABLE conversation_action_events (
  id VARCHAR2(36) PRIMARY KEY,
  action_id VARCHAR2(36) NOT NULL,
  user_id VARCHAR2(26) NOT NULL,
  operation VARCHAR2(40) NOT NULL,
  status VARCHAR2(24) NOT NULL,
  source_account_id VARCHAR2(80),
  target_id VARCHAR2(80),
  amount VARCHAR2(32),
  transaction_reference VARCHAR2(80),
  correlation_id VARCHAR2(128),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX idx_action_audit ON conversation_action_events(action_id, created_at);
