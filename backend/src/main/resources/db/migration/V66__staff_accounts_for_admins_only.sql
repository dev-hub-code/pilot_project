-- =====================================================================================
-- V66: Only admins see employees' accounts in the user directory; every other role sees investors only.
-- =====================================================================================

DELETE
FROM role_permissions rp
    USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.code = 'STAFF_ACCOUNT_VIEW'
  AND r.name NOT IN ('SUPER_ADMIN', 'ADMIN');
