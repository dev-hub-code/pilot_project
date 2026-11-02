-- Removes the seven roles the department job roles replace. SUPER_ADMIN and INVESTOR stay: bootstrap and sign-up need them.
-- Stops rather than take access away from anyone: move holders to a job role first.
DO $$
DECLARE
    holders text;
BEGIN
    SELECT string_agg(r.name || ' (' || n || ')', ', ') INTO holders
    FROM roles r
             JOIN (SELECT role_id, count(*) AS n FROM user_roles GROUP BY role_id) h ON h.role_id = r.id
    WHERE r.name IN ('ADMIN', 'ADMINISTRATION', 'FINANCE', 'REPORT_VIEWER', 'SALES', 'SUPPORT', 'VERIFICATION');
    IF holders IS NOT NULL THEN
        RAISE EXCEPTION 'Old roles still have holders: %. Move them to a job role, then run this again.', holders;
    END IF;
END $$;

-- Only the old roles held these, so they move to the job roles that do this work.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES ('PLATFORM_ADMIN', 'ROLE_MANAGE'),
             ('ORDER_EXECUTIVE', 'RECORD_BROWSE'),
             ('FINANCE_MANAGER', 'FINANCE_TICKET_HANDLE')) AS grants (role_name, code)
         JOIN roles r ON r.name = grants.role_name
         JOIN permissions p ON p.code = grants.code
ON CONFLICT DO NOTHING;

DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles
                  WHERE name IN ('ADMIN', 'ADMINISTRATION', 'FINANCE', 'REPORT_VIEWER', 'SALES', 'SUPPORT', 'VERIFICATION'));

DELETE FROM roles
WHERE name IN ('ADMIN', 'ADMINISTRATION', 'FINANCE', 'REPORT_VIEWER', 'SALES', 'SUPPORT', 'VERIFICATION');
