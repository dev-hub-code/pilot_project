-- =====================================================================================
-- V70: Admins hold PAYMENT_SEARCH too.
--
-- An administrator may only grant a role whose permissions they hold themselves, so without it they could no longer
-- give anyone the Support role. It changes nothing they see: finance view already shows them every payment.
-- =====================================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYMENT_SEARCH'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
