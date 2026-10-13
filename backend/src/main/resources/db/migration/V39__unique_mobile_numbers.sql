-- =====================================================================================
-- V39: One mobile number per account.
--
-- phone_key is the number reduced to digits, without India's +91 or leading 0, so "98765 43210",
-- "+91 98765 43210" and "098765 43210" are the same number (the application computes it, see PhoneNumbers).
-- Where existing profiles already share a number, only the oldest keeps the key; the others must enter a
-- different number the next time they save their profile.
-- =====================================================================================

ALTER TABLE user_profiles ADD COLUMN phone_key VARCHAR(20);

WITH digits AS (
    SELECT id, created_at, regexp_replace(phone, '[^0-9]', '', 'g') AS d FROM user_profiles WHERE phone IS NOT NULL
), keyed AS (
    SELECT id, created_at,
           CASE WHEN length(d) = 12 AND d LIKE '91%' THEN substr(d, 3)
                WHEN length(d) = 11 AND d LIKE '0%' THEN substr(d, 2)
                ELSE d END AS k
    FROM digits
), ranked AS (
    SELECT id, k, row_number() OVER (PARTITION BY k ORDER BY created_at, id) AS n FROM keyed WHERE k <> ''
)
UPDATE user_profiles p SET phone_key = r.k FROM ranked r WHERE r.id = p.id AND r.n = 1;

CREATE UNIQUE INDEX user_profiles_phone_key_uk ON user_profiles (phone_key) WHERE phone_key IS NOT NULL;
