-- Identity verification now also takes the investor's signature, which may be uploaded first: a draft may hold no
-- identity number yet. A submission still needs both the Aadhaar and the PAN to leave DRAFT.
ALTER TABLE kyc_submissions DROP CONSTRAINT kyc_submissions_documents_chk;
ALTER TABLE kyc_submissions ADD CONSTRAINT kyc_submissions_documents_chk CHECK (
    ((aadhaar_number_encrypted IS NULL) = (aadhaar_number_last4 IS NULL))
    AND ((pan_number_encrypted IS NULL) = (pan_number_last4 IS NULL))
    AND (
        (document_type IS NULL AND status = 'DRAFT')
        OR (document_type IS NULL AND status <> 'DRAFT'
            AND aadhaar_number_encrypted IS NOT NULL AND pan_number_encrypted IS NOT NULL)
        OR (document_type IS NOT NULL AND aadhaar_number_encrypted IS NULL AND pan_number_encrypted IS NULL
            AND document_number_encrypted IS NOT NULL AND document_number_last4 IS NOT NULL
            AND document_issuing_country IS NOT NULL AND document_expiry_date IS NOT NULL)
    )
);
