-- =====================================================================================
-- V50: Withdrawal runs made by an admin, with a bank sheet and a cash sheet.
--
-- The monthly rent and referral runs (and their withdrawal days) are replaced by runs an admin makes whenever they
-- choose: every eligible investor's rent and referral balances are withdrawn ("in progress") into two batches - one
-- paid by bank transfer, one paid in cash at the office - each with its Excel sheet. Finance pays them and uploads
-- the completed sheets, which completes each withdrawal ("withdrawn").
--
-- Cash at the office is now paid from the cash sheet too: an office cash withdrawal made by a run is batched like a
-- bank one. Withdrawals made one by one at the office before this keep their rules.
-- =====================================================================================

-- ----------------------------------------------------------------------- batches --
ALTER TABLE withdrawal_batches
    ADD COLUMN payout_method VARCHAR(20) NOT NULL DEFAULT 'BANK_TRANSFER',
    ADD CONSTRAINT withdrawal_batches_payout_method_chk CHECK (payout_method IN ('BANK_TRANSFER', 'OFFICE_CASH'));

-- ------------------------------------------------------------------- withdrawals --
ALTER TABLE withdrawals
    DROP CONSTRAINT withdrawals_cash_origin_chk,
    ADD CONSTRAINT withdrawals_cash_origin_chk CHECK (payout_method = 'BANK_TRANSFER' OR origin IN ('INVESTOR', 'OFFICE', 'SCHEDULED')),
    DROP CONSTRAINT withdrawals_batched_chk,
    ADD CONSTRAINT withdrawals_batched_chk CHECK (
        ((payout_method = 'BANK_TRANSFER' OR origin = 'SCHEDULED')
            AND (status IN ('BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'RETURNED')) = (batch_id IS NOT NULL))
            OR (payout_method = 'OFFICE_CASH' AND origin <> 'SCHEDULED' AND batch_id IS NULL
                AND status IN ('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED')));

-- -------------------------------------------------------------------------- runs --
-- One row per run. Investors with a balance who were left out (and why) are kept for finance to follow up.
CREATE TABLE withdrawal_runs
(
    id             UUID PRIMARY KEY,
    reference      VARCHAR(20)    NOT NULL,
    currency       VARCHAR(3)     NOT NULL,
    run_at         TIMESTAMPTZ    NOT NULL,
    triggered_by   UUID           NOT NULL REFERENCES users (id),
    bank_batch_id  UUID REFERENCES withdrawal_batches (id),
    cash_batch_id  UUID REFERENCES withdrawal_batches (id),
    bank_count     INT            NOT NULL DEFAULT 0,
    bank_total     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    cash_count     INT            NOT NULL DEFAULT 0,
    cash_total     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    skipped        JSONB          NOT NULL DEFAULT '[]',
    CONSTRAINT withdrawal_runs_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawal_runs_counts_chk CHECK (bank_count >= 0 AND cash_count >= 0
        AND (bank_count > 0) = (bank_batch_id IS NOT NULL) AND (cash_count > 0) = (cash_batch_id IS NOT NULL))
);
CREATE INDEX withdrawal_runs_run_at_idx ON withdrawal_runs (run_at DESC);
CREATE SEQUENCE withdrawal_run_number_seq START 1;

-- The monthly schedule and its days are gone.
DROP TABLE scheduled_withdrawal_runs;
DROP TABLE withdrawal_settings;
