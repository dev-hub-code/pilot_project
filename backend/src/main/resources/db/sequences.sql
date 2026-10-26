-- =====================================================================================
-- Number sequences. Run on every start after Hibernate updates the schema (spring.sql.init), so it must stay
-- idempotent. Hibernate creates tables and columns but not these: the services take document numbers from them.
-- =====================================================================================

CREATE SEQUENCE IF NOT EXISTS agreement_number_seq;
CREATE SEQUENCE IF NOT EXISTS investment_product_code_seq START WITH 10001;
-- Serials of generated container numbers; the demo stock uses 1xxxxx.
CREATE SEQUENCE IF NOT EXISTS container_serial_seq START WITH 200001;
CREATE SEQUENCE IF NOT EXISTS invoice_number_seq;
CREATE SEQUENCE IF NOT EXISTS lead_number_seq;
CREATE SEQUENCE IF NOT EXISTS order_number_seq START WITH 100001;
CREATE SEQUENCE IF NOT EXISTS ticket_number_seq;
CREATE SEQUENCE IF NOT EXISTS user_number_seq START WITH 3500 OWNED BY users.user_number;
CREATE SEQUENCE IF NOT EXISTS withdrawal_batch_number_seq;
CREATE SEQUENCE IF NOT EXISTS withdrawal_number_seq START WITH 100001;
CREATE SEQUENCE IF NOT EXISTS withdrawal_run_number_seq;

-- users.user_number is filled by the database (the entity never inserts it).
ALTER TABLE users ALTER COLUMN user_number SET DEFAULT nextval('user_number_seq');

-- Phone numbers saved before their country was asked for were Indian: those that are Indian mobile numbers become
-- +91 numbers in E.164 form (the comparable key gains the country code too); anything else is left as typed, without
-- a country, until its owner corrects it. Converted rows have a country, so this does nothing the second time.
UPDATE user_profiles
SET phone = '+91' || phone_key, phone_key = '91' || phone_key, phone_country = 'IN'
WHERE phone_country IS NULL AND phone_key ~ '^[6-9][0-9]{9}$';
UPDATE leads
SET phone = '+91' || right(regexp_replace(phone, '[^0-9]', '', 'g'), 10), phone_country = 'IN'
WHERE phone_country IS NULL AND regexp_replace(phone, '[^0-9]', '', 'g') ~ '^(91|0)?[6-9][0-9]{9}$';
