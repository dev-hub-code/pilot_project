-- =====================================================================================
-- V5: Containers, investment products (marketplace offerings) and capacity accounting.
--
-- Availability is tracked as amounts: available = total - committed - reserved. Counters live on
-- the product row (locked when changed) and every change is recorded as an immutable capacity
-- movement, so the product's funding history can be reconstructed and replays are idempotent.
-- =====================================================================================

-- ---------------------------------------------------------------------- containers --
CREATE TABLE containers
(
    id                   UUID PRIMARY KEY,
    -- ISO 6346: owner code (3 letters) + category (U/J/Z) + 6-digit serial + check digit.
    container_number     VARCHAR(11)   NOT NULL,
    container_type       VARCHAR(30)   NOT NULL,
    condition            VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    capacity_cbm         NUMERIC(8, 2) NOT NULL,
    max_gross_kg         INT           NOT NULL,
    tare_kg              INT           NOT NULL,
    manufacture_year     SMALLINT      NOT NULL,
    manufacturer         VARCHAR(100),
    current_location     VARCHAR(120)  NOT NULL,
    location_country     VARCHAR(2)    NOT NULL,
    acquisition_cost     NUMERIC(19, 4),
    acquisition_currency VARCHAR(3),
    notes                VARCHAR(1000),
    status_reason        VARCHAR(500),
    created_by           UUID          NOT NULL REFERENCES users (id),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    CONSTRAINT containers_number_uk UNIQUE (container_number),
    CONSTRAINT containers_number_format_chk CHECK (container_number ~ '^[A-Z]{3}[UJZ][0-9]{7}$'),
    CONSTRAINT containers_type_chk CHECK (container_type IN
        ('DRY_20FT', 'DRY_40FT', 'HIGH_CUBE_40FT', 'HIGH_CUBE_45FT', 'REEFER_20FT', 'REEFER_40FT',
         'OPEN_TOP_20FT', 'OPEN_TOP_40FT', 'FLAT_RACK_20FT', 'FLAT_RACK_40FT', 'TANK_20FT')),
    CONSTRAINT containers_condition_chk CHECK (condition IN ('NEW', 'CARGO_WORTHY', 'WIND_WATERTIGHT')),
    CONSTRAINT containers_status_chk CHECK (status IN ('AVAILABLE', 'ON_LEASE', 'MAINTENANCE', 'RETIRED')),
    CONSTRAINT containers_capacity_chk CHECK (capacity_cbm > 0),
    CONSTRAINT containers_weights_chk CHECK (tare_kg > 0 AND max_gross_kg > tare_kg),
    CONSTRAINT containers_year_chk CHECK (manufacture_year BETWEEN 1960 AND 2100),
    CONSTRAINT containers_country_chk CHECK (location_country ~ '^[A-Z]{2}$'),
    CONSTRAINT containers_cost_chk CHECK (
        (acquisition_cost IS NULL AND acquisition_currency IS NULL)
            OR (acquisition_cost >= 0 AND acquisition_currency ~ '^[A-Z]{3}$'))
);
CREATE INDEX containers_status_idx ON containers (status);

CREATE TABLE container_documents
(
    id                   UUID PRIMARY KEY,
    container_id         UUID         NOT NULL REFERENCES containers (id),
    document_id          UUID         NOT NULL REFERENCES stored_documents (id),
    purpose              VARCHAR(40)  NOT NULL,
    title                VARCHAR(140) NOT NULL,
    visible_to_investors BOOLEAN      NOT NULL,
    uploaded_by          UUID         NOT NULL REFERENCES users (id),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT container_documents_document_uk UNIQUE (document_id)
);
CREATE INDEX container_documents_container_idx ON container_documents (container_id, created_at);

-- ------------------------------------------------------------- investment products --
CREATE SEQUENCE investment_product_code_seq START WITH 10001;

CREATE TABLE investment_products
(
    id                     UUID PRIMARY KEY,
    code                   VARCHAR(20)    NOT NULL,
    container_id           UUID           NOT NULL REFERENCES containers (id),
    investment_type        VARCHAR(10)    NOT NULL,
    title                  VARCHAR(140)   NOT NULL,
    summary                VARCHAR(400)   NOT NULL,
    description            TEXT           NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    total_amount           NUMERIC(19, 4) NOT NULL,
    minimum_investment     NUMERIC(19, 4) NOT NULL,
    investment_increment   NUMERIC(19, 4) NOT NULL,
    maximum_per_investor   NUMERIC(19, 4),
    committed_amount       NUMERIC(19, 4) NOT NULL DEFAULT 0,
    reserved_amount        NUMERIC(19, 4) NOT NULL DEFAULT 0,
    expected_rental_amount NUMERIC(19, 4) NOT NULL,
    rental_frequency       VARCHAR(10)    NOT NULL,
    duration_months        INT            NOT NULL,
    lessee_name            VARCHAR(140),
    risk_level             VARCHAR(10)    NOT NULL,
    risk_disclosure        TEXT           NOT NULL,
    terms_and_conditions   TEXT           NOT NULL,
    terms_version          VARCHAR(20)    NOT NULL,
    offer_opens_at         TIMESTAMPTZ,
    offer_closes_at        TIMESTAMPTZ,
    status                 VARCHAR(20)    NOT NULL,
    published_at           TIMESTAMPTZ,
    published_by           UUID REFERENCES users (id),
    cancelled_at           TIMESTAMPTZ,
    cancellation_reason    VARCHAR(500),
    created_by             UUID           NOT NULL REFERENCES users (id),
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT investment_products_code_uk UNIQUE (code),
    CONSTRAINT investment_products_type_chk CHECK (investment_type IN ('RETAIL', 'HNI')),
    CONSTRAINT investment_products_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT investment_products_amounts_chk CHECK (
        total_amount > 0 AND minimum_investment > 0 AND investment_increment > 0
            AND minimum_investment <= total_amount AND investment_increment <= total_amount
            AND expected_rental_amount > 0),
    CONSTRAINT investment_products_max_chk CHECK (
        maximum_per_investor IS NULL OR maximum_per_investor BETWEEN minimum_investment AND total_amount),
    -- Rule 3: a standalone (HNI) container is taken by exactly one investor, in full.
    CONSTRAINT investment_products_hni_chk CHECK (
        investment_type <> 'HNI' OR (minimum_investment = total_amount AND investment_increment = total_amount)),
    -- Rule 14: capacity can never be oversold.
    CONSTRAINT investment_products_capacity_chk CHECK (
        committed_amount >= 0 AND reserved_amount >= 0 AND committed_amount + reserved_amount <= total_amount),
    CONSTRAINT investment_products_frequency_chk CHECK (rental_frequency IN ('MONTHLY', 'QUARTERLY')),
    CONSTRAINT investment_products_duration_chk CHECK (duration_months BETWEEN 1 AND 360),
    CONSTRAINT investment_products_risk_chk CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT investment_products_window_chk CHECK (
        offer_opens_at IS NULL OR offer_closes_at IS NULL OR offer_closes_at > offer_opens_at),
    CONSTRAINT investment_products_status_chk CHECK (status IN
        ('DRAFT', 'OPEN', 'FUNDED', 'ACTIVE', 'MATURED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT investment_products_published_chk CHECK (status = 'DRAFT' OR status = 'CANCELLED' OR published_at IS NOT NULL),
    CONSTRAINT investment_products_cancelled_chk CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL))
);
-- A container backs at most one live offering at a time.
CREATE UNIQUE INDEX investment_products_live_container_uk ON investment_products (container_id)
    WHERE status NOT IN ('CLOSED', 'CANCELLED');
CREATE INDEX investment_products_listing_idx ON investment_products (status, investment_type, published_at DESC);

-- ------------------------------------------------------------- capacity movements --
-- RESERVE holds capacity for a reference (a cart item or order); it later ends in exactly one
-- RELEASE (capacity returned) or COMMIT (capacity becomes invested).
CREATE TABLE capacity_movements
(
    id                UUID PRIMARY KEY,
    product_id        UUID           NOT NULL REFERENCES investment_products (id),
    movement_type     VARCHAR(10)    NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    reference         VARCHAR(100)   NOT NULL,
    investor_user_id  UUID           NOT NULL REFERENCES users (id),
    reserved_after    NUMERIC(19, 4) NOT NULL,
    committed_after   NUMERIC(19, 4) NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT capacity_movements_type_chk CHECK (movement_type IN ('RESERVE', 'RELEASE', 'COMMIT')),
    CONSTRAINT capacity_movements_amount_chk CHECK (amount > 0),
    CONSTRAINT capacity_movements_idempotency_uk UNIQUE (product_id, movement_type, reference)
);
-- A reservation is settled at most once (released or committed, never both).
CREATE UNIQUE INDEX capacity_movements_settled_uk ON capacity_movements (product_id, reference)
    WHERE movement_type IN ('RELEASE', 'COMMIT');
CREATE INDEX capacity_movements_product_idx ON capacity_movements (product_id, created_at);
CREATE INDEX capacity_movements_investor_idx ON capacity_movements (investor_user_id, product_id);
CREATE TRIGGER capacity_movements_immutable
    BEFORE UPDATE OR DELETE
    ON capacity_movements
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
