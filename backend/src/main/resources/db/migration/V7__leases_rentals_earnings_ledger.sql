-- =====================================================================================
-- V7: Leases, rental receipts, earnings distribution and the double-entry ledger.
--
-- Money flow: an offering's lease is activated (FUNDED → ACTIVE) → finance records the lessee's
-- payment for one rental period → a second finance user approves it → in one transaction the
-- receipt is split across the holdings by ownership (less the management fee), one earning row
-- per holding is written and a balanced ledger transaction is posted.
-- =====================================================================================

-- --------------------------------------------------------------------- permissions --
INSERT INTO permissions (code, category, description)
VALUES ('RENTAL_RECORD', 'FINANCE', 'Record rental payments received from lessees'),
       ('RENTAL_APPROVE', 'FINANCE', 'Approve recorded rental payments for distribution to investors');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('RENTAL_RECORD', 'RENTAL_APPROVE')
WHERE r.name IN ('SUPER_ADMIN', 'FINANCE');

-- ----------------------------------------------------------- offering fee and lease --
-- The management fee is part of the offering's terms: editable in DRAFT only, so investors are
-- always paid under the fee they accepted.
ALTER TABLE investment_products
    ADD COLUMN management_fee_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN lease_starts_on        DATE,
    ADD COLUMN lease_ends_on          DATE,
    ADD COLUMN activated_at           TIMESTAMPTZ,
    ADD COLUMN activated_by           UUID REFERENCES users (id),
    ADD COLUMN matured_at             TIMESTAMPTZ,
    ADD CONSTRAINT investment_products_fee_chk CHECK (management_fee_percent BETWEEN 0 AND 50),
    ADD CONSTRAINT investment_products_lease_chk CHECK (
        (status IN ('ACTIVE', 'MATURED', 'CLOSED')) = (lease_starts_on IS NOT NULL)
            AND (lease_starts_on IS NULL) = (lease_ends_on IS NULL)
            AND (lease_ends_on IS NULL OR lease_ends_on > lease_starts_on)),
    ADD CONSTRAINT investment_products_matured_chk CHECK ((status IN ('MATURED', 'CLOSED')) = (matured_at IS NOT NULL));

-- ------------------------------------------------------------------------ ledger --
-- Double-entry bookkeeping. Each account holds one currency. Investor accounts are liabilities
-- (money owed to the investor, withdrawable from Phase 8); platform accounts are owned by no user.
CREATE TABLE ledger_accounts
(
    id            UUID PRIMARY KEY,
    account_type  VARCHAR(30) NOT NULL,
    owner_user_id UUID REFERENCES users (id),
    currency      VARCHAR(3)  NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'PLATFORM_FEE_REVENUE', 'PLATFORM_RETAINED', 'PLATFORM_ADJUSTMENTS')),
    CONSTRAINT ledger_accounts_owner_chk CHECK ((account_type = 'INVESTOR_EARNINGS') = (owner_user_id IS NOT NULL)),
    CONSTRAINT ledger_accounts_currency_chk CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE UNIQUE INDEX ledger_accounts_investor_uk ON ledger_accounts (account_type, owner_user_id, currency)
    WHERE owner_user_id IS NOT NULL;
CREATE UNIQUE INDEX ledger_accounts_platform_uk ON ledger_accounts (account_type, currency)
    WHERE owner_user_id IS NULL;
CREATE TRIGGER ledger_accounts_immutable
    BEFORE UPDATE OR DELETE
    ON ledger_accounts
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- One business event = one transaction; (transaction_type, reference) makes posting idempotent.
CREATE TABLE ledger_transactions
(
    id               UUID PRIMARY KEY,
    transaction_type VARCHAR(30)  NOT NULL,
    reference        VARCHAR(100) NOT NULL,
    currency         VARCHAR(3)   NOT NULL,
    description      VARCHAR(300) NOT NULL,
    created_by       UUID REFERENCES users (id),
    created_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN ('RENTAL_DISTRIBUTION', 'ADJUSTMENT')),
    CONSTRAINT ledger_transactions_reference_uk UNIQUE (transaction_type, reference)
);
CREATE INDEX ledger_transactions_created_idx ON ledger_transactions (created_at DESC);
CREATE TRIGGER ledger_transactions_immutable
    BEFORE UPDATE OR DELETE
    ON ledger_transactions
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

CREATE TABLE ledger_entries
(
    id             UUID PRIMARY KEY,
    transaction_id UUID           NOT NULL REFERENCES ledger_transactions (id),
    account_id     UUID           NOT NULL REFERENCES ledger_accounts (id),
    direction      VARCHAR(6)     NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ledger_entries_direction_chk CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_entries_amount_chk CHECK (amount > 0)
);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries (transaction_id);
CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id, created_at DESC);
CREATE TRIGGER ledger_entries_immutable
    BEFORE UPDATE OR DELETE
    ON ledger_entries
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- Every transaction must balance, and all its entries must be in the transaction's currency.
-- Checked at commit (deferred), once all entries of the transaction have been inserted.
CREATE FUNCTION ledger_check_balanced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    imbalance NUMERIC;
    foreign_entries INT;
BEGIN
    SELECT coalesce(sum(CASE e.direction WHEN 'DEBIT' THEN e.amount ELSE -e.amount END), 0),
           count(*) FILTER (WHERE a.currency <> t.currency)
    INTO imbalance, foreign_entries
    FROM ledger_entries e
             JOIN ledger_accounts a ON a.id = e.account_id
             JOIN ledger_transactions t ON t.id = e.transaction_id
    WHERE e.transaction_id = NEW.transaction_id;
    IF imbalance <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced by %', NEW.transaction_id, imbalance
            USING ERRCODE = 'check_violation';
    END IF;
    IF foreign_entries > 0 THEN
        RAISE EXCEPTION 'Ledger transaction % mixes currencies', NEW.transaction_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ledger_entries_balanced
    AFTER INSERT
    ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION ledger_check_balanced();

-- ---------------------------------------------------------------- rental receipts --
-- What the lessee actually paid for one rental period. RECORDED by one finance user, then
-- DISTRIBUTED (or REJECTED) by another. A period is paid at most once.
CREATE TABLE rental_receipts
(
    id                     UUID PRIMARY KEY,
    product_id             UUID           NOT NULL REFERENCES investment_products (id),
    period_number          INT            NOT NULL,
    period_starts_on       DATE           NOT NULL,
    period_ends_on         DATE           NOT NULL,
    amount                 NUMERIC(19, 4) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    expected_amount        NUMERIC(19, 4) NOT NULL,
    received_on            DATE           NOT NULL,
    external_reference     VARCHAR(100)   NOT NULL,
    note                   VARCHAR(500),
    status                 VARCHAR(20)    NOT NULL,
    recorded_by            UUID           NOT NULL REFERENCES users (id),
    decided_by             UUID REFERENCES users (id),
    decided_at             TIMESTAMPTZ,
    rejection_reason       VARCHAR(500),
    management_fee_percent NUMERIC(5, 2),
    ledger_transaction_id  UUID REFERENCES ledger_transactions (id),
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT rental_receipts_status_chk CHECK (status IN ('RECORDED', 'DISTRIBUTED', 'REJECTED')),
    CONSTRAINT rental_receipts_amount_chk CHECK (amount > 0 AND expected_amount > 0),
    CONSTRAINT rental_receipts_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT rental_receipts_period_chk CHECK (period_number > 0 AND period_ends_on > period_starts_on),
    -- Four-eyes: whoever recorded the money cannot also release it to investors (they may void it).
    CONSTRAINT rental_receipts_four_eyes_chk CHECK (status <> 'DISTRIBUTED' OR decided_by <> recorded_by),
    CONSTRAINT rental_receipts_decided_chk CHECK ((status = 'RECORDED') = (decided_at IS NULL)),
    CONSTRAINT rental_receipts_rejected_chk CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT rental_receipts_distributed_chk CHECK (
        (status = 'DISTRIBUTED') = (ledger_transaction_id IS NOT NULL AND management_fee_percent IS NOT NULL))
);
CREATE UNIQUE INDEX rental_receipts_period_uk ON rental_receipts (product_id, period_number)
    WHERE status <> 'REJECTED';
CREATE INDEX rental_receipts_status_idx ON rental_receipts (status, created_at DESC);
CREATE INDEX rental_receipts_product_idx ON rental_receipts (product_id, period_number);

-- ------------------------------------------------------------------------ earnings --
-- One investor's share of one distributed receipt. gross = receipt × holding amount ÷ price,
-- rounded down to the currency's minor unit; fee = gross × fee %; net = gross − fee.
CREATE TABLE earnings
(
    id                UUID PRIMARY KEY,
    receipt_id        UUID           NOT NULL REFERENCES rental_receipts (id),
    holding_id        UUID           NOT NULL REFERENCES holdings (id),
    user_id           UUID           NOT NULL REFERENCES users (id),
    product_id        UUID           NOT NULL REFERENCES investment_products (id),
    period_number     INT            NOT NULL,
    ownership_percent NUMERIC(9, 4)  NOT NULL,
    gross_amount      NUMERIC(19, 4) NOT NULL,
    fee_amount        NUMERIC(19, 4) NOT NULL,
    net_amount        NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT earnings_receipt_holding_uk UNIQUE (receipt_id, holding_id),
    CONSTRAINT earnings_amounts_chk CHECK (
        gross_amount >= 0 AND fee_amount >= 0 AND net_amount >= 0 AND net_amount = gross_amount - fee_amount)
);
CREATE INDEX earnings_user_idx ON earnings (user_id, created_at DESC);
CREATE INDEX earnings_holding_idx ON earnings (holding_id);
CREATE TRIGGER earnings_immutable
    BEFORE UPDATE OR DELETE
    ON earnings
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
