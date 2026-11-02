-- Sending announcements. Sensitive: one announcement reaches every account. The super administrator holds it.
INSERT INTO permissions (code, category, description, sensitive)
VALUES ('ANNOUNCEMENT_MANAGE', 'SUPPORT', 'Send announcements to everyone, investors (all, online or network) or staff', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'ANNOUNCEMENT_MANAGE'
WHERE r.name = 'SUPER_ADMIN';
