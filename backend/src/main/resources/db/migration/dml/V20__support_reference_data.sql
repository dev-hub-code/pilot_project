-- Customer support reference data. Everything here is configuration staff can change in Support settings.
--
-- Permissions: three new catalogue entries, assigned to no role. Grant them explicitly through role management.
-- No existing role, permission or role-permission mapping is touched.
INSERT INTO permissions (code, category, description, sensitive)
VALUES ('SUPPORT_CONFIG_MANAGE', 'SUPPORT', 'Configure support: SLA policies, business hours, categories, assignment, escalation and automation', TRUE),
       ('KNOWLEDGE_BASE_MANAGE', 'SUPPORT', 'Write, publish and archive knowledge base articles', FALSE),
       ('SUPPORT_ANALYTICS_VIEW', 'SUPPORT', 'View support analytics and customer feedback', FALSE);

-- ------------------------------------------------------------------------------------------------- categories
INSERT INTO support_categories (id, parent_id, category, code, name, description, default_team, sort_order, created_at, updated_at)
VALUES (gen_random_uuid(), NULL, 'ACCOUNT', 'ACCOUNT', 'Account & profile', 'Signing up, signing in and your profile', NULL, 10, now(), now()),
       (gen_random_uuid(), NULL, 'INVESTMENT', 'INVESTMENT', 'Investments', 'Your investments, agreements and maturity', NULL, 20, now(), now()),
       (gen_random_uuid(), NULL, 'PAYMENT', 'PAYMENT', 'Payments', 'Paying for an order, refunds and discrepancies', NULL, 30, now(), now()),
       (gen_random_uuid(), NULL, 'EARNINGS', 'EARNINGS', 'Earnings & returns', 'Returns credited, statements and schedules', NULL, 40, now(), now()),
       (gen_random_uuid(), NULL, 'WITHDRAWAL', 'WITHDRAWAL', 'Withdrawals', 'Withdrawal requests and bank payouts', NULL, 50, now(), now()),
       (gen_random_uuid(), NULL, 'KYC', 'KYC', 'KYC & compliance', 'Identity and bank account verification', NULL, 60, now(), now()),
       (gen_random_uuid(), NULL, 'REFERRAL', 'REFERRAL', 'Referrals', 'Referral links and commission', NULL, 70, now(), now()),
       (gen_random_uuid(), NULL, 'CONTAINER', 'CONTAINER', 'Containers & leasing', 'Containers, leasing and documentation', NULL, 80, now(), now()),
       (gen_random_uuid(), NULL, 'TECHNICAL', 'TECHNICAL', 'Technical support', 'The website, the app and notifications', NULL, 90, now(), now()),
       (gen_random_uuid(), NULL, 'OTHER', 'OTHER', 'Something else', 'Anything not listed', NULL, 100, now(), now());

INSERT INTO support_categories (id, parent_id, category, code, name, default_priority, sort_order, created_at, updated_at)
SELECT gen_random_uuid(), p.id, p.category, s.code, s.name, s.priority, s.sort_order, now(), now()
FROM (VALUES
          ('ACCOUNT', 'ACCOUNT_REGISTRATION', 'Account registration', NULL, 1),
          ('ACCOUNT', 'ACCOUNT_LOGIN', 'Login issues', NULL, 2),
          ('ACCOUNT', 'ACCOUNT_PASSWORD', 'Password reset', NULL, 3),
          ('ACCOUNT', 'ACCOUNT_PROFILE', 'Profile updates', NULL, 4),
          ('ACCOUNT', 'ACCOUNT_SUSPENSION', 'Account suspension', 'HIGH', 5),
          ('INVESTMENT', 'INVESTMENT_QUESTION', 'Investment questions', NULL, 1),
          ('INVESTMENT', 'INVESTMENT_CONFIRMATION', 'Investment confirmation', NULL, 2),
          ('INVESTMENT', 'INVESTMENT_AGREEMENT', 'Investment agreement', NULL, 3),
          ('INVESTMENT', 'INVESTMENT_MATURITY', 'Investment maturity', NULL, 4),
          ('INVESTMENT', 'INVESTMENT_PORTFOLIO', 'Investment portfolio', NULL, 5),
          ('PAYMENT', 'PAYMENT_FAILED', 'Payment failed', 'HIGH', 1),
          ('PAYMENT', 'PAYMENT_PENDING', 'Payment pending', NULL, 2),
          ('PAYMENT', 'PAYMENT_CONFIRMATION', 'Payment confirmation', NULL, 3),
          ('PAYMENT', 'PAYMENT_REFUND', 'Refund request', 'HIGH', 4),
          ('PAYMENT', 'PAYMENT_DISCREPANCY', 'Transaction discrepancy', 'HIGH', 5),
          ('EARNINGS', 'EARNINGS_CALCULATION', 'Interest calculation', NULL, 1),
          ('EARNINGS', 'EARNINGS_MONTHLY', 'Monthly earnings', NULL, 2),
          ('EARNINGS', 'EARNINGS_MISSING', 'Missing earnings', 'HIGH', 3),
          ('EARNINGS', 'EARNINGS_STATEMENT', 'Earnings statement', NULL, 4),
          ('EARNINGS', 'EARNINGS_SCHEDULE', 'Payment schedule', NULL, 5),
          ('WITHDRAWAL', 'WITHDRAWAL_REQUEST', 'Withdrawal request', NULL, 1),
          ('WITHDRAWAL', 'WITHDRAWAL_PENDING', 'Withdrawal pending', NULL, 2),
          ('WITHDRAWAL', 'WITHDRAWAL_REJECTED', 'Withdrawal rejected', 'HIGH', 3),
          ('WITHDRAWAL', 'WITHDRAWAL_BANK', 'Bank verification', NULL, 4),
          ('WITHDRAWAL', 'WITHDRAWAL_RECONCILIATION', 'Withdrawal reconciliation', NULL, 5),
          ('KYC', 'KYC_SUBMISSION', 'KYC submission', NULL, 1),
          ('KYC', 'KYC_VERIFICATION', 'KYC verification', NULL, 2),
          ('KYC', 'KYC_REJECTION', 'Document rejection', NULL, 3),
          ('KYC', 'KYC_BANK_ACCOUNT', 'Bank account verification', NULL, 4),
          ('KYC', 'KYC_PERSONAL_DETAILS', 'Personal information update', NULL, 5),
          ('REFERRAL', 'REFERRAL_REGISTRATION', 'Referral registration', NULL, 1),
          ('REFERRAL', 'REFERRAL_COMMISSION', 'Referral commission', NULL, 2),
          ('REFERRAL', 'REFERRAL_TRACKING', 'Referral tracking', NULL, 3),
          ('REFERRAL', 'REFERRAL_DISCREPANCY', 'Commission discrepancy', NULL, 4),
          ('CONTAINER', 'CONTAINER_AVAILABILITY', 'Container availability', NULL, 1),
          ('CONTAINER', 'CONTAINER_INFORMATION', 'Container information', NULL, 2),
          ('CONTAINER', 'CONTAINER_LEASING', 'Leasing inquiry', NULL, 3),
          ('CONTAINER', 'CONTAINER_DOCUMENTS', 'Container documentation', NULL, 4),
          ('CONTAINER', 'CONTAINER_OPERATIONS', 'Operational issues', NULL, 5),
          ('TECHNICAL', 'TECHNICAL_WEBSITE', 'Website issues', NULL, 1),
          ('TECHNICAL', 'TECHNICAL_ERROR', 'Application errors', NULL, 2),
          ('TECHNICAL', 'TECHNICAL_PERFORMANCE', 'Performance issues', NULL, 3),
          ('TECHNICAL', 'TECHNICAL_NOTIFICATIONS', 'Notification issues', NULL, 4)
     ) AS s(parent, code, name, priority, sort_order)
         JOIN support_categories p ON p.code = s.parent;

-- ---------------------------------------------------------------------------------------- SLA policies & calendar
-- Starting points, to be tuned in Support settings. A business day is the configured working day (9 hours below).
INSERT INTO support_sla_policies (id, name, priority, first_response_minutes, next_response_minutes, resolution_minutes,
                                  business_hours, pause_while_waiting, created_at, updated_at)
VALUES (gen_random_uuid(), 'Critical: around the clock', 'URGENT', 15, 30, 240, FALSE, TRUE, now(), now()),
       (gen_random_uuid(), 'High', 'HIGH', 60, 120, 480, TRUE, TRUE, now(), now()),
       (gen_random_uuid(), 'Medium', 'NORMAL', 240, 480, 1080, TRUE, TRUE, now(), now()),
       (gen_random_uuid(), 'Low', 'LOW', 540, 1080, 2700, TRUE, TRUE, now(), now());

INSERT INTO support_business_hours (day_of_week, opens_at, closes_at)
VALUES (1, '09:00', '18:00'), (2, '09:00', '18:00'), (3, '09:00', '18:00'), (4, '09:00', '18:00'), (5, '09:00', '18:00');

-- ------------------------------------------------------------------------------------------- escalation levels
INSERT INTO support_escalation_levels (level, name, description)
VALUES (1, 'Assigned agent', 'The agent working on the ticket'),
       (2, 'Team lead', 'Support team lead'),
       (3, 'Support manager', 'Support manager'),
       (4, 'Senior management', 'Senior management');

-- ------------------------------------------------------------------------------------------- assignment rules
-- Manual everywhere to begin with (as today); switch a category to round-robin or least-loaded in settings.
INSERT INTO support_assignment_rules (category, strategy, updated_at)
SELECT c, 'MANUAL', now()
FROM unnest(ARRAY['ACCOUNT', 'INVESTMENT', 'PAYMENT', 'EARNINGS', 'WITHDRAWAL', 'KYC', 'REFERRAL', 'CONTAINER', 'TECHNICAL', 'OTHER']) AS c;

-- ----------------------------------------------------------------------------------------------- automation
INSERT INTO support_automation_rules (id, name, trigger, conditions, action, parameters, enabled, created_at, updated_at)
VALUES (gen_random_uuid(), 'Alert the team about critical tickets', 'TICKET_CREATED', '{"priorities": ["URGENT"]}', 'NOTIFY_TEAM', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Assign tickets left unassigned for 30 minutes', 'UNASSIGNED_FOR', '{"minutes": 30}', 'AUTO_ASSIGN', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Warn the agent at 75% of an SLA target', 'SLA_PERCENT', '{"percent": 75}', 'NOTIFY_ASSIGNEE', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Escalate when an SLA target is breached', 'SLA_BREACHED', '{}', 'ESCALATE', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Tell the agent when a customer replies to a resolved ticket', 'CUSTOMER_REPLIED_RESOLVED', '{}', 'NOTIFY_ASSIGNEE', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Ask for feedback when a ticket is resolved', 'TICKET_RESOLVED', '{}', 'REQUEST_FEEDBACK', '{}', TRUE, now(), now()),
       (gen_random_uuid(), 'Record resolution metrics when a ticket closes', 'TICKET_CLOSED', '{}', 'RECORD_RESOLUTION_METRICS', '{}', TRUE, now(), now());

-- -------------------------------------------------------------------------------------- knowledge base categories
INSERT INTO kb_categories (id, code, name, description, sort_order, created_at, updated_at)
VALUES (gen_random_uuid(), 'getting-started', 'Getting started', 'Opening an account and your first investment', 10, now(), now()),
       (gen_random_uuid(), 'account', 'Account management', 'Signing in, your profile and security', 20, now(), now()),
       (gen_random_uuid(), 'investments', 'Investments', 'Plans, agreements and maturity', 30, now(), now()),
       (gen_random_uuid(), 'payments', 'Payments', 'Paying for an order and refunds', 40, now(), now()),
       (gen_random_uuid(), 'earnings', 'Interest and earnings', 'How returns are credited', 50, now(), now()),
       (gen_random_uuid(), 'withdrawals', 'Withdrawals', 'Withdrawing to your bank account', 60, now(), now()),
       (gen_random_uuid(), 'kyc', 'KYC', 'Identity and bank account verification', 70, now(), now()),
       (gen_random_uuid(), 'referrals', 'Referrals', 'Referral links and commission', 80, now(), now()),
       (gen_random_uuid(), 'containers', 'Container leasing', 'Containers and leasing', 90, now(), now()),
       (gen_random_uuid(), 'troubleshooting', 'Troubleshooting', 'Fixing common problems', 100, now(), now());
