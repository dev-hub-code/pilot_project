-- =====================================================================================
-- V2: Identity, roles, permissions, login sessions and refresh tokens.
-- =====================================================================================

-- ---------------------------------------------------------------------------- users --
CREATE TABLE users
(
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(254) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    first_name            VARCHAR(100) NOT NULL,
    last_name             VARCHAR(100) NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ  NOT NULL,
    email_verified_at     TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT users_failed_attempts_chk CHECK (failed_login_attempts >= 0),
    -- Emails are normalised (trimmed, lower-cased) by the application before storage.
    CONSTRAINT users_email_normalised_chk CHECK (email = lower(btrim(email)))
);
CREATE UNIQUE INDEX users_email_uk ON users (email);

-- ---------------------------------------------------------------------- permissions --
-- Permission codes are defined in code (PermissionCode enum) because @PreAuthorize checks
-- reference them; rows exist so roles can be composed from them at runtime.
CREATE TABLE permissions
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(64)  NOT NULL,
    category    VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT permissions_code_uk UNIQUE (code)
);

-- ---------------------------------------------------------------------------- roles --
CREATE TABLE roles
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    -- System roles are seeded here; they can have permissions edited but cannot be deleted/renamed.
    system      BOOLEAN      NOT NULL DEFAULT FALSE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT roles_name_uk UNIQUE (name),
    CONSTRAINT roles_name_format_chk CHECK (name ~ '^[A-Z][A-Z0-9_]{1,63}$')
);

CREATE TABLE role_permissions
(
    role_id       UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles
(
    user_id     UUID        NOT NULL REFERENCES users (id),
    role_id     UUID        NOT NULL REFERENCES roles (id),
    assigned_by UUID REFERENCES users (id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX user_roles_role_idx ON user_roles (role_id);

-- -------------------------------------------------------------------- auth sessions --
-- One row per login. Access tokens carry the session id ("sid") and are rejected as soon as
-- the session is revoked, giving immediate logout / suspension despite stateless JWTs.
CREATE TABLE auth_sessions
(
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    last_used_at   TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(40),
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(512),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT auth_sessions_expiry_chk CHECK (expires_at > created_at),
    CONSTRAINT auth_sessions_revocation_chk CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);
CREATE INDEX auth_sessions_user_active_idx ON auth_sessions (user_id) WHERE revoked_at IS NULL;

-- ------------------------------------------------------------------- refresh tokens --
-- Opaque tokens; only a SHA-256 hash is stored. Each refresh consumes the presented token and
-- issues a successor in the same session. Presenting a consumed token again is treated as theft
-- and revokes the whole session.
CREATE TABLE refresh_tokens
(
    id           UUID PRIMARY KEY,
    session_id   UUID        NOT NULL REFERENCES auth_sessions (id),
    token_hash   VARCHAR(64) NOT NULL,
    issued_at    TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ,
    replaced_by  UUID REFERENCES refresh_tokens (id),
    version      BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT refresh_tokens_hash_uk UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_expiry_chk CHECK (expires_at > issued_at)
);
CREATE INDEX refresh_tokens_session_idx ON refresh_tokens (session_id);

-- ============================================================================ seeds ==
INSERT INTO permissions (code, category, description)
VALUES ('INVESTOR_PORTAL', 'INVESTOR', 'Use the investor portal: invest, earn, refer, withdraw'),

       ('USER_VIEW', 'USER', 'View user accounts'),
       ('USER_UPDATE', 'USER', 'Update user accounts'),
       ('USER_SUSPEND', 'USER', 'Suspend or reactivate user accounts'),
       ('KYC_REVIEW', 'USER', 'Review and decide KYC submissions'),

       ('ROLE_VIEW', 'ACCESS', 'View roles and permissions'),
       ('ROLE_MANAGE', 'ACCESS', 'Create, update and delete roles'),
       ('USER_ROLE_ASSIGN', 'ACCESS', 'Assign roles to users'),

       ('INVESTMENT_VIEW', 'INVESTMENT', 'View investments and products'),
       ('INVESTMENT_CREATE', 'INVESTMENT', 'Create investment products and containers'),
       ('INVESTMENT_UPDATE', 'INVESTMENT', 'Update investment products and containers'),
       ('INVESTMENT_APPROVE', 'INVESTMENT', 'Approve investments'),
       ('ORDER_VIEW', 'INVESTMENT', 'View orders and invoices'),

       ('FINANCE_VIEW', 'FINANCE', 'View payments, earnings and the ledger'),
       ('FINANCE_ADJUST', 'FINANCE', 'Post financial adjustments'),
       ('WITHDRAWAL_VIEW', 'FINANCE', 'View withdrawals and batches'),
       ('WITHDRAWAL_APPROVE', 'FINANCE', 'Approve withdrawals'),
       ('WITHDRAWAL_REJECT', 'FINANCE', 'Reject withdrawals'),
       ('WITHDRAWAL_PROCESS', 'FINANCE', 'Batch and process withdrawals'),
       ('REFERRAL_CONFIG_MANAGE', 'FINANCE', 'Change referral percentage configuration'),

       ('LEAD_VIEW', 'SALES', 'View leads'),
       ('LEAD_CREATE', 'SALES', 'Create leads'),
       ('LEAD_ASSIGN', 'SALES', 'Assign leads to sales users'),
       ('LEAD_UPDATE', 'SALES', 'Update leads and log activities'),

       ('SUPPORT_TICKET_VIEW', 'SUPPORT', 'View support tickets'),
       ('SUPPORT_TICKET_MANAGE', 'SUPPORT', 'Respond to, assign and close support tickets'),

       ('REPORT_VIEW', 'REPORTING', 'View reports'),
       ('REPORT_GENERATE', 'REPORTING', 'Generate and export reports'),

       ('AUDIT_VIEW', 'SYSTEM', 'View audit logs'),
       ('SYSTEM_SETTINGS_MANAGE', 'SYSTEM', 'Change system settings');

INSERT INTO roles (name, description, system)
VALUES ('SUPER_ADMIN', 'Unrestricted platform administrator', TRUE),
       ('ADMIN', 'Platform administrator', TRUE),
       ('SUPPORT', 'Customer support agent', TRUE),
       ('SALES', 'Sales team member', TRUE),
       ('FINANCE', 'Finance operations', TRUE),
       ('ADMINISTRATION', 'Back-office administration', TRUE),
       ('REPORT_VIEWER', 'Read-only reporting access', TRUE),
       ('INVESTOR', 'Registered investor', TRUE);

-- Default grants. These are starting points; administrators can change them at runtime.
-- SUPER_ADMIN holds every permission, so the escalation guard (you may only grant or revoke roles
-- whose permissions you hold) never blocks it.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON
    CASE r.name
        WHEN 'SUPER_ADMIN' THEN TRUE
        WHEN 'ADMIN' THEN p.code NOT IN ('INVESTOR_PORTAL', 'SYSTEM_SETTINGS_MANAGE', 'FINANCE_ADJUST',
                                         'REFERRAL_CONFIG_MANAGE')
        WHEN 'SUPPORT' THEN p.code IN ('USER_VIEW', 'INVESTMENT_VIEW', 'ORDER_VIEW', 'WITHDRAWAL_VIEW',
                                       'SUPPORT_TICKET_VIEW', 'SUPPORT_TICKET_MANAGE')
        WHEN 'SALES' THEN p.code IN ('LEAD_VIEW', 'LEAD_CREATE', 'LEAD_UPDATE', 'INVESTMENT_VIEW')
        WHEN 'FINANCE' THEN p.code IN ('USER_VIEW', 'INVESTMENT_VIEW', 'ORDER_VIEW', 'FINANCE_VIEW',
                                       'FINANCE_ADJUST', 'WITHDRAWAL_VIEW', 'WITHDRAWAL_APPROVE',
                                       'WITHDRAWAL_REJECT', 'WITHDRAWAL_PROCESS', 'REPORT_VIEW',
                                       'REPORT_GENERATE')
        WHEN 'ADMINISTRATION' THEN p.code IN ('USER_VIEW', 'USER_UPDATE', 'KYC_REVIEW', 'INVESTMENT_VIEW',
                                              'INVESTMENT_CREATE', 'INVESTMENT_UPDATE', 'ORDER_VIEW')
        WHEN 'REPORT_VIEWER' THEN p.code IN ('REPORT_VIEW', 'REPORT_GENERATE')
        WHEN 'INVESTOR' THEN p.code = 'INVESTOR_PORTAL'
        ELSE FALSE
        END;
