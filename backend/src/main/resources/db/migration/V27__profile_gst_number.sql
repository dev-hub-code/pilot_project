-- Optional GST registration number (GSTIN) of an investor who is registered for GST, e.g. 27ABCDE1234F1Z5.
-- GSTINs are public registration numbers, so it is stored as entered (upper case), not encrypted.
ALTER TABLE user_profiles ADD COLUMN gst_number VARCHAR(15);
