-- Email verification for self-registered accounts. Accounts that existed before this count as verified,
-- so nobody already using the platform is locked out.
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

-- Single-use links emailed after sign-up. Only the SHA-256 hash of the token is stored.
CREATE TABLE email_verification_tokens
(
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    CONSTRAINT email_verification_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_expiry_chk CHECK (expires_at > created_at)
);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);
