-- =====================================================================================
-- V49: Referral commission is credited every hour, with the rent it is earned on.
--
-- Each crediting run of a referred investor's rent (a rent_credits row) pays its uplines the commission on those
-- hours, so an installment now has many commissions per level: one per credit. Commissions credited at month end
-- before this have no credit (source_credit_id is null) and keep their (installment, level) uniqueness.
-- =====================================================================================

ALTER TABLE referral_earnings
    ADD COLUMN source_credit_id UUID REFERENCES rent_credits (id),
    DROP CONSTRAINT referral_earnings_source_level_uk;

CREATE UNIQUE INDEX referral_earnings_credit_level_uk ON referral_earnings (source_credit_id, level)
    WHERE source_credit_id IS NOT NULL;
CREATE UNIQUE INDEX referral_earnings_source_level_uk ON referral_earnings (source_installment_id, level)
    WHERE source_credit_id IS NULL;
CREATE INDEX referral_earnings_installment_idx ON referral_earnings (source_installment_id, level, beneficiary_user_id);
