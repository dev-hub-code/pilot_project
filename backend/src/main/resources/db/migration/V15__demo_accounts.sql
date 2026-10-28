-- Demo accounts: super administrators mark an investor as a demo account and give them dummy orders and a dummy
-- downline over a time frame. The investor's own pages then show figures worked out from these (rent on the 1st,
-- referral commission on the 5th of each month). Nothing here touches the ledger, and demo accounts are left out of
-- real withdrawal runs.
INSERT INTO permissions (code, category, description)
VALUES ('DEMO_ACCOUNT_MANAGE', 'USER', 'Mark investors as demo accounts and give them dummy orders, downline and payouts');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'DEMO_ACCOUNT_MANAGE'
WHERE r.name = 'SUPER_ADMIN';

CREATE TABLE demo_accounts
(
    user_id    UUID PRIMARY KEY,
    starts_on  DATE        NOT NULL,
    ends_on    DATE        NOT NULL,
    created_by UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT demo_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT demo_accounts_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT demo_accounts_frame_chk CHECK (ends_on >= starts_on)
);

-- The dummy downline: a member with no parent is referred by the demo investor directly (level 1).
CREATE TABLE demo_members
(
    id         UUID PRIMARY KEY,
    account_id UUID         NOT NULL,
    parent_id  UUID,
    name       VARCHAR(120) NOT NULL,
    joined_on  DATE         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT demo_members_account_id_fkey FOREIGN KEY (account_id) REFERENCES demo_accounts (user_id) ON DELETE CASCADE,
    CONSTRAINT demo_members_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES demo_members (id) ON DELETE CASCADE
);
CREATE INDEX demo_members_account_idx ON demo_members (account_id);

-- Dummy orders of a real plan (its title, container type and price copied when the order is added): the demo
-- investor's own (no member) or a downline member's, which earn the investor commission.
CREATE SEQUENCE demo_order_number_seq START WITH 900001;
CREATE TABLE demo_orders
(
    id                  UUID PRIMARY KEY,
    account_id          UUID          NOT NULL,
    member_id           UUID,
    order_number        VARCHAR(20)   NOT NULL,
    product_id          UUID          NOT NULL,
    plan_code           VARCHAR(20)   NOT NULL,
    plan_title          VARCHAR(200)  NOT NULL,
    container_type      VARCHAR(30)   NOT NULL,
    containers          INT           NOT NULL,
    price_per_container NUMERIC(19, 4) NOT NULL,
    currency            CHAR(3)       NOT NULL,
    rent_percent        NUMERIC(7, 4) NOT NULL,
    tenure_months       INT           NOT NULL,
    starts_on           DATE          NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT demo_orders_account_id_fkey FOREIGN KEY (account_id) REFERENCES demo_accounts (user_id) ON DELETE CASCADE,
    CONSTRAINT demo_orders_member_id_fkey FOREIGN KEY (member_id) REFERENCES demo_members (id) ON DELETE CASCADE,
    CONSTRAINT demo_orders_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products (id),
    CONSTRAINT demo_orders_containers_chk CHECK (containers BETWEEN 1 AND 1000),
    CONSTRAINT demo_orders_price_chk CHECK (price_per_container > 0),
    CONSTRAINT demo_orders_rent_chk CHECK (rent_percent >= 0 AND rent_percent <= 100),
    CONSTRAINT demo_orders_tenure_chk CHECK (tenure_months BETWEEN 1 AND 600)
);
CREATE INDEX demo_orders_account_idx ON demo_orders (account_id);
