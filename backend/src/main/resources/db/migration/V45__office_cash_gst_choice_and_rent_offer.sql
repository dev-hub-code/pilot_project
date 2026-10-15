-- =====================================================================================
-- V45: Paying in cash at the office, the investor may choose to pay without GST, and finance may offer a
-- monthly rent other than the plan's when it records the cash.
--
-- payments.gst_included: the amount due includes GST (always, except an office cash payment the investor chose
-- to pay without it). payments.offered_rent_percent: the monthly rent finance offered when confirming the cash;
-- every container of the order earns it instead of the plan's rent. orders.gst_waived: the order was paid
-- without GST, so its invoice carries none.
-- =====================================================================================

ALTER TABLE payments
    ADD COLUMN gst_included BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN offered_rent_percent NUMERIC(5, 2),
    ADD CONSTRAINT payments_gst_choice_chk CHECK (gst_included OR method = 'OFFICE_CASH'),
    ADD CONSTRAINT payments_offered_rent_chk CHECK (offered_rent_percent IS NULL
        OR (method = 'OFFICE_CASH' AND offered_rent_percent > 0 AND offered_rent_percent <= 20));

ALTER TABLE orders
    ADD COLUMN gst_waived BOOLEAN NOT NULL DEFAULT FALSE;
