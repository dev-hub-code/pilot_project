-- =====================================================================================
-- V18: Plans no longer carry a risk level, a terms version or an offer window.
--
-- A plan is on sale from when it is published until staff close it. Its terms are frozen once
-- published, so investors accept them by plan alone; orders and holdings no longer record a
-- terms version.
-- =====================================================================================

ALTER TABLE investment_products
    DROP CONSTRAINT investment_products_risk_chk,
    DROP CONSTRAINT investment_products_window_chk,
    DROP COLUMN risk_level,
    DROP COLUMN terms_version,
    DROP COLUMN offer_opens_at,
    DROP COLUMN offer_closes_at;

ALTER TABLE order_items DROP COLUMN terms_version;

ALTER TABLE holdings DROP COLUMN terms_version;
