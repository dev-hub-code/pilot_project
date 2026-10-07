-- =====================================================================================
-- V4: User profiles, investor classification, KYC, encrypted documents, bank accounts.
--
-- Sensitive values (tax ids, identity document numbers, bank account numbers, document files)
-- are encrypted by the application with AES-256-GCM before storage ("*_encrypted" columns).
-- Only the last four characters are kept in clear for display. Duplicate detection uses keyed
-- HMAC fingerprints, never the clear value.
-- =====================================================================================

-- ------------------------------------------------------------------ account status --
ALTER TABLE users
    ADD COLUMN status_reason     VARCHAR(500),
    ADD COLUMN status_changed_at TIMESTAMPTZ,
    ADD COLUMN status_changed_by UUID REFERENCES users (id),
    -- Two-factor readiness: enrolment is added later without another users-table migration.
    ADD COLUMN mfa_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN mfa_secret_encrypted TEXT;

-- --------------------------------------------------------------------- profiles --
CREATE TABLE user_profiles
(
    id                    UUID PRIMARY KEY,
    user_id               UUID        NOT NULL REFERENCES users (id),
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
    investor_type         VARCHAR(10) NOT NULL DEFAULT 'RETAIL',
    kyc_status            VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    email_notifications   BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_notifications     BOOLEAN     NOT NULL DEFAULT FALSE,
    version               BIGINT      NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_profiles_user_uk UNIQUE (user_id),
    CONSTRAINT user_profiles_investor_type_chk CHECK (investor_type IN ('RETAIL', 'HNI')),
    CONSTRAINT user_profiles_kyc_status_chk CHECK (kyc_status IN ('NOT_SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT user_profiles_tax_id_chk CHECK ((tax_id_encrypted IS NULL) = (tax_id_last4 IS NULL))
);
CREATE INDEX user_profiles_kyc_status_idx ON user_profiles (kyc_status);
CREATE INDEX user_profiles_investor_type_idx ON user_profiles (investor_type);

-- Accounts created before this migration get an empty profile.
INSERT INTO user_profiles (id, user_id, created_at, updated_at)
SELECT gen_random_uuid(), u.id, now(), now()
FROM users u;

-- --------------------------------------------------- investor classification history --
CREATE TABLE investor_classifications
(
    id            UUID PRIMARY KEY,
    user_id       UUID         NOT NULL REFERENCES users (id),
    previous_type VARCHAR(10)  NOT NULL,
    new_type      VARCHAR(10)  NOT NULL,
    reason        VARCHAR(500) NOT NULL,
    decided_by    UUID         NOT NULL REFERENCES users (id),
    decided_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT investor_classifications_change_chk CHECK (previous_type <> new_type)
);
CREATE INDEX investor_classifications_user_idx ON investor_classifications (user_id, decided_at DESC);
CREATE TRIGGER investor_classifications_immutable
    BEFORE UPDATE OR DELETE
    ON investor_classifications
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();

-- ----------------------------------------------------------------------- documents --
-- Encrypted file storage. Content never leaves the database unencrypted.
CREATE TABLE stored_documents
(
    id            UUID PRIMARY KEY,
    owner_user_id UUID         NOT NULL REFERENCES users (id),
    purpose       VARCHAR(40)  NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    INT          NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    content       BYTEA        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT stored_documents_size_chk CHECK (size_bytes > 0),
    CONSTRAINT stored_documents_type_chk CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png'))
);
CREATE INDEX stored_documents_owner_idx ON stored_documents (owner_user_id);

-- ---------------------------------------------------------------------------- KYC --
CREATE TABLE kyc_submissions
(
    id                        UUID PRIMARY KEY,
    user_id                   UUID         NOT NULL REFERENCES users (id),
    status                    VARCHAR(20)  NOT NULL,
    legal_first_name          VARCHAR(100) NOT NULL,
    legal_last_name           VARCHAR(100) NOT NULL,
    date_of_birth             DATE         NOT NULL,
    nationality               VARCHAR(2)      NOT NULL,
    document_type             VARCHAR(20)  NOT NULL,
    document_number_encrypted TEXT         NOT NULL,
    document_number_last4     VARCHAR(4)   NOT NULL,
    document_issuing_country  VARCHAR(2)      NOT NULL,
    document_expiry_date      DATE         NOT NULL,
    submitted_at              TIMESTAMPTZ  NOT NULL,
    reviewed_by               UUID REFERENCES users (id),
    reviewed_at               TIMESTAMPTZ,
    rejection_reason          VARCHAR(500),
    version                   BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL,
    updated_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT kyc_submissions_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT kyc_submissions_document_type_chk CHECK (document_type IN ('PASSPORT', 'NATIONAL_ID', 'DRIVING_LICENSE')),
    CONSTRAINT kyc_submissions_review_chk CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
            OR (status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CONSTRAINT kyc_submissions_rejection_chk CHECK ((status = 'REJECTED') = (rejection_reason IS NOT NULL)),
    -- Four-eyes principle: nobody approves their own identity.
    CONSTRAINT kyc_submissions_reviewer_chk CHECK (reviewed_by IS NULL OR reviewed_by <> user_id)
);
CREATE UNIQUE INDEX kyc_submissions_one_pending_uk ON kyc_submissions (user_id) WHERE status = 'PENDING';
CREATE INDEX kyc_submissions_status_idx ON kyc_submissions (status, submitted_at);
CREATE INDEX kyc_submissions_user_idx ON kyc_submissions (user_id, submitted_at DESC);

CREATE TABLE kyc_documents
(
    submission_id UUID        NOT NULL REFERENCES kyc_submissions (id),
    document_id   UUID        NOT NULL REFERENCES stored_documents (id),
    purpose       VARCHAR(40) NOT NULL,
    PRIMARY KEY (submission_id, document_id),
    CONSTRAINT kyc_documents_purpose_uk UNIQUE (submission_id, purpose)
);

-- ------------------------------------------------------------------- bank accounts --
CREATE TABLE bank_accounts
(
    id                       UUID PRIMARY KEY,
    user_id                  UUID         NOT NULL REFERENCES users (id),
    account_holder_name      VARCHAR(140) NOT NULL,
    bank_name                VARCHAR(140) NOT NULL,
    country                  VARCHAR(2)      NOT NULL,
    currency                 VARCHAR(3)      NOT NULL,
    account_number_encrypted TEXT         NOT NULL,
    account_number_last4     VARCHAR(4)   NOT NULL,
    -- HMAC of the normalised account number + routing code: finds the same account across users.
    account_fingerprint      VARCHAR(64)  NOT NULL,
    routing_code_encrypted   TEXT         NOT NULL,
    status                   VARCHAR(25)  NOT NULL,
    is_primary               BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_by              UUID REFERENCES users (id),
    verified_at              TIMESTAMPTZ,
    rejection_reason         VARCHAR(500),
    removed_at               TIMESTAMPTZ,
    version                  BIGINT       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT bank_accounts_codes_chk CHECK (country ~ '^[A-Z]{2}$' AND currency ~ '^[A-Z]{3}$'),
    CONSTRAINT bank_accounts_status_chk CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'REMOVED')),
    CONSTRAINT bank_accounts_verified_chk CHECK (status <> 'VERIFIED' OR verified_by IS NOT NULL),
    CONSTRAINT bank_accounts_removed_chk CHECK ((status = 'REMOVED') = (removed_at IS NOT NULL)),
    CONSTRAINT bank_accounts_primary_chk CHECK (NOT (is_primary AND status = 'REMOVED')),
    CONSTRAINT bank_accounts_verifier_chk CHECK (verified_by IS NULL OR verified_by <> user_id)
);
CREATE UNIQUE INDEX bank_accounts_one_primary_uk ON bank_accounts (user_id) WHERE is_primary;
CREATE UNIQUE INDEX bank_accounts_no_duplicates_uk ON bank_accounts (user_id, account_fingerprint)
    WHERE status <> 'REMOVED';
CREATE INDEX bank_accounts_fingerprint_idx ON bank_accounts (account_fingerprint);
CREATE INDEX bank_accounts_status_idx ON bank_accounts (status, created_at);

-- --------------------------------------------------------------------- permissions --
INSERT INTO permissions (code, category, description)
VALUES ('BANK_ACCOUNT_VERIFY', 'FINANCE', 'Verify or reject investor bank accounts'),
       ('INVESTOR_CLASSIFY', 'USER', 'Classify investors as retail or HNI');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('BANK_ACCOUNT_VERIFY', 'INVESTOR_CLASSIFY')
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
   OR (r.name = 'FINANCE' AND p.code = 'BANK_ACCOUNT_VERIFY')
   OR (r.name = 'ADMINISTRATION' AND p.code = 'INVESTOR_CLASSIFY');
