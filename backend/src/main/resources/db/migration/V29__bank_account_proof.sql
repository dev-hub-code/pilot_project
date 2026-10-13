-- Proof of a bank account, required to verify it: a bank statement or a cancelled cheque, stored encrypted in the
-- document store. Accounts added before this have none; they cannot be verified without one.
ALTER TABLE bank_accounts
    ADD COLUMN proof_type VARCHAR(20),
    ADD COLUMN proof_document_id UUID REFERENCES stored_documents (id),
    ADD CONSTRAINT bank_accounts_proof_chk CHECK (
        (proof_type IS NULL) = (proof_document_id IS NULL)
        AND (proof_type IS NULL OR proof_type IN ('BANK_STATEMENT', 'CANCELLED_CHEQUE')));
