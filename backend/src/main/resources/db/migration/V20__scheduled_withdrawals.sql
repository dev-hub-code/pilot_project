-- =====================================================================================
-- V20: Scheduled monthly withdrawals.
--
-- On the withdrawal day (set by an admin) a scheduler withdraws every eligible investor's earnings
-- balance: one auto-approved withdrawal each, all in one batch for the month, whose bank file is
-- generated and stored (encrypted). Finance pays the file by hand and uploads it back with a status
-- per row, which completes or fails each withdrawal.
-- =====================================================================================

CREATE TABLE withdrawal_settings
(
    id             SMALLINT    PRIMARY KEY DEFAULT 1,
    -- 29-31 fall on the last day of shorter months.
    withdrawal_day SMALLINT    NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     UUID        REFERENCES users (id),
    CONSTRAINT withdrawal_settings_single_row_chk CHECK (id = 1),
    CONSTRAINT withdrawal_settings_day_chk CHECK (withdrawal_day BETWEEN 1 AND 31)
);

INSERT INTO withdrawal_settings (id, withdrawal_day) VALUES (1, 15);

-- ---------------------------------------------------------------------- withdrawals --
-- Scheduled withdrawals are approved by the schedule itself, so they carry no approver.
ALTER TABLE withdrawals
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'INVESTOR',
    ADD CONSTRAINT withdrawals_origin_chk CHECK (origin IN ('INVESTOR', 'SCHEDULED')),
    DROP CONSTRAINT withdrawals_approved_chk,
    ADD CONSTRAINT withdrawals_approved_chk CHECK (
        status IN ('PENDING_APPROVAL', 'REJECTED', 'CANCELLED')
            OR origin = 'SCHEDULED'
            OR (first_approved_by IS NOT NULL AND (required_approvals = 1 OR second_approved_by IS NOT NULL)));

-- ------------------------------------------------------------------------- batches --
-- A scheduled batch is created by the system (no user) for one month.
ALTER TABLE withdrawal_batches
    ALTER COLUMN created_by DROP NOT NULL,
    ADD COLUMN period VARCHAR(7),
    ADD CONSTRAINT withdrawal_batches_period_chk CHECK (period IS NULL OR period ~ '^[0-9]{4}-[0-9]{2}$');

-- ---------------------------------------------------------------------------- runs --
-- One row per month and currency: claimed before the run starts, so it happens once even with
-- several instances. Investors left out (and why) are kept for finance to follow up.
CREATE TABLE scheduled_withdrawal_runs
(
    id               UUID PRIMARY KEY,
    period           VARCHAR(7)     NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    run_at           TIMESTAMPTZ    NOT NULL,
    triggered_by     UUID REFERENCES users (id),
    batch_id         UUID REFERENCES withdrawal_batches (id),
    withdrawal_count INT            NOT NULL DEFAULT 0,
    total_amount     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    skipped          JSONB          NOT NULL DEFAULT '[]',
    CONSTRAINT scheduled_withdrawal_runs_period_uk UNIQUE (period, currency),
    CONSTRAINT scheduled_withdrawal_runs_period_chk CHECK (period ~ '^[0-9]{4}-[0-9]{2}$')
);

-- --------------------------------------------------------------------------- files --
-- The bank file a batch was paid with (one per batch) and every reconciliation file uploaded
-- for it. Both hold full account numbers, so the content is encrypted.
CREATE TABLE withdrawal_batch_files
(
    id                UUID PRIMARY KEY,
    batch_id          UUID         NOT NULL REFERENCES withdrawal_batches (id),
    kind              VARCHAR(20)  NOT NULL,
    filename          VARCHAR(200) NOT NULL,
    content_encrypted TEXT         NOT NULL,
    size_bytes        INT          NOT NULL,
    uploaded_by       UUID REFERENCES users (id),
    paid_count        INT,
    failed_count      INT,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT withdrawal_batch_files_kind_chk CHECK (kind IN ('PAYMENT', 'RECONCILIATION'))
);
CREATE UNIQUE INDEX withdrawal_batch_files_payment_uk ON withdrawal_batch_files (batch_id) WHERE kind = 'PAYMENT';
CREATE INDEX withdrawal_batch_files_batch_idx ON withdrawal_batch_files (batch_id, created_at);
