-- =====================================================================================
-- V8: Referrals - codes, the (permanent) referrer of each account, effective-dated commission
-- rates for four levels, and referral earnings.
--
-- Money flow: when rent is distributed, each referred investor's gross rental share earns their
-- uplines (levels 1-4) a percentage. The platform pays it (referral expense); the investor's own
-- earnings are unaffected. Commissions are posted in the distribution's database transaction.
-- =====================================================================================

-- --------------------------------------------------------------------------- codes --
-- Created on first use. Codes are never reassigned.
CREATE TABLE referral_codes
(
    user_id    UUID PRIMARY KEY REFERENCES users (id),
    code       VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_codes_code_uk UNIQUE (code),
    CONSTRAINT referral_codes_format_chk CHECK (code ~ '^[2-9A-HJ-NP-Z]{8}$')
);
CREATE TRIGGER referral_codes_immutable
    BEFORE UPDATE OR DELETE
    ON referral_codes
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------ referrals --
-- Who referred whom. Written once, at registration, and only to an existing account, so the
-- hierarchy can never contain a cycle.
CREATE TABLE referrals
(
    user_id     UUID PRIMARY KEY REFERENCES users (id),
    referrer_id UUID        NOT NULL REFERENCES users (id),
    code        VARCHAR(12) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT referrals_not_self_chk CHECK (user_id <> referrer_id)
);
CREATE INDEX referrals_referrer_idx ON referrals (referrer_id, created_at);
CREATE TRIGGER referrals_immutable
    BEFORE UPDATE OR DELETE
    ON referrals
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ---------------------------------------------------------------------------- rates --
-- The version in force at a moment is the latest non-cancelled one whose effective_from has
-- passed. Versions may only start now or later; a version that has not yet started may be cancelled.
CREATE TABLE referral_rate_versions
(
    id             UUID PRIMARY KEY,
    effective_from TIMESTAMPTZ  NOT NULL,
    level1_percent NUMERIC(5, 3) NOT NULL,
    level2_percent NUMERIC(5, 3) NOT NULL,
    level3_percent NUMERIC(5, 3) NOT NULL,
    level4_percent NUMERIC(5, 3) NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    created_by     UUID REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    cancelled_at   TIMESTAMPTZ,
    cancelled_by   UUID REFERENCES users (id),
    CONSTRAINT referral_rate_versions_rates_chk CHECK (
        level1_percent BETWEEN 0 AND 10 AND level2_percent BETWEEN 0 AND 10
            AND level3_percent BETWEEN 0 AND 10 AND level4_percent BETWEEN 0 AND 10
            AND level1_percent + level2_percent + level3_percent + level4_percent <= 20),
    CONSTRAINT referral_rate_versions_cancelled_chk CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL))
);
CREATE UNIQUE INDEX referral_rate_versions_effective_uk ON referral_rate_versions (effective_from)
    WHERE cancelled_at IS NULL;

INSERT INTO referral_rate_versions (id, effective_from, level1_percent, level2_percent, level3_percent,
                                    level4_percent, reason, created_at)
VALUES (gen_random_uuid(), TIMESTAMPTZ '2026-01-01 00:00:00+00', 2, 1, 0.5, 0.25, 'Initial referral rates', now());

-- ------------------------------------------------------------------------- earnings --
-- One upline's commission on one referred investor's share of one rental receipt.
CREATE TABLE referral_earnings
(
    id                  UUID PRIMARY KEY,
    receipt_id          UUID           NOT NULL REFERENCES rental_receipts (id),
    source_earning_id   UUID           NOT NULL REFERENCES earnings (id),
    source_user_id      UUID           NOT NULL REFERENCES users (id),
    beneficiary_user_id UUID           NOT NULL REFERENCES users (id),
    product_id          UUID           NOT NULL REFERENCES investment_products (id),
    period_number       INT            NOT NULL,
    level               SMALLINT       NOT NULL,
    base_amount         NUMERIC(19, 4) NOT NULL,
    rate_percent        NUMERIC(5, 3)  NOT NULL,
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3)     NOT NULL,
    rate_version_id     UUID           NOT NULL REFERENCES referral_rate_versions (id),
    created_at          TIMESTAMPTZ    NOT NULL,
    CONSTRAINT referral_earnings_source_level_uk UNIQUE (source_earning_id, level),
    CONSTRAINT referral_earnings_level_chk CHECK (level BETWEEN 1 AND 4),
    CONSTRAINT referral_earnings_amount_chk CHECK (amount > 0 AND base_amount > 0),
    CONSTRAINT referral_earnings_not_self_chk CHECK (source_user_id <> beneficiary_user_id)
);
CREATE INDEX referral_earnings_beneficiary_idx ON referral_earnings (beneficiary_user_id, created_at DESC);
CREATE INDEX referral_earnings_receipt_idx ON referral_earnings (receipt_id);
CREATE TRIGGER referral_earnings_immutable
    BEFORE UPDATE OR DELETE
    ON referral_earnings
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- --------------------------------------------------------------------------- ledger --
ALTER TABLE ledger_accounts
    DROP CONSTRAINT ledger_accounts_type_chk,
    ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'PLATFORM_FEE_REVENUE', 'PLATFORM_RETAINED', 'PLATFORM_ADJUSTMENTS',
         'PLATFORM_REFERRAL_EXPENSE'));

ALTER TABLE ledger_transactions
    DROP CONSTRAINT ledger_transactions_type_chk,
    ADD CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN
        ('RENTAL_DISTRIBUTION', 'ADJUSTMENT', 'REFERRAL_COMMISSION'));
