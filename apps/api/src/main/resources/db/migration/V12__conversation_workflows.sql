CREATE TABLE conversation_workflows (
  id VARCHAR2(36) PRIMARY KEY,
  conversation_id VARCHAR2(36) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  state CLOB NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX idx_workflow_conversation ON conversation_workflows(conversation_id, updated_at);
ALTER TABLE conversation_turns ADD workflow_content CLOB;
