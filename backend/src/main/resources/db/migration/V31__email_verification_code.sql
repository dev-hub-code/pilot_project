-- The verification email also carries a 6-digit code, an alternative to following the link. Only a
-- hash is stored; a code stops working after a few wrong guesses (a new email must then be requested).
ALTER TABLE email_verification_tokens
    ADD COLUMN code_hash       VARCHAR(64),
    ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
