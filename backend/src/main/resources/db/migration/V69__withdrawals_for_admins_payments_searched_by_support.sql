-- =====================================================================================
-- V69: Withdrawals are for admins only; support finds payments by searching.
--
-- Support no longer sees withdrawals at all. It finds an investor's payments only by an exact search (a whole order
-- number, a payment reference, or one investor by readable ID or email), never by browsing. RECORD_BROWSE (V68) now
-- governs orders only, as withdrawals are admin-only.
-- =====================================================================================

DELETE
FROM role_permissions rp
    USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND p.code = 'WITHDRAWAL_VIEW'
  AND r.name = 'SUPPORT';

UPDATE permissions
SET description = 'Browse all orders; without it, only an exact search (order number or investor) finds them'
WHERE code = 'RECORD_BROWSE';

INSERT INTO permissions (code, category, description)
VALUES ('PAYMENT_SEARCH', 'FINANCE',
        'Find payments by an exact search (order number, payment reference or investor), without browsing them');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYMENT_SEARCH'
WHERE r.name = 'SUPPORT'
ON CONFLICT DO NOTHING;
