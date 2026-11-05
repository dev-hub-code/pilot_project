-- Containers are no longer part of the platform: investors buy units of a plan. This drops the container inventory,
-- every link to it, its permissions, and the container support category; demo orders count units.

-- ------------------------------------------------------------------------------------------------- guard
-- Audit logs are immutable and documents are kept, so rows using the removed values cannot be rewritten. Stop here
-- rather than leave rows the application can no longer read.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM audit_logs
               WHERE action IN ('CONTAINER_CREATED', 'CONTAINER_UPDATED', 'CONTAINER_STATUS_CHANGED',
                                'CONTAINER_DOCUMENT_ADDED', 'CONTAINER_DOCUMENT_VISIBILITY_CHANGED',
                                'HOLDING_CONTAINER_ASSIGNED', 'CONTAINER_TASK_ASSIGNED', 'CONTAINER_TASK_UNASSIGNED')) THEN
        RAISE EXCEPTION 'audit_logs has container actions; they cannot be removed';
    END IF;
    IF EXISTS (SELECT 1 FROM stored_documents WHERE purpose IN ('CONTAINER_PHOTO', 'CONTAINER_SURVEY_REPORT')) THEN
        RAISE EXCEPTION 'stored_documents has container documents; remove them first';
    END IF;
END
$$;

-- ------------------------------------------------------------------------------------------------- links
DROP INDEX holdings_active_container_uk;
ALTER TABLE holdings DROP CONSTRAINT holdings_container_id_fkey;
ALTER TABLE holdings DROP COLUMN container_id;

ALTER TABLE order_items DROP CONSTRAINT order_items_order_container_uk;
ALTER TABLE order_items DROP CONSTRAINT order_items_container_id_fkey;
ALTER TABLE order_items DROP COLUMN container_id;
ALTER TABLE order_items DROP COLUMN container_type;

ALTER TABLE lease_agreements DROP COLUMN container_number;

ALTER TABLE investment_products DROP COLUMN container_type;

ALTER TABLE demo_orders DROP COLUMN container_type;
ALTER TABLE demo_orders RENAME COLUMN containers TO units;
ALTER TABLE demo_orders RENAME COLUMN price_per_container TO price_per_unit;
ALTER TABLE demo_orders RENAME CONSTRAINT demo_orders_containers_chk TO demo_orders_units_chk;

-- ------------------------------------------------------------------------------------------------- inventory
DROP TABLE containers;
DROP SEQUENCE container_serial_seq;

-- ------------------------------------------------------------------------------------------------- permissions
DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE category = 'CONTAINERS' OR code LIKE 'CONTAINER\_%');
DELETE FROM permissions WHERE category = 'CONTAINERS' OR code LIKE 'CONTAINER\_%';

-- ------------------------------------------------------------------------------------------------- support
-- Tickets filed under containers move to "Something else"; the category, its subcategories and rules go.
UPDATE support_tickets SET category = 'OTHER', subcategory_id = NULL WHERE category = 'CONTAINER';
DELETE FROM support_categories WHERE category = 'CONTAINER' AND parent_id IS NOT NULL;
DELETE FROM support_categories WHERE category = 'CONTAINER';
DELETE FROM support_assignment_members WHERE category = 'CONTAINER';
DELETE FROM support_assignment_rules WHERE category = 'CONTAINER';
DELETE FROM support_sla_policies WHERE category = 'CONTAINER';

ALTER TABLE support_tickets DROP CONSTRAINT support_tickets_category_chk;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_category_chk CHECK (category IN
    ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'KYC', 'REFERRAL', 'TECHNICAL', 'OTHER'));
ALTER TABLE support_categories DROP CONSTRAINT support_categories_category_chk;
ALTER TABLE support_categories ADD CONSTRAINT support_categories_category_chk CHECK (category IN
    ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'KYC', 'REFERRAL', 'TECHNICAL', 'OTHER'));

-- Knowledge-base articles on container leasing move under Investments.
UPDATE kb_articles SET category_id = (SELECT id FROM kb_categories WHERE code = 'investments')
WHERE category_id IN (SELECT id FROM kb_categories WHERE code = 'containers');
UPDATE kb_categories SET parent_id = NULL
WHERE parent_id IN (SELECT id FROM kb_categories WHERE code = 'containers');
DELETE FROM kb_categories WHERE code = 'containers';

-- ------------------------------------------------------------------------------------------------- wording
UPDATE permissions SET description = 'Write the lease agreement issued to investors for each investment'
WHERE code = 'AGREEMENT_TEMPLATE_MANAGE';
UPDATE permissions SET description = 'Create investment products' WHERE code = 'INVESTMENT_CREATE';
UPDATE permissions SET description = 'Update investment products' WHERE code = 'INVESTMENT_UPDATE';
