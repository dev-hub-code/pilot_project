-- Online investors pay through Razorpay: a checkout (UPI or net banking, confirmed in seconds) or a virtual account
-- (a bank account number and UPI ID for one payment, confirmed when the money lands). Network investors keep paying by
-- bank transfer or cash at the office, confirmed by finance.

ALTER TABLE payments DROP CONSTRAINT payments_method_chk;
ALTER TABLE payments ADD CONSTRAINT payments_method_chk
    CHECK (method IN ('BANK_TRANSFER', 'OFFICE_CASH', 'CARD', 'GATEWAY', 'VIRTUAL_ACCOUNT'));

-- Where to pay a virtual-account payment: shown to the investor, all set together and only for that method.
ALTER TABLE payments
    ADD COLUMN virtual_account_number character varying(40),
    ADD COLUMN virtual_account_ifsc character varying(11),
    ADD COLUMN virtual_account_name character varying(120),
    ADD COLUMN virtual_account_bank character varying(120),
    ADD COLUMN virtual_upi_id character varying(100),
    ADD CONSTRAINT payments_virtual_account_chk CHECK (
        (virtual_account_number IS NULL AND virtual_account_ifsc IS NULL AND virtual_account_name IS NULL
            AND virtual_account_bank IS NULL AND virtual_upi_id IS NULL)
        OR (virtual_account_number IS NOT NULL AND virtual_account_ifsc IS NOT NULL AND method = 'VIRTUAL_ACCOUNT'));

-- A provider's reference names one payment, and one payment at the provider (its id, kept as the external reference)
-- is recorded once, however often its webhook is delivered.
CREATE UNIQUE INDEX payments_gateway_reference_uk ON payments (provider, provider_reference)
    WHERE provider = 'RAZORPAY';
CREATE UNIQUE INDEX payments_gateway_payment_uk ON payments (provider, external_reference)
    WHERE provider = 'RAZORPAY' AND external_reference IS NOT NULL;
