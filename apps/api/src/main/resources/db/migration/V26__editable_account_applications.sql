-- Preserve applied account-opening history and permit customer corrections before review.
-- Existing applications, identities, receipts, balances and prior audit rows are unchanged.
ALTER TABLE application_events DROP CONSTRAINT ck_event_type;
ALTER TABLE application_events ADD CONSTRAINT ck_event_type CHECK (
    event_type IN ('APPLICATION_CREATED','SUBMITTED','CHANGES_REQUESTED',
        'APPROVED','REJECTED','CANCELLED','DOCUMENT_ADDED','DOCUMENT_REVIEWED',
        'CASH_RECEIVED','ACCOUNT_OPENED','REFUND_REQUESTED','CASH_REFUNDED',
        'IDENTITY_UPDATED','APPLICATION_UPDATED')
);
ALTER TABLE application_events ADD CONSTRAINT ck_event_details_update CHECK (
    event_type<>'APPLICATION_UPDATED' OR (actor_role='CUSTOMER'
        AND from_status IN ('DRAFT','CHANGES_REQUESTED') AND to_status=from_status
        AND document_id IS NULL AND receipt_id IS NULL)
);