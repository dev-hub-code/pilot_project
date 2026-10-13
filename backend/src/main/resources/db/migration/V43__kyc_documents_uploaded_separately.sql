-- =====================================================================================
-- V43: Aadhaar and PAN can be uploaded at different times; review starts once both are in.
--
-- The first upload starts a DRAFT submission holding whichever document came first; later uploads add the other
-- (or replace one, while still a draft). Once it holds both it becomes PENDING (submitted_at is then) and is
-- reviewed as one submission, as before. Drafts are never reviewed and do not reserve their numbers.
-- =====================================================================================

ALTER TABLE kyc_submissions
    DROP CONSTRAINT kyc_submissions_status_chk,
    ADD CONSTRAINT kyc_submissions_status_chk CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    DROP CONSTRAINT kyc_submissions_review_chk,
    ADD CONSTRAINT kyc_submissions_review_chk CHECK (
        (status IN ('DRAFT', 'PENDING') AND reviewed_by IS NULL AND reviewed_at IS NULL)
            OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    -- A draft holds Aadhaar, PAN or both; anything sent for review holds both (or the earlier single document).
    DROP CONSTRAINT kyc_submissions_documents_chk,
    ADD CONSTRAINT kyc_submissions_documents_chk CHECK (
        (aadhaar_number_encrypted IS NULL) = (aadhaar_number_last4 IS NULL)
            AND (pan_number_encrypted IS NULL) = (pan_number_last4 IS NULL)
            AND (
                (document_type IS NULL AND status = 'DRAFT'
                    AND (aadhaar_number_encrypted IS NOT NULL OR pan_number_encrypted IS NOT NULL))
                OR (document_type IS NULL AND status <> 'DRAFT'
                    AND aadhaar_number_encrypted IS NOT NULL AND pan_number_encrypted IS NOT NULL)
                OR (document_type IS NOT NULL AND aadhaar_number_encrypted IS NULL AND pan_number_encrypted IS NULL
                    AND document_number_encrypted IS NOT NULL AND document_number_last4 IS NOT NULL
                    AND document_issuing_country IS NOT NULL AND document_expiry_date IS NOT NULL)));

-- One draft per investor, besides the one submission under review.
CREATE UNIQUE INDEX kyc_submissions_one_draft_uk ON kyc_submissions (user_id) WHERE status = 'DRAFT';
