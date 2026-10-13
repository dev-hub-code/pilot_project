-- =====================================================================================
-- V40: Rent is credited to the investor's wallet every day.
--
-- A payout installment is still one period of a holding's lease (a month, or part of one), but its rent (and, on
-- holdings sold before plans became rent-only, capital) is now credited one day at a time: each day gets the
-- period's amount ÷ its days, so a whole calendar month is that month's rent ÷ its days per day. Each day is
-- one ledger transaction and one rent_credits row. credited_through is the first day not yet credited; once it
-- reaches period_end the installment is PAID (fully credited) and referral commission on it is paid, as before.
-- Installments already PAID are unchanged; scheduled ones are credited from their start, catching up past days.
-- =====================================================================================

ALTER TABLE payout_installments
    ADD COLUMN credited_through DATE,
    ADD COLUMN credited_rent    NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN credited_capital NUMERIC(19, 4) NOT NULL DEFAULT 0;
UPDATE payout_installments
SET credited_through = CASE WHEN status = 'PAID' THEN period_end ELSE period_start END,
    credited_rent    = CASE WHEN status = 'PAID' THEN rent_amount ELSE 0 END,
    credited_capital = CASE WHEN status = 'PAID' THEN capital_amount ELSE 0 END;
ALTER TABLE payout_installments
    ALTER COLUMN credited_through SET NOT NULL,
    ADD CONSTRAINT payout_installments_credited_chk CHECK (
        credited_through BETWEEN period_start AND period_end
            AND credited_rent BETWEEN 0 AND rent_amount AND credited_capital BETWEEN 0 AND capital_amount
            AND (status = 'PAID') = (credited_through = period_end));
CREATE INDEX payout_installments_uncredited_idx ON payout_installments (credited_through) WHERE status = 'SCHEDULED';

CREATE TABLE rent_credits
(
    id                    UUID PRIMARY KEY,
    installment_id        UUID           NOT NULL REFERENCES payout_installments (id),
    holding_id            UUID           NOT NULL REFERENCES holdings (id),
    user_id               UUID           NOT NULL REFERENCES users (id),
    day                   DATE           NOT NULL,
    rent_amount           NUMERIC(19, 4) NOT NULL,
    capital_amount        NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    ledger_transaction_id UUID           NOT NULL REFERENCES ledger_transactions (id),
    created_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT rent_credits_day_uk UNIQUE (installment_id, day),
    CONSTRAINT rent_credits_amounts_chk CHECK (rent_amount >= 0 AND capital_amount >= 0)
);
CREATE INDEX rent_credits_user_idx ON rent_credits (user_id, day DESC);
