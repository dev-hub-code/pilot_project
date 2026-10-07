-- =====================================================================================
-- V6: Cart, orders, payments, holdings, invoices and the transactional outbox.
--
-- Money flow: cart (soft, no capacity held) → order (capacity RESERVED, payment due by
-- expires_at) → payment succeeded → order CONFIRMED in the same transaction: capacity COMMITTED,
-- holdings created, invoice issued and events written to the outbox.
-- =====================================================================================

-- --------------------------------------------------------------------- permissions --
INSERT INTO permissions (code, category, description)
VALUES ('PAYMENT_CONFIRM', 'FINANCE', 'Confirm received bank transfers');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYMENT_CONFIRM'
WHERE r.name IN ('SUPER_ADMIN', 'FINANCE');

-- -------------------------------------------------------------------------- outbox --
-- Written in the same transaction as the business change, published to Kafka by the relay.
-- The relay claims rows with FOR UPDATE SKIP LOCKED, so several instances can run it.
CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000)
);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;

-- Consumers record each event id they have handled, in the same transaction as its effects, so
-- redelivered events (Kafka is at-least-once) are applied exactly once.
CREATE TABLE processed_events
(
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID         NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

-- ---------------------------------------------------------------------------- cart --
-- One line per offering. Holds no capacity: availability is re-checked at checkout.
CREATE TABLE cart_items
(
    id         UUID PRIMARY KEY,
    user_id    UUID           NOT NULL REFERENCES users (id),
    product_id UUID           NOT NULL REFERENCES investment_products (id),
    amount     NUMERIC(19, 4) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ    NOT NULL,
    updated_at TIMESTAMPTZ    NOT NULL,
    CONSTRAINT cart_items_user_product_uk UNIQUE (user_id, product_id),
    CONSTRAINT cart_items_amount_chk CHECK (amount > 0),
    CONSTRAINT cart_items_currency_chk CHECK (currency ~ '^[A-Z]{3}$')
);

-- -------------------------------------------------------------------------- orders --
CREATE SEQUENCE order_number_seq START WITH 100001;

CREATE TABLE orders
(
    id               UUID PRIMARY KEY,
    order_number     VARCHAR(20)    NOT NULL,
    user_id          UUID           NOT NULL REFERENCES users (id),
    status           VARCHAR(20)    NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    total_amount     NUMERIC(19, 4) NOT NULL,
    -- Rule 12: a retried checkout with the same key returns the original order.
    idempotency_key  VARCHAR(100)   NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    expires_at       TIMESTAMPTZ    NOT NULL,
    confirmed_at     TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,
    close_reason     VARCHAR(500),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    CONSTRAINT orders_number_uk UNIQUE (order_number),
    CONSTRAINT orders_idempotency_uk UNIQUE (user_id, idempotency_key),
    CONSTRAINT orders_status_chk CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT orders_total_chk CHECK (total_amount > 0),
    CONSTRAINT orders_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT orders_confirmed_chk CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT orders_closed_chk CHECK ((status IN ('EXPIRED', 'CANCELLED')) = (closed_at IS NOT NULL))
);
CREATE INDEX orders_user_idx ON orders (user_id, created_at DESC);
CREATE INDEX orders_pending_expiry_idx ON orders (expires_at) WHERE status = 'PENDING_PAYMENT';

-- What the investor agreed to, frozen at checkout. Never changes afterwards.
CREATE TABLE order_items
(
    id                     UUID PRIMARY KEY,
    order_id               UUID           NOT NULL REFERENCES orders (id),
    product_id             UUID           NOT NULL REFERENCES investment_products (id),
    product_code           VARCHAR(20)    NOT NULL,
    product_title          VARCHAR(140)   NOT NULL,
    investment_type        VARCHAR(10)    NOT NULL,
    amount                 NUMERIC(19, 4) NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    ownership_percent      NUMERIC(9, 4)  NOT NULL,
    terms_version          VARCHAR(20)    NOT NULL,
    -- The investor's share of one rental payment: container rental × amount ÷ price.
    rental_per_payment     NUMERIC(19, 4) NOT NULL,
    rental_frequency       VARCHAR(10)    NOT NULL,
    duration_months        INT            NOT NULL,
    capacity_reference     VARCHAR(100)   NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT order_items_order_product_uk UNIQUE (order_id, product_id),
    CONSTRAINT order_items_reference_uk UNIQUE (capacity_reference),
    CONSTRAINT order_items_amount_chk CHECK (amount > 0),
    CONSTRAINT order_items_ownership_chk CHECK (ownership_percent > 0 AND ownership_percent <= 100)
);
CREATE INDEX order_items_order_idx ON order_items (order_id);
CREATE TRIGGER order_items_immutable
    BEFORE UPDATE OR DELETE
    ON order_items
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------ payments --
CREATE TABLE payments
(
    id                 UUID PRIMARY KEY,
    order_id           UUID           NOT NULL REFERENCES orders (id),
    user_id            UUID           NOT NULL REFERENCES users (id),
    method             VARCHAR(20)    NOT NULL,
    provider           VARCHAR(20)    NOT NULL,
    provider_reference VARCHAR(100)   NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3)     NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    idempotency_key    VARCHAR(100)   NOT NULL,
    failure_reason     VARCHAR(500),
    external_reference VARCHAR(100),
    confirmed_by       UUID REFERENCES users (id),
    settled_at         TIMESTAMPTZ,
    refunded_by        UUID REFERENCES users (id),
    refunded_at        TIMESTAMPTZ,
    refund_reference   VARCHAR(100),
    refund_reason      VARCHAR(500),
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ    NOT NULL,
    CONSTRAINT payments_provider_reference_uk UNIQUE (provider, provider_reference),
    CONSTRAINT payments_idempotency_uk UNIQUE (order_id, idempotency_key),
    CONSTRAINT payments_method_chk CHECK (method IN ('BANK_TRANSFER', 'CARD')),
    CONSTRAINT payments_status_chk CHECK (status IN
        ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUND_REQUIRED', 'REFUNDED')),
    CONSTRAINT payments_amount_chk CHECK (amount > 0),
    CONSTRAINT payments_settled_chk CHECK (
        (status IN ('SUCCEEDED', 'REFUND_REQUIRED', 'REFUNDED')) = (settled_at IS NOT NULL)),
    CONSTRAINT payments_refunded_chk CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL))
);
-- One payment attempt in flight per order, and money is taken for an order at most once.
CREATE UNIQUE INDEX payments_one_pending_uk ON payments (order_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX payments_one_succeeded_uk ON payments (order_id) WHERE status = 'SUCCEEDED';
CREATE INDEX payments_order_idx ON payments (order_id, created_at);
CREATE INDEX payments_status_idx ON payments (status, created_at);

-- Every provider notification as received. The unique key makes webhook redelivery a no-op.
CREATE TABLE payment_events
(
    id                UUID PRIMARY KEY,
    provider          VARCHAR(20)  NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL,
    payment_id        UUID REFERENCES payments (id),
    event_type        VARCHAR(50)  NOT NULL,
    payload           JSONB        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT payment_events_provider_event_uk UNIQUE (provider, provider_event_id)
);
CREATE TRIGGER payment_events_immutable
    BEFORE UPDATE OR DELETE
    ON payment_events
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------ holdings --
-- A confirmed investment: the investor's share of one offering, created from one order item.
CREATE TABLE holdings
(
    id                UUID PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES users (id),
    product_id        UUID           NOT NULL REFERENCES investment_products (id),
    order_id          UUID           NOT NULL REFERENCES orders (id),
    order_item_id     UUID           NOT NULL REFERENCES order_items (id),
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    ownership_percent NUMERIC(9, 4)  NOT NULL,
    terms_version     VARCHAR(20)    NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    confirmed_at      TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    CONSTRAINT holdings_order_item_uk UNIQUE (order_item_id),
    CONSTRAINT holdings_amount_chk CHECK (amount > 0),
    CONSTRAINT holdings_ownership_chk CHECK (ownership_percent > 0 AND ownership_percent <= 100),
    CONSTRAINT holdings_status_chk CHECK (status IN ('ACTIVE', 'MATURED', 'CLOSED'))
);
CREATE INDEX holdings_user_idx ON holdings (user_id, confirmed_at DESC);
CREATE INDEX holdings_product_idx ON holdings (product_id);

-- ------------------------------------------------------------------------ invoices --
CREATE SEQUENCE invoice_number_seq START WITH 1;

-- Issued documents are immutable; seller and buyer details are snapshots at the time of issue.
CREATE TABLE invoices
(
    id             UUID PRIMARY KEY,
    invoice_number VARCHAR(30)    NOT NULL,
    order_id       UUID           NOT NULL REFERENCES orders (id),
    user_id        UUID           NOT NULL REFERENCES users (id),
    payment_id     UUID           NOT NULL REFERENCES payments (id),
    currency       VARCHAR(3)     NOT NULL,
    total_amount   NUMERIC(19, 4) NOT NULL,
    issuer_name    VARCHAR(200)   NOT NULL,
    issuer_address VARCHAR(500)   NOT NULL,
    issuer_tax_id  VARCHAR(50),
    buyer_name     VARCHAR(200)   NOT NULL,
    buyer_email    VARCHAR(320)   NOT NULL,
    buyer_address  VARCHAR(500),
    notes          VARCHAR(1000),
    issued_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT invoices_number_uk UNIQUE (invoice_number),
    CONSTRAINT invoices_order_uk UNIQUE (order_id),
    CONSTRAINT invoices_total_chk CHECK (total_amount > 0)
);
CREATE INDEX invoices_user_idx ON invoices (user_id, issued_at DESC);
CREATE TRIGGER invoices_immutable
    BEFORE UPDATE OR DELETE
    ON invoices
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

CREATE TABLE invoice_lines
(
    id          UUID PRIMARY KEY,
    invoice_id  UUID           NOT NULL REFERENCES invoices (id),
    line_number INT            NOT NULL,
    description VARCHAR(300)   NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    CONSTRAINT invoice_lines_number_uk UNIQUE (invoice_id, line_number),
    CONSTRAINT invoice_lines_amount_chk CHECK (amount > 0)
);
CREATE TRIGGER invoice_lines_immutable
    BEFORE UPDATE OR DELETE
    ON invoice_lines
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
