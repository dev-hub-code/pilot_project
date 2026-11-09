-- Staff grant only permissions they hold, so with the support permissions given to job roles in V28 but not to the
-- super administrator, nobody could create a support manager, senior support executive or BI manager.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES ('SUPER_ADMIN', 'SUPPORT_CONFIG_MANAGE'),
             ('SUPER_ADMIN', 'KNOWLEDGE_BASE_MANAGE'),
             ('SUPER_ADMIN', 'SUPPORT_ANALYTICS_VIEW')) AS g(role_name, permission_code)
JOIN roles r ON r.name = g.role_name
JOIN permissions p ON p.code = g.permission_code
ON CONFLICT DO NOTHING;
