-- Identity verification now takes exactly two documents: the Aadhaar card (front and back) and the PAN card.
-- Both numbers are stored encrypted, with their last 4 characters for masked display. Earlier submissions keep
-- the single identity document they were made with.
ALTER TABLE kyc_submissions
    ALTER COLUMN document_type DROP NOT NULL,
    ALTER COLUMN document_number_encrypted DROP NOT NULL,
    ALTER COLUMN document_number_last4 DROP NOT NULL,
    ALTER COLUMN document_issuing_country DROP NOT NULL,
    ALTER COLUMN document_expiry_date DROP NOT NULL,
    ADD COLUMN aadhaar_number_encrypted TEXT,
    ADD COLUMN aadhaar_number_last4 VARCHAR(4),
    ADD COLUMN pan_number_encrypted TEXT,
    ADD COLUMN pan_number_last4 VARCHAR(4),
    -- Either the earlier single document, or Aadhaar and PAN together.
    ADD CONSTRAINT kyc_submissions_documents_chk CHECK (
        (aadhaar_number_encrypted IS NOT NULL AND aadhaar_number_last4 IS NOT NULL
            AND pan_number_encrypted IS NOT NULL AND pan_number_last4 IS NOT NULL AND document_type IS NULL)
        OR (aadhaar_number_encrypted IS NULL AND pan_number_encrypted IS NULL AND document_type IS NOT NULL
            AND document_number_encrypted IS NOT NULL AND document_number_last4 IS NOT NULL
            AND document_issuing_country IS NOT NULL AND document_expiry_date IS NOT NULL));
