-- =====================================================================================
-- V14: Every investor may invest in shared and standalone containers alike.
--
-- Investors are no longer classified (retail / HNI): the classification, its history and the
-- permission to change it are removed. Offerings and leads name the kind of container instead of
-- a kind of investor: RETAIL → SHARED, HNI → STANDALONE.
-- =====================================================================================

-- ------------------------------------------------------------ investor classification --
DROP TABLE investor_classifications;

DROP INDEX user_profiles_investor_type_idx;
ALTER TABLE user_profiles
    DROP CONSTRAINT user_profiles_investor_type_chk,
    DROP COLUMN investor_type;

DELETE FROM role_permissions
WHERE permission_id = (SELECT id FROM permissions WHERE code = 'INVESTOR_CLASSIFY');
DELETE FROM permissions WHERE code = 'INVESTOR_CLASSIFY';

-- ------------------------------------------------------------------- offering types --
ALTER TABLE investment_products
    DROP CONSTRAINT investment_products_type_chk,
    DROP CONSTRAINT investment_products_hni_chk;
ALTER TABLE investment_products ALTER COLUMN investment_type TYPE VARCHAR(12);
UPDATE investment_products
SET investment_type = CASE investment_type WHEN 'HNI' THEN 'STANDALONE' ELSE 'SHARED' END;
ALTER TABLE investment_products
    ADD CONSTRAINT investment_products_type_chk CHECK (investment_type IN ('SHARED', 'STANDALONE')),
    -- Rule 3: a standalone container is taken by exactly one investor, in full.
    ADD CONSTRAINT investment_products_standalone_chk CHECK (
        investment_type <> 'STANDALONE' OR (minimum_investment = total_amount AND investment_increment = total_amount));

-- Order lines keep the type they were bought as; they are append-only, so the relabelling (same
-- meaning, new name) briefly lifts the immutability trigger.
ALTER TABLE order_items ALTER COLUMN investment_type TYPE VARCHAR(12);
ALTER TABLE order_items DISABLE TRIGGER order_items_immutable;
UPDATE order_items
SET investment_type = CASE investment_type WHEN 'HNI' THEN 'STANDALONE' ELSE 'SHARED' END;
ALTER TABLE order_items ENABLE TRIGGER order_items_immutable;

-- ----------------------------------------------------------------------- lead interest --
ALTER TABLE leads DROP CONSTRAINT leads_interest_chk;
UPDATE leads
SET interest = CASE interest WHEN 'HNI' THEN 'STANDALONE' WHEN 'RETAIL' THEN 'SHARED' ELSE interest END;
ALTER TABLE leads
    ADD CONSTRAINT leads_interest_chk CHECK (interest IN ('SHARED', 'STANDALONE', 'UNSURE'));
