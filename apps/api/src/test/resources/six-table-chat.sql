CREATE TABLE conversations (
    id VARCHAR2(36) PRIMARY KEY,
    user_id VARCHAR2(26) NOT NULL REFERENCES customers(user_id),
    title VARCHAR2(120 CHAR) DEFAULT 'New conversation' NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX ix_conversations_owner ON conversations(user_id, created_at DESC, id);

-- One immutable exchange per row. No audio, raw voice transcript or duplicated
-- conversation JSON. The client key makes retries safe after a lost response.
CREATE TABLE conversation_turns (
    sequence_id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id VARCHAR2(36) NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    client_id VARCHAR2(36) NOT NULL,
    source VARCHAR2(5) NOT NULL CHECK (source IN ('TEXT', 'VOICE')),
    intent VARCHAR2(40) NOT NULL,
    user_text CLOB NOT NULL,
    assistant_text CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uk_conversation_turn_retry UNIQUE (conversation_id, client_id)
);
CREATE INDEX ix_conversation_turn_page ON conversation_turns(conversation_id, sequence_id DESC);
