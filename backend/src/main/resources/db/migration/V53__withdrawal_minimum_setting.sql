-- =====================================================================================
-- V53: The minimum withdrawal, set by an admin.
--
-- A withdrawal run pays an investor's row only when it comes to at least this. Until an admin sets it (NULL), the
-- configured default (app.withdrawals.minimum-amount) applies.
-- =====================================================================================
CREATE TABLE withdrawal_settings
(
    id             SMALLINT PRIMARY KEY DEFAULT 1,
    minimum_amount NUMERIC(19, 4),
    updated_at     TIMESTAMPTZ,
    updated_by     UUID REFERENCES users (id),
    CONSTRAINT withdrawal_settings_single_row_chk CHECK (id = 1),
    CONSTRAINT withdrawal_settings_minimum_chk CHECK (minimum_amount IS NULL OR minimum_amount > 0)
);

INSERT INTO withdrawal_settings (id) VALUES (1);
