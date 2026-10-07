-- =====================================================================================
-- V17: Staff choose each plan's lease tenure, and the whole price is returned over it.
--
-- The monthly capital return is no longer a fixed platform percentage: it is 100 ÷ tenure % of the
-- price (16 months → 6.25%), so the investor gets their full investment back by the end of the lease,
-- on top of the monthly rent. The percentage is stored to four decimals (12 months → 8.3333%);
-- amounts are computed as price ÷ tenure, with the last payout absorbing the rounding.
-- =====================================================================================

ALTER TABLE investment_products
    DROP CONSTRAINT investment_products_capital_chk,
    ALTER COLUMN monthly_capital_return_percent TYPE NUMERIC(9, 4);
UPDATE investment_products SET monthly_capital_return_percent = round(100.0 / tenure_months, 4)
WHERE status = 'DRAFT';
ALTER TABLE investment_products
    ADD CONSTRAINT investment_products_capital_chk CHECK (
        status <> 'DRAFT' OR monthly_capital_return_percent = round(100.0 / tenure_months, 4));

ALTER TABLE order_items DISABLE TRIGGER order_items_immutable;
ALTER TABLE order_items ALTER COLUMN monthly_capital_return_percent TYPE NUMERIC(9, 4);
ALTER TABLE order_items ENABLE TRIGGER order_items_immutable;

ALTER TABLE holdings ALTER COLUMN monthly_capital_return_percent TYPE NUMERIC(9, 4);
