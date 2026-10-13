-- Payouts are made on one day of the month, set by an admin. A container bought on another day is
-- paid a part month first (and a part month last), in proportion to the days.

CREATE TABLE payout_settings
(
    id         SMALLINT     PRIMARY KEY DEFAULT 1,
    -- 29-31 fall on the last day of shorter months.
    payout_day SMALLINT     NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by UUID         REFERENCES users (id),
    CONSTRAINT payout_settings_single_row_chk CHECK (id = 1),
    CONSTRAINT payout_settings_day_chk CHECK (payout_day BETWEEN 1 AND 31)
);

INSERT INTO payout_settings (id, payout_day) VALUES (1, 10);

-- The days each payout covers, as a share of a whole month (a lease's last period can be a little
-- over a month when it starts on the 29th-31st), and how many payouts its holding has
-- (the tenure, plus one when the lease starts between payout days).
ALTER TABLE payout_installments
    ADD COLUMN period_start      DATE,
    ADD COLUMN period_end        DATE,
    ADD COLUMN month_share       NUMERIC(9, 6),
    ADD COLUMN installment_count INTEGER;

-- Existing schedules were whole months ending on their due date.
UPDATE payout_installments p
SET period_start      = (p.due_on - INTERVAL '1 month')::date,
    period_end        = p.due_on,
    month_share       = 1,
    installment_count = (SELECT count(*) FROM payout_installments o WHERE o.holding_id = p.holding_id);

ALTER TABLE payout_installments
    ALTER COLUMN period_start SET NOT NULL,
    ALTER COLUMN period_end SET NOT NULL,
    ALTER COLUMN month_share SET NOT NULL,
    ALTER COLUMN installment_count SET NOT NULL,
    ADD CONSTRAINT payout_installments_period_chk CHECK (period_start < period_end),
    ADD CONSTRAINT payout_installments_share_chk CHECK (month_share > 0 AND month_share <= 1.2),
    ADD CONSTRAINT payout_installments_count_chk CHECK (installment_number <= installment_count);
