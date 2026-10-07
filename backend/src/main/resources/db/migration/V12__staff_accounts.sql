-- =====================================================================================
-- V12: Staff accounts created by administrators with a temporary password.
--
-- A temporary password must be replaced at first sign-in (until then the API answers only the
-- password change, /me and sign-out) and stops working after it expires.
-- =====================================================================================

ALTER TABLE users
    ADD COLUMN must_change_password          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN temporary_password_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT users_temporary_password_chk CHECK (
        must_change_password = (temporary_password_expires_at IS NOT NULL));
