-- =====================================================================================
-- V56: Rent and referral are withdrawn by separate runs.
--
-- An admin generates rent withdrawals (rent paid by bank and rent collected in cash) and referral withdrawals
-- separately, each run with its own bank and cash sheets. Runs made before took both (kind is null).
-- =====================================================================================
ALTER TABLE withdrawal_runs
    ADD COLUMN kind VARCHAR(20),
    ADD CONSTRAINT withdrawal_runs_kind_chk CHECK (kind IS NULL OR kind IN ('RENT', 'REFERRAL'));
