# Scenario: Super Admin

`SUPER_ADMIN` — Unrestricted platform administrator

**Given** an employee who holds only this role, signed in with their own account.
**When** they use every admin page and call every admin API endpoint.
**Then** they get exactly what the role's permissions allow, and are refused everything else.

**Result: PASS** — 155 of 155 endpoints allowed, 35 of 35 pages open, 0 problem(s).

## Permissions (54)

`AGREEMENT_TEMPLATE_MANAGE`, `APPROVAL_DECIDE`, `APPROVAL_VIEW`, `AUDIT_VIEW`, `BANK_ACCOUNT_VERIFY`, `COMPANY_BANK_ACCOUNT_MANAGE`, `COMPANY_BANK_ACCOUNT_VIEW`, `DEMO_ACCOUNT_MANAGE`, `DEPARTMENT_MANAGE`, `DEPARTMENT_VIEW`, `FINANCE_ADJUST`, `FINANCE_TASK_PROCESS`, `FINANCE_TICKET_HANDLE`, `FINANCE_VIEW`, `INVESTMENT_APPROVE`, `INVESTMENT_CREATE`, `INVESTMENT_UPDATE`, `INVESTMENT_VIEW`, `INVESTOR_ACCOUNT_CREATE`, `KYC_REVIEW`, `LEAD_ASSIGN`, `LEAD_CREATE`, `LEAD_UPDATE`, `LEAD_VIEW`, `LEDGER_VIEW`, `ORDER_VIEW`, `PAYMENT_CONFIRM`, `PAYMENT_REJECT`, `PAYMENT_SEARCH`, `PAYOUT_PROCESS`, `PAYOUT_VIEW`, `PHONE_NUMBER_VIEW`, `RECORD_BROWSE`, `REFERRAL_CONFIG_MANAGE`, `REFERRAL_TREE_MANAGE`, `REPORT_GENERATE`, `REPORT_VIEW`, `ROLE_MANAGE`, `ROLE_VIEW`, `SECURITY_VIEW`, `STAFF_ACCOUNT_VIEW`, `SUPPORT_TICKET_MANAGE`, `SUPPORT_TICKET_VIEW`, `SYSTEM_SETTINGS_MANAGE`, `TERMS_MANAGE`, `USER_DISABLE`, `USER_ROLE_ASSIGN`, `USER_SUSPEND`, `USER_UPDATE`, `USER_VIEW`, `WITHDRAWAL_APPROVE`, `WITHDRAWAL_PROCESS`, `WITHDRAWAL_REJECT`, `WITHDRAWAL_VIEW`

## Pages

| Page | Should open | In menu | What happened | Result |
|---|---|---|---|---|
| Dashboard (`/admin`) | Yes | Yes | open | PASS |
| Investor Management (`/admin/users`) | Yes | Yes | open | PASS |
| Investment Products (`/admin/products`) | Yes | Yes | open | PASS |
| Orders & Agreements (`/admin/orders`) | Yes | Yes | open | PASS |
| Payments (`/admin/payments`) | Yes | Yes | open | PASS |
| Investor Payouts (`/admin/payouts`) | Yes | Yes | open | PASS |
| Withdrawals (`/admin/withdrawals`) | Yes | Yes | open | PASS |
| Finance Tasks (`/admin/finance-tasks`) | Yes | Yes | open | PASS |
| Financial Ledger (`/admin/ledger`) | Yes | Yes | open | PASS |
| KYC & Bank Verification (`/admin/verification`) | Yes | Yes | open | PASS |
| CRM & Leads (`/admin/leads`) | Yes | Yes | open | PASS |
| Support Tickets (`/admin/support`) | Yes | Yes | open | PASS |
| Referral Management (`/admin/downlines`) | Yes | Yes | open | PASS |
| Referral Commissions (`/admin/referrals`) | Yes | Yes | open | PASS |
| Reports (`/admin/reports`) | Yes | Yes | open | PASS |
| Staff Management (`/admin/staff`) | Yes | Yes | open | PASS |
| Departments (`/admin/departments`) | Yes | Yes | open | PASS |
| Roles (`/admin/roles`) | Yes | Yes | open | PASS |
| Permissions (`/admin/permissions`) | Yes | Yes | open | PASS |
| Approvals (`/admin/approvals`) | Yes | Yes | open | PASS |
| Demo Accounts (`/admin/demo-accounts`) | Yes | Yes | open | PASS |
| Return Configuration (`/admin/platform-config/returns`) | Yes | Yes | open | PASS |
| Withdrawal Configuration (`/admin/platform-config/withdrawals`) | Yes | Yes | open | PASS |
| Referral Configuration (`/admin/platform-config/referral-rates`) | Yes | Yes | open | PASS |
| Company Bank Accounts (`/admin/platform-config/company-accounts`) | Yes | Yes | open | PASS |
| Terms & Conditions (`/admin/terms`) | Yes | Yes | open | PASS |
| Agreement Templates (`/admin/agreement`) | Yes | Yes | open | PASS |
| Security Dashboard (`/admin/security`) | Yes | Yes | open | PASS |
| Audit Logs (`/admin/audit`) | Yes | Yes | open | PASS |
| /admin/roles/new (`/admin/roles/new`) | Yes | n/a | open | PASS |
| /admin/staff/new (`/admin/staff/new`) | Yes | n/a | open | PASS |
| /admin/users/new-investor (`/admin/users/new-investor`) | Yes | n/a | open | PASS |
| /admin/leads/new (`/admin/leads/new`) | Yes | n/a | open | PASS |
| /admin/products/new (`/admin/products/new`) | Yes | n/a | open | PASS |
| /admin/withdrawals/batches (`/admin/withdrawals/batches`) | Yes | n/a | open | PASS |

## What the role can do through the API

### agreement-template (4 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /agreement-template` | AGREEMENT_TEMPLATE_MANAGE | Allowed | 200 | PASS |
| `PUT /agreement-template` | AGREEMENT_TEMPLATE_MANAGE | Allowed | 200 | PASS |
| `POST /agreement-template/preview` | AGREEMENT_TEMPLATE_MANAGE | Allowed | 200 | PASS |
| `GET /agreement-template/sample` | AGREEMENT_TEMPLATE_MANAGE | Allowed | 200 | PASS |

### agreements (2 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /agreements` | ORDER_VIEW | Allowed | 200 | PASS |
| `GET /agreements/{holdingId}/pdf` | ORDER_VIEW | Allowed | 404 | PASS |

### approvals (5 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /approvals` | APPROVAL_VIEW, APPROVAL_DECIDE | Allowed | 200 | PASS |
| `POST /approvals` | FINANCE_ADJUST | Allowed | 201 | PASS |
| `GET /approvals/{id}` | APPROVAL_VIEW, APPROVAL_DECIDE | Allowed | 404 | PASS |
| `POST /approvals/{id}/approve` | APPROVAL_DECIDE | Allowed | 404 | PASS |
| `POST /approvals/{id}/reject` | APPROVAL_DECIDE | Allowed | 404 | PASS |

### audit-logs (3 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /audit-logs` | AUDIT_VIEW, SECURITY_VIEW | Allowed | 200 | PASS |
| `GET /audit-logs/export` | AUDIT_VIEW, SECURITY_VIEW | Allowed | 200 | PASS |
| `GET /audit-logs/{id}` | AUDIT_VIEW, SECURITY_VIEW | Allowed | 404 | PASS |

### bank-accounts (6 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /bank-accounts` | BANK_ACCOUNT_VERIFY | Allowed | 200 | PASS |
| `POST /bank-accounts/{accountId}/assign` | BANK_ACCOUNT_VERIFY | Allowed | 404 | PASS |
| `GET /bank-accounts/{accountId}/proof` | BANK_ACCOUNT_VERIFY | Allowed | 404 | PASS |
| `POST /bank-accounts/{accountId}/reject` | BANK_ACCOUNT_VERIFY | Allowed | 404 | PASS |
| `POST /bank-accounts/{accountId}/unassign` | BANK_ACCOUNT_VERIFY | Allowed | 404 | PASS |
| `POST /bank-accounts/{accountId}/verify` | BANK_ACCOUNT_VERIFY | Allowed | 404 | PASS |

### company-bank-accounts (6 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /company-bank-accounts` | COMPANY_BANK_ACCOUNT_MANAGE, COMPANY_BANK_ACCOUNT_VIEW, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /company-bank-accounts` | COMPANY_BANK_ACCOUNT_MANAGE | Allowed | 409 | PASS |
| `GET /company-bank-accounts/ifsc/{code}` | COMPANY_BANK_ACCOUNT_MANAGE | Allowed | 200 | PASS |
| `PUT /company-bank-accounts/{accountId}` | COMPANY_BANK_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `POST /company-bank-accounts/{accountId}/activate` | COMPANY_BANK_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `POST /company-bank-accounts/{accountId}/deactivate` | COMPANY_BANK_ACCOUNT_MANAGE | Allowed | 404 | PASS |

### dashboard (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /dashboard` | Any signed-in user | Allowed | 200 | PASS |

### demo-accounts (9 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /demo-accounts` | DEMO_ACCOUNT_MANAGE | Allowed | 200 | PASS |
| `POST /demo-accounts` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `DELETE /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `GET /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `PUT /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `POST /demo-accounts/{userId}/members` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `DELETE /demo-accounts/{userId}/members/{memberId}` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `POST /demo-accounts/{userId}/orders` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |
| `DELETE /demo-accounts/{userId}/orders/{orderId}` | DEMO_ACCOUNT_MANAGE | Allowed | 404 | PASS |

### departments (9 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /departments` | DEPARTMENT_VIEW, DEPARTMENT_MANAGE | Allowed | 200 | PASS |
| `POST /departments` | DEPARTMENT_MANAGE | Allowed | 201 | PASS |
| `GET /departments/{id}` | DEPARTMENT_VIEW, DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `PUT /departments/{id}` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `POST /departments/{id}/activate` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `POST /departments/{id}/deactivate` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `POST /departments/{id}/employees` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `DELETE /departments/{id}/employees/{userId}` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |
| `PUT /departments/{id}/head` | DEPARTMENT_MANAGE | Allowed | 404 | PASS |

### finance-tasks (4 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /finance-tasks` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Allowed | 200 | PASS |
| `POST /finance-tasks/{withdrawalId}` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Allowed | 404 | PASS |
| `POST /finance-tasks/{withdrawalId}/release` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Allowed | 404 | PASS |
| `POST /finance-tasks/{withdrawalId}/take` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Allowed | 404 | PASS |

### investment-products (8 of 8 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /investment-products` | INVESTMENT_VIEW | Allowed | 200 | PASS |
| `POST /investment-products` | INVESTMENT_CREATE | Allowed | 201 | PASS |
| `GET /investment-products/{productId}` | INVESTMENT_VIEW | Allowed | 404 | PASS |
| `PUT /investment-products/{productId}` | INVESTMENT_UPDATE | Allowed | 404 | PASS |
| `POST /investment-products/{productId}/cancel` | INVESTMENT_APPROVE | Allowed | 404 | PASS |
| `POST /investment-products/{productId}/close` | INVESTMENT_APPROVE | Allowed | 404 | PASS |
| `POST /investment-products/{productId}/publish` | INVESTMENT_APPROVE | Allowed | 404 | PASS |
| `POST /investment-products/{productId}/reopen` | INVESTMENT_APPROVE | Allowed | 404 | PASS |

### investors (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `POST /investors` | INVESTOR_ACCOUNT_CREATE | Allowed | 201 | PASS |

### kyc (7 of 7 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /kyc` | KYC_REVIEW | Allowed | 200 | PASS |
| `GET /kyc/{submissionId}` | KYC_REVIEW | Allowed | 404 | PASS |
| `POST /kyc/{submissionId}/approve` | KYC_REVIEW | Allowed | 404 | PASS |
| `POST /kyc/{submissionId}/assign` | KYC_REVIEW | Allowed | 404 | PASS |
| `GET /kyc/{submissionId}/documents/{documentId}` | KYC_REVIEW | Allowed | 404 | PASS |
| `POST /kyc/{submissionId}/reject` | KYC_REVIEW | Allowed | 404 | PASS |
| `POST /kyc/{submissionId}/unassign` | KYC_REVIEW | Allowed | 404 | PASS |

### leads (10 of 10 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /leads` | LEAD_VIEW | Allowed | 200 | PASS |
| `POST /leads` | LEAD_CREATE | Allowed | 409 | PASS |
| `GET /leads/assignees` | LEAD_ASSIGN | Allowed | 200 | PASS |
| `GET /leads/pipeline` | LEAD_VIEW | Allowed | 200 | PASS |
| `GET /leads/{leadId}` | LEAD_VIEW | Allowed | 404 | PASS |
| `PUT /leads/{leadId}` | LEAD_UPDATE | Allowed | 404 | PASS |
| `POST /leads/{leadId}/activities` | LEAD_UPDATE | Allowed | 404 | PASS |
| `POST /leads/{leadId}/assign` | LEAD_ASSIGN | Allowed | 404 | PASS |
| `POST /leads/{leadId}/claim` | LEAD_UPDATE | Allowed | 404 | PASS |
| `POST /leads/{leadId}/stage` | LEAD_UPDATE | Allowed | 404 | PASS |

### ledger (5 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /ledger/accounts` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 200 | PASS |
| `GET /ledger/accounts/{accountId}` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 404 | PASS |
| `GET /ledger/accounts/{accountId}/entries` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 404 | PASS |
| `POST /ledger/adjustments` | FINANCE_ADJUST | Allowed | 422 | PASS |
| `GET /ledger/trial-balance` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 200 | PASS |

### orders (5 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /orders` | ORDER_VIEW | Allowed | 200 | PASS |
| `GET /orders/{orderId}` | ORDER_VIEW | Allowed | 404 | PASS |
| `GET /orders/{orderId}/holdings` | ORDER_VIEW | Allowed | 200 | PASS |
| `GET /orders/{orderId}/invoice` | ORDER_VIEW | Allowed | 404 | PASS |
| `GET /orders/{orderId}/payments` | FINANCE_VIEW, ORDER_VIEW | Allowed | 404 | PASS |

### payments (6 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /payments` | FINANCE_VIEW, PAYMENT_CONFIRM, PAYMENT_SEARCH | Allowed | 200 | PASS |
| `POST /payments/{paymentId}/confirm` | PAYMENT_CONFIRM | Allowed | 404 | PASS |
| `POST /payments/{paymentId}/refund` | FINANCE_ADJUST | Allowed | 422 | PASS |
| `POST /payments/{paymentId}/reject` | PAYMENT_CONFIRM, PAYMENT_REJECT | Allowed | 404 | PASS |
| `POST /payments/{paymentId}/release` | PAYMENT_CONFIRM | Allowed | 404 | PASS |
| `POST /payments/{paymentId}/take` | PAYMENT_CONFIRM | Allowed | 404 | PASS |

### payouts (3 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /payouts` | PAYOUT_VIEW, FINANCE_VIEW, PAYOUT_PROCESS | Allowed | 200 | PASS |
| `GET /payouts/due` | PAYOUT_VIEW, FINANCE_VIEW, PAYOUT_PROCESS | Allowed | 200 | PASS |
| `POST /payouts/run` | PAYOUT_PROCESS | Allowed | 200 | PASS |

### permissions (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /permissions` | ROLE_VIEW | Allowed | 200 | PASS |

### referral-earnings (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /referral-earnings` | FINANCE_VIEW | Allowed | 200 | PASS |

### referral-rates (3 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /referral-rates` | REFERRAL_CONFIG_MANAGE, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /referral-rates` | REFERRAL_CONFIG_MANAGE | Allowed | 201 | PASS |
| `POST /referral-rates/{versionId}/cancel` | REFERRAL_CONFIG_MANAGE | Allowed | 404 | PASS |

### reports (4 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /reports/financial-summary` | REPORT_VIEW, REPORT_GENERATE | Allowed | 200 | PASS |
| `GET /reports/invoices/{orderId}` | ORDER_VIEW | Allowed | 404 | PASS |
| `GET /reports/offerings` | REPORT_VIEW, REPORT_GENERATE | Allowed | 200 | PASS |
| `GET /reports/statement` | REPORT_VIEW, REPORT_GENERATE | Allowed | 404 | PASS |

### roles (6 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /roles` | ROLE_VIEW | Allowed | 200 | PASS |
| `POST /roles` | ROLE_MANAGE | Allowed | 201 | PASS |
| `DELETE /roles/{id}` | ROLE_MANAGE | Allowed | 404 | PASS |
| `GET /roles/{id}` | ROLE_VIEW | Allowed | 404 | PASS |
| `PUT /roles/{id}` | ROLE_MANAGE | Allowed | 404 | PASS |
| `GET /roles/{id}/revisions` | ROLE_VIEW | Allowed | 404 | PASS |

### security (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /security/overview` | AUDIT_VIEW, SECURITY_VIEW | Allowed | 200 | PASS |

### settings (4 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /settings/rent-credit` | SYSTEM_SETTINGS_MANAGE, PAYOUT_PROCESS, FINANCE_VIEW | Allowed | 200 | PASS |
| `PUT /settings/rent-credit` | SYSTEM_SETTINGS_MANAGE, PAYOUT_PROCESS | Allowed | 200 | PASS |
| `GET /settings/withdrawals` | SYSTEM_SETTINGS_MANAGE, WITHDRAWAL_VIEW | Allowed | 200 | PASS |
| `PUT /settings/withdrawals` | SYSTEM_SETTINGS_MANAGE, WITHDRAWAL_PROCESS | Allowed | 200 | PASS |

### staff (4 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /staff` | STAFF_ACCOUNT_VIEW | Allowed | 200 | PASS |
| `POST /staff` | USER_ROLE_ASSIGN | Allowed | 201 | PASS |
| `GET /staff/search` | STAFF_ACCOUNT_VIEW | Allowed | 200 | PASS |
| `GET /staff/{userId}` | STAFF_ACCOUNT_VIEW | Allowed | 404 | PASS |

### support (9 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /support/agents` | SUPPORT_TICKET_MANAGE | Allowed | 200 | PASS |
| `GET /support/tickets` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Allowed | 200 | PASS |
| `GET /support/tickets/{ticketId}` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `POST /support/tickets/{ticketId}/assign` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `GET /support/tickets/{ticketId}/attachments/{attachmentId}` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `POST /support/tickets/{ticketId}/messages` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `POST /support/tickets/{ticketId}/priority` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `POST /support/tickets/{ticketId}/status` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |
| `POST /support/tickets/{ticketId}/team` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Allowed | 404 | PASS |

### terms (2 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /terms` | TERMS_MANAGE | Allowed | 200 | PASS |
| `PUT /terms` | TERMS_MANAGE | Allowed | 200 | PASS |

### users (16 of 16 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /users` | USER_VIEW | Allowed | 200 | PASS |
| `GET /users/{userId}` | USER_VIEW | Allowed | 404 | PASS |
| `GET /users/{userId}/bank-accounts` | BANK_ACCOUNT_VERIFY, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /users/{userId}/disable` | USER_DISABLE | Allowed | 404 | PASS |
| `GET /users/{userId}/downline` | USER_VIEW, REFERRAL_TREE_MANAGE | Allowed | 404 | PASS |
| `POST /users/{userId}/enable` | USER_DISABLE | Allowed | 404 | PASS |
| `GET /users/{userId}/kyc` | KYC_REVIEW | Allowed | 200 | PASS |
| `POST /users/{userId}/reactivate` | USER_SUSPEND | Allowed | 404 | PASS |
| `GET /users/{userId}/referral-mode` | WITHDRAWAL_VIEW | Allowed | 200 | PASS |
| `PUT /users/{userId}/referral-mode` | WITHDRAWAL_PROCESS | Allowed | 422 | PASS |
| `GET /users/{userId}/referrals` | USER_VIEW | Allowed | 404 | PASS |
| `POST /users/{userId}/referrer` | REFERRAL_TREE_MANAGE | Allowed | 404 | PASS |
| `GET /users/{userId}/roles` | ROLE_VIEW | Allowed | 200 | PASS |
| `PUT /users/{userId}/roles` | USER_ROLE_ASSIGN | Allowed | 404 | PASS |
| `POST /users/{userId}/suspend` | USER_SUSPEND | Allowed | 404 | PASS |
| `POST /users/{userId}/temporary-password` | USER_ROLE_ASSIGN | Allowed | 422 | PASS |

### withdrawal-batches (5 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawal-batches` | WITHDRAWAL_VIEW | Allowed | 200 | PASS |
| `GET /withdrawal-batches/{batchId}` | WITHDRAWAL_VIEW | Allowed | 404 | PASS |
| `GET /withdrawal-batches/{batchId}/file` | WITHDRAWAL_PROCESS | Allowed | 404 | PASS |
| `GET /withdrawal-batches/{batchId}/files` | WITHDRAWAL_VIEW | Allowed | 404 | PASS |
| `GET /withdrawal-batches/{batchId}/files/{fileId}` | WITHDRAWAL_PROCESS | Allowed | 404 | PASS |

### withdrawal-runs (2 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawal-runs` | WITHDRAWAL_VIEW | Allowed | 200 | PASS |
| `POST /withdrawal-runs` | WITHDRAWAL_PROCESS | Allowed | 201 | PASS |

### withdrawals (3 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawals` | WITHDRAWAL_VIEW | Allowed | 200 | PASS |
| `GET /withdrawals/{withdrawalId}` | WITHDRAWAL_VIEW | Allowed | 404 | PASS |
| `POST /withdrawals/{withdrawalId}/resolve` | WITHDRAWAL_PROCESS | Allowed | 404 | PASS |
