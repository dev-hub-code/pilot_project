-- Forgot password: a 6-digit code emailed to the account's address lets its owner set a new password.
-- Only a hash of the code is stored; a code works once, for a short time, and stops after a few wrong guesses.
CREATE TABLE password_reset_codes
(
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id),
    code_hash       VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    failed_attempts INT         NOT NULL DEFAULT 0,
    CONSTRAINT password_reset_codes_expiry_chk CHECK (expires_at > created_at)
);
CREATE INDEX password_reset_codes_user_idx ON password_reset_codes (user_id);
