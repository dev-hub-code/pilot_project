-- Departments organise staff; they never grant permissions. Role revisions keep each role's past permissions.
-- Approval requests hold sensitive operations until a second person decides them.

ALTER TABLE permissions
    ADD COLUMN sensitive BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reserved BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE departments (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    code character varying(64) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(255) NOT NULL DEFAULT '',
    active boolean NOT NULL DEFAULT TRUE,
    head_user_id uuid,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT departments_pkey PRIMARY KEY (id),
    CONSTRAINT departments_code_uk UNIQUE (code),
    CONSTRAINT departments_code_format_chk CHECK (code ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT departments_head_fkey FOREIGN KEY (head_user_id) REFERENCES users (id)
);

ALTER TABLE roles
    ADD COLUMN department_id uuid,
    ADD COLUMN protected_role boolean NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT roles_department_fkey FOREIGN KEY (department_id) REFERENCES departments (id);
CREATE INDEX roles_department_idx ON roles (department_id);

CREATE TABLE staff_departments (
    user_id uuid NOT NULL,
    department_id uuid NOT NULL,
    primary_assignment boolean NOT NULL DEFAULT FALSE,
    assigned_by uuid,
    assigned_at timestamp with time zone NOT NULL,
    CONSTRAINT staff_departments_pkey PRIMARY KEY (user_id, department_id),
    CONSTRAINT staff_departments_user_fkey FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT staff_departments_department_fkey FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT staff_departments_assigned_by_fkey FOREIGN KEY (assigned_by) REFERENCES users (id)
);
CREATE INDEX staff_departments_department_idx ON staff_departments (department_id);
-- One primary department each.
CREATE UNIQUE INDEX staff_departments_primary_uk ON staff_departments (user_id) WHERE primary_assignment;

CREATE TABLE department_history (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    department_id uuid NOT NULL,
    action character varying(40) NOT NULL,
    detail character varying(255) NOT NULL DEFAULT '',
    actor_id uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT department_history_pkey PRIMARY KEY (id),
    CONSTRAINT department_history_department_fkey FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT department_history_actor_fkey FOREIGN KEY (actor_id) REFERENCES users (id)
);
CREATE INDEX department_history_department_idx ON department_history (department_id, created_at DESC);
CREATE TRIGGER department_history_immutable BEFORE DELETE OR UPDATE ON department_history
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TABLE role_revisions (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    role_id uuid NOT NULL,
    revision bigint NOT NULL,
    description character varying(255) NOT NULL,
    permissions text NOT NULL,
    changed_by uuid,
    changed_at timestamp with time zone NOT NULL,
    CONSTRAINT role_revisions_pkey PRIMARY KEY (id),
    CONSTRAINT role_revisions_role_uk UNIQUE (role_id, revision),
    CONSTRAINT role_revisions_role_fkey FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT role_revisions_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE TABLE approval_requests (
    id uuid NOT NULL,
    type character varying(40) NOT NULL,
    status character varying(20) NOT NULL,
    summary character varying(255) NOT NULL,
    reason character varying(500) NOT NULL,
    payload text NOT NULL,
    created_by uuid NOT NULL,
    assigned_to uuid,
    decided_by uuid,
    decided_at timestamp with time zone,
    decision_reason character varying(500),
    transaction_reference character varying(120),
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT approval_requests_pkey PRIMARY KEY (id),
    CONSTRAINT approval_requests_status_chk CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    -- The database itself refuses a maker deciding their own request.
    CONSTRAINT approval_requests_separation_chk CHECK (decided_by IS NULL OR decided_by <> created_by),
    CONSTRAINT approval_requests_assignee_chk CHECK (assigned_to IS NULL OR assigned_to <> created_by),
    CONSTRAINT approval_requests_decided_chk CHECK ((status = 'PENDING') = (decided_by IS NULL)),
    CONSTRAINT approval_requests_rejection_chk CHECK (status <> 'REJECTED' OR decision_reason IS NOT NULL),
    CONSTRAINT approval_requests_created_by_fkey FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT approval_requests_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users (id),
    CONSTRAINT approval_requests_decided_by_fkey FOREIGN KEY (decided_by) REFERENCES users (id)
);
CREATE INDEX approval_requests_status_idx ON approval_requests (status, created_at DESC);
