-- Local card records only: no payment-network PAN, CVV, issuance, or money movement.
-- Existing cards remain readable and are not silently approved or reissued.
ALTER TABLE accounts ADD (
  card_request_id VARCHAR2(36),
  card_request_hash VARCHAR2(64),
  card_slot_key VARCHAR2(100),
  card_customer_block NUMBER(1) DEFAULT 0 NOT NULL
);
CREATE UNIQUE INDEX uk_card_request ON accounts(card_request_id);
CREATE UNIQUE INDEX uk_card_slot ON accounts(card_slot_key);
ALTER TABLE accounts ADD CONSTRAINT ck_card_customer_block CHECK(card_customer_block IN (0,1));
ALTER TABLE accounts ADD CONSTRAINT ck_local_card_application CHECK (
  card_request_id IS NULL OR (
    account_type='CARD' AND account_category='CUSTOMER' AND customer_id IS NOT NULL
    AND funding_account_id IS NOT NULL AND product_id IS NOT NULL
    AND card_request_hash IS NOT NULL AND currency_code='INR'
    AND product_type IN ('DEBIT','CREDIT') AND balance>=0 AND credit_limit IS NOT NULL
    AND minimum_payment IS NOT NULL AND credit_limit>=0 AND minimum_payment>=0
    AND product_status IN ('PENDING_APPROVAL','ACTIVE','BLOCKED','REJECTED','CLOSED')
    AND (product_type='CREDIT' OR credit_limit=0)
    AND (product_status NOT IN ('PENDING_APPROVAL','REJECTED') OR credit_limit=0)
    AND (product_status IN ('REJECTED','CLOSED') OR card_slot_key IS NOT NULL)
  )
);
