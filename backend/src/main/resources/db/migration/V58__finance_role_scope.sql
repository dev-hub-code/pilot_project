-- =====================================================================================
-- V58: The finance team's narrower scope, and finance work assigned to one person.
--
-- Finance views users, assigns containers, verifies bank accounts, sees the company bank accounts (without changing
-- them) and handles the support tickets the support team hands to it. Withdrawals, payments, the ledger, rent,
-- referrals, reports, plans and orders are no longer theirs; admins confirm payments instead.
--
-- Like KYC and bank reviews (V57), a paid investment waiting for its container and a ticket handed to finance are
-- tasks a finance member assigns to themselves before working on them.
-- =====================================================================================

-- ------------------------------------------------------------------- permissions --
INSERT INTO permissions (code, category, description)
VALUES ('COMPANY_BANK_ACCOUNT_VIEW', 'FINANCE', 'View the company bank accounts investors pay into'),
       ('FINANCE_TICKET_HANDLE', 'SUPPORT', 'Handle the support tickets handed to the finance team');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code IN ('COMPANY_BANK_ACCOUNT_VIEW', 'FINANCE_TICKET_HANDLE')
WHERE r.name IN ('SUPER_ADMIN', 'FINANCE')
   OR (r.name = 'ADMIN' AND p.code = 'COMPANY_BANK_ACCOUNT_VIEW')
ON CONFLICT DO NOTHING;

-- Admins confirm investors' payments now that finance does not.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         JOIN permissions p ON p.code = 'PAYMENT_CONFIRM'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- Finance keeps only these.
DELETE
FROM role_permissions rp
    USING roles r, permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'FINANCE'
  AND p.code NOT IN ('USER_VIEW', 'CONTAINER_ASSIGN', 'BANK_ACCOUNT_VERIFY', 'COMPANY_BANK_ACCOUNT_VIEW',
                     'FINANCE_TICKET_HANDLE');

-- ------------------------------------------------------- containers to assign --
ALTER TABLE holdings
    ADD COLUMN task_assignee_id UUID REFERENCES users (id),
    ADD COLUMN task_assigned_at TIMESTAMPTZ,
    ADD CONSTRAINT holdings_task_assignment_chk CHECK ((task_assignee_id IS NULL) = (task_assigned_at IS NULL));

-- ------------------------------------------------------------- support tickets --
-- The team a ticket is with: support, or finance once support hands it over.
ALTER TABLE support_tickets
    ADD COLUMN team VARCHAR(20) NOT NULL DEFAULT 'SUPPORT',
    ADD CONSTRAINT support_tickets_team_chk CHECK (team IN ('SUPPORT', 'FINANCE'));
CREATE INDEX support_tickets_team_idx ON support_tickets (team, last_message_at DESC);

ALTER TABLE ticket_events
    DROP CONSTRAINT ticket_events_type_chk,
    ADD CONSTRAINT ticket_events_type_chk CHECK (type IN
        ('OPENED', 'STATUS_CHANGED', 'REOPENED', 'ASSIGNED', 'UNASSIGNED', 'PRIORITY_CHANGED', 'TEAM_CHANGED'));
