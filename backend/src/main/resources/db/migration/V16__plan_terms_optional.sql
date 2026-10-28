-- Plans no longer carry their own risk disclosure or terms and conditions: neither is asked for or shown. The columns
-- stay, with what earlier plans had, but new plans leave them empty.
ALTER TABLE investment_products
    ALTER COLUMN risk_disclosure DROP NOT NULL,
    ALTER COLUMN terms_and_conditions DROP NOT NULL;
