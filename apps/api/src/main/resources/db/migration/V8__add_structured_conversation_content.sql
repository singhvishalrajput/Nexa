-- Older turns remain readable with null structured content. Snapshots preserve
-- what was shown at the time; expanding history queries the authenticated API.
ALTER TABLE conversation_turns ADD banking_content CLOB;
ALTER TABLE conversation_turns ADD CONSTRAINT ck_turn_banking_json CHECK (banking_content IS JSON);
