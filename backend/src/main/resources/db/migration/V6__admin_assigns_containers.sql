-- Admins assign containers to paid investments, and no longer generate withdrawals (WITHDRAWAL_PROCESS: the monthly
-- withdrawal runs, the bank files and resolving failed withdrawals), which stays with super admins.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.code = 'CONTAINER_ASSIGN'
ON CONFLICT DO NOTHING;

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'ADMIN')
  AND permission_id = (SELECT id FROM permissions WHERE code = 'WITHDRAWAL_PROCESS');
