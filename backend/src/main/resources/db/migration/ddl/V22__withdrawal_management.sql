-- Withdrawal management: online investors' requests are reviewed before payment, with charges and tax worked out by
-- the backend, a timeline of every status change, internal notes, and payout-provider webhooks recorded once each.
-- Additive: existing withdrawals keep their status; their net amount is their amount (no charges were taken).

-- ------------------------------------------------------------------------------------------- withdrawals
ALTER TABLE withdrawals
    ADD COLUMN fee_amount numeric(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN tax_amount numeric(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN net_amount numeric(19,4),
    ADD COLUMN review_assignee_id uuid,
    ADD COLUMN review_assigned_at timestamp with time zone,
    ADD COLUMN hold_reason character varying(500),
    ADD COLUMN info_requested boolean NOT NULL DEFAULT FALSE,
    ADD COLUMN status_changed_at timestamp with time zone,
    ADD COLUMN payment_initiated_by uuid,
    ADD COLUMN payment_initiated_at timestamp with time zone,
    ADD COLUMN payout_provider character varying(30);

UPDATE withdrawals SET net_amount = amount, status_changed_at = coalesce(closed_at, updated_at);
ALTER TABLE withdrawals ALTER COLUMN net_amount SET NOT NULL;
ALTER TABLE withdrawals ALTER COLUMN status_changed_at SET NOT NULL;

ALTER TABLE withdrawals
    ADD CONSTRAINT withdrawals_charges_chk CHECK (fee_amount >= 0 AND tax_amount >= 0 AND net_amount > 0
        AND net_amount = amount - fee_amount - tax_amount),
    ADD CONSTRAINT withdrawals_review_assignment_chk CHECK ((review_assignee_id IS NULL) = (review_assigned_at IS NULL)),
    ADD CONSTRAINT withdrawals_hold_chk CHECK ((status <> 'ON_HOLD' OR hold_reason IS NOT NULL)
        AND (NOT info_requested OR status = 'ON_HOLD')),
    ADD CONSTRAINT withdrawals_payment_initiated_chk CHECK ((payment_initiated_by IS NULL) = (payment_initiated_at IS NULL)),
    ADD CONSTRAINT withdrawals_review_not_own_chk CHECK (review_assignee_id IS NULL OR review_assignee_id <> user_id),
    ADD CONSTRAINT withdrawals_review_assignee_fkey FOREIGN KEY (review_assignee_id) REFERENCES users (id),
    ADD CONSTRAINT withdrawals_payment_initiated_by_fkey FOREIGN KEY (payment_initiated_by) REFERENCES users (id);

-- New statuses: SUBMITTED (waiting for a reviewer), UNDER_REVIEW and ON_HOLD.
ALTER TABLE withdrawals DROP CONSTRAINT withdrawals_status_chk;
ALTER TABLE withdrawals ADD CONSTRAINT withdrawals_status_chk CHECK (status IN
    ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'PAID', 'FAILED',
     'RETURNED', 'REJECTED', 'CANCELLED'));

-- One withdrawal in progress per investor, currency and wallet: now including the review statuses.
DROP INDEX withdrawals_one_open_uk;
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals (user_id, currency, balance) WHERE status IN
    ('SUBMITTED', 'UNDER_REVIEW', 'ON_HOLD', 'PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'FAILED');
CREATE INDEX withdrawals_review_assignee_idx ON withdrawals (review_assignee_id) WHERE review_assignee_id IS NOT NULL;

-- ------------------------------------------------------------------------------------------- timeline
-- Every status change, who made it and why. Immutable. visible_to_investor: shown on the investor's tracking page.
CREATE TABLE withdrawal_events (
    id uuid NOT NULL,
    withdrawal_id uuid NOT NULL,
    from_status character varying(20),
    to_status character varying(20) NOT NULL,
    actor_id uuid,
    reason character varying(1000),
    visible_to_investor boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT withdrawal_events_pkey PRIMARY KEY (id),
    CONSTRAINT withdrawal_events_withdrawal_fkey FOREIGN KEY (withdrawal_id) REFERENCES withdrawals (id),
    CONSTRAINT withdrawal_events_actor_fkey FOREIGN KEY (actor_id) REFERENCES users (id)
);
CREATE INDEX withdrawal_events_withdrawal_idx ON withdrawal_events (withdrawal_id, created_at);
CREATE TRIGGER withdrawal_events_immutable BEFORE DELETE OR UPDATE ON withdrawal_events
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Existing withdrawals start their timeline where they stand.
INSERT INTO withdrawal_events (id, withdrawal_id, from_status, to_status, actor_id, reason, visible_to_investor, created_at)
SELECT gen_random_uuid(), id, NULL, status, NULL, 'Recorded before withdrawal tracking', TRUE, status_changed_at
FROM withdrawals;

-- ------------------------------------------------------------------------------------------- notes
-- Staff's internal notes on a withdrawal. Never shown to the investor. Immutable.
CREATE TABLE withdrawal_notes (
    id uuid NOT NULL,
    withdrawal_id uuid NOT NULL,
    author_id uuid NOT NULL,
    body character varying(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT withdrawal_notes_pkey PRIMARY KEY (id),
    CONSTRAINT withdrawal_notes_withdrawal_fkey FOREIGN KEY (withdrawal_id) REFERENCES withdrawals (id),
    CONSTRAINT withdrawal_notes_author_fkey FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT withdrawal_notes_body_chk CHECK (length(btrim(body)) > 0)
);
CREATE INDEX withdrawal_notes_withdrawal_idx ON withdrawal_notes (withdrawal_id, created_at);
CREATE TRIGGER withdrawal_notes_immutable BEFORE DELETE OR UPDATE ON withdrawal_notes
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------------------------- payout webhooks
-- Each provider event is recorded once (provider, event id), so a redelivered webhook changes nothing.
CREATE TABLE payout_webhook_events (
    id uuid NOT NULL,
    provider character varying(30) NOT NULL,
    event_id character varying(100) NOT NULL,
    event_type character varying(60) NOT NULL,
    withdrawal_id uuid,
    provider_reference character varying(100),
    outcome character varying(30) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamp with time zone NOT NULL,
    CONSTRAINT payout_webhook_events_pkey PRIMARY KEY (id),
    CONSTRAINT payout_webhook_events_uk UNIQUE (provider, event_id),
    CONSTRAINT payout_webhook_events_withdrawal_fkey FOREIGN KEY (withdrawal_id) REFERENCES withdrawals (id)
);
CREATE INDEX payout_webhook_events_withdrawal_idx ON payout_webhook_events (withdrawal_id) WHERE withdrawal_id IS NOT NULL;

-- ------------------------------------------------------------------------------------------- settings
-- Charges and tax withheld (all zero until finance sets them), the amount above which a second approver is needed,
-- and the processing time shown to investors. NULL threshold: the configured app.withdrawals.dual-approval-threshold.
ALTER TABLE withdrawal_settings
    ADD COLUMN fee_flat numeric(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN fee_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN tax_percent numeric(7,4) NOT NULL DEFAULT 0,
    ADD COLUMN dual_approval_threshold numeric(19,4),
    ADD COLUMN processing_days smallint;
ALTER TABLE withdrawal_settings
    ADD CONSTRAINT withdrawal_settings_fees_chk CHECK (fee_flat >= 0 AND fee_percent >= 0 AND fee_percent < 100
        AND tax_percent >= 0 AND tax_percent < 100),
    ADD CONSTRAINT withdrawal_settings_threshold_chk CHECK (dual_approval_threshold IS NULL OR dual_approval_threshold > 0),
    ADD CONSTRAINT withdrawal_settings_days_chk CHECK (processing_days IS NULL OR processing_days BETWEEN 1 AND 60);

-- ------------------------------------------------------------------------------------------- ledger
-- Charges kept by the company, and tax withheld from withdrawals (owed to the tax authority).
ALTER TABLE ledger_accounts DROP CONSTRAINT ledger_accounts_type_chk;
ALTER TABLE ledger_accounts ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
    ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS', 'PLATFORM_ADJUSTMENTS',
     'PLATFORM_REFERRAL_EXPENSE', 'WITHDRAWALS_IN_TRANSIT', 'PLATFORM_RENT_EXPENSE', 'PLATFORM_CAPITAL_RETURNS',
     'WITHDRAWAL_FEE_INCOME', 'TAX_WITHHELD'));
