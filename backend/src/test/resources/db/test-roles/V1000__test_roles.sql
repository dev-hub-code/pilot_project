-- Tests only: fixed bundles of permissions that many integration tests sign in with. These are not production roles.
INSERT INTO roles (name, description, system) VALUES ('ADMINISTRATION', 'Back-office administration', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMINISTRATION' AND p.code IN ('INVESTMENT_CREATE', 'INVESTMENT_UPDATE', 'INVESTMENT_VIEW', 'KYC_REVIEW', 'ORDER_VIEW', 'RECORD_BROWSE', 'USER_UPDATE', 'USER_VIEW');

INSERT INTO roles (name, description, system) VALUES ('ADMIN', 'Platform administrator', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.code IN ('AGREEMENT_TEMPLATE_MANAGE', 'AUDIT_VIEW', 'BANK_ACCOUNT_VERIFY', 'COMPANY_BANK_ACCOUNT_MANAGE', 'COMPANY_BANK_ACCOUNT_VIEW', 'FINANCE_TASK_PROCESS', 'FINANCE_VIEW', 'INVESTMENT_APPROVE', 'INVESTMENT_CREATE', 'INVESTMENT_UPDATE', 'INVESTMENT_VIEW', 'INVESTOR_ACCOUNT_CREATE', 'KYC_REVIEW', 'LEAD_ASSIGN', 'LEAD_CREATE', 'LEAD_UPDATE', 'LEAD_VIEW', 'ORDER_VIEW', 'PAYMENT_CONFIRM', 'PAYMENT_SEARCH', 'PHONE_NUMBER_VIEW', 'RECORD_BROWSE', 'REPORT_GENERATE', 'REPORT_VIEW', 'ROLE_MANAGE', 'ROLE_VIEW', 'STAFF_ACCOUNT_VIEW', 'SUPPORT_TICKET_MANAGE', 'SUPPORT_TICKET_VIEW', 'USER_ROLE_ASSIGN', 'USER_SUSPEND', 'USER_UPDATE', 'USER_VIEW', 'WITHDRAWAL_APPROVE', 'WITHDRAWAL_REJECT', 'WITHDRAWAL_VIEW');

INSERT INTO roles (name, description, system) VALUES ('FINANCE', 'Finance operations', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FINANCE' AND p.code IN ('BANK_ACCOUNT_VERIFY', 'COMPANY_BANK_ACCOUNT_VIEW', 'FINANCE_TASK_PROCESS', 'FINANCE_TICKET_HANDLE', 'PAYMENT_CONFIRM', 'USER_VIEW');

INSERT INTO roles (name, description, system) VALUES ('REPORT_VIEWER', 'Read-only reporting access', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'REPORT_VIEWER' AND p.code IN ('REPORT_GENERATE', 'REPORT_VIEW');

INSERT INTO roles (name, description, system) VALUES ('SALES', 'Sales team member', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SALES' AND p.code IN ('INVESTMENT_VIEW', 'LEAD_CREATE', 'LEAD_UPDATE', 'LEAD_VIEW');

INSERT INTO roles (name, description, system) VALUES ('SUPPORT', 'Customer support agent', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPPORT' AND p.code IN ('INVESTMENT_VIEW', 'ORDER_VIEW', 'PAYMENT_SEARCH', 'SUPPORT_TICKET_MANAGE', 'SUPPORT_TICKET_VIEW', 'USER_VIEW');

INSERT INTO roles (name, description, system) VALUES ('VERIFICATION', 'Verifies investors'' identity (KYC) and bank accounts', TRUE);
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'VERIFICATION' AND p.code IN ('BANK_ACCOUNT_VERIFY', 'KYC_REVIEW', 'USER_VIEW');

