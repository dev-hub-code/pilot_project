-- IFSC codes are no longer looked up in the online directory of bank branches, so where one was found is not kept.
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS ifsc_bank;
ALTER TABLE bank_accounts DROP COLUMN IF EXISTS ifsc_branch;
