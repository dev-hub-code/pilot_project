-- =====================================================================================
-- V57: A verification team, and reviews assigned to one reviewer.
--
-- VERIFICATION is a staff role that sees users and reviews their identity (KYC) and bank accounts. Every KYC
-- submission and bank account waiting for review is a task: a reviewer assigns it to themselves before they may
-- approve or reject it, so two reviewers never work on the same one. The assignee is kept after the decision.
-- =====================================================================================

ALTER TABLE kyc_submissions
    ADD COLUMN assigned_to UUID REFERENCES users (id),
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD CONSTRAINT kyc_submissions_assignment_chk CHECK ((assigned_to IS NULL) = (assigned_at IS NULL));

ALTER TABLE bank_accounts
    ADD COLUMN assigned_to UUID REFERENCES users (id),
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD CONSTRAINT bank_accounts_assignment_chk CHECK ((assigned_to IS NULL) = (assigned_at IS NULL));

INSERT INTO roles (name, description, system)
VALUES ('VERIFICATION', 'Verifies investors'' identity (KYC) and bank accounts', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('USER_VIEW', 'KYC_REVIEW', 'BANK_ACCOUNT_VERIFY')
WHERE r.name = 'VERIFICATION';
