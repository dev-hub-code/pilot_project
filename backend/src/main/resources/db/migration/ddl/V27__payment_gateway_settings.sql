-- How online payments through Razorpay run, set by an admin (one row). A null column follows the server's configuration
-- (app.payments.razorpay.*). The API keys and webhook secret are never stored here: they stay in the environment.
CREATE TABLE payment_gateway_settings (
    id smallint DEFAULT 1 NOT NULL,
    online_payments_enabled boolean,
    virtual_accounts_enabled boolean,
    max_amount numeric(19,4),
    checkout_window_minutes integer,
    virtual_account_window_hours integer,
    updated_by uuid,
    updated_at timestamp with time zone,
    CONSTRAINT payment_gateway_settings_pkey PRIMARY KEY (id),
    CONSTRAINT payment_gateway_settings_single_row_chk CHECK (id = 1),
    CONSTRAINT payment_gateway_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT payment_gateway_settings_max_amount_chk CHECK (max_amount IS NULL OR max_amount >= 0),
    CONSTRAINT payment_gateway_settings_checkout_window_chk
        CHECK (checkout_window_minutes IS NULL OR checkout_window_minutes BETWEEN 5 AND 1440),
    CONSTRAINT payment_gateway_settings_va_window_chk
        CHECK (virtual_account_window_hours IS NULL OR virtual_account_window_hours BETWEEN 1 AND 720)
);

INSERT INTO payment_gateway_settings (id) VALUES (1);
