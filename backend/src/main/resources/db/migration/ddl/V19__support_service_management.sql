-- Customer support & service management: configurable categories, SLA policies on a business calendar, escalation,
-- assignment rules, automation, customer feedback and a knowledge base.
-- Additive only: existing tickets, messages, events and attachments are untouched (and stay immutable); allowed values
-- are widened, never narrowed; no role, permission or role-permission mapping is changed here.

-- ---------------------------------------------------------------------------------------------- allowed values
-- Statuses: NEW (not yet picked up), IN_PROGRESS, WAITING_ON_INTERNAL, ESCALATED and REOPENED join the existing four.
ALTER TABLE support_tickets DROP CONSTRAINT support_tickets_status_chk;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_status_chk CHECK (status IN
    ('NEW', 'OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'WAITING_ON_INTERNAL', 'ESCALATED', 'RESOLVED', 'CLOSED', 'REOPENED'));

-- Categories: KYC & compliance, container & leasing, technical join the existing seven.
ALTER TABLE support_tickets DROP CONSTRAINT support_tickets_category_chk;
ALTER TABLE support_tickets ADD CONSTRAINT support_tickets_category_chk CHECK (category IN
    ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'KYC', 'REFERRAL', 'CONTAINER', 'TECHNICAL', 'OTHER'));

-- The category may now be corrected by staff (it was fixed at creation).
ALTER TABLE ticket_events DROP CONSTRAINT ticket_events_type_chk;
ALTER TABLE ticket_events ADD CONSTRAINT ticket_events_type_chk CHECK (type IN
    ('OPENED', 'STATUS_CHANGED', 'REOPENED', 'ASSIGNED', 'UNASSIGNED', 'PRIORITY_CHANGED', 'TEAM_CHANGED',
     'CATEGORY_CHANGED', 'ESCALATED', 'SLA_WARNING', 'SLA_BREACHED', 'SLA_PAUSED', 'SLA_RESUMED', 'FEEDBACK'));

-- ------------------------------------------------------------------------------------------------- categories
-- Top-level categories are the ticket's category column (routing depends on them); subcategories are configurable.
CREATE TABLE support_categories (
    id uuid NOT NULL,
    parent_id uuid,
    category character varying(20) NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(80) NOT NULL,
    description character varying(255),
    default_priority character varying(10),
    default_team character varying(20),
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT support_categories_pkey PRIMARY KEY (id),
    CONSTRAINT support_categories_code_uk UNIQUE (code),
    CONSTRAINT support_categories_parent_fk FOREIGN KEY (parent_id) REFERENCES support_categories (id),
    CONSTRAINT support_categories_category_chk CHECK (category IN
        ('ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'KYC', 'REFERRAL', 'CONTAINER', 'TECHNICAL', 'OTHER')),
    CONSTRAINT support_categories_priority_chk CHECK (default_priority IS NULL OR default_priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_categories_team_chk CHECK (default_team IS NULL OR default_team IN ('SUPPORT', 'FINANCE')),
    -- A top-level row is its category; subcategories sit one level below.
    CONSTRAINT support_categories_top_chk CHECK (parent_id IS NOT NULL OR code = category)
);
CREATE INDEX support_categories_parent_idx ON support_categories (parent_id, sort_order);

-- --------------------------------------------------------------------------------------- SLA & business calendar
CREATE TABLE support_sla_policies (
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    -- Each criterion empty matches anything; the most specific active policy applies.
    priority character varying(10),
    category character varying(20),
    customer_type character varying(20),
    first_response_minutes integer NOT NULL,
    next_response_minutes integer,
    resolution_minutes integer NOT NULL,
    -- Targets count business time (working hours, working days, not holidays) or every minute.
    business_hours boolean NOT NULL,
    -- While a ticket waits on the customer, its resolution clock stops.
    pause_while_waiting boolean NOT NULL DEFAULT true,
    active boolean NOT NULL DEFAULT true,
    updated_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT support_sla_policies_pkey PRIMARY KEY (id),
    CONSTRAINT support_sla_policies_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT support_sla_policies_priority_chk CHECK (priority IS NULL OR priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT support_sla_policies_customer_chk CHECK (customer_type IS NULL OR customer_type IN ('NETWORK', 'ONLINE')),
    CONSTRAINT support_sla_policies_minutes_chk CHECK (first_response_minutes > 0 AND resolution_minutes > 0
        AND (next_response_minutes IS NULL OR next_response_minutes > 0) AND resolution_minutes >= first_response_minutes)
);

-- Working hours per ISO day of week (1 Monday … 7 Sunday), in the display time zone; a missing day is closed.
CREATE TABLE support_business_hours (
    day_of_week smallint NOT NULL,
    opens_at time NOT NULL,
    closes_at time NOT NULL,
    CONSTRAINT support_business_hours_pkey PRIMARY KEY (day_of_week),
    CONSTRAINT support_business_hours_day_chk CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT support_business_hours_range_chk CHECK (closes_at > opens_at)
);

CREATE TABLE support_holidays (
    day date NOT NULL,
    name character varying(100) NOT NULL,
    CONSTRAINT support_holidays_pkey PRIMARY KEY (day)
);

ALTER TABLE support_tickets
    ADD COLUMN subcategory_id uuid,
    ADD COLUMN channel character varying(20) NOT NULL DEFAULT 'PORTAL',
    ADD COLUMN created_by uuid,
    ADD COLUMN sla_policy_id uuid,
    ADD COLUMN resolution_due_at timestamp with time zone,
    ADD COLUMN next_response_due_at timestamp with time zone,
    ADD COLUMN sla_paused_at timestamp with time zone,
    -- Business minutes the resolution clock has been stopped in total: added to the resolution target.
    ADD COLUMN sla_paused_minutes integer NOT NULL DEFAULT 0,
    ADD COLUMN first_response_breached_at timestamp with time zone,
    ADD COLUMN next_response_breached_at timestamp with time zone,
    ADD COLUMN resolution_breached_at timestamp with time zone,
    ADD COLUMN acknowledged_at timestamp with time zone,
    ADD COLUMN escalation_level smallint NOT NULL DEFAULT 0,
    ADD COLUMN reopen_count integer NOT NULL DEFAULT 0,
    ADD COLUMN customer_follow_ups integer NOT NULL DEFAULT 0,
    ADD COLUMN first_response_business_minutes integer,
    ADD COLUMN resolution_business_minutes integer,
    ADD COLUMN feedback_requested_at timestamp with time zone,
    ADD CONSTRAINT support_tickets_subcategory_fk FOREIGN KEY (subcategory_id) REFERENCES support_categories (id),
    ADD CONSTRAINT support_tickets_created_by_fk FOREIGN KEY (created_by) REFERENCES users (id),
    ADD CONSTRAINT support_tickets_sla_policy_fk FOREIGN KEY (sla_policy_id) REFERENCES support_sla_policies (id),
    ADD CONSTRAINT support_tickets_channel_chk CHECK (channel IN ('PORTAL', 'STAFF', 'SYSTEM', 'EMAIL')),
    ADD CONSTRAINT support_tickets_escalation_chk CHECK (escalation_level BETWEEN 0 AND 4);

-- Human-readable references, SUP-<year>-<number>: one counter row per year, taken under a row lock.
CREATE TABLE support_ticket_counters (
    year integer NOT NULL,
    last_number bigint NOT NULL,
    CONSTRAINT support_ticket_counters_pkey PRIMARY KEY (year)
);

-- -------------------------------------------------------------------------------------------------- escalation
CREATE TABLE support_escalation_levels (
    level smallint NOT NULL,
    name character varying(60) NOT NULL,
    description character varying(255),
    CONSTRAINT support_escalation_levels_pkey PRIMARY KEY (level),
    CONSTRAINT support_escalation_levels_chk CHECK (level BETWEEN 1 AND 4)
);

-- Who is told at each level: existing staff accounts (level 1 is always the assignee as well).
CREATE TABLE support_escalation_contacts (
    level smallint NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT support_escalation_contacts_pkey PRIMARY KEY (level, user_id),
    CONSTRAINT support_escalation_contacts_level_fk FOREIGN KEY (level) REFERENCES support_escalation_levels (level),
    CONSTRAINT support_escalation_contacts_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE support_escalations (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    level smallint NOT NULL,
    previous_level smallint NOT NULL,
    trigger character varying(30) NOT NULL,
    reason character varying(500) NOT NULL,
    escalated_by uuid,
    owner_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT support_escalations_pkey PRIMARY KEY (id),
    CONSTRAINT support_escalations_ticket_fk FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT support_escalations_by_fk FOREIGN KEY (escalated_by) REFERENCES users (id),
    CONSTRAINT support_escalations_owner_fk FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT support_escalations_level_chk CHECK (level BETWEEN 1 AND 4 AND previous_level BETWEEN 0 AND 3 AND level > previous_level),
    CONSTRAINT support_escalations_trigger_chk CHECK (trigger IN ('MANUAL', 'FIRST_RESPONSE_AT_RISK', 'FIRST_RESPONSE_BREACHED',
        'RESOLUTION_BREACHED', 'CRITICAL_UNACKNOWLEDGED', 'REPEATED_FOLLOW_UPS', 'REPEATED_REOPENS', 'AUTOMATION'))
);
CREATE INDEX support_escalations_ticket_idx ON support_escalations (ticket_id, created_at);
CREATE INDEX support_escalations_created_idx ON support_escalations (created_at DESC);
CREATE TRIGGER support_escalations_immutable BEFORE DELETE OR UPDATE ON support_escalations FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- -------------------------------------------------------------------------------------------------- assignment
CREATE TABLE support_assignment_rules (
    category character varying(20) NOT NULL,
    strategy character varying(20) NOT NULL,
    -- Round-robin position: the agent picked last.
    last_assignee_id uuid,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT support_assignment_rules_pkey PRIMARY KEY (category),
    CONSTRAINT support_assignment_rules_last_fk FOREIGN KEY (last_assignee_id) REFERENCES users (id),
    CONSTRAINT support_assignment_rules_by_fk FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT support_assignment_rules_strategy_chk CHECK (strategy IN ('MANUAL', 'ROUND_ROBIN', 'LEAST_LOADED'))
);

-- Optional pool per category; empty means every agent who may handle the ticket's team.
CREATE TABLE support_assignment_members (
    category character varying(20) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT support_assignment_members_pkey PRIMARY KEY (category, user_id),
    CONSTRAINT support_assignment_members_rule_fk FOREIGN KEY (category) REFERENCES support_assignment_rules (category),
    CONSTRAINT support_assignment_members_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);

-- -------------------------------------------------------------------------------------------------- automation
CREATE TABLE support_automation_rules (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    trigger character varying(30) NOT NULL,
    -- {"priorities": [...], "categories": [...], "teams": [...], "minutes": n, "percent": n}: values only, no code.
    conditions jsonb NOT NULL DEFAULT '{}'::jsonb,
    action character varying(30) NOT NULL,
    parameters jsonb NOT NULL DEFAULT '{}'::jsonb,
    enabled boolean NOT NULL DEFAULT true,
    updated_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT support_automation_rules_pkey PRIMARY KEY (id),
    CONSTRAINT support_automation_rules_by_fk FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT support_automation_rules_trigger_chk CHECK (trigger IN ('TICKET_CREATED', 'UNASSIGNED_FOR', 'SLA_PERCENT',
        'SLA_BREACHED', 'CUSTOMER_REPLIED_RESOLVED', 'TICKET_RESOLVED', 'TICKET_CLOSED', 'TICKET_REOPENED')),
    CONSTRAINT support_automation_rules_action_chk CHECK (action IN ('NOTIFY_ASSIGNEE', 'NOTIFY_TEAM',
        'NOTIFY_ESCALATION_CONTACTS', 'AUTO_ASSIGN', 'ESCALATE', 'SET_PRIORITY', 'REQUEST_FEEDBACK', 'RECORD_RESOLUTION_METRICS'))
);

CREATE TABLE support_automation_runs (
    id uuid NOT NULL,
    rule_id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    -- One run per rule, ticket and occurrence (e.g. the SLA target it fired for): reruns are no-ops.
    dedupe_key character varying(200) NOT NULL,
    status character varying(20) NOT NULL,
    attempts integer NOT NULL,
    last_error character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT support_automation_runs_pkey PRIMARY KEY (id),
    CONSTRAINT support_automation_runs_dedupe_uk UNIQUE (dedupe_key),
    CONSTRAINT support_automation_runs_rule_fk FOREIGN KEY (rule_id) REFERENCES support_automation_rules (id),
    CONSTRAINT support_automation_runs_ticket_fk FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT support_automation_runs_status_chk CHECK (status IN ('SUCCEEDED', 'FAILED', 'SKIPPED'))
);
CREATE INDEX support_automation_runs_rule_idx ON support_automation_runs (rule_id, created_at DESC);
CREATE INDEX support_automation_runs_retry_idx ON support_automation_runs (status, updated_at) WHERE status = 'FAILED';

-- ---------------------------------------------------------------------------------------------------- feedback
CREATE TABLE support_feedback (
    id uuid NOT NULL,
    ticket_id uuid NOT NULL,
    user_id uuid NOT NULL,
    rating smallint NOT NULL,
    comment character varying(1000),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT support_feedback_pkey PRIMARY KEY (id),
    CONSTRAINT support_feedback_ticket_uk UNIQUE (ticket_id),
    CONSTRAINT support_feedback_ticket_fk FOREIGN KEY (ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT support_feedback_user_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT support_feedback_rating_chk CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX support_feedback_created_idx ON support_feedback (created_at);
CREATE TRIGGER support_feedback_immutable BEFORE DELETE OR UPDATE ON support_feedback FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- ---------------------------------------------------------------------------------------------- knowledge base
CREATE TABLE kb_categories (
    id uuid NOT NULL,
    parent_id uuid,
    code character varying(40) NOT NULL,
    name character varying(80) NOT NULL,
    description character varying(255),
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT kb_categories_pkey PRIMARY KEY (id),
    CONSTRAINT kb_categories_code_uk UNIQUE (code),
    CONSTRAINT kb_categories_parent_fk FOREIGN KEY (parent_id) REFERENCES kb_categories (id)
);

CREATE TABLE kb_articles (
    id uuid NOT NULL,
    slug character varying(120) NOT NULL,
    category_id uuid NOT NULL,
    title character varying(200) NOT NULL,
    summary character varying(300) NOT NULL,
    body text NOT NULL,
    tags character varying(400) NOT NULL DEFAULT '',
    -- PUBLIC: anyone; CUSTOMER: signed-in customers; INTERNAL: staff only.
    visibility character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    current_version integer NOT NULL,
    author_id uuid NOT NULL,
    updated_by uuid NOT NULL,
    published_at timestamp with time zone,
    view_count bigint NOT NULL DEFAULT 0,
    helpful_count integer NOT NULL DEFAULT 0,
    not_helpful_count integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A')
        || setweight(to_tsvector('english', coalesce(summary, '') || ' ' || coalesce(tags, '')), 'B')
        || setweight(to_tsvector('english', coalesce(body, '')), 'C')) STORED,
    CONSTRAINT kb_articles_pkey PRIMARY KEY (id),
    CONSTRAINT kb_articles_slug_uk UNIQUE (slug),
    CONSTRAINT kb_articles_category_fk FOREIGN KEY (category_id) REFERENCES kb_categories (id),
    CONSTRAINT kb_articles_author_fk FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT kb_articles_updated_by_fk FOREIGN KEY (updated_by) REFERENCES users (id),
    CONSTRAINT kb_articles_visibility_chk CHECK (visibility IN ('PUBLIC', 'CUSTOMER', 'INTERNAL')),
    CONSTRAINT kb_articles_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT kb_articles_slug_chk CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT kb_articles_body_chk CHECK (length(body) BETWEEN 1 AND 100000),
    CONSTRAINT kb_articles_published_chk CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);
CREATE INDEX kb_articles_search_idx ON kb_articles USING gin (search_vector);
CREATE INDEX kb_articles_category_idx ON kb_articles (category_id, status);
CREATE INDEX kb_articles_listing_idx ON kb_articles (status, visibility, updated_at DESC);

-- Every saved edit, in order: the article's history.
CREATE TABLE kb_article_versions (
    id uuid NOT NULL,
    article_id uuid NOT NULL,
    version integer NOT NULL,
    title character varying(200) NOT NULL,
    summary character varying(300) NOT NULL,
    body text NOT NULL,
    edited_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT kb_article_versions_pkey PRIMARY KEY (id),
    CONSTRAINT kb_article_versions_uk UNIQUE (article_id, version),
    CONSTRAINT kb_article_versions_article_fk FOREIGN KEY (article_id) REFERENCES kb_articles (id),
    CONSTRAINT kb_article_versions_by_fk FOREIGN KEY (edited_by) REFERENCES users (id)
);
CREATE TRIGGER kb_article_versions_immutable BEFORE DELETE OR UPDATE ON kb_article_versions FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE kb_article_related (
    article_id uuid NOT NULL,
    related_id uuid NOT NULL,
    CONSTRAINT kb_article_related_pkey PRIMARY KEY (article_id, related_id),
    CONSTRAINT kb_article_related_article_fk FOREIGN KEY (article_id) REFERENCES kb_articles (id),
    CONSTRAINT kb_article_related_related_fk FOREIGN KEY (related_id) REFERENCES kb_articles (id),
    CONSTRAINT kb_article_related_self_chk CHECK (article_id <> related_id)
);

CREATE TABLE kb_article_feedback (
    id uuid NOT NULL,
    article_id uuid NOT NULL,
    user_id uuid,
    helpful boolean NOT NULL,
    comment character varying(1000),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT kb_article_feedback_pkey PRIMARY KEY (id),
    CONSTRAINT kb_article_feedback_article_fk FOREIGN KEY (article_id) REFERENCES kb_articles (id),
    CONSTRAINT kb_article_feedback_user_fk FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX kb_article_feedback_article_idx ON kb_article_feedback (article_id, created_at DESC);
-- A signed-in customer rates an article once.
CREATE UNIQUE INDEX kb_article_feedback_user_uk ON kb_article_feedback (article_id, user_id) WHERE user_id IS NOT NULL;

-- ------------------------------------------------------------------------------------------- ticket query indexes
CREATE INDEX IF NOT EXISTS support_tickets_created_idx ON support_tickets (created_at);
CREATE INDEX IF NOT EXISTS support_tickets_status_priority_idx ON support_tickets (status, priority, created_at);
CREATE INDEX IF NOT EXISTS support_tickets_resolution_due_idx ON support_tickets (resolution_due_at)
    WHERE status NOT IN ('RESOLVED', 'CLOSED');
CREATE INDEX IF NOT EXISTS support_tickets_resolved_idx ON support_tickets (resolved_at) WHERE resolved_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS support_tickets_category_idx ON support_tickets (category, subcategory_id);
