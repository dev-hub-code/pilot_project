-- An investor changes a verified payout account by submitting a replacement. The old account keeps receiving
-- payouts until finance verifies the new one; verification then retires the old account and moves primary over.
ALTER TABLE bank_accounts
    ADD COLUMN replaces_account_id UUID REFERENCES bank_accounts (id),
    ADD CONSTRAINT bank_accounts_replaces_chk CHECK (replaces_account_id IS NULL OR replaces_account_id <> id);

-- At most one open change request per account.
CREATE UNIQUE INDEX bank_accounts_one_open_change_uk ON bank_accounts (replaces_account_id)
    WHERE status = 'PENDING_VERIFICATION';
