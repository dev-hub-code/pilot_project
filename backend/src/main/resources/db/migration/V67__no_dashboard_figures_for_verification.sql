-- =====================================================================================
-- V67: Verification does not see business figures on the overview.
--
-- Permissions add up across a user's roles: someone in both Support and Verification got the figures from
-- Verification, and the investment and withdrawal access from Support, so they saw every figure V63 hid from support.
-- Verification works its KYC and bank-account queues and needs none of them.
-- =====================================================================================

DELETE
FROM role_permissions rp
    USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.code = 'DASHBOARD_FIGURES_VIEW'
  AND r.name = 'VERIFICATION';
