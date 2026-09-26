-- Retire only the original, explicitly identified local-demo login.
-- Both the historical subject and email must match; a same-name customer or
-- an ordinary customer using this email is not a match. Fresh installs are a no-op.
-- Do not delete accounts, money, journals, loan records or audit/conversation history.
-- Access-token revocation is enforced separately by runtime active-user checks.

UPDATE customer_credentials
SET password_hash=NULL
WHERE credential_type='PASSWORD'
  AND user_id='usr_01JDEMO000000000000001'
  AND EXISTS (
      SELECT 1 FROM customers c
      WHERE c.user_id=customer_credentials.user_id
        AND LOWER(c.email)='vishal@example.com'
  );

UPDATE customer_credentials
SET revoked_at=COALESCE(revoked_at,SYSTIMESTAMP)
WHERE credential_type='REFRESH'
  AND user_id='usr_01JDEMO000000000000001'
  AND EXISTS (
      SELECT 1 FROM customers c
      WHERE c.user_id=customer_credentials.user_id
        AND LOWER(c.email)='vishal@example.com'
  );

-- Increment optimistic-lock versions when an active account is blocked.
-- Closed/already-blocked accounts and every monetary value remain unchanged.
UPDATE accounts
SET status='BLOCKED', version=version+1, updated_at=SYS_EXTRACT_UTC(SYSTIMESTAMP)
WHERE status='ACTIVE'
  AND account_category='CUSTOMER'
  AND customer_id IN (
      SELECT id FROM customers
      WHERE user_id='usr_01JDEMO000000000000001'
        AND LOWER(email)='vishal@example.com'
  );

UPDATE customers
SET status='DISABLED', updated_at=SYS_EXTRACT_UTC(SYSTIMESTAMP)
WHERE user_id='usr_01JDEMO000000000000001'
  AND LOWER(email)='vishal@example.com'
  AND status<>'DISABLED';
