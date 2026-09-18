-- Extend the existing account/product and typed transaction model; no new banking tables.
ALTER TABLE accounts ADD (
 application_key VARCHAR2(80), loan_purpose VARCHAR2(200), tenure_months NUMBER(10),
 approved_at TIMESTAMP, closed_at TIMESTAMP
);
CREATE UNIQUE INDEX uk_loan_application ON accounts(CASE WHEN application_key IS NOT NULL THEN customer_id END, application_key);
ALTER TABLE accounts ADD CONSTRAINT ck_loan_terms CHECK (tenure_months IS NULL OR
 (account_type='LOAN' AND application_key IS NOT NULL AND loan_purpose IS NOT NULL
 AND tenure_months BETWEEN 1 AND 60 AND principal_amount BETWEEN 1000 AND 1000000
 AND interest_rate BETWEEN 0 AND 50 AND periodic_payment>0 AND approved_at IS NOT NULL));
ALTER TABLE transactions ADD (
 installment_number NUMBER(10), principal_component NUMBER(19,2), interest_component NUMBER(19,2)
);
ALTER TABLE transactions DROP CONSTRAINT ck_tx_record_kind;
ALTER TABLE transactions ADD CONSTRAINT ck_tx_record_kind CHECK(record_kind IN
 ('PAYMENT','MANDATE','MANDATE_EVENT','BILL','SCHEDULED_PAYMENT','BENEFICIARY','TRANSFER_REVIEW',
 'SIMULATION','LEGACY_TRANSFER','PRODUCT_HISTORY','ADMIN_EVENT','LOAN_INSTALLMENT'));
CREATE UNIQUE INDEX uk_loan_installment ON transactions(CASE WHEN installment_number IS NOT NULL THEN target_id END, installment_number);
ALTER TABLE transactions ADD CONSTRAINT ck_loan_installment CHECK(record_kind <> 'LOAN_INSTALLMENT' OR
 (target_id IS NOT NULL AND installment_number BETWEEN 1 AND 60 AND due_at IS NOT NULL
 AND principal_component IS NOT NULL AND principal_component>0
 AND interest_component IS NOT NULL AND interest_component>=0
 AND amount=principal_component+interest_component AND status IN ('PENDING','PAID')));
-- A credit-normal system clearing account receives earned loan interest.
INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)
VALUES('NEXA-LOAN-INTEREST','Loan interest income','CLEARING','SYSTEM','INR',0,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
