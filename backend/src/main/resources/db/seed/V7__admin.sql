-- The platform administrator. Its password is changed from the app, never here.

INSERT INTO users (id, email, password_hash, first_name, last_name, status, failed_login_attempts, locked_until, last_login_at, password_changed_at, email_verified_at, status_reason, status_changed_at, status_changed_by, mfa_enabled, must_change_password, temporary_password_expires_at, user_number, version, created_at, updated_at, terms_version, terms_accepted_at)
VALUES ('7f000101-a154-1113-81a1-544131ec0000', 'admin@sealease.in', '{argon2id}$argon2id$v=19$m=19456,t=2,p=1$qec2tN5ZcrnArAy/orqHxg$Ynr6EB0DHC8xmHS30Cr/QrpIC+WSlvB6Kp5XWcu8zX4', 'Platform', 'Administrator', 'ACTIVE', 0, NULL, '2026-11-01T19:59:41.437024+00:00', '2026-10-27T16:38:09.894578+00:00', '2026-10-19T13:02:01.171016+00:00', NULL, NULL, NULL, FALSE, FALSE, NULL, 3500, 143, '2026-10-19T13:02:01.203239+00:00', '2026-11-01T19:59:41.494021+00:00', NULL, NULL);

INSERT INTO user_profiles (id, user_id, phone, date_of_birth, nationality, address_line1, address_line2, city, state_region, postal_code, country, tax_residency_country, tax_id_encrypted, tax_id_last4, kyc_status, email_notifications, sms_notifications, phone_key, version, created_at, updated_at, phone_country)
VALUES ('7f000101-a154-1113-81a1-544132180001', '7f000101-a154-1113-81a1-544131ec0000', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'NOT_SUBMITTED', TRUE, FALSE, NULL, 0, '2026-10-19T13:02:01.240985+00:00', '2026-10-19T13:02:01.240985+00:00', NULL);

INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT u.id, r.id, NULL, u.created_at
FROM users u
         JOIN roles r ON r.name = 'SUPER_ADMIN';

-- Numbering continues after this row.
SELECT setval('user_number_seq', (SELECT max(user_number) FROM users));
