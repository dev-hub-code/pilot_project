-- =====================================================================================
-- V16: Investment plans with allocated containers and a fixed monthly payout schedule.
--
-- An investor buys whole containers under a plan (container type, price per container, monthly
-- rent %). Containers of the plan's type are reserved at checkout and allocated to the investor,
-- by container number, once payment succeeds. Each allocated container is leased for the plan's
-- tenure (16 months); every month the investor is paid rent (the plan's monthly %) plus a fixed
-- part of their capital (5.5% a month), into their wallet.
--
-- Shared (fractional) offerings, capacity accounting, lessee rental receipts and their
-- distribution are gone. This migration rebuilds the investment tables and so requires that no
-- offerings exist yet: reset the database before upgrading.
-- =====================================================================================

DO
$$
BEGIN
    IF EXISTS (SELECT 1 FROM investment_products) THEN
        RAISE EXCEPTION 'V16 replaces the investment model and needs an empty investment_products table; reset the database';
    END IF;
END
$$;

-- ----------------------------------------------------------------- old investment model --
DROP TABLE referral_earnings;
DROP TABLE earnings;
DROP TABLE rental_receipts;
DROP TABLE capacity_movements;
DROP TABLE holdings;
DROP TABLE order_items;
DROP TABLE cart_items;
DROP TABLE investment_products;
DROP SEQUENCE investment_product_code_seq;

-- --------------------------------------------------------------------- permissions --
DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE code IN ('RENTAL_RECORD', 'RENTAL_APPROVE'));
DELETE FROM permissions WHERE code IN ('RENTAL_RECORD', 'RENTAL_APPROVE');

INSERT INTO permissions (code, category, description)
VALUES ('PAYOUT_PROCESS', 'FINANCE', 'Pay monthly investor payouts that have fallen due');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYOUT_PROCESS'
WHERE r.name IN ('SUPER_ADMIN', 'FINANCE');

-- ---------------------------------------------------------------------- containers --
-- RESERVED: held for an order awaiting payment. ON_LEASE: allocated to an investor.
ALTER TABLE containers
    ADD COLUMN reserved_order_id UUID REFERENCES orders (id),
    DROP CONSTRAINT containers_status_chk,
    ADD CONSTRAINT containers_status_chk CHECK (status IN ('AVAILABLE', 'RESERVED', 'ON_LEASE', 'MAINTENANCE', 'RETIRED')),
    ADD CONSTRAINT containers_reserved_chk CHECK ((status = 'RESERVED') = (reserved_order_id IS NOT NULL));
CREATE INDEX containers_allocation_idx ON containers (container_type, status, created_at);
CREATE INDEX containers_reserved_order_idx ON containers (reserved_order_id) WHERE reserved_order_id IS NOT NULL;

-- --------------------------------------------------------------------------- plans --
CREATE SEQUENCE investment_product_code_seq START WITH 10001;

-- Terms are frozen once published. Tenure and the capital part of the payout are platform
-- constants, copied onto each plan so that investors are always paid what they accepted.
CREATE TABLE investment_products
(
    id                             UUID PRIMARY KEY,
    code                           VARCHAR(20)    NOT NULL,
    container_type                 VARCHAR(30)    NOT NULL,
    title                          VARCHAR(140)   NOT NULL,
    summary                        VARCHAR(400)   NOT NULL,
    description                    TEXT           NOT NULL,
    currency                       VARCHAR(3)     NOT NULL,
    price                          NUMERIC(19, 4) NOT NULL,
    monthly_rent_percent           NUMERIC(5, 2)  NOT NULL,
    monthly_capital_return_percent NUMERIC(5, 2)  NOT NULL,
    tenure_months                  INT            NOT NULL,
    risk_level                     VARCHAR(10)    NOT NULL,
    risk_disclosure                TEXT           NOT NULL,
    terms_and_conditions           TEXT           NOT NULL,
    terms_version                  VARCHAR(20)    NOT NULL,
    offer_opens_at                 TIMESTAMPTZ,
    offer_closes_at                TIMESTAMPTZ,
    status                         VARCHAR(20)    NOT NULL,
    published_at                   TIMESTAMPTZ,
    published_by                   UUID REFERENCES users (id),
    closed_at                      TIMESTAMPTZ,
    cancelled_at                   TIMESTAMPTZ,
    cancellation_reason            VARCHAR(500),
    created_by                     UUID           NOT NULL REFERENCES users (id),
    version                        BIGINT         NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ    NOT NULL,
    updated_at                     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT investment_products_code_uk UNIQUE (code),
    CONSTRAINT investment_products_container_type_chk CHECK (container_type IN
        ('DRY_20FT', 'DRY_40FT', 'HIGH_CUBE_40FT', 'HIGH_CUBE_45FT', 'REEFER_20FT', 'REEFER_40FT',
         'OPEN_TOP_20FT', 'OPEN_TOP_40FT', 'FLAT_RACK_20FT', 'FLAT_RACK_40FT', 'TANK_20FT')),
    CONSTRAINT investment_products_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT investment_products_price_chk CHECK (price > 0),
    CONSTRAINT investment_products_rent_chk CHECK (monthly_rent_percent > 0 AND monthly_rent_percent <= 20),
    CONSTRAINT investment_products_capital_chk CHECK (
        monthly_capital_return_percent >= 0 AND monthly_capital_return_percent * tenure_months <= 100),
    CONSTRAINT investment_products_tenure_chk CHECK (tenure_months BETWEEN 1 AND 120),
    CONSTRAINT investment_products_risk_chk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT investment_products_window_chk CHECK (
        offer_opens_at IS NULL OR offer_closes_at IS NULL OR offer_closes_at > offer_opens_at),
    CONSTRAINT investment_products_status_chk CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT investment_products_published_chk CHECK (status IN ('DRAFT', 'CANCELLED') OR published_at IS NOT NULL),
    CONSTRAINT investment_products_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT investment_products_cancelled_chk CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
CREATE INDEX investment_products_listing_idx ON investment_products (status, published_at DESC);

-- ---------------------------------------------------------------------------- cart --
-- How many containers of a plan the investor intends to buy. Nothing is reserved.
CREATE TABLE cart_items
(
    id         UUID PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id),
    product_id UUID        NOT NULL REFERENCES investment_products (id),
    quantity   INT         NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT cart_items_user_product_uk UNIQUE (user_id, product_id),
    CONSTRAINT cart_items_quantity_chk CHECK (quantity BETWEEN 1 AND 50)
);

-- --------------------------------------------------------------------- order items --
-- One container of an order, with the plan terms the investor accepted - frozen at checkout. The
-- container is reserved for the order; its number is shown to the investor once paid.
CREATE TABLE order_items
(
    id                             UUID PRIMARY KEY,
    order_id                       UUID           NOT NULL REFERENCES orders (id),
    product_id                     UUID           NOT NULL REFERENCES investment_products (id),
    product_code                   VARCHAR(20)    NOT NULL,
    product_title                  VARCHAR(140)   NOT NULL,
    container_type                 VARCHAR(30)    NOT NULL,
    container_id                   UUID           NOT NULL REFERENCES containers (id),
    amount                         NUMERIC(19, 4) NOT NULL,
    currency                       VARCHAR(3)     NOT NULL,
    monthly_rent_percent           NUMERIC(5, 2)  NOT NULL,
    monthly_capital_return_percent NUMERIC(5, 2)  NOT NULL,
    tenure_months                  INT            NOT NULL,
    terms_version                  VARCHAR(20)    NOT NULL,
    created_at                     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT order_items_order_container_uk UNIQUE (order_id, container_id),
    CONSTRAINT order_items_amount_chk CHECK (amount > 0)
);
CREATE INDEX order_items_order_idx ON order_items (order_id);
CREATE TRIGGER order_items_immutable
    BEFORE UPDATE OR DELETE
    ON order_items
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------ holdings --
-- One container allocated to one investor, leased for the tenure from the day payment was confirmed.
CREATE TABLE holdings
(
    id                             UUID PRIMARY KEY,
    user_id                        UUID           NOT NULL REFERENCES users (id),
    product_id                     UUID           NOT NULL REFERENCES investment_products (id),
    order_id                       UUID           NOT NULL REFERENCES orders (id),
    order_item_id                  UUID           NOT NULL REFERENCES order_items (id),
    container_id                   UUID           NOT NULL REFERENCES containers (id),
    amount                         NUMERIC(19, 4) NOT NULL,
    currency                       VARCHAR(3)     NOT NULL,
    monthly_rent_percent           NUMERIC(5, 2)  NOT NULL,
    monthly_capital_return_percent NUMERIC(5, 2)  NOT NULL,
    tenure_months                  INT            NOT NULL,
    terms_version                  VARCHAR(20)    NOT NULL,
    lease_starts_on                DATE           NOT NULL,
    lease_ends_on                  DATE           NOT NULL,
    status                         VARCHAR(20)    NOT NULL,
    confirmed_at                   TIMESTAMPTZ    NOT NULL,
    matured_at                     TIMESTAMPTZ,
    version                        BIGINT         NOT NULL DEFAULT 0,
    created_at                     TIMESTAMPTZ    NOT NULL,
    updated_at                     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT holdings_order_item_uk UNIQUE (order_item_id),
    CONSTRAINT holdings_amount_chk CHECK (amount > 0),
    CONSTRAINT holdings_lease_chk CHECK (lease_ends_on > lease_starts_on),
    CONSTRAINT holdings_status_chk CHECK (status IN ('ACTIVE', 'MATURED')),
    CONSTRAINT holdings_matured_chk CHECK ((status = 'MATURED') = (matured_at IS NOT NULL))
);
CREATE INDEX holdings_user_idx ON holdings (user_id, confirmed_at DESC);
CREATE INDEX holdings_product_idx ON holdings (product_id);
-- A container is allocated to at most one investor at a time.
CREATE UNIQUE INDEX holdings_active_container_uk ON holdings (container_id) WHERE status = 'ACTIVE';

-- ------------------------------------------------------------------ payout schedule --
-- Installment n of a holding falls due n months after the lease started (in arrears). Paying it
-- credits the investor's wallet in one balanced ledger transaction.
CREATE TABLE payout_installments
(
    id                    UUID PRIMARY KEY,
    holding_id            UUID           NOT NULL REFERENCES holdings (id),
    user_id               UUID           NOT NULL REFERENCES users (id),
    product_id            UUID           NOT NULL REFERENCES investment_products (id),
    installment_number    INT            NOT NULL,
    due_on                DATE           NOT NULL,
    rent_amount           NUMERIC(19, 4) NOT NULL,
    capital_amount        NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    status                VARCHAR(20)    NOT NULL,
    paid_at               TIMESTAMPTZ,
    ledger_transaction_id UUID REFERENCES ledger_transactions (id),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT payout_installments_number_uk UNIQUE (holding_id, installment_number),
    CONSTRAINT payout_installments_number_chk CHECK (installment_number > 0),
    CONSTRAINT payout_installments_amounts_chk CHECK (rent_amount >= 0 AND capital_amount >= 0
        AND rent_amount + capital_amount > 0),
    CONSTRAINT payout_installments_status_chk CHECK (status IN ('SCHEDULED', 'PAID')),
    CONSTRAINT payout_installments_paid_chk CHECK (
        (status = 'PAID') = (paid_at IS NOT NULL AND ledger_transaction_id IS NOT NULL))
);
CREATE INDEX payout_installments_due_idx ON payout_installments (due_on) WHERE status = 'SCHEDULED';
CREATE INDEX payout_installments_user_idx ON payout_installments (user_id, due_on);

-- ------------------------------------------------------------ referral commissions --
-- Commissions on the rent part of each payout to a referred investor (the capital part earns none).
CREATE TABLE referral_earnings
(
    id                    UUID PRIMARY KEY,
    source_installment_id UUID           NOT NULL REFERENCES payout_installments (id),
    source_user_id        UUID           NOT NULL REFERENCES users (id),
    beneficiary_user_id   UUID           NOT NULL REFERENCES users (id),
    product_id            UUID           NOT NULL REFERENCES investment_products (id),
    installment_number    INT            NOT NULL,
    level                 SMALLINT       NOT NULL,
    base_amount           NUMERIC(19, 4) NOT NULL,
    rate_percent          NUMERIC(5, 3)  NOT NULL,
    amount                NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3)     NOT NULL,
    rate_version_id       UUID           NOT NULL REFERENCES referral_rate_versions (id),
    created_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT referral_earnings_source_level_uk UNIQUE (source_installment_id, level),
    CONSTRAINT referral_earnings_level_chk CHECK (level BETWEEN 1 AND 4),
    CONSTRAINT referral_earnings_amount_chk CHECK (amount > 0 AND base_amount > 0),
    CONSTRAINT referral_earnings_not_self_chk CHECK (source_user_id <> beneficiary_user_id)
);
CREATE INDEX referral_earnings_beneficiary_idx ON referral_earnings (beneficiary_user_id, created_at DESC);
CREATE TRIGGER referral_earnings_immutable
    BEFORE UPDATE OR DELETE
    ON referral_earnings
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- -------------------------------------------------------------------------- ledger --
-- Payouts are platform expenses: rent paid to investors, and capital returned to them.
ALTER TABLE ledger_accounts
    DROP CONSTRAINT ledger_accounts_type_chk,
    ADD CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN
        ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'PLATFORM_ADJUSTMENTS', 'PLATFORM_REFERRAL_EXPENSE',
         'WITHDRAWALS_IN_TRANSIT', 'PLATFORM_RENT_EXPENSE', 'PLATFORM_CAPITAL_RETURNS'));
ALTER TABLE ledger_transactions
    DROP CONSTRAINT ledger_transactions_type_chk,
    ADD CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN
        ('INVESTOR_PAYOUT', 'ADJUSTMENT', 'REFERRAL_COMMISSION', 'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE',
         'WITHDRAWAL_PAYOUT'));

-- -------------------------------------------------------------------- lead interest --
-- Shared containers are no longer offered: prospects who wanted them want containers.
ALTER TABLE leads DROP CONSTRAINT leads_interest_chk;
UPDATE leads SET interest = 'STANDALONE' WHERE interest = 'SHARED';
ALTER TABLE leads ADD CONSTRAINT leads_interest_chk CHECK (interest IN ('STANDALONE', 'UNSURE'));
