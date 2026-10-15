-- =====================================================================================
-- V65: Investors' full mobile numbers on the staff user detail are for admins only.
--
-- Every other role sees the number masked to its last four digits.
-- =====================================================================================

INSERT INTO permissions (code, category, description)
VALUES ('PHONE_NUMBER_VIEW', 'USER', 'See users'' full mobile numbers; others see only the last four digits');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PHONE_NUMBER_VIEW'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN')
ON CONFLICT DO NOTHING;
