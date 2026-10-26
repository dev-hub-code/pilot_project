-- Every lease agreement issued opens a review task: a reviewer (report viewers, super admins) assigns it to
-- themselves, reads the agreement, comments on it and marks it done.
CREATE TABLE agreement_review_tasks
(
    id           UUID PRIMARY KEY,
    agreement_id UUID NOT NULL,
    status       VARCHAR(20) NOT NULL,
    assigned_to  UUID,
    assigned_at  TIMESTAMPTZ,
    completed_by UUID,
    completed_at TIMESTAMPTZ,
    version      BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT agreement_review_tasks_agreement_uk UNIQUE (agreement_id),
    CONSTRAINT agreement_review_tasks_agreement_id_fkey FOREIGN KEY (agreement_id) REFERENCES lease_agreements(id),
    CONSTRAINT agreement_review_tasks_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT agreement_review_tasks_completed_by_fkey FOREIGN KEY (completed_by) REFERENCES users(id),
    CONSTRAINT agreement_review_tasks_status_chk CHECK (status IN ('OPEN', 'DONE')),
    CONSTRAINT agreement_review_tasks_assignment_chk CHECK ((assigned_to IS NULL) = (assigned_at IS NULL)),
    CONSTRAINT agreement_review_tasks_done_chk
        CHECK ((status = 'DONE') = (completed_at IS NOT NULL AND completed_by IS NOT NULL))
);
CREATE INDEX agreement_review_tasks_status_idx ON agreement_review_tasks (status, created_at);

CREATE TABLE agreement_review_comments
(
    id         UUID PRIMARY KEY,
    task_id    UUID NOT NULL,
    author_id  UUID NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT agreement_review_comments_task_id_fkey FOREIGN KEY (task_id) REFERENCES agreement_review_tasks(id),
    CONSTRAINT agreement_review_comments_author_id_fkey FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT agreement_review_comments_body_chk CHECK (length(btrim(body)) > 0)
);
CREATE INDEX agreement_review_comments_task_idx ON agreement_review_comments (task_id, created_at);

-- Agreements issued before this change get a task too.
INSERT INTO agreement_review_tasks (id, agreement_id, status, version, created_at, updated_at)
SELECT gen_random_uuid(), id, 'OPEN', 0, generated_at, generated_at
FROM lease_agreements;

INSERT INTO permissions (code, category, description)
VALUES ('AGREEMENT_REVIEW', 'REPORTING', 'Read investors'' lease agreements and work on their review tasks');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'AGREEMENT_REVIEW'
WHERE r.name IN ('REPORT_VIEWER', 'SUPER_ADMIN');
