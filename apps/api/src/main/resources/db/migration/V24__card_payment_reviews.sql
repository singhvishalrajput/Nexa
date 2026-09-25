-- Separate real payment reviews from historical provider simulations.
ALTER TABLE transactions DROP CONSTRAINT ck_tx_record_kind;
ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IN
 ('PAYMENT','MANDATE','MANDATE_EVENT','BILL','SCHEDULED_PAYMENT','BENEFICIARY','TRANSFER_REVIEW',
 'SIMULATION','LEGACY_TRANSFER','PRODUCT_HISTORY','ADMIN_EVENT','LOAN_INSTALLMENT','PAYMENT_REVIEW'));
ALTER TABLE transactions ADD CONSTRAINT ck_card_bill_review CHECK (
 record_kind <> 'PAYMENT_REVIEW' OR
 (operation IN ('PAY_CARD','PAY_BILL') AND source_account_id IS NOT NULL AND target_id IS NOT NULL
 AND amount IS NOT NULL AND amount>0 AND expires_at IS NOT NULL
 AND status IN ('REVIEW','COMPLETED','CANCELLED')
 AND (status <> 'COMPLETED' OR (transaction_reference IS NOT NULL AND completed_at IS NOT NULL)))
);
