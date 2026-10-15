-- =====================================================================================
-- V48: Rent is credited every hour instead of every day.
--
-- A lease starts on the hour (the first full hour after its container is assigned) and each installment - still one
-- calendar month, or a part month at each end - now carries its exact start and end instants. Each crediting run
-- credits every whole hour of an installment that has ended since credited_until, in one ledger transaction and one
-- rent_credits row (from_at to to_at). period_start / period_end stay as the dates the period runs on, for display.
--
-- Existing installments run from 00:00 UTC of their dates; credited_through (a day) becomes credited_until (an
-- instant) at 00:00 UTC of that day.
-- =====================================================================================

ALTER TABLE payout_installments
    ADD COLUMN period_start_at TIMESTAMPTZ,
    ADD COLUMN period_end_at   TIMESTAMPTZ,
    ADD COLUMN credited_until  TIMESTAMPTZ;

UPDATE payout_installments
SET period_start_at = period_start::timestamp AT TIME ZONE 'UTC',
    period_end_at   = period_end::timestamp AT TIME ZONE 'UTC',
    credited_until  = credited_through::timestamp AT TIME ZONE 'UTC';

DROP INDEX payout_installments_uncredited_idx;
ALTER TABLE payout_installments
    DROP CONSTRAINT payout_installments_credited_chk,
    DROP COLUMN credited_through,
    ALTER COLUMN period_start_at SET NOT NULL,
    ALTER COLUMN period_end_at SET NOT NULL,
    ALTER COLUMN credited_until SET NOT NULL,
    ADD CONSTRAINT payout_installments_period_at_chk CHECK (period_start_at < period_end_at),
    ADD CONSTRAINT payout_installments_credited_chk CHECK (
        credited_until BETWEEN period_start_at AND period_end_at
            AND credited_rent BETWEEN 0 AND rent_amount AND credited_capital BETWEEN 0 AND capital_amount
            AND (status = 'PAID') = (credited_until = period_end_at));
CREATE INDEX payout_installments_uncredited_idx ON payout_installments (credited_until) WHERE status = 'SCHEDULED';

-- One row per crediting run of an installment: the hours from from_at to to_at. day is the day from_at falls on.
ALTER TABLE rent_credits
    ADD COLUMN from_at TIMESTAMPTZ,
    ADD COLUMN to_at   TIMESTAMPTZ;
UPDATE rent_credits
SET from_at = day::timestamp AT TIME ZONE 'UTC',
    to_at   = (day + 1)::timestamp AT TIME ZONE 'UTC';
ALTER TABLE rent_credits
    ALTER COLUMN from_at SET NOT NULL,
    ALTER COLUMN to_at SET NOT NULL,
    DROP CONSTRAINT rent_credits_day_uk,
    ADD CONSTRAINT rent_credits_from_uk UNIQUE (installment_id, from_at),
    ADD CONSTRAINT rent_credits_hours_chk CHECK (from_at < to_at);
