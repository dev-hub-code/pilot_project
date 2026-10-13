-- A rejected bank account is hidden from its owner, who is asked to add it again with the right details,
-- so only accounts in use (waiting for verification or verified) block adding the same account again.
DROP INDEX bank_accounts_no_duplicates_uk;
CREATE UNIQUE INDEX bank_accounts_no_duplicates_uk ON bank_accounts (user_id, account_fingerprint)
    WHERE status IN ('PENDING_VERIFICATION', 'VERIFIED');
