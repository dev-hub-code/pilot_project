-- =====================================================================================
-- V63: Business figures on the admin overview get their own permission.
--
-- Investors, capital invested, withdrawals in progress and the growth charts used to show to anyone who could view
-- the underlying records. Support needs to look up users, investments and withdrawals to answer tickets, but should
-- see only its ticket workload on the overview. Every other role keeps the figures.
-- =====================================================================================

INSERT INTO permissions (code, category, description)
VALUES ('DASHBOARD_FIGURES_VIEW', 'REPORTING',
        'See business figures on the admin overview: investors, capital invested, withdrawals and growth charts');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'DASHBOARD_FIGURES_VIEW'
WHERE r.name NOT IN ('SUPPORT', 'INVESTOR')
ON CONFLICT DO NOTHING;
