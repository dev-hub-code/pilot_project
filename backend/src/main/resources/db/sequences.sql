-- =====================================================================================
-- Number sequences. Run on every start after Hibernate updates the schema (spring.sql.init), so it must stay
-- idempotent. Hibernate creates tables and columns but not these: the services take document numbers from them.
-- =====================================================================================

CREATE SEQUENCE IF NOT EXISTS agreement_number_seq;
CREATE SEQUENCE IF NOT EXISTS investment_product_code_seq START WITH 10001;
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
