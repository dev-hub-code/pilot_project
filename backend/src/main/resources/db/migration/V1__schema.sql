-- =====================================================================================
-- V1: The SeaLease schema.
--
-- Money is NUMERIC(19, 4) with its ISO currency beside it; times are TIMESTAMPTZ (UTC). Tables that record facts
-- (audit, ledger, invoices, agreements, referrals, ticket history) are append-only: a trigger rejects UPDATE and
-- DELETE. Countries, states and cities are loaded by V2; reference data (roles, permissions, settings, the lease
-- agreement) is seeded at the end of this file.
-- =====================================================================================

-- ------------------------------------------------------------------------- shared functions

CREATE OR REPLACE FUNCTION forbid_mutation()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not permitted', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'integrity_constraint_violation';
END;
$function$;

CREATE OR REPLACE FUNCTION ledger_check_balanced()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    imbalance NUMERIC;
    foreign_entries INT;
BEGIN
    SELECT coalesce(sum(CASE e.direction WHEN 'DEBIT' THEN e.amount ELSE -e.amount END), 0),
           count(*) FILTER (WHERE a.currency <> t.currency)
    INTO imbalance, foreign_entries
    FROM ledger_entries e
             JOIN ledger_accounts a ON a.id = e.account_id
             JOIN ledger_transactions t ON t.id = e.transaction_id
    WHERE e.transaction_id = NEW.transaction_id;
    IF imbalance <> 0 THEN
        RAISE EXCEPTION 'Ledger transaction % is unbalanced by %', NEW.transaction_id, imbalance
            USING ERRCODE = 'check_violation';
    END IF;
    IF foreign_entries > 0 THEN
        RAISE EXCEPTION 'Ledger transaction % mixes currencies', NEW.transaction_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$function$;

-- ------------------------------------------------------------------------- number sequences

CREATE SEQUENCE agreement_number_seq;
CREATE SEQUENCE investment_product_code_seq START WITH 10001;
CREATE SEQUENCE invoice_number_seq;
CREATE SEQUENCE lead_number_seq;
CREATE SEQUENCE order_number_seq START WITH 100001;
CREATE SEQUENCE ticket_number_seq;
CREATE SEQUENCE user_number_seq START WITH 3500;
CREATE SEQUENCE withdrawal_batch_number_seq;
CREATE SEQUENCE withdrawal_number_seq START WITH 100001;
CREATE SEQUENCE withdrawal_run_number_seq;

-- ------------------------------------------------------------------------- identity and access

CREATE TABLE users
(
    id                            UUID PRIMARY KEY,
    email                         VARCHAR(254) NOT NULL,
    password_hash                 VARCHAR(255) NOT NULL,
    first_name                    VARCHAR(100) NOT NULL,
    last_name                     VARCHAR(100) NOT NULL,
    status                        VARCHAR(20) NOT NULL,
    failed_login_attempts         INT DEFAULT 0 NOT NULL,
    locked_until                  TIMESTAMPTZ,
    last_login_at                 TIMESTAMPTZ,
    password_changed_at           TIMESTAMPTZ NOT NULL,
    email_verified_at             TIMESTAMPTZ,
    status_reason                 VARCHAR(500),
    status_changed_at             TIMESTAMPTZ,
    status_changed_by             UUID,
    mfa_enabled                   BOOLEAN DEFAULT false NOT NULL,
    must_change_password          BOOLEAN DEFAULT false NOT NULL,
    temporary_password_expires_at TIMESTAMPTZ,
    user_number                   BIGINT DEFAULT nextval('user_number_seq') NOT NULL,
    version                       BIGINT DEFAULT 0 NOT NULL,
    created_at                    TIMESTAMPTZ NOT NULL,
    updated_at                    TIMESTAMPTZ NOT NULL,
    CONSTRAINT users_user_number_uk UNIQUE (user_number),
    CONSTRAINT users_status_changed_by_fkey FOREIGN KEY (status_changed_by) REFERENCES users(id),
    CONSTRAINT users_email_normalised_chk CHECK (email = lower(btrim(email))),
    CONSTRAINT users_failed_attempts_chk CHECK (failed_login_attempts >= 0),
    CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT users_temporary_password_chk CHECK (must_change_password = (temporary_password_expires_at IS NOT NULL))
);
CREATE UNIQUE INDEX users_email_uk ON users (email);

CREATE TABLE roles
(
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    system      BOOLEAN DEFAULT false NOT NULL,
    version     BIGINT DEFAULT 0 NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT now() NOT NULL,
    updated_at  TIMESTAMPTZ DEFAULT now() NOT NULL,
    CONSTRAINT roles_name_uk UNIQUE (name),
    CONSTRAINT roles_name_format_chk CHECK (name ~ '^[A-Z][A-Z0-9_]{1,63}$')
);

CREATE TABLE permissions
(
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    category    VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT permissions_code_uk UNIQUE (code)
);

CREATE TABLE role_permissions
(
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id),
    CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES permissions(id),
    CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

CREATE TABLE user_roles
(
    user_id     UUID NOT NULL,
    role_id     UUID NOT NULL,
    assigned_by UUID,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id),
    CONSTRAINT user_roles_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES users(id),
    CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX user_roles_role_idx ON user_roles (role_id);

CREATE TABLE auth_sessions
(
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    last_used_at   TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(40),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    version        BIGINT DEFAULT 0 NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT auth_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT auth_sessions_expiry_chk CHECK (expires_at > created_at),
    CONSTRAINT auth_sessions_revocation_chk CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);
CREATE INDEX auth_sessions_user_active_idx ON auth_sessions (user_id) WHERE (revoked_at IS NULL);

CREATE TABLE refresh_tokens
(
    id          UUID PRIMARY KEY,
    session_id  UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    replaced_by UUID,
    version     BIGINT DEFAULT 0 NOT NULL,
    CONSTRAINT refresh_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_replaced_by_fkey FOREIGN KEY (replaced_by) REFERENCES refresh_tokens(id),
    CONSTRAINT refresh_tokens_session_id_fkey FOREIGN KEY (session_id) REFERENCES auth_sessions(id),
    CONSTRAINT refresh_tokens_expiry_chk CHECK (expires_at > issued_at)
);
CREATE INDEX refresh_tokens_session_idx ON refresh_tokens (session_id);

CREATE TABLE email_verification_tokens
(
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    code_hash       VARCHAR(64),
    failed_attempts INT DEFAULT 0 NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT email_verification_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT email_verification_tokens_expiry_chk CHECK (expires_at > created_at)
);
CREATE INDEX email_verification_tokens_user_idx ON email_verification_tokens (user_id);

CREATE TABLE password_reset_codes
(
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    code_hash       VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    failed_attempts INT DEFAULT 0 NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT password_reset_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT password_reset_codes_expiry_chk CHECK (expires_at > created_at)
);
CREATE INDEX password_reset_codes_user_idx ON password_reset_codes (user_id);

CREATE TABLE audit_logs
(
    id             UUID PRIMARY KEY,
    actor_user_id  UUID,
    action         VARCHAR(64) NOT NULL,
    entity_type    VARCHAR(64) NOT NULL,
    entity_id      VARCHAR(64),
    old_value      JSONB,
    new_value      JSONB,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_logs_action_idx ON audit_logs (action, created_at DESC);
CREATE INDEX audit_logs_actor_idx ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX audit_logs_ip_idx ON audit_logs (ip_address, created_at DESC) WHERE (ip_address IS NOT NULL);
CREATE TRIGGER audit_logs_immutable BEFORE DELETE OR UPDATE ON audit_logs FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------- documents, profiles, kyc and bank accounts

CREATE TABLE stored_documents
(
    id            UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    purpose       VARCHAR(40) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    INT NOT NULL,
    sha256        VARCHAR(64) NOT NULL,
    content       bytea NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT stored_documents_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT stored_documents_size_chk CHECK (size_bytes > 0),
    CONSTRAINT stored_documents_type_chk CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png'))
);
CREATE INDEX stored_documents_owner_idx ON stored_documents (owner_user_id);

CREATE TABLE user_profiles
(
    id                    UUID PRIMARY KEY,
    user_id               UUID NOT NULL,
    phone                 VARCHAR(20),
    date_of_birth         DATE,
    nationality           VARCHAR(2),
    address_line1         VARCHAR(200),
    address_line2         VARCHAR(200),
    city                  VARCHAR(100),
    state_region          VARCHAR(100),
    postal_code           VARCHAR(20),
    country               VARCHAR(2),
    tax_residency_country VARCHAR(2),
    tax_id_encrypted      TEXT,
    tax_id_last4          VARCHAR(4),
    kyc_status            VARCHAR(20) DEFAULT 'NOT_SUBMITTED' NOT NULL,
    email_notifications   BOOLEAN DEFAULT true NOT NULL,
    sms_notifications     BOOLEAN DEFAULT false NOT NULL,
    phone_key             VARCHAR(20),
    version               BIGINT DEFAULT 0 NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_profiles_user_uk UNIQUE (user_id),
    CONSTRAINT user_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT user_profiles_kyc_status_chk CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT user_profiles_tax_id_chk CHECK ((tax_id_encrypted IS NULL) = (tax_id_last4 IS NULL))
);
CREATE INDEX user_profiles_kyc_status_idx ON user_profiles (kyc_status);
CREATE UNIQUE INDEX user_profiles_phone_key_uk ON user_profiles (phone_key) WHERE (phone_key IS NOT NULL);

CREATE TABLE kyc_submissions
(
    id                        UUID PRIMARY KEY,
    user_id                   UUID NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    legal_first_name          VARCHAR(100) NOT NULL,
    legal_last_name           VARCHAR(100) NOT NULL,
    date_of_birth             DATE NOT NULL,
    nationality               VARCHAR(2) NOT NULL,
    document_type             VARCHAR(20),
    document_number_encrypted TEXT,
    document_number_last4     VARCHAR(4),
    document_issuing_country  VARCHAR(2),
    document_expiry_date      DATE,
    submitted_at              TIMESTAMPTZ NOT NULL,
    reviewed_by               UUID,
    reviewed_at               TIMESTAMPTZ,
    rejection_reason          VARCHAR(500),
    aadhaar_number_encrypted  TEXT,
    aadhaar_number_last4      VARCHAR(4),
    pan_number_encrypted      TEXT,
    pan_number_last4          VARCHAR(4),
    aadhaar_fingerprint       VARCHAR(64),
    pan_fingerprint           VARCHAR(64),
    assigned_to               UUID,
    assigned_at               TIMESTAMPTZ,
    version                   BIGINT DEFAULT 0 NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT kyc_submissions_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT kyc_submissions_reviewed_by_fkey FOREIGN KEY (reviewed_by) REFERENCES users(id),
    CONSTRAINT kyc_submissions_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT kyc_submissions_assignment_chk CHECK ((assigned_to IS NULL) = (assigned_at IS NULL)),
    CONSTRAINT kyc_submissions_document_type_chk CHECK (document_type IN ('PASSPORT', 'NATIONAL_ID', 'DRIVING_LICENSE')),
    CONSTRAINT kyc_submissions_documents_chk CHECK (((aadhaar_number_encrypted IS NULL) = (aadhaar_number_last4 IS NULL)) AND ((pan_number_encrypted IS NULL) = (pan_number_last4 IS NULL)) AND (((document_type IS NULL) AND (status = 'DRAFT') AND ((aadhaar_number_encrypted IS NOT NULL) OR (pan_number_encrypted IS NOT NULL))) OR ((document_type IS NULL) AND (status <> 'DRAFT') AND (aadhaar_number_encrypted IS NOT NULL) AND (pan_number_encrypted IS NOT NULL)) OR ((document_type IS NOT NULL) AND (aadhaar_number_encrypted IS NULL) AND (pan_number_encrypted IS NULL) AND (document_number_encrypted IS NOT NULL) AND (document_number_last4 IS NOT NULL) AND (document_issuing_country IS NOT NULL) AND (document_expiry_date IS NOT NULL)))),
    CONSTRAINT kyc_submissions_rejection_chk CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT kyc_submissions_review_chk CHECK (((status IN ('DRAFT', 'PENDING')) AND (reviewed_by IS NULL) AND (reviewed_at IS NULL)) OR ((status IN ('APPROVED', 'REJECTED')) AND (reviewed_by IS NOT NULL) AND (reviewed_at IS NOT NULL))),
    CONSTRAINT kyc_submissions_reviewer_chk CHECK ((reviewed_by IS NULL) OR (reviewed_by <> user_id)),
    CONSTRAINT kyc_submissions_status_chk CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED'))
);
CREATE UNIQUE INDEX kyc_submissions_active_aadhaar_uk ON kyc_submissions (aadhaar_fingerprint) WHERE ((status IN ('PENDING', 'APPROVED')) AND (aadhaar_fingerprint IS NOT NULL));
CREATE UNIQUE INDEX kyc_submissions_active_pan_uk ON kyc_submissions (pan_fingerprint) WHERE ((status IN ('PENDING', 'APPROVED')) AND (pan_fingerprint IS NOT NULL));
CREATE UNIQUE INDEX kyc_submissions_one_draft_uk ON kyc_submissions (user_id) WHERE (status = 'DRAFT');
CREATE UNIQUE INDEX kyc_submissions_one_pending_uk ON kyc_submissions (user_id) WHERE (status = 'PENDING');
CREATE INDEX kyc_submissions_status_idx ON kyc_submissions (status, submitted_at);
CREATE INDEX kyc_submissions_user_idx ON kyc_submissions (user_id, submitted_at DESC);

CREATE TABLE kyc_documents
(
    submission_id UUID NOT NULL,
    document_id   UUID NOT NULL,
    purpose       VARCHAR(40) NOT NULL,
    CONSTRAINT kyc_documents_pkey PRIMARY KEY (submission_id, document_id),
    CONSTRAINT kyc_documents_purpose_uk UNIQUE (submission_id, purpose),
    CONSTRAINT kyc_documents_document_id_fkey FOREIGN KEY (document_id) REFERENCES stored_documents(id),
    CONSTRAINT kyc_documents_submission_id_fkey FOREIGN KEY (submission_id) REFERENCES kyc_submissions(id)
);

CREATE TABLE bank_accounts
(
    id                       UUID PRIMARY KEY,
    user_id                  UUID NOT NULL,
    account_holder_name      VARCHAR(140) NOT NULL,
    bank_name                VARCHAR(140) NOT NULL,
    country                  VARCHAR(2) NOT NULL,
    currency                 VARCHAR(3) NOT NULL,
    account_number_encrypted TEXT NOT NULL,
    account_number_last4     VARCHAR(4) NOT NULL,
    account_fingerprint      VARCHAR(64) NOT NULL,
    routing_code_encrypted   TEXT NOT NULL,
    status                   VARCHAR(25) NOT NULL,
    is_primary               BOOLEAN DEFAULT false NOT NULL,
    verified_by              UUID,
    verified_at              TIMESTAMPTZ,
    rejection_reason         VARCHAR(500),
    removed_at               TIMESTAMPTZ,
    replaces_account_id      UUID,
    proof_type               VARCHAR(20),
    proof_document_id        UUID,
    assigned_to              UUID,
    assigned_at              TIMESTAMPTZ,
    version                  BIGINT DEFAULT 0 NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    CONSTRAINT bank_accounts_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT bank_accounts_proof_document_id_fkey FOREIGN KEY (proof_document_id) REFERENCES stored_documents(id),
    CONSTRAINT bank_accounts_replaces_account_id_fkey FOREIGN KEY (replaces_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT bank_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT bank_accounts_verified_by_fkey FOREIGN KEY (verified_by) REFERENCES users(id),
    CONSTRAINT bank_accounts_assignment_chk CHECK ((assigned_to IS NULL) = (assigned_at IS NULL)),
    CONSTRAINT bank_accounts_codes_chk CHECK ((country ~ '^[A-Z]{2}$') AND (currency ~ '^[A-Z]{3}$')),
    CONSTRAINT bank_accounts_primary_chk CHECK (NOT (is_primary AND (status = 'REMOVED'))),
    CONSTRAINT bank_accounts_proof_chk CHECK (((proof_type IS NULL) = (proof_document_id IS NULL)) AND ((proof_type IS NULL) OR (proof_type IN ('BANK_STATEMENT', 'CANCELLED_CHEQUE')))),
    CONSTRAINT bank_accounts_removed_chk CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL)),
    CONSTRAINT bank_accounts_replaces_chk CHECK ((replaces_account_id IS NULL) OR (replaces_account_id <> id)),
    CONSTRAINT bank_accounts_status_chk CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'REMOVED')),
    CONSTRAINT bank_accounts_verified_chk CHECK ((status <> 'VERIFIED') OR (verified_by IS NOT NULL)),
    CONSTRAINT bank_accounts_verifier_chk CHECK ((verified_by IS NULL) OR (verified_by <> user_id))
);
CREATE INDEX bank_accounts_fingerprint_idx ON bank_accounts (account_fingerprint);
CREATE UNIQUE INDEX bank_accounts_no_duplicates_uk ON bank_accounts (user_id, account_fingerprint) WHERE (status IN ('PENDING_VERIFICATION', 'VERIFIED'));
CREATE UNIQUE INDEX bank_accounts_one_open_change_uk ON bank_accounts (replaces_account_id) WHERE (status = 'PENDING_VERIFICATION');
CREATE UNIQUE INDEX bank_accounts_one_primary_uk ON bank_accounts (user_id) WHERE is_primary;
CREATE INDEX bank_accounts_status_idx ON bank_accounts (status, created_at);

CREATE TABLE company_bank_accounts
(
    id             UUID PRIMARY KEY,
    account_name   VARCHAR(140) NOT NULL,
    bank_name      VARCHAR(140) NOT NULL,
    branch         VARCHAR(140),
    account_number VARCHAR(34) NOT NULL,
    ifsc_code      VARCHAR(11) NOT NULL,
    upi_id         VARCHAR(100),
    active         BOOLEAN DEFAULT true NOT NULL,
    version        BIGINT DEFAULT 0 NOT NULL,
    created_by     UUID NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT company_bank_accounts_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT company_bank_accounts_ifsc_chk CHECK (ifsc_code ~ '^[A-Z]{4}0[A-Z0-9]{6}$'),
    CONSTRAINT company_bank_accounts_number_chk CHECK (account_number ~ '^[0-9]{6,18}$')
);
CREATE UNIQUE INDEX company_bank_accounts_number_uk ON company_bank_accounts (ifsc_code, account_number);

-- ------------------------------------------------------------------------- countries, states and cities (loaded by v2)

CREATE TABLE countries
(
    code       VARCHAR(2) PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    iso3       VARCHAR(3),
    phone_code VARCHAR(20),
    CONSTRAINT countries_code_chk CHECK (code ~ '^[A-Z]{2}$')
);

CREATE TABLE states
(
    id           INT PRIMARY KEY,
    country_code VARCHAR(2) NOT NULL,
    name         VARCHAR(120) NOT NULL,
    code         VARCHAR(10),
    CONSTRAINT states_country_code_fkey FOREIGN KEY (country_code) REFERENCES countries(code)
);
CREATE INDEX states_country_idx ON states (country_code, name);

CREATE TABLE cities
(
    id       INT PRIMARY KEY,
    state_id INT NOT NULL,
    name     VARCHAR(120) NOT NULL,
    CONSTRAINT cities_state_id_fkey FOREIGN KEY (state_id) REFERENCES states(id)
);
CREATE INDEX cities_state_idx ON cities (state_id, name);

-- ------------------------------------------------------------------------- investment plans

CREATE TABLE investment_products
(
    id                             UUID PRIMARY KEY,
    code                           VARCHAR(20) NOT NULL,
    container_type                 VARCHAR(30) NOT NULL,
    title                          VARCHAR(140) NOT NULL,
    summary                        VARCHAR(400) NOT NULL,
    description                    TEXT NOT NULL,
    currency                       VARCHAR(3) NOT NULL,
    price                          NUMERIC(19, 4) NOT NULL,
    monthly_capital_return_percent NUMERIC(9, 4) NOT NULL,
    risk_disclosure                TEXT NOT NULL,
    terms_and_conditions           TEXT NOT NULL,
    status                         VARCHAR(20) NOT NULL,
    published_at                   TIMESTAMPTZ,
    published_by                   UUID,
    closed_at                      TIMESTAMPTZ,
    cancelled_at                   TIMESTAMPTZ,
    cancellation_reason            VARCHAR(500),
    min_tenure_years               INT NOT NULL,
    max_tenure_years               INT NOT NULL,
    version                        BIGINT DEFAULT 0 NOT NULL,
    created_by                     UUID NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,
    CONSTRAINT investment_products_code_uk UNIQUE (code),
    CONSTRAINT investment_products_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT investment_products_published_by_fkey FOREIGN KEY (published_by) REFERENCES users(id),
    CONSTRAINT investment_products_cancelled_chk CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT investment_products_capital_chk CHECK (monthly_capital_return_percent = (0)),
    CONSTRAINT investment_products_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT investment_products_container_type_chk CHECK (container_type IN ('DRY_10FT', 'DRY_20FT', 'DRY_40FT', 'HIGH_CUBE_40FT', 'HIGH_CUBE_45FT', 'REEFER_20FT', 'REEFER_40FT', 'OPEN_TOP_20FT', 'OPEN_TOP_40FT', 'FLAT_RACK_20FT', 'FLAT_RACK_40FT', 'TANK_20FT')),
    CONSTRAINT investment_products_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT investment_products_price_chk CHECK (price > (0)),
    CONSTRAINT investment_products_published_chk CHECK ((status IN ('DRAFT', 'CANCELLED')) OR (published_at IS NOT NULL)),
    CONSTRAINT investment_products_status_chk CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT investment_products_tenure_chk CHECK (((min_tenure_years >= 1) AND (min_tenure_years <= 30)) AND ((max_tenure_years >= min_tenure_years) AND (max_tenure_years <= 30)))
);
CREATE INDEX investment_products_listing_idx ON investment_products (status, published_at DESC);

CREATE TABLE plan_photos
(
    id          UUID PRIMARY KEY,
    product_id  UUID NOT NULL,
    document_id UUID NOT NULL,
    uploaded_by UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT plan_photos_uk UNIQUE (product_id, document_id),
    CONSTRAINT plan_photos_document_id_fkey FOREIGN KEY (document_id) REFERENCES stored_documents(id),
    CONSTRAINT plan_photos_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT plan_photos_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users(id)
);
CREATE INDEX plan_photos_product_idx ON plan_photos (product_id, created_at);

-- ------------------------------------------------------------------------- cart, orders, containers, payments and invoices

CREATE TABLE cart_items
(
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    product_id   UUID NOT NULL,
    quantity     INT NOT NULL,
    tenure_years INT NOT NULL,
    version      BIGINT DEFAULT 0 NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT cart_items_user_product_uk UNIQUE (user_id, product_id),
    CONSTRAINT cart_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT cart_items_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT cart_items_quantity_chk CHECK ((quantity >= 1) AND (quantity <= 50)),
    CONSTRAINT cart_items_tenure_chk CHECK ((tenure_years >= 1) AND (tenure_years <= 30))
);

CREATE TABLE orders
(
    id              UUID PRIMARY KEY,
    order_number    VARCHAR(20) NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    total_amount    NUMERIC(19, 4) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    confirmed_at    TIMESTAMPTZ,
    closed_at       TIMESTAMPTZ,
    close_reason    VARCHAR(500),
    version         BIGINT DEFAULT 0 NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT orders_idempotency_uk UNIQUE (user_id, idempotency_key),
    CONSTRAINT orders_number_uk UNIQUE (order_number),
    CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT orders_closed_chk CHECK ((status IN ('EXPIRED', 'CANCELLED')) = (closed_at IS NOT NULL)),
    CONSTRAINT orders_confirmed_chk CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT orders_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT orders_status_chk CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT orders_total_chk CHECK (total_amount > (0))
);
CREATE INDEX orders_pending_expiry_idx ON orders (expires_at) WHERE (status = 'PENDING_PAYMENT');
CREATE INDEX orders_user_idx ON orders (user_id, created_at DESC);

CREATE TABLE containers
(
    id                   UUID PRIMARY KEY,
    container_number     VARCHAR(20) NOT NULL,
    container_type       VARCHAR(30) NOT NULL,
    condition            VARCHAR(20),
    status               VARCHAR(20) NOT NULL,
    capacity_cbm         NUMERIC(8, 2),
    max_gross_kg         INT,
    tare_kg              INT,
    manufacture_year     SMALLINT,
    manufacturer         VARCHAR(100),
    current_location     VARCHAR(120),
    location_country     VARCHAR(2),
    acquisition_cost     NUMERIC(19, 4),
    acquisition_currency VARCHAR(3),
    notes                VARCHAR(1000),
    status_reason        VARCHAR(500),
    reserved_order_id    UUID,
    version              BIGINT DEFAULT 0 NOT NULL,
    created_by           UUID NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT containers_number_uk UNIQUE (container_number),
    CONSTRAINT containers_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT containers_reserved_order_id_fkey FOREIGN KEY (reserved_order_id) REFERENCES orders(id),
    CONSTRAINT containers_capacity_chk CHECK (capacity_cbm > (0)),
    CONSTRAINT containers_condition_chk CHECK (condition IN ('NEW', 'CARGO_WORTHY', 'WIND_WATERTIGHT')),
    CONSTRAINT containers_cost_chk CHECK (((acquisition_cost IS NULL) AND (acquisition_currency IS NULL)) OR ((acquisition_cost >= (0)) AND (acquisition_currency ~ '^[A-Z]{3}$'))),
    CONSTRAINT containers_country_chk CHECK (location_country ~ '^[A-Z]{2}$'),
    CONSTRAINT containers_reserved_chk CHECK ((status = 'RESERVED') = (reserved_order_id IS NOT NULL)),
    CONSTRAINT containers_status_chk CHECK (status IN ('AVAILABLE', 'RESERVED', 'ON_LEASE', 'MAINTENANCE', 'RETIRED')),
    CONSTRAINT containers_type_chk CHECK (container_type IN ('DRY_10FT', 'DRY_20FT', 'DRY_40FT', 'HIGH_CUBE_40FT', 'HIGH_CUBE_45FT', 'REEFER_20FT', 'REEFER_40FT', 'OPEN_TOP_20FT', 'OPEN_TOP_40FT', 'FLAT_RACK_20FT', 'FLAT_RACK_40FT', 'TANK_20FT')),
    CONSTRAINT containers_weights_chk CHECK ((tare_kg > 0) AND (max_gross_kg > tare_kg)),
    CONSTRAINT containers_year_chk CHECK ((manufacture_year >= 1960) AND (manufacture_year <= 2100))
);
CREATE INDEX containers_allocation_idx ON containers (container_type, status, created_at);
CREATE INDEX containers_reserved_order_idx ON containers (reserved_order_id) WHERE (reserved_order_id IS NOT NULL);
CREATE INDEX containers_status_idx ON containers (status);

CREATE TABLE order_items
(
    id                             UUID PRIMARY KEY,
    order_id                       UUID NOT NULL,
    product_id                     UUID NOT NULL,
    product_code                   VARCHAR(20) NOT NULL,
    product_title                  VARCHAR(140) NOT NULL,
    container_type                 VARCHAR(30) NOT NULL,
    container_id                   UUID,
    amount                         NUMERIC(19, 4) NOT NULL,
    currency                       VARCHAR(3) NOT NULL,
    monthly_rent_percent           NUMERIC(5, 2) NOT NULL,
    monthly_capital_return_percent NUMERIC(9, 4) NOT NULL,
    tenure_months                  INT NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    CONSTRAINT order_items_order_container_uk UNIQUE (order_id, container_id),
    CONSTRAINT order_items_container_id_fkey FOREIGN KEY (container_id) REFERENCES containers(id),
    CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT order_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT order_items_amount_chk CHECK (amount > (0))
);
CREATE INDEX order_items_order_idx ON order_items (order_id);
CREATE TRIGGER order_items_immutable BEFORE DELETE OR UPDATE ON order_items FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE payments
(
    id                      UUID PRIMARY KEY,
    order_id                UUID NOT NULL,
    user_id                 UUID NOT NULL,
    method                  VARCHAR(20) NOT NULL,
    provider                VARCHAR(20) NOT NULL,
    provider_reference      VARCHAR(100) NOT NULL,
    amount                  NUMERIC(19, 4) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    status                  VARCHAR(20) NOT NULL,
    idempotency_key         VARCHAR(100) NOT NULL,
    failure_reason          VARCHAR(500),
    external_reference      VARCHAR(100),
    confirmed_by            UUID,
    settled_at              TIMESTAMPTZ,
    refunded_by             UUID,
    refunded_at             TIMESTAMPTZ,
    refund_reference        VARCHAR(100),
    refund_reason           VARCHAR(500),
    company_bank_account_id UUID,
    deposit_mode            VARCHAR(20),
    deposit_reference       VARCHAR(100),
    deposit_submitted_at    TIMESTAMPTZ,
    task_assignee_id        UUID,
    task_assigned_at        TIMESTAMPTZ,
    version                 BIGINT DEFAULT 0 NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT payments_idempotency_uk UNIQUE (order_id, idempotency_key),
    CONSTRAINT payments_provider_reference_uk UNIQUE (provider, provider_reference),
    CONSTRAINT payments_company_bank_account_id_fkey FOREIGN KEY (company_bank_account_id) REFERENCES company_bank_accounts(id),
    CONSTRAINT payments_confirmed_by_fkey FOREIGN KEY (confirmed_by) REFERENCES users(id),
    CONSTRAINT payments_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT payments_refunded_by_fkey FOREIGN KEY (refunded_by) REFERENCES users(id),
    CONSTRAINT payments_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id),
    CONSTRAINT payments_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT payments_amount_chk CHECK (amount > (0)),
    CONSTRAINT payments_deposit_complete_chk CHECK (((company_bank_account_id IS NULL) AND (deposit_mode IS NULL) AND (deposit_reference IS NULL) AND (deposit_submitted_at IS NULL)) OR ((company_bank_account_id IS NOT NULL) AND (deposit_mode IS NOT NULL) AND (deposit_reference IS NOT NULL) AND (deposit_submitted_at IS NOT NULL) AND (method = 'BANK_TRANSFER'))),
    CONSTRAINT payments_deposit_mode_chk CHECK (deposit_mode IN ('ONLINE', 'CHEQUE', 'CASH_DEPOSIT')),
    CONSTRAINT payments_method_chk CHECK (method IN ('BANK_TRANSFER', 'OFFICE_CASH', 'CARD')),
    CONSTRAINT payments_refunded_chk CHECK ((status = 'REFUNDED') = (refunded_at IS NOT NULL)),
    CONSTRAINT payments_settled_chk CHECK ((status IN ('SUCCEEDED', 'REFUND_REQUIRED', 'REFUNDED')) = (settled_at IS NOT NULL)),
    CONSTRAINT payments_status_chk CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'REFUND_REQUIRED', 'REFUNDED')),
    CONSTRAINT payments_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL))
);
CREATE UNIQUE INDEX payments_one_pending_uk ON payments (order_id) WHERE (status = 'PENDING');
CREATE UNIQUE INDEX payments_one_succeeded_uk ON payments (order_id) WHERE (status = 'SUCCEEDED');
CREATE INDEX payments_order_idx ON payments (order_id, created_at);
CREATE INDEX payments_status_idx ON payments (status, created_at);

CREATE TABLE invoices
(
    id             UUID PRIMARY KEY,
    invoice_number VARCHAR(30) NOT NULL,
    order_id       UUID NOT NULL,
    user_id        UUID NOT NULL,
    payment_id     UUID NOT NULL,
    currency       VARCHAR(3) NOT NULL,
    total_amount   NUMERIC(19, 4) NOT NULL,
    issuer_name    VARCHAR(200) NOT NULL,
    issuer_address VARCHAR(500) NOT NULL,
    issuer_tax_id  VARCHAR(50),
    buyer_name     VARCHAR(200) NOT NULL,
    buyer_email    VARCHAR(320) NOT NULL,
    buyer_address  VARCHAR(500),
    notes          VARCHAR(1000),
    issued_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT invoices_number_uk UNIQUE (invoice_number),
    CONSTRAINT invoices_order_uk UNIQUE (order_id),
    CONSTRAINT invoices_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT invoices_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT invoices_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT invoices_total_chk CHECK (total_amount > (0))
);
CREATE INDEX invoices_user_idx ON invoices (user_id, issued_at DESC);
CREATE TRIGGER invoices_immutable BEFORE DELETE OR UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE invoice_lines
(
    id          UUID PRIMARY KEY,
    invoice_id  UUID NOT NULL,
    line_number INT NOT NULL,
    description VARCHAR(300) NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    CONSTRAINT invoice_lines_number_uk UNIQUE (invoice_id, line_number),
    CONSTRAINT invoice_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT invoice_lines_amount_chk CHECK (amount > (0))
);
CREATE TRIGGER invoice_lines_immutable BEFORE DELETE OR UPDATE ON invoice_lines FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------- holdings and lease agreements

CREATE TABLE holdings
(
    id                             UUID PRIMARY KEY,
    user_id                        UUID NOT NULL,
    product_id                     UUID NOT NULL,
    order_id                       UUID NOT NULL,
    order_item_id                  UUID NOT NULL,
    container_id                   UUID,
    amount                         NUMERIC(19, 4) NOT NULL,
    currency                       VARCHAR(3) NOT NULL,
    monthly_rent_percent           NUMERIC(5, 2) NOT NULL,
    monthly_capital_return_percent NUMERIC(9, 4) NOT NULL,
    tenure_months                  INT NOT NULL,
    lease_starts_on                DATE,
    lease_ends_on                  DATE,
    status                         VARCHAR(20) NOT NULL,
    confirmed_at                   TIMESTAMPTZ NOT NULL,
    matured_at                     TIMESTAMPTZ,
    assigned_at                    TIMESTAMPTZ,
    assigned_by                    UUID,
    paid_at_office                 BOOLEAN DEFAULT false NOT NULL,
    task_assignee_id               UUID,
    task_assigned_at               TIMESTAMPTZ,
    version                        BIGINT DEFAULT 0 NOT NULL,
    created_at                     TIMESTAMPTZ NOT NULL,
    updated_at                     TIMESTAMPTZ NOT NULL,
    CONSTRAINT holdings_order_item_uk UNIQUE (order_item_id),
    CONSTRAINT holdings_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES users(id),
    CONSTRAINT holdings_container_id_fkey FOREIGN KEY (container_id) REFERENCES containers(id),
    CONSTRAINT holdings_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT holdings_order_item_id_fkey FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT holdings_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT holdings_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id),
    CONSTRAINT holdings_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT holdings_amount_chk CHECK (amount > (0)),
    CONSTRAINT holdings_assignment_chk CHECK (((status = 'AWAITING_CONTAINER') = (container_id IS NULL)) AND ((status IN ('AWAITING_CONTAINER', 'AWAITING_ACCEPTANCE')) = (lease_starts_on IS NULL)) AND ((lease_starts_on IS NULL) = (lease_ends_on IS NULL))),
    CONSTRAINT holdings_lease_chk CHECK (lease_ends_on > lease_starts_on),
    CONSTRAINT holdings_matured_chk CHECK ((status = 'MATURED') = (matured_at IS NOT NULL)),
    CONSTRAINT holdings_status_chk CHECK (status IN ('AWAITING_CONTAINER', 'AWAITING_ACCEPTANCE', 'ACTIVE', 'MATURED')),
    CONSTRAINT holdings_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL))
);
CREATE UNIQUE INDEX holdings_active_container_uk ON holdings (container_id) WHERE (status IN ('AWAITING_ACCEPTANCE', 'ACTIVE'));
CREATE INDEX holdings_awaiting_idx ON holdings (confirmed_at) WHERE (status = 'AWAITING_CONTAINER');
CREATE INDEX holdings_product_idx ON holdings (product_id);
CREATE INDEX holdings_user_idx ON holdings (user_id, confirmed_at DESC);

CREATE TABLE agreement_templates
(
    id         UUID PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    version    INT NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT agreement_templates_version_uk UNIQUE (version),
    CONSTRAINT agreement_templates_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT agreement_templates_body_chk CHECK ((length(body) >= 1) AND (length(body) <= 50000)),
    CONSTRAINT agreement_templates_version_chk CHECK (version > 0)
);
CREATE TRIGGER agreement_templates_immutable BEFORE DELETE OR UPDATE ON agreement_templates FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE lease_agreements
(
    id                  UUID PRIMARY KEY,
    agreement_number    VARCHAR(30) NOT NULL,
    holding_id          UUID NOT NULL,
    order_id            UUID NOT NULL,
    user_id             UUID NOT NULL,
    container_number    VARCHAR(20) NOT NULL,
    template_id         UUID NOT NULL,
    title               VARCHAR(200) NOT NULL,
    body                TEXT NOT NULL,
    generated_by        UUID,
    generated_at        TIMESTAMPTZ NOT NULL,
    acceptance_required BOOLEAN DEFAULT false NOT NULL,
    CONSTRAINT lease_agreements_holding_uk UNIQUE (holding_id),
    CONSTRAINT lease_agreements_number_uk UNIQUE (agreement_number),
    CONSTRAINT lease_agreements_generated_by_fkey FOREIGN KEY (generated_by) REFERENCES users(id),
    CONSTRAINT lease_agreements_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id),
    CONSTRAINT lease_agreements_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT lease_agreements_template_id_fkey FOREIGN KEY (template_id) REFERENCES agreement_templates(id),
    CONSTRAINT lease_agreements_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX lease_agreements_order_idx ON lease_agreements (order_id);
CREATE INDEX lease_agreements_user_idx ON lease_agreements (user_id, generated_at DESC);
CREATE TRIGGER lease_agreements_immutable BEFORE DELETE OR UPDATE ON lease_agreements FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE lease_agreement_acceptances
(
    agreement_id    UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    accepted_at     TIMESTAMPTZ NOT NULL,
    lease_starts_on DATE NOT NULL,
    lease_ends_on   DATE NOT NULL,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(512),
    CONSTRAINT lease_agreement_acceptances_agreement_id_fkey FOREIGN KEY (agreement_id) REFERENCES lease_agreements(id),
    CONSTRAINT lease_agreement_acceptances_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT lease_agreement_acceptances_lease_chk CHECK (lease_ends_on > lease_starts_on)
);
CREATE TRIGGER lease_agreement_acceptances_immutable BEFORE DELETE OR UPDATE ON lease_agreement_acceptances FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ------------------------------------------------------------------------- ledger, rent credits and accruals

CREATE TABLE ledger_accounts
(
    id            UUID PRIMARY KEY,
    account_type  VARCHAR(30) NOT NULL,
    owner_user_id UUID,
    currency      VARCHAR(3) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_accounts_owner_user_id_fkey FOREIGN KEY (owner_user_id) REFERENCES users(id),
    CONSTRAINT ledger_accounts_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ledger_accounts_owner_chk CHECK ((account_type IN ('INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS')) = (owner_user_id IS NOT NULL)),
    CONSTRAINT ledger_accounts_type_chk CHECK (account_type IN ('RENTAL_CASH', 'INVESTOR_EARNINGS', 'INVESTOR_CASH_EARNINGS', 'INVESTOR_REFERRAL_EARNINGS', 'PLATFORM_ADJUSTMENTS', 'PLATFORM_REFERRAL_EXPENSE', 'WITHDRAWALS_IN_TRANSIT', 'PLATFORM_RENT_EXPENSE', 'PLATFORM_CAPITAL_RETURNS'))
);
CREATE UNIQUE INDEX ledger_accounts_investor_uk ON ledger_accounts (account_type, owner_user_id, currency) WHERE (owner_user_id IS NOT NULL);
CREATE UNIQUE INDEX ledger_accounts_platform_uk ON ledger_accounts (account_type, currency) WHERE (owner_user_id IS NULL);
CREATE TRIGGER ledger_accounts_immutable BEFORE DELETE OR UPDATE ON ledger_accounts FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE ledger_transactions
(
    id               UUID PRIMARY KEY,
    transaction_type VARCHAR(30) NOT NULL,
    reference        VARCHAR(100) NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    description      VARCHAR(300) NOT NULL,
    created_by       UUID,
    created_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_transactions_reference_uk UNIQUE (transaction_type, reference),
    CONSTRAINT ledger_transactions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT ledger_transactions_type_chk CHECK (transaction_type IN ('INVESTOR_PAYOUT', 'ADJUSTMENT', 'REFERRAL_COMMISSION', 'WITHDRAWAL_RESERVE', 'WITHDRAWAL_RELEASE', 'WITHDRAWAL_PAYOUT'))
);
CREATE INDEX ledger_transactions_created_idx ON ledger_transactions (created_at DESC);
CREATE TRIGGER ledger_transactions_immutable BEFORE DELETE OR UPDATE ON ledger_transactions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE ledger_entries
(
    id             UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id     UUID NOT NULL,
    direction      VARCHAR(6) NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT ledger_entries_account_id_fkey FOREIGN KEY (account_id) REFERENCES ledger_accounts(id),
    CONSTRAINT ledger_entries_transaction_id_fkey FOREIGN KEY (transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT ledger_entries_amount_chk CHECK (amount > (0)),
    CONSTRAINT ledger_entries_direction_chk CHECK (direction IN ('DEBIT', 'CREDIT'))
);
CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id, created_at DESC);
CREATE INDEX ledger_entries_transaction_idx ON ledger_entries (transaction_id);
CREATE CONSTRAINT TRIGGER ledger_entries_balanced AFTER INSERT ON ledger_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION ledger_check_balanced();
CREATE TRIGGER ledger_entries_immutable BEFORE DELETE OR UPDATE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE rent_credit_settings
(
    id                      SMALLINT DEFAULT 1 PRIMARY KEY,
    credit_interval_minutes INT,
    updated_by              UUID,
    updated_at              TIMESTAMPTZ,
    CONSTRAINT rent_credit_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT rent_credit_settings_interval_chk CHECK ((credit_interval_minutes IS NULL) OR (((credit_interval_minutes >= 5) AND (credit_interval_minutes <= 1440)) AND ((1440 % credit_interval_minutes) = 0))),
    CONSTRAINT rent_credit_settings_single_row_chk CHECK (id = 1)
);

-- The smallest total an order may have; zero means no minimum. One row, changed by staff.
CREATE TABLE order_settings
(
    id               SMALLINT DEFAULT 1 PRIMARY KEY,
    min_order_amount NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    updated_by       UUID,
    updated_at       TIMESTAMPTZ,
    CONSTRAINT order_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT order_settings_min_chk CHECK (min_order_amount >= (0)),
    CONSTRAINT order_settings_single_row_chk CHECK (id = 1)
);

-- Monthly rent by order value: an order whose total is from min_order_amount to max_order_amount (inclusive, whole
-- rupees; NULL for no upper limit) earns monthly_rent_percent of each container's price a month. Bands do not overlap
-- or leave gaps; an order outside every band cannot be placed.
CREATE TABLE rent_rate_tiers
(
    min_order_amount     NUMERIC(19, 4) PRIMARY KEY,
    max_order_amount     NUMERIC(19, 4),
    monthly_rent_percent NUMERIC(5, 2)  NOT NULL,
    updated_by           UUID,
    updated_at           TIMESTAMPTZ    NOT NULL,
    CONSTRAINT rent_rate_tiers_max_chk CHECK ((max_order_amount IS NULL) OR (max_order_amount >= min_order_amount)),
    CONSTRAINT rent_rate_tiers_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT rent_rate_tiers_min_chk CHECK (min_order_amount >= (0)),
    CONSTRAINT rent_rate_tiers_rent_chk CHECK ((monthly_rent_percent > (0)) AND (monthly_rent_percent <= (20)))
);

CREATE TABLE rent_credits
(
    id                    UUID PRIMARY KEY,
    holding_id            UUID NOT NULL,
    user_id               UUID NOT NULL,
    day                   DATE NOT NULL,
    rent_amount           NUMERIC(19, 4) NOT NULL,
    capital_amount        NUMERIC(19, 4) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    ledger_transaction_id UUID NOT NULL,
    from_at               TIMESTAMPTZ NOT NULL,
    to_at                 TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT rent_credits_from_uk UNIQUE (holding_id, from_at),
    CONSTRAINT rent_credits_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id),
    CONSTRAINT rent_credits_ledger_transaction_id_fkey FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT rent_credits_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT rent_credits_amounts_chk CHECK ((rent_amount >= (0)) AND (capital_amount >= (0))),
    CONSTRAINT rent_credits_hours_chk CHECK (from_at < to_at)
);
CREATE INDEX rent_credits_holding_idx ON rent_credits (holding_id, from_at);
CREATE INDEX rent_credits_user_idx ON rent_credits (user_id, day DESC);

CREATE TABLE rent_accruals
(
    id               UUID PRIMARY KEY,
    holding_id       UUID NOT NULL,
    user_id          UUID NOT NULL,
    product_id       UUID NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    monthly_rent     NUMERIC(19, 4) NOT NULL,
    capital_total    NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    tenure_months    INT NOT NULL,
    starts_at        TIMESTAMPTZ NOT NULL,
    ends_at          TIMESTAMPTZ NOT NULL,
    credited_until   TIMESTAMPTZ NOT NULL,
    credited_rent    NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    credited_capital NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    completed_at     TIMESTAMPTZ,
    version          BIGINT DEFAULT 0 NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT rent_accruals_holding_uk UNIQUE (holding_id),
    CONSTRAINT rent_accruals_holding_id_fkey FOREIGN KEY (holding_id) REFERENCES holdings(id),
    CONSTRAINT rent_accruals_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT rent_accruals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT rent_accruals_amounts_chk CHECK ((monthly_rent >= (0)) AND (capital_total >= (0)) AND (tenure_months > 0) AND ((monthly_rent + capital_total) > (0))),
    CONSTRAINT rent_accruals_credited_chk CHECK ((credited_until >= starts_at) AND (credited_until <= ends_at) AND (credited_rent >= (0)) AND (credited_rent <= (monthly_rent * (tenure_months))) AND (credited_capital >= (0)) AND (credited_capital <= capital_total) AND ((completed_at IS NOT NULL) = (credited_until = ends_at))),
    CONSTRAINT rent_accruals_lease_chk CHECK (starts_at < ends_at)
);
CREATE INDEX rent_accruals_running_idx ON rent_accruals (credited_until) WHERE (completed_at IS NULL);
CREATE INDEX rent_accruals_user_idx ON rent_accruals (user_id, starts_at);

-- ------------------------------------------------------------------------- referrals

CREATE TABLE referral_codes
(
    user_id    UUID PRIMARY KEY,
    code       VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_codes_code_uk UNIQUE (code),
    CONSTRAINT referral_codes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT referral_codes_format_chk CHECK (code ~ '^[2-9A-HJ-NP-Z]{8}$')
);
CREATE TRIGGER referral_codes_immutable BEFORE DELETE OR UPDATE ON referral_codes FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE referrals
(
    user_id     UUID PRIMARY KEY,
    referrer_id UUID NOT NULL,
    code        VARCHAR(12) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT referrals_referrer_id_fkey FOREIGN KEY (referrer_id) REFERENCES users(id),
    CONSTRAINT referrals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT referrals_not_self_chk CHECK (user_id <> referrer_id)
);
CREATE INDEX referrals_referrer_idx ON referrals (referrer_id, created_at);
CREATE TRIGGER referrals_immutable BEFORE DELETE OR UPDATE ON referrals FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE referral_rate_versions
(
    id             UUID PRIMARY KEY,
    effective_from TIMESTAMPTZ NOT NULL,
    level1_percent NUMERIC(5, 3) NOT NULL,
    level2_percent NUMERIC(5, 3) NOT NULL,
    level3_percent NUMERIC(5, 3) NOT NULL,
    level4_percent NUMERIC(5, 3) NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    cancelled_at   TIMESTAMPTZ,
    cancelled_by   UUID,
    created_by     UUID,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_rate_versions_cancelled_by_fkey FOREIGN KEY (cancelled_by) REFERENCES users(id),
    CONSTRAINT referral_rate_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT referral_rate_versions_cancelled_chk CHECK ((cancelled_at IS NULL) = (cancelled_by IS NULL)),
    CONSTRAINT referral_rate_versions_rates_chk CHECK (((level1_percent >= (0)) AND (level1_percent <= (10))) AND ((level2_percent >= (0)) AND (level2_percent <= (10))) AND ((level3_percent >= (0)) AND (level3_percent <= (10))) AND ((level4_percent >= (0)) AND (level4_percent <= (10))) AND ((((level1_percent + level2_percent) + level3_percent) + level4_percent) <= (20)))
);
CREATE UNIQUE INDEX referral_rate_versions_effective_uk ON referral_rate_versions (effective_from) WHERE (cancelled_at IS NULL);

CREATE TABLE referral_earnings
(
    id                  UUID PRIMARY KEY,
    source_user_id      UUID NOT NULL,
    beneficiary_user_id UUID NOT NULL,
    product_id          UUID NOT NULL,
    level               SMALLINT NOT NULL,
    base_amount         NUMERIC(19, 4) NOT NULL,
    rate_percent        NUMERIC(5, 3) NOT NULL,
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    rate_version_id     UUID NOT NULL,
    source_credit_id    UUID,
    source_holding_id   UUID NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_earnings_beneficiary_user_id_fkey FOREIGN KEY (beneficiary_user_id) REFERENCES users(id),
    CONSTRAINT referral_earnings_product_id_fkey FOREIGN KEY (product_id) REFERENCES investment_products(id),
    CONSTRAINT referral_earnings_rate_version_id_fkey FOREIGN KEY (rate_version_id) REFERENCES referral_rate_versions(id),
    CONSTRAINT referral_earnings_source_credit_id_fkey FOREIGN KEY (source_credit_id) REFERENCES rent_credits(id),
    CONSTRAINT referral_earnings_source_holding_id_fkey FOREIGN KEY (source_holding_id) REFERENCES holdings(id),
    CONSTRAINT referral_earnings_source_user_id_fkey FOREIGN KEY (source_user_id) REFERENCES users(id),
    CONSTRAINT referral_earnings_amount_chk CHECK ((amount > (0)) AND (base_amount > (0))),
    CONSTRAINT referral_earnings_level_chk CHECK ((level >= 1) AND (level <= 4)),
    CONSTRAINT referral_earnings_not_self_chk CHECK (source_user_id <> beneficiary_user_id)
);
CREATE INDEX referral_earnings_beneficiary_idx ON referral_earnings (beneficiary_user_id, created_at DESC);
CREATE UNIQUE INDEX referral_earnings_credit_level_uk ON referral_earnings (source_credit_id, level) WHERE (source_credit_id IS NOT NULL);
CREATE INDEX referral_earnings_holding_idx ON referral_earnings (source_holding_id, level, beneficiary_user_id);
CREATE TRIGGER referral_earnings_immutable BEFORE DELETE OR UPDATE ON referral_earnings FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE referral_payout_modes
(
    id        UUID PRIMARY KEY,
    user_id   UUID NOT NULL,
    year      SMALLINT NOT NULL,
    mode      VARCHAR(20) NOT NULL,
    chosen_by UUID NOT NULL,
    chosen_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT referral_payout_modes_user_year_uk UNIQUE (user_id, year),
    CONSTRAINT referral_payout_modes_chosen_by_fkey FOREIGN KEY (chosen_by) REFERENCES users(id),
    CONSTRAINT referral_payout_modes_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT referral_payout_modes_mode_chk CHECK (mode IN ('BANK_TRANSFER', 'OFFICE_CASH'))
);

-- ------------------------------------------------------------------------- support, kyc follow-ups, crm and notifications

CREATE TABLE support_tickets
(
    id                    UUID PRIMARY KEY,
    reference             VARCHAR(20) NOT NULL,
    user_id               UUID NOT NULL,
    subject               VARCHAR(200) NOT NULL,
    category              VARCHAR(20) NOT NULL,
    related_type          VARCHAR(20),
    related_id            UUID,
    related_label         VARCHAR(60),
    status                VARCHAR(25) NOT NULL,
    priority              VARCHAR(10) NOT NULL,
    assignee_id           UUID,
    first_response_due_at TIMESTAMPTZ NOT NULL,
    first_responded_at    TIMESTAMPTZ,
    last_message_at       TIMESTAMPTZ NOT NULL,
    resolved_at           TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    team                  VARCHAR(20) DEFAULT 'SUPPORT' NOT NULL,
    version               BIGINT DEFAULT 0 NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT support_tickets_reference_uk UNIQUE (reference),
    CONSTRAINT support_tickets_assignee_id_fkey FOREIGN KEY (assignee_id) REFERENCES users(id),
    CONSTRAINT support_tickets_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT support_tickets_category_chk CHECK (category IN ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'REFERRAL', 'OTHER')),
    CONSTRAINT support_tickets_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT support_tickets_priority_chk CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_tickets_related_chk CHECK (((related_type IS NULL) AND (related_id IS NULL) AND (related_label IS NULL)) OR ((related_type IN ('ORDER', 'WITHDRAWAL', 'HOLDING')) AND (related_id IS NOT NULL) AND (related_label IS NOT NULL))),
    CONSTRAINT support_tickets_resolved_chk CHECK ((status <> 'RESOLVED') OR (resolved_at IS NOT NULL)),
    CONSTRAINT support_tickets_status_chk CHECK (status IN ('OPEN', 'WAITING_ON_CUSTOMER', 'RESOLVED', 'CLOSED')),
    CONSTRAINT support_tickets_team_chk CHECK (team IN ('SUPPORT', 'FINANCE'))
);
CREATE INDEX support_tickets_assignee_idx ON support_tickets (assignee_id, status);
CREATE INDEX support_tickets_queue_idx ON support_tickets (status, first_response_due_at);
CREATE INDEX support_tickets_team_idx ON support_tickets (team, last_message_at DESC);
CREATE INDEX support_tickets_user_idx ON support_tickets (user_id, last_message_at DESC);

CREATE TABLE ticket_messages
(
    id         UUID PRIMARY KEY,
    ticket_id  UUID NOT NULL,
    author_id  UUID,
    from_staff BOOLEAN NOT NULL,
    internal   BOOLEAN NOT NULL,
    body       VARCHAR(8000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_messages_author_id_fkey FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT ticket_messages_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    CONSTRAINT ticket_messages_internal_chk CHECK ((NOT internal) OR from_staff)
);
CREATE INDEX ticket_messages_ticket_idx ON ticket_messages (ticket_id, created_at);
CREATE TRIGGER ticket_messages_immutable BEFORE DELETE OR UPDATE ON ticket_messages FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE ticket_attachments
(
    id           UUID PRIMARY KEY,
    ticket_id    UUID NOT NULL,
    message_id   UUID NOT NULL,
    document_id  UUID NOT NULL,
    filename     VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_attachments_document_uk UNIQUE (document_id),
    CONSTRAINT ticket_attachments_document_id_fkey FOREIGN KEY (document_id) REFERENCES stored_documents(id),
    CONSTRAINT ticket_attachments_message_id_fkey FOREIGN KEY (message_id) REFERENCES ticket_messages(id),
    CONSTRAINT ticket_attachments_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id)
);
CREATE INDEX ticket_attachments_message_idx ON ticket_attachments (message_id);
CREATE TRIGGER ticket_attachments_immutable BEFORE DELETE OR UPDATE ON ticket_attachments FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE ticket_events
(
    id              UUID PRIMARY KEY,
    ticket_id       UUID NOT NULL,
    actor_id        UUID,
    actor_is_staff  BOOLEAN NOT NULL,
    type            VARCHAR(20) NOT NULL,
    from_value      VARCHAR(25),
    to_value        VARCHAR(25),
    subject_user_id UUID,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ticket_events_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ticket_events_subject_user_id_fkey FOREIGN KEY (subject_user_id) REFERENCES users(id),
    CONSTRAINT ticket_events_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    CONSTRAINT ticket_events_type_chk CHECK (type IN ('OPENED', 'STATUS_CHANGED', 'REOPENED', 'ASSIGNED', 'UNASSIGNED', 'PRIORITY_CHANGED', 'TEAM_CHANGED'))
);
CREATE INDEX ticket_events_ticket_idx ON ticket_events (ticket_id, created_at);
CREATE TRIGGER ticket_events_immutable BEFORE DELETE OR UPDATE ON ticket_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE kyc_follow_ups
(
    user_id   UUID PRIMARY KEY,
    ticket_id UUID NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT kyc_follow_ups_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    CONSTRAINT kyc_follow_ups_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE leads
(
    id                 UUID PRIMARY KEY,
    reference          VARCHAR(20) NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100),
    email              VARCHAR(254),
    phone              VARCHAR(40),
    country            VARCHAR(2),
    source             VARCHAR(20) NOT NULL,
    interest           VARCHAR(20) NOT NULL,
    estimated_amount   NUMERIC(19, 4),
    estimated_currency VARCHAR(3),
    message            VARCHAR(2000),
    stage              VARCHAR(20) NOT NULL,
    lost_reason        VARCHAR(500),
    owner_id           UUID,
    user_id            UUID,
    won_amount         NUMERIC(19, 4),
    won_currency       VARCHAR(3),
    won_order_id       UUID,
    closed_at          TIMESTAMPTZ,
    next_follow_up_at  TIMESTAMPTZ,
    consent_at         TIMESTAMPTZ,
    version            BIGINT DEFAULT 0 NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT leads_reference_uk UNIQUE (reference),
    CONSTRAINT leads_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT leads_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT leads_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT leads_won_order_id_fkey FOREIGN KEY (won_order_id) REFERENCES orders(id),
    CONSTRAINT leads_closed_chk CHECK ((stage IN ('WON', 'LOST')) = (closed_at IS NOT NULL)),
    CONSTRAINT leads_consent_chk CHECK ((source <> 'WEBSITE') OR ((consent_at IS NOT NULL) AND (email IS NOT NULL))),
    CONSTRAINT leads_contact_chk CHECK ((email IS NOT NULL) OR (phone IS NOT NULL)),
    CONSTRAINT leads_country_chk CHECK ((country IS NULL) OR (country ~ '^[A-Z]{2}$')),
    CONSTRAINT leads_email_chk CHECK ((email IS NULL) OR (email = lower(email))),
    CONSTRAINT leads_estimate_chk CHECK (((estimated_amount IS NULL) AND (estimated_currency IS NULL)) OR ((estimated_amount > (0)) AND (estimated_currency ~ '^[A-Z]{3}$'))),
    CONSTRAINT leads_interest_chk CHECK (interest IN ('STANDALONE', 'UNSURE')),
    CONSTRAINT leads_lost_chk CHECK ((stage = 'LOST') = (lost_reason IS NOT NULL)),
    CONSTRAINT leads_source_chk CHECK (source IN ('WEBSITE', 'STAFF')),
    CONSTRAINT leads_stage_chk CHECK (stage IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'WON', 'LOST')),
    CONSTRAINT leads_won_chk CHECK (((won_amount IS NULL) AND (won_currency IS NULL)) OR ((stage = 'WON') AND (won_amount > (0)) AND (won_currency ~ '^[A-Z]{3}$')))
);
CREATE INDEX leads_follow_up_idx ON leads (next_follow_up_at) WHERE (stage <> ALL ((ARRAY['WON', 'LOST'])::text[]));
CREATE UNIQUE INDEX leads_open_email_uk ON leads (email) WHERE ((email IS NOT NULL) AND (stage <> ALL ((ARRAY['WON', 'LOST'])::text[])));
CREATE INDEX leads_owner_idx ON leads (owner_id, stage);
CREATE INDEX leads_stage_idx ON leads (stage, created_at DESC);
CREATE INDEX leads_user_idx ON leads (user_id);

CREATE TABLE lead_activities
(
    id            UUID PRIMARY KEY,
    lead_id       UUID NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    body          VARCHAR(4000) NOT NULL,
    actor_id      UUID,
    created_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT lead_activities_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT lead_activities_lead_id_fkey FOREIGN KEY (lead_id) REFERENCES leads(id),
    CONSTRAINT lead_activities_type_chk CHECK (activity_type IN ('NOTE', 'CALL', 'EMAIL', 'MEETING', 'STAGE_CHANGE', 'ASSIGNMENT', 'SYSTEM'))
);
CREATE INDEX lead_activities_lead_idx ON lead_activities (lead_id, created_at DESC);
CREATE TRIGGER lead_activities_immutable BEFORE DELETE OR UPDATE ON lead_activities FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE notifications
(
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    type       VARCHAR(40) NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(1000),
    link       VARCHAR(300),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX notifications_unread_idx ON notifications (user_id) WHERE (read_at IS NULL);
CREATE INDEX notifications_user_idx ON notifications (user_id, created_at DESC);

-- ------------------------------------------------------------------------- withdrawals

CREATE TABLE withdrawal_settings
(
    id             SMALLINT DEFAULT 1 PRIMARY KEY,
    minimum_amount NUMERIC(19, 4),
    updated_by     UUID,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT withdrawal_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id),
    CONSTRAINT withdrawal_settings_minimum_chk CHECK ((minimum_amount IS NULL) OR (minimum_amount > (0))),
    CONSTRAINT withdrawal_settings_single_row_chk CHECK (id = 1)
);

CREATE TABLE withdrawal_batches
(
    id            UUID PRIMARY KEY,
    reference     VARCHAR(20) NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    item_count    INT NOT NULL,
    total_amount  NUMERIC(19, 4) NOT NULL,
    sent_by       UUID,
    sent_at       TIMESTAMPTZ,
    closed_at     TIMESTAMPTZ,
    period        VARCHAR(7),
    kind          VARCHAR(20),
    payout_method VARCHAR(20) DEFAULT 'BANK_TRANSFER' NOT NULL,
    version       BIGINT DEFAULT 0 NOT NULL,
    created_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT withdrawal_batches_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawal_batches_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT withdrawal_batches_sent_by_fkey FOREIGN KEY (sent_by) REFERENCES users(id),
    CONSTRAINT withdrawal_batches_closed_chk CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT withdrawal_batches_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT withdrawal_batches_kind_chk CHECK ((kind IS NULL) OR (kind IN ('RENT', 'REFERRAL'))),
    CONSTRAINT withdrawal_batches_payout_method_chk CHECK (payout_method IN ('BANK_TRANSFER', 'OFFICE_CASH')),
    CONSTRAINT withdrawal_batches_period_chk CHECK ((period IS NULL) OR (period ~ '^[0-9]{4}-[0-9]{2}$')),
    CONSTRAINT withdrawal_batches_sent_chk CHECK ((status IN ('SENT', 'CLOSED')) = (sent_at IS NOT NULL)),
    CONSTRAINT withdrawal_batches_status_chk CHECK (status IN ('CREATED', 'SENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT withdrawal_batches_totals_chk CHECK ((item_count > 0) AND (total_amount > (0)))
);
CREATE INDEX withdrawal_batches_status_idx ON withdrawal_batches (status, created_at DESC);

CREATE TABLE withdrawal_batch_files
(
    id                UUID PRIMARY KEY,
    batch_id          UUID NOT NULL,
    kind              VARCHAR(20) NOT NULL,
    filename          VARCHAR(200) NOT NULL,
    content_encrypted TEXT NOT NULL,
    size_bytes        INT NOT NULL,
    uploaded_by       UUID,
    paid_count        INT,
    failed_count      INT,
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT withdrawal_batch_files_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES withdrawal_batches(id),
    CONSTRAINT withdrawal_batch_files_uploaded_by_fkey FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT withdrawal_batch_files_kind_chk CHECK (kind IN ('PAYMENT', 'RECONCILIATION'))
);
CREATE INDEX withdrawal_batch_files_batch_idx ON withdrawal_batch_files (batch_id, created_at);
CREATE UNIQUE INDEX withdrawal_batch_files_payment_uk ON withdrawal_batch_files (batch_id) WHERE (kind = 'PAYMENT');

CREATE TABLE withdrawal_runs
(
    id            UUID PRIMARY KEY,
    reference     VARCHAR(20) NOT NULL,
    currency      VARCHAR(3) NOT NULL,
    run_at        TIMESTAMPTZ NOT NULL,
    triggered_by  UUID NOT NULL,
    bank_batch_id UUID,
    cash_batch_id UUID,
    bank_count    INT DEFAULT 0 NOT NULL,
    bank_total    NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    cash_count    INT DEFAULT 0 NOT NULL,
    cash_total    NUMERIC(19, 4) DEFAULT 0 NOT NULL,
    skipped       JSONB DEFAULT '[]'::jsonb NOT NULL,
    kind          VARCHAR(20),
    CONSTRAINT withdrawal_runs_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawal_runs_bank_batch_id_fkey FOREIGN KEY (bank_batch_id) REFERENCES withdrawal_batches(id),
    CONSTRAINT withdrawal_runs_cash_batch_id_fkey FOREIGN KEY (cash_batch_id) REFERENCES withdrawal_batches(id),
    CONSTRAINT withdrawal_runs_triggered_by_fkey FOREIGN KEY (triggered_by) REFERENCES users(id),
    CONSTRAINT withdrawal_runs_counts_chk CHECK ((bank_count >= 0) AND (cash_count >= 0) AND ((bank_count > 0) = (bank_batch_id IS NOT NULL)) AND ((cash_count > 0) = (cash_batch_id IS NOT NULL))),
    CONSTRAINT withdrawal_runs_kind_chk CHECK ((kind IS NULL) OR (kind IN ('RENT', 'REFERRAL')))
);
CREATE INDEX withdrawal_runs_run_at_idx ON withdrawal_runs (run_at DESC);

CREATE TABLE withdrawals
(
    id                 UUID PRIMARY KEY,
    reference          VARCHAR(20) NOT NULL,
    user_id            UUID NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    currency           VARCHAR(3) NOT NULL,
    status             VARCHAR(20) NOT NULL,
    idempotency_key    VARCHAR(100) NOT NULL,
    bank_account_id    UUID,
    bank_holder_name   VARCHAR(140),
    bank_name          VARCHAR(140),
    bank_account_last4 VARCHAR(4),
    required_approvals SMALLINT NOT NULL,
    first_approved_by  UUID,
    first_approved_at  TIMESTAMPTZ,
    second_approved_by UUID,
    second_approved_at TIMESTAMPTZ,
    rejected_by        UUID,
    rejection_reason   VARCHAR(500),
    batch_id           UUID,
    payout_reference   VARCHAR(100),
    failure_reason     VARCHAR(500),
    closed_at          TIMESTAMPTZ,
    origin             VARCHAR(20) DEFAULT 'INVESTOR' NOT NULL,
    payout_method      VARCHAR(20) DEFAULT 'BANK_TRANSFER' NOT NULL,
    balance            VARCHAR(20) DEFAULT 'RENT_BANK' NOT NULL,
    ticket_id          UUID,
    task_assignee_id   UUID,
    task_assigned_at   TIMESTAMPTZ,
    task_done_at       TIMESTAMPTZ,
    version            BIGINT DEFAULT 0 NOT NULL,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT withdrawals_idempotency_uk UNIQUE (user_id, idempotency_key),
    CONSTRAINT withdrawals_reference_uk UNIQUE (reference),
    CONSTRAINT withdrawals_bank_account_id_fkey FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT withdrawals_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES withdrawal_batches(id),
    CONSTRAINT withdrawals_created_by_fkey FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT withdrawals_first_approved_by_fkey FOREIGN KEY (first_approved_by) REFERENCES users(id),
    CONSTRAINT withdrawals_rejected_by_fkey FOREIGN KEY (rejected_by) REFERENCES users(id),
    CONSTRAINT withdrawals_second_approved_by_fkey FOREIGN KEY (second_approved_by) REFERENCES users(id),
    CONSTRAINT withdrawals_task_assignee_id_fkey FOREIGN KEY (task_assignee_id) REFERENCES users(id),
    CONSTRAINT withdrawals_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    CONSTRAINT withdrawals_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT withdrawals_amount_chk CHECK (amount > (0)),
    CONSTRAINT withdrawals_approvals_chk CHECK (required_approvals IN (1, 2)),
    CONSTRAINT withdrawals_approved_chk CHECK ((status IN ('PENDING_APPROVAL', 'REJECTED', 'CANCELLED')) OR (origin IN ('SCHEDULED', 'OFFICE')) OR ((first_approved_by IS NOT NULL) AND ((required_approvals = 1) OR (second_approved_by IS NOT NULL)))),
    CONSTRAINT withdrawals_balance_chk CHECK (balance IN ('RENT_BANK', 'RENT_CASH', 'REFERRAL')),
    CONSTRAINT withdrawals_balance_method_chk CHECK ((balance = 'REFERRAL') OR ((balance = 'RENT_BANK') AND (payout_method = 'BANK_TRANSFER')) OR ((balance = 'RENT_CASH') AND (payout_method = 'OFFICE_CASH'))),
    CONSTRAINT withdrawals_batched_chk CHECK ((((payout_method = 'BANK_TRANSFER') OR (origin = 'SCHEDULED')) AND ((status IN ('BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'RETURNED')) = (batch_id IS NOT NULL))) OR ((payout_method = 'OFFICE_CASH') AND (origin <> 'SCHEDULED') AND (batch_id IS NULL) AND (status IN ('PENDING_APPROVAL', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED')))),
    CONSTRAINT withdrawals_cash_origin_chk CHECK ((payout_method = 'BANK_TRANSFER') OR (origin IN ('INVESTOR', 'OFFICE', 'SCHEDULED'))),
    CONSTRAINT withdrawals_closed_chk CHECK ((status IN ('PAID', 'RETURNED', 'REJECTED', 'CANCELLED')) = (closed_at IS NOT NULL)),
    CONSTRAINT withdrawals_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT withdrawals_failed_chk CHECK (((status <> 'FAILED') OR (failure_reason IS NOT NULL)) AND ((failure_reason IS NULL) OR (status IN ('FAILED', 'RETURNED', 'PAID')))),
    CONSTRAINT withdrawals_not_own_chk CHECK (((first_approved_by IS NULL) OR (first_approved_by <> user_id)) AND ((second_approved_by IS NULL) OR ((second_approved_by <> user_id) AND (second_approved_by <> first_approved_by))) AND ((rejected_by IS NULL) OR (rejected_by <> user_id))),
    CONSTRAINT withdrawals_office_chk CHECK (((origin = 'OFFICE') = (created_by IS NOT NULL)) AND ((origin <> 'OFFICE') OR (payout_method = 'OFFICE_CASH'))),
    CONSTRAINT withdrawals_origin_chk CHECK (origin IN ('INVESTOR', 'SCHEDULED', 'OFFICE')),
    CONSTRAINT withdrawals_payout_bank_chk CHECK ((payout_method = 'BANK_TRANSFER') = ((bank_account_id IS NOT NULL) AND (bank_holder_name IS NOT NULL) AND (bank_name IS NOT NULL) AND (bank_account_last4 IS NOT NULL))),
    CONSTRAINT withdrawals_payout_method_chk CHECK (payout_method IN ('BANK_TRANSFER', 'OFFICE_CASH')),
    CONSTRAINT withdrawals_rejected_chk CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    CONSTRAINT withdrawals_status_chk CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'PAID', 'FAILED', 'RETURNED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT withdrawals_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL))
);
CREATE INDEX withdrawals_batch_idx ON withdrawals (batch_id);
CREATE UNIQUE INDEX withdrawals_one_open_uk ON withdrawals (user_id, currency, balance) WHERE (status IN ('PENDING_APPROVAL', 'APPROVED', 'BATCHED', 'PROCESSING', 'FAILED'));
CREATE INDEX withdrawals_status_idx ON withdrawals (status, currency, created_at);
CREATE INDEX withdrawals_task_done_idx ON withdrawals (task_done_at) WHERE (task_done_at IS NOT NULL);
CREATE INDEX withdrawals_user_idx ON withdrawals (user_id, created_at DESC);

-- ------------------------------------------------------------------------- dashboards

CREATE TABLE dashboard_layouts
(
    user_id    UUID PRIMARY KEY,
    cards      TEXT[] NOT NULL,
    hidden     TEXT[] NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dashboard_layouts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE role_dashboard_cards
(
    role_id    UUID PRIMARY KEY,
    cards      TEXT[] NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT role_dashboard_cards_role_id_fkey FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT role_dashboard_cards_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- ------------------------------------------------------------------------- transactional outbox and consumed events

CREATE TABLE outbox_events
(
    id             UUID PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    payload        JSONB NOT NULL,
    correlation_id VARCHAR(100),
    published_at   TIMESTAMPTZ,
    attempts       INT DEFAULT 0 NOT NULL,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at) WHERE (published_at IS NULL);

CREATE TABLE processed_events
(
    consumer     VARCHAR(100) NOT NULL,
    event_id     UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT processed_events_pkey PRIMARY KEY (consumer, event_id)
);

ALTER SEQUENCE user_number_seq OWNED BY users.user_number;

-- ------------------------------------------------------------------------- reference data

INSERT INTO roles (name, description, system)
VALUES ('ADMIN', 'Platform administrator', TRUE),
       ('ADMINISTRATION', 'Back-office administration', TRUE),
       ('FINANCE', 'Finance operations', TRUE),
       ('INVESTOR', 'Registered investor', TRUE),
       ('REPORT_VIEWER', 'Read-only reporting access', TRUE),
       ('SALES', 'Sales team member', TRUE),
       ('SUPER_ADMIN', 'Unrestricted platform administrator', TRUE),
       ('SUPPORT', 'Customer support agent', TRUE),
       ('VERIFICATION', 'Verifies investors'' identity (KYC) and bank accounts', TRUE);

INSERT INTO permissions (code, category, description)
VALUES ('ROLE_MANAGE',                 'ACCESS',     'Create, update and delete roles'),
       ('ROLE_VIEW',                   'ACCESS',     'View roles and permissions'),
       ('USER_ROLE_ASSIGN',            'ACCESS',     'Assign roles to users'),
       ('BANK_ACCOUNT_VERIFY',         'FINANCE',    'Verify or reject investor bank accounts'),
       ('COMPANY_BANK_ACCOUNT_MANAGE', 'FINANCE',    'Manage the company bank accounts investors pay into'),
       ('COMPANY_BANK_ACCOUNT_VIEW',   'FINANCE',    'View the company bank accounts investors pay into'),
       ('CONTAINER_ASSIGN',            'FINANCE',    'Assign containers to paid investments'),
       ('FINANCE_ADJUST',              'FINANCE',    'Post financial adjustments'),
       ('FINANCE_TASK_PROCESS',        'FINANCE',    'Pay finance tasks (withdrawals made by runs) and mark them paid or failed'),
       ('FINANCE_VIEW',                'FINANCE',    'View payments, earnings and the ledger'),
       ('PAYMENT_CONFIRM',             'FINANCE',    'Confirm received bank transfers'),
       ('PAYMENT_SEARCH',              'FINANCE',    'Find payments by an exact search (order number, payment reference or investor), without browsing them'),
       ('PAYOUT_PROCESS',              'FINANCE',    'Pay monthly investor payouts that have fallen due'),
       ('REFERRAL_CONFIG_MANAGE',      'FINANCE',    'Change referral percentage configuration'),
       ('WITHDRAWAL_APPROVE',          'FINANCE',    'Approve withdrawals'),
       ('WITHDRAWAL_PROCESS',          'FINANCE',    'Batch and process withdrawals'),
       ('WITHDRAWAL_REJECT',           'FINANCE',    'Reject withdrawals'),
       ('WITHDRAWAL_VIEW',             'FINANCE',    'View withdrawals and batches'),
       ('AGREEMENT_TEMPLATE_MANAGE',   'INVESTMENT', 'Write the lease agreement issued to investors when their container is assigned'),
       ('INVESTMENT_APPROVE',          'INVESTMENT', 'Approve investments'),
       ('INVESTMENT_CREATE',           'INVESTMENT', 'Create investment products and containers'),
       ('INVESTMENT_UPDATE',           'INVESTMENT', 'Update investment products and containers'),
       ('INVESTMENT_VIEW',             'INVESTMENT', 'View investments and products'),
       ('ORDER_VIEW',                  'INVESTMENT', 'View orders and invoices'),
       ('RECORD_BROWSE',               'INVESTMENT', 'Browse all orders; without it, only an exact search (order number or investor) finds them'),
       ('INVESTOR_PORTAL',             'INVESTOR',   'Use the investor portal: invest, earn, refer, withdraw'),
       ('DASHBOARD_FIGURES_VIEW',      'REPORTING',  'See business figures on the admin overview: investors, capital invested, withdrawals and growth charts'),
       ('REPORT_GENERATE',             'REPORTING',  'Generate and export reports'),
       ('REPORT_VIEW',                 'REPORTING',  'View reports'),
       ('LEAD_ASSIGN',                 'SALES',      'Assign leads to sales users'),
       ('LEAD_CREATE',                 'SALES',      'Create leads'),
       ('LEAD_UPDATE',                 'SALES',      'Update leads and log activities'),
       ('LEAD_VIEW',                   'SALES',      'View leads'),
       ('FINANCE_TICKET_HANDLE',       'SUPPORT',    'Handle the support tickets handed to the finance team'),
       ('SUPPORT_TICKET_MANAGE',       'SUPPORT',    'Respond to, assign and close support tickets'),
       ('SUPPORT_TICKET_VIEW',         'SUPPORT',    'View support tickets'),
       ('AUDIT_VIEW',                  'SYSTEM',     'View audit logs'),
       ('SYSTEM_SETTINGS_MANAGE',      'SYSTEM',     'Change system settings'),
       ('KYC_REVIEW',                  'USER',       'Review and decide KYC submissions'),
       ('PHONE_NUMBER_VIEW',           'USER',       'See users'' full mobile numbers; others see only the last four digits'),
       ('STAFF_ACCOUNT_VIEW',          'USER',       'See employees'' accounts in the user directory, not only investors'),
       ('USER_SUSPEND',                'USER',       'Suspend or reactivate user accounts'),
       ('USER_UPDATE',                 'USER',       'Update user accounts'),
       ('USER_VIEW',                   'USER',       'View user accounts');

-- What each role may do.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    ('ADMIN', 'AGREEMENT_TEMPLATE_MANAGE'),
    ('ADMIN', 'AUDIT_VIEW'),
    ('ADMIN', 'BANK_ACCOUNT_VERIFY'),
    ('ADMIN', 'COMPANY_BANK_ACCOUNT_MANAGE'),
    ('ADMIN', 'COMPANY_BANK_ACCOUNT_VIEW'),
    ('ADMIN', 'DASHBOARD_FIGURES_VIEW'),
    ('ADMIN', 'FINANCE_TASK_PROCESS'),
    ('ADMIN', 'FINANCE_VIEW'),
    ('ADMIN', 'INVESTMENT_APPROVE'),
    ('ADMIN', 'INVESTMENT_CREATE'),
    ('ADMIN', 'INVESTMENT_UPDATE'),
    ('ADMIN', 'INVESTMENT_VIEW'),
    ('ADMIN', 'KYC_REVIEW'),
    ('ADMIN', 'LEAD_ASSIGN'),
    ('ADMIN', 'LEAD_CREATE'),
    ('ADMIN', 'LEAD_UPDATE'),
    ('ADMIN', 'LEAD_VIEW'),
    ('ADMIN', 'ORDER_VIEW'),
    ('ADMIN', 'PAYMENT_CONFIRM'),
    ('ADMIN', 'PAYMENT_SEARCH'),
    ('ADMIN', 'PHONE_NUMBER_VIEW'),
    ('ADMIN', 'RECORD_BROWSE'),
    ('ADMIN', 'REPORT_GENERATE'),
    ('ADMIN', 'REPORT_VIEW'),
    ('ADMIN', 'ROLE_MANAGE'),
    ('ADMIN', 'ROLE_VIEW'),
    ('ADMIN', 'STAFF_ACCOUNT_VIEW'),
    ('ADMIN', 'SUPPORT_TICKET_MANAGE'),
    ('ADMIN', 'SUPPORT_TICKET_VIEW'),
    ('ADMIN', 'USER_ROLE_ASSIGN'),
    ('ADMIN', 'USER_SUSPEND'),
    ('ADMIN', 'USER_UPDATE'),
    ('ADMIN', 'USER_VIEW'),
    ('ADMIN', 'WITHDRAWAL_APPROVE'),
    ('ADMIN', 'WITHDRAWAL_PROCESS'),
    ('ADMIN', 'WITHDRAWAL_REJECT'),
    ('ADMIN', 'WITHDRAWAL_VIEW'),
    ('ADMINISTRATION', 'DASHBOARD_FIGURES_VIEW'),
    ('ADMINISTRATION', 'INVESTMENT_CREATE'),
    ('ADMINISTRATION', 'INVESTMENT_UPDATE'),
    ('ADMINISTRATION', 'INVESTMENT_VIEW'),
    ('ADMINISTRATION', 'KYC_REVIEW'),
    ('ADMINISTRATION', 'ORDER_VIEW'),
    ('ADMINISTRATION', 'RECORD_BROWSE'),
    ('ADMINISTRATION', 'USER_UPDATE'),
    ('ADMINISTRATION', 'USER_VIEW'),
    ('FINANCE', 'BANK_ACCOUNT_VERIFY'),
    ('FINANCE', 'COMPANY_BANK_ACCOUNT_VIEW'),
    ('FINANCE', 'CONTAINER_ASSIGN'),
    ('FINANCE', 'DASHBOARD_FIGURES_VIEW'),
    ('FINANCE', 'FINANCE_TASK_PROCESS'),
    ('FINANCE', 'FINANCE_TICKET_HANDLE'),
    ('FINANCE', 'PAYMENT_CONFIRM'),
    ('FINANCE', 'USER_VIEW'),
    ('INVESTOR', 'INVESTOR_PORTAL'),
    ('REPORT_VIEWER', 'DASHBOARD_FIGURES_VIEW'),
    ('REPORT_VIEWER', 'REPORT_GENERATE'),
    ('REPORT_VIEWER', 'REPORT_VIEW'),
    ('SALES', 'DASHBOARD_FIGURES_VIEW'),
    ('SALES', 'INVESTMENT_VIEW'),
    ('SALES', 'LEAD_CREATE'),
    ('SALES', 'LEAD_UPDATE'),
    ('SALES', 'LEAD_VIEW'),
    ('SUPER_ADMIN', 'AGREEMENT_TEMPLATE_MANAGE'),
    ('SUPER_ADMIN', 'AUDIT_VIEW'),
    ('SUPER_ADMIN', 'BANK_ACCOUNT_VERIFY'),
    ('SUPER_ADMIN', 'COMPANY_BANK_ACCOUNT_MANAGE'),
    ('SUPER_ADMIN', 'COMPANY_BANK_ACCOUNT_VIEW'),
    ('SUPER_ADMIN', 'CONTAINER_ASSIGN'),
    ('SUPER_ADMIN', 'DASHBOARD_FIGURES_VIEW'),
    ('SUPER_ADMIN', 'FINANCE_ADJUST'),
    ('SUPER_ADMIN', 'FINANCE_TASK_PROCESS'),
    ('SUPER_ADMIN', 'FINANCE_TICKET_HANDLE'),
    ('SUPER_ADMIN', 'FINANCE_VIEW'),
    ('SUPER_ADMIN', 'INVESTMENT_APPROVE'),
    ('SUPER_ADMIN', 'INVESTMENT_CREATE'),
    ('SUPER_ADMIN', 'INVESTMENT_UPDATE'),
    ('SUPER_ADMIN', 'INVESTMENT_VIEW'),
    ('SUPER_ADMIN', 'KYC_REVIEW'),
    ('SUPER_ADMIN', 'LEAD_ASSIGN'),
    ('SUPER_ADMIN', 'LEAD_CREATE'),
    ('SUPER_ADMIN', 'LEAD_UPDATE'),
    ('SUPER_ADMIN', 'LEAD_VIEW'),
    ('SUPER_ADMIN', 'ORDER_VIEW'),
    ('SUPER_ADMIN', 'PAYMENT_CONFIRM'),
    ('SUPER_ADMIN', 'PAYMENT_SEARCH'),
    ('SUPER_ADMIN', 'PAYOUT_PROCESS'),
    ('SUPER_ADMIN', 'PHONE_NUMBER_VIEW'),
    ('SUPER_ADMIN', 'RECORD_BROWSE'),
    ('SUPER_ADMIN', 'REFERRAL_CONFIG_MANAGE'),
    ('SUPER_ADMIN', 'REPORT_GENERATE'),
    ('SUPER_ADMIN', 'REPORT_VIEW'),
    ('SUPER_ADMIN', 'ROLE_MANAGE'),
    ('SUPER_ADMIN', 'ROLE_VIEW'),
    ('SUPER_ADMIN', 'STAFF_ACCOUNT_VIEW'),
    ('SUPER_ADMIN', 'SUPPORT_TICKET_MANAGE'),
    ('SUPER_ADMIN', 'SUPPORT_TICKET_VIEW'),
    ('SUPER_ADMIN', 'SYSTEM_SETTINGS_MANAGE'),
    ('SUPER_ADMIN', 'USER_ROLE_ASSIGN'),
    ('SUPER_ADMIN', 'USER_SUSPEND'),
    ('SUPER_ADMIN', 'USER_UPDATE'),
    ('SUPER_ADMIN', 'USER_VIEW'),
    ('SUPER_ADMIN', 'WITHDRAWAL_APPROVE'),
    ('SUPER_ADMIN', 'WITHDRAWAL_PROCESS'),
    ('SUPER_ADMIN', 'WITHDRAWAL_REJECT'),
    ('SUPER_ADMIN', 'WITHDRAWAL_VIEW'),
    ('SUPPORT', 'INVESTMENT_VIEW'),
    ('SUPPORT', 'ORDER_VIEW'),
    ('SUPPORT', 'PAYMENT_SEARCH'),
    ('SUPPORT', 'SUPPORT_TICKET_MANAGE'),
    ('SUPPORT', 'SUPPORT_TICKET_VIEW'),
    ('SUPPORT', 'USER_VIEW'),
    ('VERIFICATION', 'BANK_ACCOUNT_VERIFY'),
    ('VERIFICATION', 'KYC_REVIEW'),
    ('VERIFICATION', 'USER_VIEW')
) AS grants (role, permission)
         JOIN roles r ON r.name = grants.role
         JOIN permissions p ON p.code = grants.permission;

INSERT INTO referral_rate_versions (id, effective_from, level1_percent, level2_percent, level3_percent, level4_percent, reason,
                                    created_at)
VALUES (gen_random_uuid(), '2026-01-01 00:00:00+00', 2.0, 1.0, 0.5, 0.25, 'Initial referral rates', now());

-- Singleton settings rows: empty until staff change them, so the configured defaults apply.
INSERT INTO rent_credit_settings (id) VALUES (1);
INSERT INTO withdrawal_settings (id) VALUES (1);
INSERT INTO order_settings (id) VALUES (1);

INSERT INTO rent_rate_tiers (min_order_amount, max_order_amount, monthly_rent_percent, updated_at)
VALUES (0, 99999, 2.00, now()),
       (100000, NULL, 5.00, now());

INSERT INTO agreement_templates (id, version, title, body, created_at)
VALUES (gen_random_uuid(), 1, 'Container Lease Agreement',
$body$This Container Lease Agreement ("Agreement") is made on {{agreementDate}} between {{companyName}}, {{companyAddress}} (the "Company"), and {{investorName}} (investor ID {{investorCode}}), {{investorAddress}} (the "Owner").

# 1. The container
The Owner has bought, under order {{orderNumber}} (container listing {{planCode}}), the {{containerType}} shipping container numbered {{containerNumber}} for {{purchasePrice}}. The container is currently located at {{containerLocation}}.

# 2. Lease
The Owner leases the container to the Company for {{tenureMonths}} months, from {{leaseStartDate}} to {{leaseEndDate}}. During the lease the Company manages, insures, maintains and sub-leases the container at its own cost.

# 3. Rent
The Company pays the Owner rent of {{monthlyRentPercent}} of the purchase price a month, {{monthlyRent}} a month and {{totalRent}} over the lease. Rent is credited to the Owner's SeaLease wallet every hour from the start of the lease and may be withdrawn to the Owner's verified bank account.

# 4. End of the lease
When the lease ends the container returns to the Company's stock. The purchase price is not repaid under this Agreement.

# 5. Acceptance
This Agreement takes effect when the Owner accepts it in their SeaLease account. The lease, and the rent, start at the first full hour after acceptance; no rent is due for any time before it.

# 6. General
This Agreement is governed by the laws of India. It was generated electronically by SeaLease and is valid without a signature once accepted.

Agreement {{agreementNumber}}$body$, now());
