-- =====================================================================================
-- V10: Sales CRM - leads, their pipeline stage and an append-only activity log.
--
-- Leads come from sales staff or the public interest form. A lead links to an account when that
-- email registers (or already exists); the account's first confirmed investment marks it WON.
-- =====================================================================================

CREATE SEQUENCE lead_number_seq START WITH 1;

CREATE TABLE leads
(
    id                     UUID PRIMARY KEY,
    reference              VARCHAR(20)    NOT NULL,
    first_name             VARCHAR(100)   NOT NULL,
    last_name              VARCHAR(100),
    -- Normalised (lower-case); required from the website, optional for staff (phone-only leads).
    email                  VARCHAR(254),
    phone                  VARCHAR(40),
    country                VARCHAR(2),
    source                 VARCHAR(20)    NOT NULL,
    interest               VARCHAR(20)    NOT NULL,
    estimated_amount       NUMERIC(19, 4),
    estimated_currency     VARCHAR(3),
    message                VARCHAR(2000),
    stage                  VARCHAR(20)    NOT NULL,
    lost_reason            VARCHAR(500),
    owner_id               UUID REFERENCES users (id),
    user_id                UUID REFERENCES users (id),
    won_amount             NUMERIC(19, 4),
    won_currency           VARCHAR(3),
    won_order_id           UUID REFERENCES orders (id),
    closed_at              TIMESTAMPTZ,
    next_follow_up_at      TIMESTAMPTZ,
    consent_at             TIMESTAMPTZ,
    created_by             UUID REFERENCES users (id),
    version                BIGINT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT leads_reference_uk UNIQUE (reference),
    CONSTRAINT leads_contact_chk CHECK (email IS NOT NULL OR phone IS NOT NULL),
    CONSTRAINT leads_email_chk CHECK (email IS NULL OR email = lower(email)),
    CONSTRAINT leads_country_chk CHECK (country IS NULL OR country ~ '^[A-Z]{2}$'),
    CONSTRAINT leads_source_chk CHECK (source IN ('WEBSITE', 'STAFF')),
    CONSTRAINT leads_interest_chk CHECK (interest IN ('RETAIL', 'HNI', 'UNSURE')),
    CONSTRAINT leads_estimate_chk CHECK (
        (estimated_amount IS NULL AND estimated_currency IS NULL)
            OR (estimated_amount > 0 AND estimated_currency ~ '^[A-Z]{3}$')),
    CONSTRAINT leads_stage_chk CHECK (stage IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'WON', 'LOST')),
    CONSTRAINT leads_lost_chk CHECK ((stage = 'LOST') = (lost_reason IS NOT NULL)),
    CONSTRAINT leads_closed_chk CHECK ((stage IN ('WON', 'LOST')) = (closed_at IS NOT NULL)),
    CONSTRAINT leads_won_chk CHECK (
        (won_amount IS NULL AND won_currency IS NULL) OR (stage = 'WON' AND won_amount > 0 AND won_currency ~ '^[A-Z]{3}$')),
    -- Website leads are only accepted with consent to be contacted.
    CONSTRAINT leads_consent_chk CHECK (source <> 'WEBSITE' OR (consent_at IS NOT NULL AND email IS NOT NULL))
);
-- One open lead per email: a repeat enquiry is logged on the existing lead.
CREATE UNIQUE INDEX leads_open_email_uk ON leads (email) WHERE email IS NOT NULL AND stage NOT IN ('WON', 'LOST');
CREATE INDEX leads_owner_idx ON leads (owner_id, stage);
CREATE INDEX leads_stage_idx ON leads (stage, created_at DESC);
CREATE INDEX leads_user_idx ON leads (user_id);
CREATE INDEX leads_follow_up_idx ON leads (next_follow_up_at) WHERE stage NOT IN ('WON', 'LOST');

CREATE TABLE lead_activities
(
    id            UUID PRIMARY KEY,
    lead_id       UUID         NOT NULL REFERENCES leads (id),
    activity_type VARCHAR(20)  NOT NULL,
    body          VARCHAR(4000) NOT NULL,
    actor_id      UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT lead_activities_type_chk CHECK (activity_type IN
        ('NOTE', 'CALL', 'EMAIL', 'MEETING', 'STAGE_CHANGE', 'ASSIGNMENT', 'SYSTEM'))
);
CREATE INDEX lead_activities_lead_idx ON lead_activities (lead_id, created_at DESC);
CREATE TRIGGER lead_activities_immutable
    BEFORE UPDATE OR DELETE
    ON lead_activities
    FOR EACH ROW
EXECUTE FUNCTION forbid_mutation();
