-- Phone numbers are stored in international (E.164) form, e.g. '+919876543210', with the country they belong to
-- (validated against that country's numbering plan). The countries and their dialling codes (countries.phone_code)
-- are loaded by V2.

ALTER TABLE user_profiles
    ADD COLUMN phone_country VARCHAR(2),
    ADD CONSTRAINT user_profiles_phone_country_fkey FOREIGN KEY (phone_country) REFERENCES countries (code);

ALTER TABLE leads
    ADD COLUMN phone_country VARCHAR(2),
    ADD CONSTRAINT leads_phone_country_fkey FOREIGN KEY (phone_country) REFERENCES countries (code);

-- Numbers saved before the country was asked for were Indian: those that are Indian mobile numbers become +91
-- numbers (the comparable key gains the country code too). Anything else is left as typed, without a country,
-- until its owner corrects it. Databases built by Hibernate get the same conversion from db/sequences.sql.
UPDATE user_profiles
SET phone         = '+91' || phone_key,
    phone_key     = '91' || phone_key,
    phone_country = 'IN'
WHERE phone_key ~ '^[6-9][0-9]{9}$';

UPDATE leads
SET phone         = '+91' || right(regexp_replace(phone, '[^0-9]', '', 'g'), 10),
    phone_country = 'IN'
WHERE regexp_replace(phone, '[^0-9]', '', 'g') ~ '^(91|0)?[6-9][0-9]{9}$';
