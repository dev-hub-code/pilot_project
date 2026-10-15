-- =====================================================================================
-- V51: Countries, their states and their cities, for address dropdowns (loaded by V52).
--
-- Reference data from the open countries-states-cities database (ODbL-1.0); see scripts/generate-locations.py.
-- Ids are the dataset's, so a later load can update rows in place. Addresses still store names, not these ids.
-- =====================================================================================

CREATE TABLE countries
(
    code       VARCHAR(2)   PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    iso3       VARCHAR(3),
    phone_code VARCHAR(20),
    CONSTRAINT countries_code_chk CHECK (code ~ '^[A-Z]{2}$')
);

CREATE TABLE states
(
    id           INT          PRIMARY KEY,
    country_code VARCHAR(2)   NOT NULL REFERENCES countries (code),
    name         VARCHAR(120) NOT NULL,
    code         VARCHAR(10)
);
CREATE INDEX states_country_idx ON states (country_code, name);

CREATE TABLE cities
(
    id       INT          PRIMARY KEY,
    state_id INT          NOT NULL REFERENCES states (id),
    name     VARCHAR(120) NOT NULL
);
CREATE INDEX cities_state_idx ON cities (state_id, name);
