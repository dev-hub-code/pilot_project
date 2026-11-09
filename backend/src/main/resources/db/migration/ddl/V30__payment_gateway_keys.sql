-- The Razorpay API keys, webhook secret and merchant name, as an admin sets them in Platform config → Payment gateway.
-- Null follows the server's configuration (app.payments.razorpay.*). The secrets are encrypted by the application
-- (FieldEncryptor, AES-256-GCM) and never shown again once saved. The key id and its secret are always set together.
ALTER TABLE payment_gateway_settings
    ADD COLUMN key_id varchar(60),
    ADD COLUMN key_secret_encrypted text,
    ADD COLUMN webhook_secret_encrypted text,
    ADD COLUMN merchant_name varchar(60),
    ADD COLUMN keys_updated_by uuid,
    ADD COLUMN keys_updated_at timestamp with time zone,
    ADD CONSTRAINT payment_gateway_settings_keys_updated_by_fkey FOREIGN KEY (keys_updated_by) REFERENCES users (id),
    ADD CONSTRAINT payment_gateway_settings_key_pair_chk
        CHECK ((key_id IS NULL) = (key_secret_encrypted IS NULL));

COMMENT ON COLUMN payment_gateway_settings.key_id IS 'Razorpay API key id (rzp_test_… or rzp_live_…); null: the server''s';
COMMENT ON COLUMN payment_gateway_settings.key_secret_encrypted IS 'Razorpay API key secret, encrypted; set with key_id';
COMMENT ON COLUMN payment_gateway_settings.webhook_secret_encrypted IS 'Razorpay webhook secret, encrypted; null: the server''s';
