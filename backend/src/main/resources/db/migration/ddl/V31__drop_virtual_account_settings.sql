-- Bank transfer into a Razorpay virtual account is no longer offered: its switch and hold window go. Payments already
-- made that way keep their details (payments.virtual_account_*).
ALTER TABLE payment_gateway_settings
    DROP CONSTRAINT IF EXISTS payment_gateway_settings_va_window_chk,
    DROP COLUMN virtual_accounts_enabled,
    DROP COLUMN virtual_account_window_hours;
