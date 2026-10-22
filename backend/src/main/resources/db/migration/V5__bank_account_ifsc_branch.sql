-- Where an account's IFSC was found in the online directory of bank branches when it was added (null: not checked).
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS ifsc_bank VARCHAR(140);
ALTER TABLE bank_accounts ADD COLUMN IF NOT EXISTS ifsc_branch VARCHAR(400);
