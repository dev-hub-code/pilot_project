-- =====================================================================================
-- V34: The investor chooses the lease tenure, in whole years, within the plan's limits.
--
-- A plan no longer has one tenure: staff set a minimum and a maximum in years, and each investor
-- picks how many years to lease every container they buy (stored on the cart line, then copied to
-- the order item and the holding in months, as before). Existing plans get a minimum of their
-- tenure rounded up to whole years (16 months -> 2 years) and a maximum of 10 years (the old limit
-- of 120 months), or their minimum if that is longer. Holdings keep their exact tenure and schedule.
-- =====================================================================================

ALTER TABLE investment_products
    ADD COLUMN min_tenure_years INT,
    ADD COLUMN max_tenure_years INT;
UPDATE investment_products
SET min_tenure_years = ceil(tenure_months / 12.0)::int,
    max_tenure_years = greatest(ceil(tenure_months / 12.0)::int, 10);
ALTER TABLE investment_products
    ALTER COLUMN min_tenure_years SET NOT NULL,
    ALTER COLUMN max_tenure_years SET NOT NULL,
    DROP CONSTRAINT investment_products_tenure_chk,
    DROP COLUMN tenure_months,
    ADD CONSTRAINT investment_products_tenure_chk CHECK (
        min_tenure_years BETWEEN 1 AND 30 AND max_tenure_years BETWEEN min_tenure_years AND 30);

-- The investor's choice for each plan in their cart; lines already there take the plan's minimum.
ALTER TABLE cart_items ADD COLUMN tenure_years INT;
UPDATE cart_items c SET tenure_years = p.min_tenure_years FROM investment_products p WHERE p.id = c.product_id;
ALTER TABLE cart_items
    ALTER COLUMN tenure_years SET NOT NULL,
    ADD CONSTRAINT cart_items_tenure_chk CHECK (tenure_years BETWEEN 1 AND 30);
