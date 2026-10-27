-- The receipt number the investor was given when they paid cash at the office, for finance to match when confirming.
ALTER TABLE payments
    ADD COLUMN receipt_number       VARCHAR(100),
    ADD COLUMN receipt_submitted_at TIMESTAMPTZ,
    ADD CONSTRAINT payments_receipt_complete_chk CHECK (
        ((receipt_number IS NULL) AND (receipt_submitted_at IS NULL))
        OR ((receipt_number IS NOT NULL) AND (receipt_submitted_at IS NOT NULL) AND (method = 'OFFICE_CASH')));
