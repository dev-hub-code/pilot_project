-- =====================================================================================
-- V33: Plans pay rent only. The price is no longer returned over the tenure (V17).
--
-- Every plan, including those on sale, now carries a monthly capital return of 0%, so new purchases
-- are paid the monthly rent for the tenure and nothing more. Holdings and order items keep the terms
-- they were sold under (100 ÷ tenure %), so their scheduled payouts - rent plus capital - are unchanged.
-- =====================================================================================

ALTER TABLE investment_products DROP CONSTRAINT investment_products_capital_chk;
UPDATE investment_products SET monthly_capital_return_percent = 0;
ALTER TABLE investment_products
    ADD CONSTRAINT investment_products_capital_chk CHECK (monthly_capital_return_percent = 0);
