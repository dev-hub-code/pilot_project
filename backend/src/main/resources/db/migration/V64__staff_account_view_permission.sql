-- =====================================================================================
-- V64: Staff accounts in the user directory get their own permission.
--
-- Support looks up investors to answer their tickets, but should not see employees' accounts. Every other role that
-- can view users keeps seeing staff.
-- =====================================================================================

INSERT INTO permissions (code, category, description)
VALUES ('STAFF_ACCOUNT_VIEW', 'USER', 'See employees'' accounts in the user directory, not only investors');

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
         JOIN permissions v ON v.id = rp.permission_id AND v.code = 'USER_VIEW'
         JOIN roles r ON r.id = rp.role_id
         CROSS JOIN permissions p
WHERE p.code = 'STAFF_ACCOUNT_VIEW'
  AND r.name <> 'SUPPORT'
ON CONFLICT DO NOTHING;
