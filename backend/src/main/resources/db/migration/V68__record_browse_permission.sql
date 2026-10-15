-- =====================================================================================
-- V68: Browsing all orders and withdrawals gets its own permission.
--
-- Support opens an investor's order or withdrawal to answer a ticket, but should not page through everyone's: without
-- this permission the lists show only what an exact search finds (an order number, a withdrawal reference, or one
-- investor by readable ID or email). Every other role that can view orders or withdrawals keeps browsing.
-- =====================================================================================

INSERT INTO permissions (code, category, description)
VALUES ('RECORD_BROWSE', 'INVESTMENT',
        'Browse all orders and withdrawals; without it, only an exact search (number, reference or investor) finds them');

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
FROM role_permissions rp
         JOIN permissions v ON v.id = rp.permission_id AND v.code IN ('ORDER_VIEW', 'WITHDRAWAL_VIEW')
         JOIN roles r ON r.id = rp.role_id
         CROSS JOIN permissions p
WHERE p.code = 'RECORD_BROWSE'
  AND r.name <> 'SUPPORT'
ON CONFLICT DO NOTHING;
