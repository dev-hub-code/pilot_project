-- The website's terms and conditions, which super administrators maintain (TERMS_MANAGE) and everyone signing up must
-- accept. Each save is a new version; an account keeps the version it accepted and when.
INSERT INTO permissions (code, category, description)
VALUES ('TERMS_MANAGE', 'ACCESS', 'Write the terms and conditions people accept when they sign up');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'TERMS_MANAGE'
WHERE r.name = 'SUPER_ADMIN';

CREATE TABLE terms_versions
(
    id         UUID PRIMARY KEY,
    version    INT          NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT terms_versions_version_uk UNIQUE (version),
    CONSTRAINT terms_versions_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT terms_versions_body_chk CHECK (length(body) BETWEEN 1 AND 100000),
    CONSTRAINT terms_versions_version_chk CHECK (version > 0)
);
CREATE TRIGGER terms_versions_immutable BEFORE DELETE OR UPDATE ON terms_versions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Accounts made before this have accepted no version.
ALTER TABLE users
    ADD COLUMN terms_version     INT,
    ADD COLUMN terms_accepted_at TIMESTAMPTZ;

-- Starting wording, for super administrators to replace (created_by NULL: as shipped).
INSERT INTO terms_versions (id, version, title, body, created_by, created_at)
VALUES (gen_random_uuid(), 1, 'Terms and Conditions', $body$# About these terms

These terms apply to your use of the SeaLease website and your SeaLease account. By creating an account you agree to them.

# Your account

Give accurate details when you sign up and keep them up to date. Keep your password to yourself: you are responsible for what is done with your account.

# Buying containers

Each container you buy is leased out for the tenure you choose and is covered by its own lease agreement, which you accept before your container is assigned. Rent is credited to your wallet as that agreement sets out.

# Risks

Rent depends on SeaLease leasing out its containers. Containers can be damaged, lost or off-hire, and the shipping market changes. Past rent does not promise future rent.

# Referrals

Referral commission is paid as the referral rates in force set out. SeaLease may change those rates for the future.

# Changes to these terms

SeaLease may change these terms. The version in force is always on this page.$body$, NULL, now());
