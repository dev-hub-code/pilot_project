-- =====================================================================================
-- V36: GST on the container price.
--
-- Each plan has a GST rate set by staff (0-28%). At checkout the investor pays, per container, the price plus
-- GST on it; rent and referral commission stay based on the price without GST. Order items record the rate and
-- the GST amount. Invoices split the GST by place of supply: CGST + SGST within the seller's state, IGST across
-- states, with the taxable value and every tax amount stored on the invoice and its lines.
-- Existing plans, orders and invoices carry no GST.
-- =====================================================================================

ALTER TABLE investment_products
    ADD COLUMN gst_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT investment_products_gst_chk CHECK (gst_rate_percent BETWEEN 0 AND 28);

ALTER TABLE order_items
    ADD COLUMN gst_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN gst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT order_items_gst_chk CHECK (gst_rate_percent BETWEEN 0 AND 28 AND gst_amount >= 0);

ALTER TABLE invoice_lines
    ADD COLUMN gst_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cgst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN sgst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN igst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

-- taxable_amount is the total before GST; NULL on invoices issued before GST (their total had none).
ALTER TABLE invoices
    ADD COLUMN taxable_amount NUMERIC(19, 4),
    ADD COLUMN cgst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN sgst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN igst_amount NUMERIC(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN place_of_supply VARCHAR(80),
    ADD COLUMN buyer_gstin VARCHAR(15);
