-- =====================================================================================
-- V59: Finance confirms investors' order payments again, each one assigned to one member first.
--
-- Finance sees the payments waiting for confirmation (not the payment history) and, like every finance task, a member
-- assigns a payment to themselves before confirming or rejecting it; only they may then decide it.
-- =====================================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYMENT_CONFIRM'
WHERE r.name = 'FINANCE'
ON CONFLICT DO NOTHING;

ALTER TABLE payments
    ADD COLUMN task_assignee_id UUID REFERENCES users (id),
    ADD COLUMN task_assigned_at TIMESTAMPTZ,
    ADD CONSTRAINT payments_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL));
