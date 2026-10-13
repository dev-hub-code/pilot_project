-- Container numbers are recorded as finance types them (tidied: no spaces or dashes, upper case); the ISO 6346
-- format is no longer enforced.
ALTER TABLE containers
    DROP CONSTRAINT containers_number_format_chk,
    ALTER COLUMN container_number TYPE VARCHAR(20);
