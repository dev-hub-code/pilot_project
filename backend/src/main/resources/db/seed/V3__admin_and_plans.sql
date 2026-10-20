-- =====================================================================================
-- V3: Environment data taken from the database: the platform administrator and the container listings.
--
-- Kept apart from the schema (db/migration) so the integration tests, which create their own super admin,
-- never load it. Applied only where Flyway includes classpath:db/seed (see spring.flyway.locations).
-- =====================================================================================

-- ------------------------------------------------------------------------- platform administrator

-- Sign-in: admin@sealease.local (password as set for this environment; change it after first sign-in).
INSERT INTO users (id, email, password_hash, first_name, last_name, status, failed_login_attempts, locked_until, last_login_at, password_changed_at, email_verified_at, status_reason, status_changed_at, status_changed_by, mfa_enabled, must_change_password, temporary_password_expires_at, user_number, version, created_at, updated_at)
VALUES ('7f000101-a154-1113-81a1-544131ec0000', 'admin@sealease.local', '{argon2id}$argon2id$v=19$m=19456,t=2,p=1$rQ5S+Nb28huNGb7JCSx3og$ko1VDT+5JQkydZup8vzlyg0HcjmL3MCBDbYlyu7Sid8', 'Platform', 'Administrator', 'ACTIVE', 0, NULL, '2026-10-20T06:39:04.691916+00:00', '2026-10-19T13:02:01.170998+00:00', '2026-10-19T13:02:01.171016+00:00', NULL, NULL, NULL, FALSE, FALSE, NULL, 3500, 34, '2026-10-19T13:02:01.203239+00:00', '2026-10-20T06:39:04.773761+00:00');

INSERT INTO user_profiles (id, user_id, phone, date_of_birth, nationality, address_line1, address_line2, city, state_region, postal_code, country, tax_residency_country, tax_id_encrypted, tax_id_last4, kyc_status, email_notifications, sms_notifications, phone_key, version, created_at, updated_at)
VALUES ('7f000101-a154-1113-81a1-544132180001', '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NOT_SUBMITTED', TRUE, FALSE, NULL, 0, '2026-10-19T13:02:01.240985+00:00', '2026-10-19T13:02:01.240985+00:00');

INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT u.id, r.id, NULL, u.created_at
FROM users u
         JOIN roles r ON r.name = 'SUPER_ADMIN'
WHERE u.email = 'admin@sealease.local';

-- ------------------------------------------------------------------------- container listings

INSERT INTO investment_products (id, code, container_type, title, summary, description, currency, monthly_capital_return_percent, risk_disclosure, terms_and_conditions, status, published_at, published_by, closed_at, cancelled_at, cancellation_reason, min_tenure_years, max_tenure_years, version, created_by, created_at, updated_at, price)
VALUES ('7f000101-a154-1113-81a1-544589a6000f', 'LST-10001', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:06:45.799027+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1113-81a1-544605a3001b', 'LST-10002', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:07:17.539262+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1735-81a1-54475ea10005', 'LST-10003', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:08:45.857228+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1735-81a1-544782d10019', 'LST-10004', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:08:55.12167+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1735-81a1-5447cad70049', 'LST-10005', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:09:13.56006+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1855-81a1-544880a40005', 'LST-10006', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 3, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:10:00.100499+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a154-1855-81a1-5448cd890041', 'LST-10007', 'DRY_40FT', '40ft Dry container', 'Own a container, earn rent', 'Buy a cargo-worthy container and lease it out.', 'INR', 0.0, 'Payouts depend on the platform.', 'Standard terms.', 'DRAFT', NULL, '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 1, 5, 5, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T13:10:19.785562+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0),
       ('7f000101-a155-1723-81a1-552b2c400002', 'LST-10008', 'DRY_10FT', '10ft Dry container', '10 ft Dry Container', '20 feet dry container', 'INR', 0.0, '10 feet dry container', 'jknvklsjelblkbfnkjbsdfl;jblkasjldkfkljkkldjafoajljaksdv', 'OPEN', '2026-10-20T05:40:22.455293+00:00', '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, 3, 10, 8, '7f000101-a154-1113-81a1-544131ec0000', '2026-10-19T17:17:35.168835+00:00', '2026-10-20T06:04:07.709323+00:00', 50000.0);

-- ------------------------------------------------------------------------- minimum order and rent by order value

UPDATE order_settings SET min_order_amount = 100000.0 WHERE id = 1;
DELETE FROM rent_rate_tiers;
INSERT INTO rent_rate_tiers (min_order_amount, max_order_amount, monthly_rent_percent, updated_at)
VALUES (100000.0, 499999.0, 5.0, now()),
       (500000.0, 999999.0, 5.5, now()),
       (1000000.0, NULL, 6.0, now());

-- Numbering continues after the rows above.
SELECT setval('user_number_seq', (SELECT max(user_number) FROM users));
SELECT setval('investment_product_code_seq', (SELECT max(substring(code FROM '[0-9]+$')::BIGINT) FROM investment_products));
