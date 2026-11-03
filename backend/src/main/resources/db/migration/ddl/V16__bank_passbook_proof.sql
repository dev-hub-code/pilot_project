-- A bank passbook (its first page, with the holder, account number and IFSC) proves an account too.
ALTER TABLE bank_accounts DROP CONSTRAINT bank_accounts_proof_chk;
ALTER TABLE bank_accounts ADD CONSTRAINT bank_accounts_proof_chk
    CHECK (((proof_type IS NULL) = (proof_document_id IS NULL))
        AND ((proof_type IS NULL) OR (proof_type IN ('BANK_STATEMENT', 'CANCELLED_CHEQUE', 'PASSBOOK'))));
