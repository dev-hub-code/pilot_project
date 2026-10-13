-- Containers are no longer reserved at checkout. A paid order line becomes a holding AWAITING_CONTAINER;
-- finance assigns an available container of the plan's type, and the lease and payouts start that day.

INSERT INTO permissions (code, category, description)
VALUES ('CONTAINER_ASSIGN', 'FINANCE', 'Assign containers to paid investments');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'CONTAINER_ASSIGN'
WHERE r.name IN ('SUPER_ADMIN', 'FINANCE');

-- New order lines carry no container; lines of earlier orders keep the one reserved for them.
ALTER TABLE order_items ALTER COLUMN container_id DROP NOT NULL;

ALTER TABLE holdings
    ALTER COLUMN container_id DROP NOT NULL,
    ALTER COLUMN lease_starts_on DROP NOT NULL,
    ALTER COLUMN lease_ends_on DROP NOT NULL,
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD COLUMN assigned_by UUID REFERENCES users (id),
    DROP CONSTRAINT holdings_status_chk,
    ADD CONSTRAINT holdings_status_chk CHECK (status IN ('AWAITING_CONTAINER', 'ACTIVE', 'MATURED')),
    -- A container and its lease dates come together, exactly when the holding is no longer awaiting one.
    ADD CONSTRAINT holdings_assignment_chk CHECK (
        (status = 'AWAITING_CONTAINER') = (container_id IS NULL)
        AND (container_id IS NULL) = (lease_starts_on IS NULL)
        AND (lease_starts_on IS NULL) = (lease_ends_on IS NULL));

CREATE INDEX holdings_awaiting_idx ON holdings (confirmed_at) WHERE status = 'AWAITING_CONTAINER';
