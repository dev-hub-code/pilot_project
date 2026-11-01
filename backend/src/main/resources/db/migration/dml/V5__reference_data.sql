-- The data the platform needs to run: roles, permissions, settings and the first wording of its documents.

-- -------------------- Roles and permissions

INSERT INTO roles (id, name, description, system, version, created_at, updated_at)
VALUES ('2a79082a-939e-4119-99f5-b9a04500fc39', 'ADMIN', 'Platform administrator', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('37f606ee-e1dd-4447-ba57-2ea7473bfd1a', 'ADMINISTRATION', 'Back-office administration', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('2d6013d6-ef8b-49c3-8bee-4dd3a5e4c397', 'FINANCE', 'Finance operations', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('9b28e2c6-1994-4c67-a4f9-655dee04a877', 'INVESTOR', 'Registered investor', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('ada8299f-8100-4f69-9e99-cef3f5a5b5b1', 'REPORT_VIEWER', 'Read-only reporting access', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('8a82422d-62de-460d-a23c-14a810032bde', 'SALES', 'Sales team member', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('57891f67-d48c-41a1-a1f4-d75eac87e0d4', 'SUPER_ADMIN', 'Unrestricted platform administrator', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('409856b8-98ac-4832-a794-29d3b1e38d8b', 'SUPPORT', 'Customer support agent', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00'),
       ('7b73f416-c558-424b-940a-e21467b9147e', 'VERIFICATION', 'Verifies investors'' identity (KYC) and bank accounts', TRUE, 0, '2026-11-01T19:50:39.165074+00:00', '2026-11-01T19:50:39.165074+00:00');

INSERT INTO permissions (id, code, category, description)
VALUES ('501642f7-2c0a-4a7a-b260-fdcc98fe47b1', 'ROLE_MANAGE', 'ACCESS', 'Create, update and delete roles'),
       ('033ba111-6aff-40e1-8629-9c847825b9b9', 'ROLE_VIEW', 'ACCESS', 'View roles and permissions'),
       ('0ca11f7c-92e3-4378-9d3d-586ac0d12748', 'TERMS_MANAGE', 'ACCESS', 'Write the terms and conditions people accept when they sign up'),
       ('f853a471-a4d6-488a-bf73-6131d06d4acd', 'USER_ROLE_ASSIGN', 'ACCESS', 'Assign roles to users'),
       ('cb184bda-1471-458f-9fbe-c81ce4de92cd', 'BANK_ACCOUNT_VERIFY', 'FINANCE', 'Verify or reject investor bank accounts'),
       ('b3c5914a-bd9d-4ab0-8876-b28ceacea646', 'COMPANY_BANK_ACCOUNT_MANAGE', 'FINANCE', 'Manage the company bank accounts investors pay into'),
       ('ad9059b4-b2da-4989-8ff9-4971079e0018', 'COMPANY_BANK_ACCOUNT_VIEW', 'FINANCE', 'View the company bank accounts investors pay into'),
       ('d61392db-2f98-4d74-820c-178db00b08e3', 'FINANCE_ADJUST', 'FINANCE', 'Post financial adjustments'),
       ('a5078564-0a09-4dcb-b197-16f1ad97495e', 'FINANCE_TASK_PROCESS', 'FINANCE', 'Pay finance tasks (withdrawals made by runs) and mark them paid or failed'),
       ('f040b815-fa2b-4ccc-b297-844894334232', 'FINANCE_VIEW', 'FINANCE', 'View payments, earnings and the ledger'),
       ('1da36c83-032b-4ce9-a9e5-b5764bf3a241', 'PAYMENT_CONFIRM', 'FINANCE', 'Confirm received bank transfers'),
       ('4496f1f2-70e9-4f9d-bf9a-ebcc86a67b30', 'PAYMENT_SEARCH', 'FINANCE', 'Find payments by an exact search (order number, payment reference or investor), without browsing them'),
       ('f59b56af-c874-4b1e-b191-8a8093aff249', 'PAYOUT_PROCESS', 'FINANCE', 'Pay monthly investor payouts that have fallen due'),
       ('b8479517-0686-4a3f-9ea8-a6dc50a1d2df', 'REFERRAL_CONFIG_MANAGE', 'FINANCE', 'Change referral percentage configuration'),
       ('dd291f08-ed1a-4093-b87c-6c08cb687984', 'REFERRAL_TREE_MANAGE', 'FINANCE', 'View any investor''s downline and move members to another referrer'),
       ('d4a26b13-5654-45cc-9e27-dcbcbe5fc611', 'WITHDRAWAL_APPROVE', 'FINANCE', 'Approve withdrawals'),
       ('a87c4749-d019-4fc5-b52c-f95ac67d3202', 'WITHDRAWAL_PROCESS', 'FINANCE', 'Batch and process withdrawals'),
       ('465ed019-a0ee-45bc-9c66-23194fffda2b', 'WITHDRAWAL_REJECT', 'FINANCE', 'Reject withdrawals'),
       ('15138938-c204-4adc-a3a1-c81ce0359525', 'WITHDRAWAL_VIEW', 'FINANCE', 'View withdrawals and batches'),
       ('63d938d3-a0ad-4b49-9300-a3ff4206bc87', 'AGREEMENT_TEMPLATE_MANAGE', 'INVESTMENT', 'Write the lease agreement issued to investors when their container is assigned'),
       ('32c8e2b1-3be2-4058-910a-c41569467776', 'INVESTMENT_APPROVE', 'INVESTMENT', 'Approve investments'),
       ('95c5f243-95b1-4458-b8c3-57f29888e056', 'INVESTMENT_CREATE', 'INVESTMENT', 'Create investment products and containers'),
       ('46ce1fd9-b1d4-415c-a280-f34359afc401', 'INVESTMENT_UPDATE', 'INVESTMENT', 'Update investment products and containers'),
       ('29da7bb0-7323-4bab-8b9f-473c80765240', 'INVESTMENT_VIEW', 'INVESTMENT', 'View investments and products'),
       ('40b760fe-73f7-4d5f-9b55-8b5c44a0de72', 'ORDER_VIEW', 'INVESTMENT', 'View orders and invoices'),
       ('fb489349-374c-4fbe-a01e-5134b3a7fcc0', 'RECORD_BROWSE', 'INVESTMENT', 'Browse all orders; without it, only an exact search (order number or investor) finds them'),
       ('1399b37c-bade-4d5e-9b49-dbfb33def9b7', 'INVESTOR_PORTAL', 'INVESTOR', 'Use the investor portal: invest, earn, refer, withdraw'),
       ('9446aa04-cb8e-492c-9ef4-774171d2ec14', 'REPORT_GENERATE', 'REPORTING', 'Generate and export reports'),
       ('5a02bfbf-131e-473d-90b9-3c5af820d003', 'REPORT_VIEW', 'REPORTING', 'View reports'),
       ('dcbbcfe8-15b3-4912-8890-e0941d91bd1e', 'LEAD_ASSIGN', 'SALES', 'Assign leads to sales users'),
       ('7b3d409e-9674-457e-913b-b8f201bc9941', 'LEAD_CREATE', 'SALES', 'Create leads'),
       ('b73b52c0-aad3-4ea6-b959-d17c7c7005cd', 'LEAD_UPDATE', 'SALES', 'Update leads and log activities'),
       ('c6a00177-1ab6-4040-afa8-d9a34800ed9e', 'LEAD_VIEW', 'SALES', 'View leads'),
       ('c9937cc7-4cb3-4176-9c48-9f097c32c661', 'FINANCE_TICKET_HANDLE', 'SUPPORT', 'Handle the support tickets handed to the finance team'),
       ('0d3de9d9-481a-4a94-bf2d-b9d54de290d0', 'SUPPORT_TICKET_MANAGE', 'SUPPORT', 'Respond to, assign and close support tickets'),
       ('0777a6ee-d83c-4dbe-96c9-eb1d7d91bed4', 'SUPPORT_TICKET_VIEW', 'SUPPORT', 'View support tickets'),
       ('b8cb07d0-01dd-4b8e-884a-0e566a7d2f94', 'AUDIT_VIEW', 'SYSTEM', 'View audit logs'),
       ('0e4caa21-7277-49a6-aeba-125162fc3395', 'SYSTEM_SETTINGS_MANAGE', 'SYSTEM', 'Change system settings'),
       ('90246bf1-36b2-41e2-9910-44517201c4ba', 'DEMO_ACCOUNT_MANAGE', 'USER', 'Mark investors as demo accounts and give them dummy orders, downline and payouts'),
       ('3b0728cb-13b4-491d-be43-dd4c2a072be8', 'INVESTOR_ACCOUNT_CREATE', 'USER', 'Create network investor accounts'),
       ('27d6656d-71b9-4fc4-a6c3-acdd2ee44c10', 'KYC_REVIEW', 'USER', 'Review and decide KYC submissions'),
       ('a3e3d2c7-324e-4de2-922c-f7d548edb5d3', 'PHONE_NUMBER_VIEW', 'USER', 'See users'' full mobile numbers; others see only the last four digits'),
       ('9ebcb169-64aa-4477-b4c4-6e1bca431b07', 'STAFF_ACCOUNT_VIEW', 'USER', 'See employees'' accounts in the user directory, not only investors'),
       ('1f781a2c-dfa3-448b-8b6b-a011096be055', 'USER_DISABLE', 'USER', 'Disable user accounts, and enable disabled ones'),
       ('6a452413-45e1-4fcf-af01-06658531a3c6', 'USER_SUSPEND', 'USER', 'Suspend or reactivate user accounts'),
       ('41d8cac4-7964-4700-b175-df70fa2a948e', 'USER_UPDATE', 'USER', 'Update user accounts'),
       ('c4215375-35b6-4940-acf5-5a36e71bc9a8', 'USER_VIEW', 'USER', 'View user accounts');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES ('ADMIN', 'AGREEMENT_TEMPLATE_MANAGE'),
             ('ADMIN', 'AUDIT_VIEW'),
             ('ADMIN', 'BANK_ACCOUNT_VERIFY'),
             ('ADMIN', 'COMPANY_BANK_ACCOUNT_MANAGE'),
             ('ADMIN', 'COMPANY_BANK_ACCOUNT_VIEW'),
             ('ADMIN', 'FINANCE_TASK_PROCESS'),
             ('ADMIN', 'FINANCE_VIEW'),
             ('ADMIN', 'INVESTMENT_APPROVE'),
             ('ADMIN', 'INVESTMENT_CREATE'),
             ('ADMIN', 'INVESTMENT_UPDATE'),
             ('ADMIN', 'INVESTMENT_VIEW'),
             ('ADMIN', 'INVESTOR_ACCOUNT_CREATE'),
             ('ADMIN', 'KYC_REVIEW'),
             ('ADMIN', 'LEAD_ASSIGN'),
             ('ADMIN', 'LEAD_CREATE'),
             ('ADMIN', 'LEAD_UPDATE'),
             ('ADMIN', 'LEAD_VIEW'),
             ('ADMIN', 'ORDER_VIEW'),
             ('ADMIN', 'PAYMENT_CONFIRM'),
             ('ADMIN', 'PAYMENT_SEARCH'),
             ('ADMIN', 'PHONE_NUMBER_VIEW'),
             ('ADMIN', 'RECORD_BROWSE'),
             ('ADMIN', 'REPORT_GENERATE'),
             ('ADMIN', 'REPORT_VIEW'),
             ('ADMIN', 'ROLE_MANAGE'),
             ('ADMIN', 'ROLE_VIEW'),
             ('ADMIN', 'STAFF_ACCOUNT_VIEW'),
             ('ADMIN', 'SUPPORT_TICKET_MANAGE'),
             ('ADMIN', 'SUPPORT_TICKET_VIEW'),
             ('ADMIN', 'USER_ROLE_ASSIGN'),
             ('ADMIN', 'USER_SUSPEND'),
             ('ADMIN', 'USER_UPDATE'),
             ('ADMIN', 'USER_VIEW'),
             ('ADMIN', 'WITHDRAWAL_APPROVE'),
             ('ADMIN', 'WITHDRAWAL_REJECT'),
             ('ADMIN', 'WITHDRAWAL_VIEW'),
             ('ADMINISTRATION', 'INVESTMENT_CREATE'),
             ('ADMINISTRATION', 'INVESTMENT_UPDATE'),
             ('ADMINISTRATION', 'INVESTMENT_VIEW'),
             ('ADMINISTRATION', 'KYC_REVIEW'),
             ('ADMINISTRATION', 'ORDER_VIEW'),
             ('ADMINISTRATION', 'RECORD_BROWSE'),
             ('ADMINISTRATION', 'USER_UPDATE'),
             ('ADMINISTRATION', 'USER_VIEW'),
             ('FINANCE', 'BANK_ACCOUNT_VERIFY'),
             ('FINANCE', 'COMPANY_BANK_ACCOUNT_VIEW'),
             ('FINANCE', 'FINANCE_TASK_PROCESS'),
             ('FINANCE', 'FINANCE_TICKET_HANDLE'),
             ('FINANCE', 'PAYMENT_CONFIRM'),
             ('FINANCE', 'USER_VIEW'),
             ('INVESTOR', 'INVESTOR_PORTAL'),
             ('REPORT_VIEWER', 'REPORT_GENERATE'),
             ('REPORT_VIEWER', 'REPORT_VIEW'),
             ('SALES', 'INVESTMENT_VIEW'),
             ('SALES', 'LEAD_CREATE'),
             ('SALES', 'LEAD_UPDATE'),
             ('SALES', 'LEAD_VIEW'),
             ('SUPER_ADMIN', 'AGREEMENT_TEMPLATE_MANAGE'),
             ('SUPER_ADMIN', 'AUDIT_VIEW'),
             ('SUPER_ADMIN', 'BANK_ACCOUNT_VERIFY'),
             ('SUPER_ADMIN', 'COMPANY_BANK_ACCOUNT_MANAGE'),
             ('SUPER_ADMIN', 'COMPANY_BANK_ACCOUNT_VIEW'),
             ('SUPER_ADMIN', 'DEMO_ACCOUNT_MANAGE'),
             ('SUPER_ADMIN', 'FINANCE_ADJUST'),
             ('SUPER_ADMIN', 'FINANCE_TASK_PROCESS'),
             ('SUPER_ADMIN', 'FINANCE_TICKET_HANDLE'),
             ('SUPER_ADMIN', 'FINANCE_VIEW'),
             ('SUPER_ADMIN', 'INVESTMENT_APPROVE'),
             ('SUPER_ADMIN', 'INVESTMENT_CREATE'),
             ('SUPER_ADMIN', 'INVESTMENT_UPDATE'),
             ('SUPER_ADMIN', 'INVESTMENT_VIEW'),
             ('SUPER_ADMIN', 'INVESTOR_ACCOUNT_CREATE'),
             ('SUPER_ADMIN', 'KYC_REVIEW'),
             ('SUPER_ADMIN', 'LEAD_ASSIGN'),
             ('SUPER_ADMIN', 'LEAD_CREATE'),
             ('SUPER_ADMIN', 'LEAD_UPDATE'),
             ('SUPER_ADMIN', 'LEAD_VIEW'),
             ('SUPER_ADMIN', 'ORDER_VIEW'),
             ('SUPER_ADMIN', 'PAYMENT_CONFIRM'),
             ('SUPER_ADMIN', 'PAYMENT_SEARCH'),
             ('SUPER_ADMIN', 'PAYOUT_PROCESS'),
             ('SUPER_ADMIN', 'PHONE_NUMBER_VIEW'),
             ('SUPER_ADMIN', 'RECORD_BROWSE'),
             ('SUPER_ADMIN', 'REFERRAL_CONFIG_MANAGE'),
             ('SUPER_ADMIN', 'REFERRAL_TREE_MANAGE'),
             ('SUPER_ADMIN', 'REPORT_GENERATE'),
             ('SUPER_ADMIN', 'REPORT_VIEW'),
             ('SUPER_ADMIN', 'ROLE_MANAGE'),
             ('SUPER_ADMIN', 'ROLE_VIEW'),
             ('SUPER_ADMIN', 'STAFF_ACCOUNT_VIEW'),
             ('SUPER_ADMIN', 'SUPPORT_TICKET_MANAGE'),
             ('SUPER_ADMIN', 'SUPPORT_TICKET_VIEW'),
             ('SUPER_ADMIN', 'SYSTEM_SETTINGS_MANAGE'),
             ('SUPER_ADMIN', 'TERMS_MANAGE'),
             ('SUPER_ADMIN', 'USER_DISABLE'),
             ('SUPER_ADMIN', 'USER_ROLE_ASSIGN'),
             ('SUPER_ADMIN', 'USER_SUSPEND'),
             ('SUPER_ADMIN', 'USER_UPDATE'),
             ('SUPER_ADMIN', 'USER_VIEW'),
             ('SUPER_ADMIN', 'WITHDRAWAL_APPROVE'),
             ('SUPER_ADMIN', 'WITHDRAWAL_PROCESS'),
             ('SUPER_ADMIN', 'WITHDRAWAL_REJECT'),
             ('SUPER_ADMIN', 'WITHDRAWAL_VIEW'),
             ('SUPPORT', 'INVESTMENT_VIEW'),
             ('SUPPORT', 'ORDER_VIEW'),
             ('SUPPORT', 'PAYMENT_SEARCH'),
             ('SUPPORT', 'SUPPORT_TICKET_MANAGE'),
             ('SUPPORT', 'SUPPORT_TICKET_VIEW'),
             ('SUPPORT', 'USER_VIEW'),
             ('VERIFICATION', 'BANK_ACCOUNT_VERIFY'),
             ('VERIFICATION', 'KYC_REVIEW'),
             ('VERIFICATION', 'USER_VIEW')) AS granted (role_name, code)
         JOIN roles r ON r.name = granted.role_name
         JOIN permissions p ON p.code = granted.code;

-- -------------------- Settings

INSERT INTO rent_credit_settings (id, credit_interval_minutes, updated_by, updated_at)
VALUES (1, NULL, NULL, NULL);

INSERT INTO withdrawal_settings (id, minimum_amount, updated_by, updated_at)
VALUES (1, NULL, NULL, NULL);

INSERT INTO referral_rate_versions (id, effective_from, level1_percent, level2_percent, level3_percent, level4_percent, reason, cancelled_at, cancelled_by, created_by, created_at, one_time_percent)
VALUES ('82193a3d-c63d-4b3e-b154-c113ed21d069', '2026-01-01T00:00:00+00:00', 2.0, 1.0, 0.5, 0.25, 'Initial referral rates', NULL, NULL, NULL, '2026-11-01T19:50:39.165074+00:00', 2.0);

-- -------------------- Agreement and terms

INSERT INTO agreement_templates (id, title, body, version, created_by, created_at)
VALUES ('5eaf11fb-1f0b-4442-a971-e8e2bc6faddd', 'Investment Agreement', $body$This Investment Agreement ("Agreement") is made on {{agreementDate}} between {{companyName}}, {{companyAddress}} (the "Company"), and {{investorName}} (investor ID {{investorCode}}), {{investorAddress}} (the "Investor").

# 1. The investment
The Investor has invested {{investmentAmount}} in the plan {{planTitle}} ({{planCode}}) under order {{orderNumber}}.

# 2. Use of the investment
The Company uses investments in its business of owning and leasing shipping containers, which it manages, insures and maintains at its own cost, and pays returns from that business.

# 3. Term
This Agreement runs for {{tenureMonths}} months, from {{startDate}} to {{endDate}}.

# 4. Returns
The Company pays the Investor returns of {{annualReturnsPercent}} of the investment a year, paid monthly: {{monthlyReturns}} a month and {{totalReturns}} over the term. Returns are credited to the Investor's SeaLease wallet from the start of the term and may be withdrawn as the Investor's account allows.

# 5. End of the term
The investment amount is not repaid under this Agreement.

# 6. Acceptance
This Agreement takes effect when the Investor accepts it in their SeaLease account. The term, and the returns, start at the first full hour after acceptance; no returns are due for any time before it.

# 7. General
This Agreement is governed by the laws of India. It was generated electronically by SeaLease and is valid without a signature once accepted.

Agreement {{agreementNumber}}$body$, 1, NULL, '2026-11-01T19:50:40.40233+00:00');

INSERT INTO terms_versions (id, version, title, body, created_by, created_at)
VALUES ('1304ccf9-7bb0-4a39-9aed-5dc2b858eb94', 1, 'Terms and Conditions', $body$# About these terms

These terms apply to your use of the SeaLease website and your SeaLease account. By creating an account you agree to them.

# Your account

Give accurate details when you sign up and keep them up to date. Keep your password to yourself: you are responsible for what is done with your account.

# Buying containers

Each container you buy is leased out for the tenure you choose and is covered by its own lease agreement, which you accept before your container is assigned. Rent is credited to your wallet as that agreement sets out.

# Risks

Rent depends on SeaLease leasing out its containers. Containers can be damaged, lost or off-hire, and the shipping market changes. Past rent does not promise future rent.

# Referrals

Referral commission is paid as the referral rates in force set out. SeaLease may change those rates for the future.

# Changes to these terms

SeaLease may change these terms. The version in force is always on this page.$body$, NULL, '2026-11-01T19:50:39.989836+00:00');
