-- Constraints for typed instructions and the extended posting types.
ALTER TABLE transactions ADD CONSTRAINT ck_posting_accounts CHECK (
 record_kind <> 'PAYMENT' OR
 (transaction_type='DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL) OR
 (transaction_type='WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL) OR
 (transaction_type IN ('TRANSFER','LOAN_DISBURSEMENT','LOAN_REPAYMENT') AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id<>destination_account_id)
);
ALTER TABLE transactions ADD CONSTRAINT ck_mandate_authorization CHECK (
 record_kind <> 'MANDATE' OR operation IS NULL OR
 (operation='TRANSFER' AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id<>destination_account_id AND amount IS NOT NULL AND amount>0 AND effective_date IS NOT NULL)
);
ALTER TABLE transactions ADD CONSTRAINT ck_review_shape CHECK (
 record_kind <> 'TRANSFER_REVIEW' OR
 (source_account_id IS NOT NULL AND destination_account_id IS NOT NULL AND source_account_id<>destination_account_id AND amount IS NOT NULL AND amount>0 AND expires_at IS NOT NULL AND
 ((status='READY' AND transaction_reference IS NULL AND completed_at IS NULL) OR (status='COMPLETED' AND transaction_reference IS NOT NULL AND completed_at IS NOT NULL)))
);
ALTER TABLE customers ADD CONSTRAINT ck_customer_access_status CHECK(status IN ('ACTIVE','LOCKED','DISABLED'));
ALTER TABLE customers ADD CONSTRAINT ck_customer_access_role CHECK(role IN ('CUSTOMER','SUPPORT_AGENT','FRAUD_ANALYST','ADMIN'));
CREATE INDEX idx_product_history ON transactions(target_id,record_kind,created_at);
CREATE UNIQUE INDEX uk_beneficiary_destination ON transactions(CASE WHEN record_kind='BENEFICIARY' THEN user_id END,CASE WHEN record_kind='BENEFICIARY' THEN destination_hash END);
