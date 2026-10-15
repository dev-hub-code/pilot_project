-- =====================================================================================
-- V60: Finance pays the finance tasks again, each one assigned to one member first.
--
-- Finance tasks are the withdrawals a withdrawal run (made by an admin) puts in its bank and cash sheets: finance pays
-- each and marks it paid or failed. Finance gets only that (FINANCE_TASK_PROCESS), not withdrawals, runs or batches.
-- Like every finance task, a member assigns a withdrawal to themselves before marking it; only they may then.
-- =====================================================================================

INSERT INTO permissions (code, category, description)
VALUES ('FINANCE_TASK_PROCESS', 'FINANCE', 'Pay finance tasks (withdrawals made by runs) and mark them paid or failed');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'FINANCE_TASK_PROCESS'
WHERE r.name IN ('SUPER_ADMIN', 'ADMIN', 'FINANCE')
ON CONFLICT DO NOTHING;

ALTER TABLE withdrawals
    ADD COLUMN task_assignee_id UUID REFERENCES users (id),
    ADD COLUMN task_assigned_at TIMESTAMPTZ,
    ADD CONSTRAINT withdrawals_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL));
