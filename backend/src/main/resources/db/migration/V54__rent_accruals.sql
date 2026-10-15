-- =====================================================================================
-- V54: Rent accrues per lease, not per monthly installment.
--
-- Withdrawals are made whenever an admin runs them, so monthly payouts mean nothing any more. Each holding now has
-- one rent accrual: its lease (start to end, on the hour), its monthly rent and capital to return, and how far it has
-- been credited. Rent owed up to an instant is the monthly rent × lease months elapsed (the current month counted by
-- its hours), so hourly credits add up to exactly rent × tenure at the lease end.
--
-- The installments are folded into the accruals and dropped, with the payout day they were scheduled by.
-- Commissions keep the holding their rent came from instead of the installment.
-- =====================================================================================

CREATE TABLE rent_accruals
(
    id               UUID PRIMARY KEY,
    holding_id       UUID           NOT NULL REFERENCES holdings (id),
    user_id          UUID           NOT NULL REFERENCES users (id),
    product_id       UUID           NOT NULL REFERENCES investment_products (id),
    currency         VARCHAR(3)     NOT NULL,
    monthly_rent     NUMERIC(19, 4) NOT NULL,
    capital_total    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    tenure_months    INT            NOT NULL,
    starts_at        TIMESTAMPTZ    NOT NULL,
    ends_at          TIMESTAMPTZ    NOT NULL,
    credited_until   TIMESTAMPTZ    NOT NULL,
    credited_rent    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credited_capital NUMERIC(19, 4) NOT NULL DEFAULT 0,
    completed_at     TIMESTAMPTZ,
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT rent_accruals_holding_uk UNIQUE (holding_id),
    CONSTRAINT rent_accruals_amounts_chk CHECK (monthly_rent >= 0 AND capital_total >= 0 AND tenure_months > 0
        AND monthly_rent + capital_total > 0),
    CONSTRAINT rent_accruals_lease_chk CHECK (starts_at < ends_at),
    CONSTRAINT rent_accruals_credited_chk CHECK (credited_until >= starts_at AND credited_until <= ends_at
        AND credited_rent >= 0 AND credited_rent <= monthly_rent * tenure_months
        AND credited_capital >= 0 AND credited_capital <= capital_total
        AND (completed_at IS NOT NULL) = (credited_until = ends_at))
);
CREATE INDEX rent_accruals_running_idx ON rent_accruals (credited_until) WHERE completed_at IS NULL;
CREATE INDEX rent_accruals_user_idx ON rent_accruals (user_id, starts_at);

-- One accrual per holding from its installments: the lease is their periods end to end, credited up to the first
-- month not yet fully credited.
INSERT INTO rent_accruals (id, holding_id, user_id, product_id, currency, monthly_rent, capital_total, tenure_months,
                           starts_at, ends_at, credited_until, credited_rent, credited_capital, completed_at,
                           created_at, updated_at)
SELECT gen_random_uuid(), p.holding_id, h.user_id, h.product_id, h.currency,
       sum(p.rent_amount) / h.tenure_months, sum(p.capital_amount), h.tenure_months,
       min(p.period_start_at), max(p.period_end_at),
       coalesce(min(p.credited_until) FILTER (WHERE p.status = 'SCHEDULED'), max(p.period_end_at)),
       sum(p.credited_rent), sum(p.credited_capital),
       CASE WHEN bool_and(p.status = 'PAID') THEN max(p.paid_at) END,
       min(p.created_at), now()
FROM payout_installments p
         JOIN holdings h ON h.id = p.holding_id
GROUP BY p.holding_id, h.user_id, h.product_id, h.currency, h.tenure_months;

-- ------------------------------------------------------------------ rent credits --
-- A credit is a run of hours of one holding's lease (within one calendar month).
ALTER TABLE rent_credits
    DROP CONSTRAINT rent_credits_from_uk,
    DROP COLUMN installment_id,
    ADD CONSTRAINT rent_credits_from_uk UNIQUE (holding_id, from_at);
CREATE INDEX rent_credits_holding_idx ON rent_credits (holding_id, from_at);

-- ------------------------------------------------------------- referral earnings --
ALTER TABLE referral_earnings ADD COLUMN source_holding_id UUID REFERENCES holdings (id);
ALTER TABLE referral_earnings DISABLE TRIGGER referral_earnings_immutable;
UPDATE referral_earnings e
SET source_holding_id = p.holding_id
FROM payout_installments p
WHERE p.id = e.source_installment_id;
ALTER TABLE referral_earnings ENABLE TRIGGER referral_earnings_immutable;

DROP INDEX referral_earnings_source_level_uk;
DROP INDEX referral_earnings_installment_idx;
ALTER TABLE referral_earnings
    ALTER COLUMN source_holding_id SET NOT NULL,
    DROP COLUMN source_installment_id,
    DROP COLUMN installment_number;
CREATE INDEX referral_earnings_holding_idx ON referral_earnings (source_holding_id, level, beneficiary_user_id);

-- ------------------------------------------------------------------------ gone --
DROP TABLE payout_installments;
DROP TABLE payout_settings;
