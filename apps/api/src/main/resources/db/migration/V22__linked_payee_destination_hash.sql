-- Oracle indexes (owner,NULL) for a payee: every linked destination must have its own key.
-- Preserve all payees and their IDs; align existing links with the application's canonical hash.
UPDATE transactions SET destination_hash=LOWER(RAWTOHEX(STANDARD_HASH('NEXA:'||TO_CHAR(destination_account_id),'SHA256')))
WHERE record_kind='BENEFICIARY' AND destination_account_id IS NOT NULL;
