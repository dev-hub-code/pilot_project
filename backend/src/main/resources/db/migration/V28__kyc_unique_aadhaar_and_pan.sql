-- One Aadhaar number and one PAN per investor: a keyed fingerprint (HMAC) of each number lets submissions be compared
-- without decrypting them. Only submissions under review or approved hold a number; a rejected one frees it again.
-- Submissions made before this are fingerprinted by the application at startup.
ALTER TABLE kyc_submissions
    ADD COLUMN aadhaar_fingerprint VARCHAR(64),
    ADD COLUMN pan_fingerprint VARCHAR(64);

CREATE UNIQUE INDEX kyc_submissions_active_aadhaar_uk ON kyc_submissions (aadhaar_fingerprint)
    WHERE status IN ('PENDING', 'APPROVED') AND aadhaar_fingerprint IS NOT NULL;
CREATE UNIQUE INDEX kyc_submissions_active_pan_uk ON kyc_submissions (pan_fingerprint)
    WHERE status IN ('PENDING', 'APPROVED') AND pan_fingerprint IS NOT NULL;
