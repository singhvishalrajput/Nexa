-- Administrative changes stay in the transaction audit trail. No new banking tables.
ALTER TABLE transactions DROP CONSTRAINT ck_tx_record_kind;
ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IN ('PAYMENT','MANDATE','MANDATE_EVENT','BILL','SCHEDULED_PAYMENT','BENEFICIARY','TRANSFER_REVIEW','SIMULATION','LEGACY_TRANSFER','PRODUCT_HISTORY','ADMIN_EVENT'));
ALTER TABLE transactions ADD (audit_reason VARCHAR2(500), before_name VARCHAR2(120), after_name VARCHAR2(120), before_status VARCHAR2(32), after_status VARCHAR2(32));
ALTER TABLE transactions DROP CONSTRAINT ck_review_shape;
ALTER TABLE transactions ADD CONSTRAINT ck_review_shape CHECK (
 record_kind <> 'TRANSFER_REVIEW' OR
 (source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id<>destination_account_id AND amount IS NOT NULL AND amount>0 AND expires_at IS NOT NULL AND
 ((status IN ('READY','CANCELLED') AND transaction_reference IS NULL AND completed_at IS NULL) OR (status='COMPLETED' AND transaction_reference IS NOT NULL AND completed_at IS NOT NULL)))
);
CREATE INDEX idx_admin_account_audit ON transactions(record_kind,source_account_id,created_at);
