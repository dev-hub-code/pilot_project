-- =====================================================================================
-- V13: An account is either an investor or staff, never both.
--
-- SUPER_ADMIN was seeded with every permission, including the investor portal. Escalation guards
-- ignore INVESTOR_PORTAL (it confers no power over others), so it is not needed to grant the
-- INVESTOR role. The application refuses role assignments and role definitions that combine the
-- investor portal with staff permissions.
-- =====================================================================================

DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE name = 'SUPER_ADMIN')
  AND permission_id = (SELECT id FROM permissions WHERE code = 'INVESTOR_PORTAL');
