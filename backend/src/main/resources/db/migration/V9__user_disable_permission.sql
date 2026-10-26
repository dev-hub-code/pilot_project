-- Disabling an account, and enabling a disabled one, is for super administrators only; admins keep suspend/reactivate.
INSERT INTO permissions (code, category, description)
VALUES ('USER_DISABLE', 'USER', 'Disable user accounts, and enable disabled ones');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'USER_DISABLE'
WHERE r.name = 'SUPER_ADMIN';
