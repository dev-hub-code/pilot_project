-- =====================================================================================
-- V41: Withdrawals follow a fixed monthly schedule; investors no longer request them.
--
--   Rent (bank)      the day before the rent withdrawal day (default the 10th) an Excel file of every investor's
--                    rent balance is made for finance, who pays it by hand on the day and uploads it back.
--   Referral (bank)  likewise the day before the referral withdrawal day (default the 15th), for investors whose
--                    referral payout mode this year is BANK.
--   Cash at office   rent on containers paid in cash, and referral commission of investors whose mode is CASH, is
--                    withdrawn by finance at the office in front of the investor (origin OFFICE), on or after the
--                    withdrawal day, and handed over against a receipt.
--
-- A failed bank payment is now held: the withdrawal stays FAILED (still open, the money still in transit) with a
-- support ticket for finance, until finance marks it PAID (paid by hand) or RETURNED (back to the balance).
-- Withdrawals that failed before this already had their money returned, so they become RETURNED.
-- =====================================================================================

-- ------------------------------------------------------------------------ settings --
ALTER TABLE withdrawal_settings RENAME COLUMN withdrawal_day TO rent_withdrawal_day;
ALTER TABLE withdrawal_settings
    ADD COLUMN referral_withdrawal_day SMALLINT NOT NULL DEFAULT 15,
    DROP CONSTRAINT withdrawal_settings_day_chk;
UPDATE withdrawal_settings SET rent_withdrawal_day = 10, referral_withdrawal_day = 15;
-- The file is made the day before, so a withdrawal day is never the 1st; 28 exists in every month.
ALTER TABLE withdrawal_settings
    ADD CONSTRAINT withdrawal_settings_days_chk CHECK (rent_withdrawal_day BETWEEN 2 AND 28
        AND referral_withdrawal_day BETWEEN 2 AND 28);

-- ---------------------------------------------------------------- referral mode --
-- How an investor's referral commission is withdrawn, chosen once per calendar year.
CREATE TABLE referral_payout_modes
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    year       SMALLINT    NOT NULL,
    mode       VARCHAR(20) NOT NULL,
    chosen_by  UUID        NOT NULL REFERENCES users (id),
    chosen_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_payout_modes_user_year_uk UNIQUE (user_id, year),
    CONSTRAINT referral_payout_modes_mode_chk CHECK (mode IN ('BANK_TRANSFER', 'OFFICE_CASH'))
);

-- --------------------------------------------------------------------- runs, batches --
ALTER TABLE scheduled_withdrawal_runs
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'RENT',
    ADD CONSTRAINT scheduled_withdrawal_runs_kind_chk CHECK (kind IN ('RENT', 'REFERRAL')),
    DROP CONSTRAINT scheduled_withdrawal_runs_period_uk,
    ADD CONSTRAINT scheduled_withdrawal_runs_period_uk UNIQUE (period, currency, kind);
ALTER TABLE scheduled_withdrawal_runs ALTER COLUMN kind DROP DEFAULT;

ALTER TABLE withdrawal_batches
    ADD COLUMN kind VARCHAR(20),
    ADD CONSTRAINT withdrawal_batches_kind_chk CHECK (kind IS NULL OR kind IN ('RENT', 'REFERRAL'));
UPDATE withdrawal_batches SET kind = 'RENT' WHERE period IS NOT NULL;

-- ------------------------------------------------------------------------ withdrawals --
UPDATE withdrawals SET status = 'RETURNED' WHERE status = 'FAILED';

ALTER TABLE withdrawals
    ADD COLUMN created_by UUID REFERENCES users (id),
    ADD COLUMN ticket_id  UUID REFERENCES support_tickets (id),
    DROP CONSTRAINT withdrawals_status_chk,
    ADD CONSTRAINT withdrawals_status_chk CHECK (status IN
        ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'RETURNED', 'REJECTED', 'CANCELLED')),
    DROP CONSTRAINT withdrawals_origin_chk,
    ADD CONSTRAINT withdrawals_origin_chk CHECK (origin IN ('INVESTOR', 'SCHEDULED', 'OFFICE')),
    -- Made at the office by a finance user, in front of the investor.
    ADD CONSTRAINT withdrawals_office_chk CHECK ((origin = 'OFFICE') = (created_by IS NOT NULL)
        AND (origin <> 'OFFICE' OR payout_method = 'OFFICE_CASH')),
    DROP CONSTRAINT withdrawals_cash_origin_chk,
    ADD CONSTRAINT withdrawals_cash_origin_chk CHECK (payout_method = 'BANK_TRANSFER' OR origin IN ('INVESTOR', 'OFFICE')),
    DROP CONSTRAINT withdrawals_approved_chk,
    ADD CONSTRAINT withdrawals_approved_chk CHECK (
        status IN ('PENDING_APPROVAL', 'REJECTED', 'CANCELLED')
            OR origin IN ('SCHEDULED', 'OFFICE')
            OR (first_approved_by IS NOT NULL AND (required_approvals = 1 OR second_approved_by IS NOT NULL))),
    DROP CONSTRAINT withdrawals_batched_chk,
    ADD CONSTRAINT withdrawals_batched_chk CHECK (
        (payout_method = 'BANK_TRANSFER'
            AND (status IN ('BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'RETURNED')) = (batch_id IS NOT NULL))
            OR (payout_method = 'OFFICE_CASH' AND batch_id IS NULL
                AND status IN ('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED'))),
    -- A failure reason stays with a failed withdrawal after it is returned.
    DROP CONSTRAINT withdrawals_failed_chk,
    ADD CONSTRAINT withdrawals_failed_chk CHECK ((status IN ('FAILED', 'RETURNED')) = (failure_reason IS NOT NULL)),
    -- FAILED is open now: its money is held until finance resolves it.
    DROP CONSTRAINT withdrawals_closed_chk,
    ADD CONSTRAINT withdrawals_closed_chk CHECK (
        (status IN ('PAID', 'RETURNED', 'REJECTED', 'CANCELLED')) = (closed_at IS NOT NULL));

DROP INDEX withdrawals_one_open_uk;
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals (user_id, currency, balance)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'FAILED');

-- ------------------------------------------------------------------------- finance --
-- Failed bank payments become support tickets assigned to finance, so finance works tickets.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('SUPPORT_TICKET_VIEW', 'SUPPORT_TICKET_MANAGE')
WHERE r.name = 'FINANCE'
ON CONFLICT DO NOTHING;
