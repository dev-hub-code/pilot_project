-- =====================================================================================
-- V9: Withdrawals - requests, approvals, payout batches and reconciliation.
--
-- Money flow: request → the amount moves from the investor's earnings to "withdrawals in transit"
-- (so it cannot be spent twice) → approved (a second approver above a threshold) → batched per
-- currency and paid by bank file → each item is reconciled: PAID (in transit → cash) or FAILED
-- (in transit → back to the investor). Rejection and cancellation also return the money.
-- =====================================================================================

-- -------------------------------------------------------------------------- batches --
CREATE SEQUENCE withdrawal_batch_number_seq START WITH 1;

-- CREATED (file can be downloaded, batch can still be cancelled) → SENT (uploaded to the bank)
-- → CLOSED (every item reconciled); or CANCELLED before it was sent.
CREATE TABLE withdrawal_batches
(
    id           UUID PRIMARY KEY,
    reference    VARCHAR(20)    NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    item_count   INT            NOT NULL,
    total_amount NUMERIC(19, 4) NOT NULL,
    created_by   UUID           NOT NULL REFERENCES users (id),
    sent_by      UUID REFERENCES users (id),
    sent_at      TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    version      BIGINT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ    NOT NULL,
    updated_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT withdrawal_batches_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawal_batches_status_chk CHECK (status IN ('CREATED', 'SENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT withdrawal_batches_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT withdrawal_batches_totals_chk CHECK (item_count > 0 AND total_amount > 0),
    CONSTRAINT withdrawal_batches_sent_chk CHECK ((status IN ('SENT', 'CLOSED')) = (sent_at IS NOT NULL)),
    CONSTRAINT withdrawal_batches_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);
CREATE INDEX withdrawal_batches_status_idx ON withdrawal_batches (status, created_at DESC);

-- ---------------------------------------------------------------------- withdrawals --
CREATE SEQUENCE withdrawal_number_seq START WITH 100001;

CREATE TABLE withdrawals
(
    id                  UUID PRIMARY KEY,
    reference           VARCHAR(20)    NOT NULL,
    user_id             UUID           NOT NULL REFERENCES users (id),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    idempotency_key     VARCHAR(100)   NOT NULL,
    -- Where the money goes, as the investor chose it; display details are a snapshot.
    bank_account_id     UUID           NOT NULL REFERENCES bank_accounts (id),
    bank_holder_name    VARCHAR(140)   NOT NULL,
    bank_name           VARCHAR(140)   NOT NULL,
    bank_account_last4  VARCHAR(4)     NOT NULL,
    required_approvals  SMALLINT       NOT NULL,
    first_approved_by   UUID REFERENCES users (id),
    first_approved_at   TIMESTAMPTZ,
    second_approved_by  UUID REFERENCES users (id),
    second_approved_at  TIMESTAMPTZ,
    rejected_by         UUID REFERENCES users (id),
    rejection_reason    VARCHAR(500),
    batch_id            UUID REFERENCES withdrawal_batches (id),
    payout_reference    VARCHAR(100),
    failure_reason      VARCHAR(500),
    closed_at           TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT withdrawals_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawals_idempotency_uk UNIQUE (user_id, idempotency_key),
    CONSTRAINT withdrawals_status_chk CHECK (status IN
        ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT withdrawals_amount_chk CHECK (amount > 0),
    CONSTRAINT withdrawals_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT withdrawals_approvals_chk CHECK (required_approvals IN (1, 2)),
    -- Nobody approves their own withdrawal, and two approvals come from two people.
    CONSTRAINT withdrawals_not_own_chk CHECK (
        (first_approved_by IS NULL OR first_approved_by <> user_id)
            AND (second_approved_by IS NULL OR (second_approved_by <> user_id AND second_approved_by <> first_approved_by))
            AND (rejected_by IS NULL OR rejected_by <> user_id)),
    CONSTRAINT withdrawals_approved_chk CHECK (
        status IN ('PENDING_APPROVAL', 'REJECTED', 'CANCELLED')
            OR (first_approved_by IS NOT NULL AND (required_approvals = 1 OR second_approved_by IS NOT NULL))),
    CONSTRAINT withdrawals_batched_chk CHECK ((status IN ('BATCHED', 'PROCESSING', 'PAID', 'FAILED')) = (batch_id IS NOT NULL)),
    CONSTRAINT withdrawals_rejected_chk CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT withdrawals_failed_chk CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT withdrawals_closed_chk CHECK ((status IN ('PAID', 'FAILED', 'REJECTED', 'CANCELLED')) = (closed_at IS NOT NULL))
);
-- One open withdrawal per investor and currency.
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals (user_id, currency)
    WHERE status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING');
CREATE INDEX withdrawals_user_idx ON withdrawals (user_id, created_at DESC);
CREATE INDEX withdrawals_status_idx ON withdrawals (status, currency, created_at);
CREATE INDEX withdrawals_batch_idx ON withdrawals (batch_id);

-- --------------------------------------------------------------------------- ledger --
ALTER TABLE ledger_accounts
    DROP CONSTRAINT ledger_accounts_type_chk,
    ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'PLATFORM_FEE_REVENUE', 'PLATFORM_RETAINED', 'PLATFORM_ADJUSTMENTS',
         'PLATFORM_REFERRAL_EXPENSE', 'WITHDRAWALS_IN_TRANSIT'));

ALTER TABLE ledger_transactions
    DROP CONSTRAINT ledger_transactions_type_chk,
    ADD CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN
        ('RENTAL_DISTRIBUTION', 'ADJUSTMENT', 'REFERRAL_COMMISSION', 'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE',
         'WITHDRAWAL_PAYOUT'));
