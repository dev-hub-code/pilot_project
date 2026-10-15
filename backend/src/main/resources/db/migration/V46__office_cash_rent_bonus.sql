-- =====================================================================================
-- V46: Paying in cash at the office is always without GST and earns a rent bonus.
--
-- Instead of the investor choosing whether to pay GST and finance offering a rent (V45), every office cash payment
-- leaves out GST and adds a fixed bonus (app.office.cash-rent-bonus-percent) to each container's monthly rent.
-- payments.rent_bonus_percent freezes the bonus when the investor chooses to pay at the office; payments made
-- before this keep the terms they were started with (no bonus).
-- =====================================================================================

ALTER TABLE payments
    DROP CONSTRAINT payments_offered_rent_chk,
    DROP COLUMN offered_rent_percent,
    ADD COLUMN rent_bonus_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT payments_rent_bonus_chk CHECK (rent_bonus_percent >= 0 AND rent_bonus_percent <= 5
        AND (rent_bonus_percent = 0 OR method = 'OFFICE_CASH'));
