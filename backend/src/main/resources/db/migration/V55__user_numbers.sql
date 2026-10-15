-- =====================================================================================
-- V55: A readable number for every user, shown as their ID ("SL03500").
--
-- Numbers start at 3500 and come from a sequence as accounts are created; existing accounts are numbered in the
-- order they were created. The UUID stays the key everywhere else.
-- =====================================================================================
CREATE SEQUENCE user_number_seq START 3500;

ALTER TABLE users ADD COLUMN user_number BIGINT;

UPDATE users u
SET user_number = n.num
FROM (SELECT id, 3499 + row_number() OVER (ORDER BY created_at, id) AS num FROM users) n
WHERE u.id = n.id;

SELECT setval('user_number_seq', greatest(3500, coalesce((SELECT max(user_number) + 1 FROM users), 3500)), false);

ALTER TABLE users
    ALTER COLUMN user_number SET DEFAULT nextval('user_number_seq'),
    ALTER COLUMN user_number SET NOT NULL,
    ADD CONSTRAINT users_user_number_uk UNIQUE (user_number);
ALTER SEQUENCE user_number_seq OWNED BY users.user_number;
