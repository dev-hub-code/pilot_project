-- =====================================================================================
-- V47: Early withdrawal files.
--
-- Finance may make a rent or referral file before the withdrawal day: every eligible investor's balance up to today
-- is withdrawn into it. Unlike the monthly run, an early run does not use up the month: the monthly run still
-- happens on its day and withdraws only what was credited after the early file. So only monthly runs are unique per
-- month; early runs record the day they ran up to.
-- =====================================================================================

ALTER TABLE scheduled_withdrawal_runs
    ADD COLUMN early BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN until_date DATE,
    ADD CONSTRAINT scheduled_withdrawal_runs_until_chk CHECK (early = (until_date IS NOT NULL)),
    DROP CONSTRAINT scheduled_withdrawal_runs_period_uk;

CREATE UNIQUE INDEX scheduled_withdrawal_runs_period_uk ON scheduled_withdrawal_runs (period, currency, kind)
    WHERE NOT early;
