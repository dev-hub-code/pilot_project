-- Work no job role could do. Support settings and the knowledge base editor were open only to holders of
-- SYSTEM_SETTINGS_MANAGE (super and platform administrators), because the support permissions added in V20 went to no
-- role; announcements and disabling accounts were the super administrator's alone. Each now also goes to the job role
-- whose work it is. Demo accounts stay with the super administrator alone. Admins can still change any of this on the
-- Roles page.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES ('SUPPORT_MANAGER', 'SUPPORT_CONFIG_MANAGE'),
             ('SUPPORT_MANAGER', 'KNOWLEDGE_BASE_MANAGE'),
             ('SUPPORT_MANAGER', 'SUPPORT_ANALYTICS_VIEW'),
             ('SUPPORT_MANAGER', 'ANNOUNCEMENT_MANAGE'),
             ('SENIOR_SUPPORT_EXECUTIVE', 'KNOWLEDGE_BASE_MANAGE'),
             ('BI_MANAGER', 'SUPPORT_ANALYTICS_VIEW'),
             ('PLATFORM_ADMIN', 'ANNOUNCEMENT_MANAGE'),
             ('PLATFORM_ADMIN', 'USER_DISABLE')) AS granted (role_name, code)
         JOIN roles r ON r.name = granted.role_name
         JOIN permissions p ON p.code = granted.code
ON CONFLICT DO NOTHING;
