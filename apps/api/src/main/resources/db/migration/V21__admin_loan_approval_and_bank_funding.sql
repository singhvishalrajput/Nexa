-- Preserve existing loans; only un-disbursed applications return to administrator review.
ALTER TABLE accounts ADD (reviewed_by VARCHAR2(26) REFERENCES customers(user_id), review_reason VARCHAR2(500), reviewed_at TIMESTAMP);
ALTER TABLE accounts DROP CONSTRAINT ck_loan_terms;
ALTER TABLE accounts ADD CONSTRAINT ck_loan_terms CHECK (tenure_months IS NULL OR
 (account_type='LOAN' AND application_key IS NOT NULL AND loan_purpose IS NOT NULL
 AND tenure_months BETWEEN 1 AND 60 AND principal_amount BETWEEN 1000 AND 1000000
 AND interest_rate BETWEEN 0 AND 50 AND periodic_payment>0
 AND (product_status IN ('PENDING_APPROVAL','REJECTED') OR approved_at IS NOT NULL)));
UPDATE accounts SET product_status='PENDING_APPROVAL',approved_at=NULL
 WHERE account_type='LOAN' AND product_status IN ('CREATED','APPROVED');
CREATE INDEX idx_loan_review_queue ON accounts(account_type,product_status,created_at);
INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)
VALUES('NEXA-BANK-FUNDING','Nexa Bank funding and repayments','CLEARING','SYSTEM','INR',0,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO accounts(account_number,account_name,account_type,account_category,currency_code,balance,status,version,created_at,updated_at)
VALUES('NEXA-LOAN-CONTROL','Bank loan funding control','CLEARING','SYSTEM','INR',0,'ACTIVE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
-- Allocate historical outstanding principal to the funding/control pair without replaying customer payments.
INSERT INTO transactions(id,record_kind,transaction_type,source_account_id,destination_account_id,amount,currency_code,status,operation,transaction_reference,created_at,completed_at)
SELECT 'TX-LOAN-FUNDING-MIGRATION','PAYMENT','TRANSFER',b.id,c.id,SUM(l.balance),'INR','SUCCESS','LOAN_FUNDING_MIGRATION','TX-LOAN-FUNDING-MIGRATION',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM accounts b CROSS JOIN accounts c CROSS JOIN accounts l WHERE b.account_number='NEXA-BANK-FUNDING' AND c.account_number='NEXA-LOAN-CONTROL' AND l.account_type='LOAN'
GROUP BY b.id,c.id HAVING SUM(l.balance)>0;
INSERT INTO journal_entries(transaction_id,entry_reference,entry_type,status,created_at)
SELECT id,'J-LOAN-FUNDING-MIGRATION','TRANSACTION','POSTED',CURRENT_TIMESTAMP FROM transactions WHERE id='TX-LOAN-FUNDING-MIGRATION';
INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)
SELECT j.id,t.source_account_id,'DEBIT',t.amount,CURRENT_TIMESTAMP FROM journal_entries j JOIN transactions t ON t.id=j.transaction_id WHERE t.id='TX-LOAN-FUNDING-MIGRATION';
INSERT INTO ledger_entries(journal_entry_id,account_id,entry_type,amount,created_at)
SELECT j.id,t.destination_account_id,'CREDIT',t.amount,CURRENT_TIMESTAMP FROM journal_entries j JOIN transactions t ON t.id=j.transaction_id WHERE t.id='TX-LOAN-FUNDING-MIGRATION';
UPDATE accounts SET balance=-COALESCE((SELECT amount FROM transactions WHERE id='TX-LOAN-FUNDING-MIGRATION'),0) WHERE account_number='NEXA-BANK-FUNDING';
UPDATE accounts SET balance=COALESCE((SELECT amount FROM transactions WHERE id='TX-LOAN-FUNDING-MIGRATION'),0) WHERE account_number='NEXA-LOAN-CONTROL';
