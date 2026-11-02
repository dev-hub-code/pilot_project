# Scenario: Referral Executive

`REFERRAL_EXECUTIVE` — Referral executive

**Given** an employee who holds only this role, signed in with their own account.
**When** they use every admin page and call every admin API endpoint.
**Then** they get exactly what the role's permissions allow, and are refused everything else.

**Result: PASS** — 14 of 155 endpoints allowed, 8 of 35 pages open, 0 problem(s).

## Permissions (1)

`FINANCE_VIEW`

## Pages

| Page | Should open | In menu | What happened | Result |
|---|---|---|---|---|
| Dashboard (`/admin`) | Yes | Yes | open | PASS |
| Investor Management (`/admin/users`) | No | No | blocked | PASS |
| Investment Products (`/admin/products`) | No | No | blocked | PASS |
| Orders & Agreements (`/admin/orders`) | No | No | blocked | PASS |
| Payments (`/admin/payments`) | Yes | Yes | open | PASS |
| Investor Payouts (`/admin/payouts`) | Yes | Yes | open | PASS |
| Withdrawals (`/admin/withdrawals`) | No | No | blocked | PASS |
| Finance Tasks (`/admin/finance-tasks`) | No | No | blocked | PASS |
| Financial Ledger (`/admin/ledger`) | Yes | Yes | open | PASS |
| KYC & Bank Verification (`/admin/verification`) | No | No | blocked | PASS |
| CRM & Leads (`/admin/leads`) | No | No | blocked | PASS |
| Support Tickets (`/admin/support`) | No | No | blocked | PASS |
| Referral Management (`/admin/downlines`) | No | No | blocked | PASS |
| Referral Commissions (`/admin/referrals`) | Yes | Yes | open | PASS |
| Reports (`/admin/reports`) | No | No | blocked | PASS |
| Staff Management (`/admin/staff`) | No | No | blocked | PASS |
| Departments (`/admin/departments`) | No | No | blocked | PASS |
| Roles (`/admin/roles`) | No | No | blocked | PASS |
| Permissions (`/admin/permissions`) | No | No | blocked | PASS |
| Approvals (`/admin/approvals`) | No | No | blocked | PASS |
| Demo Accounts (`/admin/demo-accounts`) | No | No | blocked | PASS |
| Return Configuration (`/admin/platform-config/returns`) | Yes | Yes | open | PASS |
| Withdrawal Configuration (`/admin/platform-config/withdrawals`) | No | No | blocked | PASS |
| Referral Configuration (`/admin/platform-config/referral-rates`) | Yes | Yes | open | PASS |
| Company Bank Accounts (`/admin/platform-config/company-accounts`) | Yes | Yes | open | PASS |
| Terms & Conditions (`/admin/terms`) | No | No | blocked | PASS |
| Agreement Templates (`/admin/agreement`) | No | No | blocked | PASS |
| Security Dashboard (`/admin/security`) | No | No | blocked | PASS |
| Audit Logs (`/admin/audit`) | No | No | blocked | PASS |
| /admin/roles/new (`/admin/roles/new`) | No | n/a | blocked | PASS |
| /admin/staff/new (`/admin/staff/new`) | No | n/a | blocked | PASS |
| /admin/users/new-investor (`/admin/users/new-investor`) | No | n/a | blocked | PASS |
| /admin/leads/new (`/admin/leads/new`) | No | n/a | blocked | PASS |
| /admin/products/new (`/admin/products/new`) | No | n/a | blocked | PASS |
| /admin/withdrawals/batches (`/admin/withdrawals/batches`) | No | n/a | blocked | PASS |

## What the role can do through the API

### agreement-template (0 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /agreement-template` | AGREEMENT_TEMPLATE_MANAGE | Refused | 403 | PASS |
| `PUT /agreement-template` | AGREEMENT_TEMPLATE_MANAGE | Refused | 403 | PASS |
| `POST /agreement-template/preview` | AGREEMENT_TEMPLATE_MANAGE | Refused | 403 | PASS |
| `GET /agreement-template/sample` | AGREEMENT_TEMPLATE_MANAGE | Refused | 403 | PASS |

### agreements (0 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /agreements` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /agreements/{holdingId}/pdf` | ORDER_VIEW | Refused | 403 | PASS |

### approvals (0 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /approvals` | APPROVAL_VIEW, APPROVAL_DECIDE | Refused | 403 | PASS |
| `POST /approvals` | FINANCE_ADJUST | Refused | 403 | PASS |
| `GET /approvals/{id}` | APPROVAL_VIEW, APPROVAL_DECIDE | Refused | 403 | PASS |
| `POST /approvals/{id}/approve` | APPROVAL_DECIDE | Refused | 403 | PASS |
| `POST /approvals/{id}/reject` | APPROVAL_DECIDE | Refused | 403 | PASS |

### audit-logs (0 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /audit-logs` | AUDIT_VIEW, SECURITY_VIEW | Refused | 403 | PASS |
| `GET /audit-logs/export` | AUDIT_VIEW, SECURITY_VIEW | Refused | 403 | PASS |
| `GET /audit-logs/{id}` | AUDIT_VIEW, SECURITY_VIEW | Refused | 403 | PASS |

### bank-accounts (0 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /bank-accounts` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |
| `POST /bank-accounts/{accountId}/assign` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |
| `GET /bank-accounts/{accountId}/proof` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |
| `POST /bank-accounts/{accountId}/reject` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |
| `POST /bank-accounts/{accountId}/unassign` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |
| `POST /bank-accounts/{accountId}/verify` | BANK_ACCOUNT_VERIFY | Refused | 403 | PASS |

### company-bank-accounts (1 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /company-bank-accounts` | COMPANY_BANK_ACCOUNT_MANAGE, COMPANY_BANK_ACCOUNT_VIEW, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /company-bank-accounts` | COMPANY_BANK_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `GET /company-bank-accounts/ifsc/{code}` | COMPANY_BANK_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `PUT /company-bank-accounts/{accountId}` | COMPANY_BANK_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `POST /company-bank-accounts/{accountId}/activate` | COMPANY_BANK_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `POST /company-bank-accounts/{accountId}/deactivate` | COMPANY_BANK_ACCOUNT_MANAGE | Refused | 403 | PASS |

### dashboard (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /dashboard` | Any signed-in user | Allowed | 200 | PASS |

### demo-accounts (0 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /demo-accounts` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `POST /demo-accounts` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `DELETE /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `GET /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `PUT /demo-accounts/{userId}` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `POST /demo-accounts/{userId}/members` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `DELETE /demo-accounts/{userId}/members/{memberId}` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `POST /demo-accounts/{userId}/orders` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |
| `DELETE /demo-accounts/{userId}/orders/{orderId}` | DEMO_ACCOUNT_MANAGE | Refused | 403 | PASS |

### departments (0 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /departments` | DEPARTMENT_VIEW, DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `POST /departments` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `GET /departments/{id}` | DEPARTMENT_VIEW, DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `PUT /departments/{id}` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `POST /departments/{id}/activate` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `POST /departments/{id}/deactivate` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `POST /departments/{id}/employees` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `DELETE /departments/{id}/employees/{userId}` | DEPARTMENT_MANAGE | Refused | 403 | PASS |
| `PUT /departments/{id}/head` | DEPARTMENT_MANAGE | Refused | 403 | PASS |

### finance-tasks (0 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /finance-tasks` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Refused | 403 | PASS |
| `POST /finance-tasks/{withdrawalId}` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Refused | 403 | PASS |
| `POST /finance-tasks/{withdrawalId}/release` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Refused | 403 | PASS |
| `POST /finance-tasks/{withdrawalId}/take` | WITHDRAWAL_PROCESS, FINANCE_TASK_PROCESS | Refused | 403 | PASS |

### investment-products (0 of 8 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /investment-products` | INVESTMENT_VIEW | Refused | 403 | PASS |
| `POST /investment-products` | INVESTMENT_CREATE | Refused | 403 | PASS |
| `GET /investment-products/{productId}` | INVESTMENT_VIEW | Refused | 403 | PASS |
| `PUT /investment-products/{productId}` | INVESTMENT_UPDATE | Refused | 403 | PASS |
| `POST /investment-products/{productId}/cancel` | INVESTMENT_APPROVE | Refused | 403 | PASS |
| `POST /investment-products/{productId}/close` | INVESTMENT_APPROVE | Refused | 403 | PASS |
| `POST /investment-products/{productId}/publish` | INVESTMENT_APPROVE | Refused | 403 | PASS |
| `POST /investment-products/{productId}/reopen` | INVESTMENT_APPROVE | Refused | 403 | PASS |

### investors (0 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `POST /investors` | INVESTOR_ACCOUNT_CREATE | Refused | 403 | PASS |

### kyc (0 of 7 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /kyc` | KYC_REVIEW | Refused | 403 | PASS |
| `GET /kyc/{submissionId}` | KYC_REVIEW | Refused | 403 | PASS |
| `POST /kyc/{submissionId}/approve` | KYC_REVIEW | Refused | 403 | PASS |
| `POST /kyc/{submissionId}/assign` | KYC_REVIEW | Refused | 403 | PASS |
| `GET /kyc/{submissionId}/documents/{documentId}` | KYC_REVIEW | Refused | 403 | PASS |
| `POST /kyc/{submissionId}/reject` | KYC_REVIEW | Refused | 403 | PASS |
| `POST /kyc/{submissionId}/unassign` | KYC_REVIEW | Refused | 403 | PASS |

### leads (0 of 10 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /leads` | LEAD_VIEW | Refused | 403 | PASS |
| `POST /leads` | LEAD_CREATE | Refused | 403 | PASS |
| `GET /leads/assignees` | LEAD_ASSIGN | Refused | 403 | PASS |
| `GET /leads/pipeline` | LEAD_VIEW | Refused | 403 | PASS |
| `GET /leads/{leadId}` | LEAD_VIEW | Refused | 403 | PASS |
| `PUT /leads/{leadId}` | LEAD_UPDATE | Refused | 403 | PASS |
| `POST /leads/{leadId}/activities` | LEAD_UPDATE | Refused | 403 | PASS |
| `POST /leads/{leadId}/assign` | LEAD_ASSIGN | Refused | 403 | PASS |
| `POST /leads/{leadId}/claim` | LEAD_UPDATE | Refused | 403 | PASS |
| `POST /leads/{leadId}/stage` | LEAD_UPDATE | Refused | 403 | PASS |

### ledger (4 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /ledger/accounts` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 200 | PASS |
| `GET /ledger/accounts/{accountId}` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 404 | PASS |
| `GET /ledger/accounts/{accountId}/entries` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 404 | PASS |
| `POST /ledger/adjustments` | FINANCE_ADJUST | Refused | 403 | PASS |
| `GET /ledger/trial-balance` | FINANCE_VIEW, LEDGER_VIEW | Allowed | 200 | PASS |

### orders (1 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /orders` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /orders/{orderId}` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /orders/{orderId}/holdings` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /orders/{orderId}/invoice` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /orders/{orderId}/payments` | FINANCE_VIEW, ORDER_VIEW | Allowed | 404 | PASS |

### payments (1 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /payments` | FINANCE_VIEW, PAYMENT_CONFIRM, PAYMENT_SEARCH | Allowed | 200 | PASS |
| `POST /payments/{paymentId}/confirm` | PAYMENT_CONFIRM | Refused | 403 | PASS |
| `POST /payments/{paymentId}/refund` | FINANCE_ADJUST | Refused | 403 | PASS |
| `POST /payments/{paymentId}/reject` | PAYMENT_CONFIRM, PAYMENT_REJECT | Refused | 403 | PASS |
| `POST /payments/{paymentId}/release` | PAYMENT_CONFIRM | Refused | 403 | PASS |
| `POST /payments/{paymentId}/take` | PAYMENT_CONFIRM | Refused | 403 | PASS |

### payouts (2 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /payouts` | PAYOUT_VIEW, FINANCE_VIEW, PAYOUT_PROCESS | Allowed | 200 | PASS |
| `GET /payouts/due` | PAYOUT_VIEW, FINANCE_VIEW, PAYOUT_PROCESS | Allowed | 200 | PASS |
| `POST /payouts/run` | PAYOUT_PROCESS | Refused | 403 | PASS |

### permissions (0 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /permissions` | ROLE_VIEW | Refused | 403 | PASS |

### referral-earnings (1 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /referral-earnings` | FINANCE_VIEW | Allowed | 200 | PASS |

### referral-rates (1 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /referral-rates` | REFERRAL_CONFIG_MANAGE, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /referral-rates` | REFERRAL_CONFIG_MANAGE | Refused | 403 | PASS |
| `POST /referral-rates/{versionId}/cancel` | REFERRAL_CONFIG_MANAGE | Refused | 403 | PASS |

### reports (0 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /reports/financial-summary` | REPORT_VIEW, REPORT_GENERATE | Refused | 403 | PASS |
| `GET /reports/invoices/{orderId}` | ORDER_VIEW | Refused | 403 | PASS |
| `GET /reports/offerings` | REPORT_VIEW, REPORT_GENERATE | Refused | 403 | PASS |
| `GET /reports/statement` | REPORT_VIEW, REPORT_GENERATE | Refused | 403 | PASS |

### roles (0 of 6 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /roles` | ROLE_VIEW | Refused | 403 | PASS |
| `POST /roles` | ROLE_MANAGE | Refused | 403 | PASS |
| `DELETE /roles/{id}` | ROLE_MANAGE | Refused | 403 | PASS |
| `GET /roles/{id}` | ROLE_VIEW | Refused | 403 | PASS |
| `PUT /roles/{id}` | ROLE_MANAGE | Refused | 403 | PASS |
| `GET /roles/{id}/revisions` | ROLE_VIEW | Refused | 403 | PASS |

### security (0 of 1 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /security/overview` | AUDIT_VIEW, SECURITY_VIEW | Refused | 403 | PASS |

### settings (1 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /settings/rent-credit` | SYSTEM_SETTINGS_MANAGE, PAYOUT_PROCESS, FINANCE_VIEW | Allowed | 200 | PASS |
| `PUT /settings/rent-credit` | SYSTEM_SETTINGS_MANAGE, PAYOUT_PROCESS | Refused | 403 | PASS |
| `GET /settings/withdrawals` | SYSTEM_SETTINGS_MANAGE, WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `PUT /settings/withdrawals` | SYSTEM_SETTINGS_MANAGE, WITHDRAWAL_PROCESS | Refused | 403 | PASS |

### staff (0 of 4 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /staff` | STAFF_ACCOUNT_VIEW | Refused | 403 | PASS |
| `POST /staff` | USER_ROLE_ASSIGN | Refused | 403 | PASS |
| `GET /staff/search` | STAFF_ACCOUNT_VIEW | Refused | 403 | PASS |
| `GET /staff/{userId}` | STAFF_ACCOUNT_VIEW | Refused | 403 | PASS |

### support (0 of 9 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /support/agents` | SUPPORT_TICKET_MANAGE | Refused | 403 | PASS |
| `GET /support/tickets` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `GET /support/tickets/{ticketId}` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `POST /support/tickets/{ticketId}/assign` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `GET /support/tickets/{ticketId}/attachments/{attachmentId}` | SUPPORT_TICKET_VIEW, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `POST /support/tickets/{ticketId}/messages` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `POST /support/tickets/{ticketId}/priority` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `POST /support/tickets/{ticketId}/status` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |
| `POST /support/tickets/{ticketId}/team` | SUPPORT_TICKET_MANAGE, FINANCE_TICKET_HANDLE | Refused | 403 | PASS |

### terms (0 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /terms` | TERMS_MANAGE | Refused | 403 | PASS |
| `PUT /terms` | TERMS_MANAGE | Refused | 403 | PASS |

### users (1 of 16 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /users` | USER_VIEW | Refused | 403 | PASS |
| `GET /users/{userId}` | USER_VIEW | Refused | 403 | PASS |
| `GET /users/{userId}/bank-accounts` | BANK_ACCOUNT_VERIFY, FINANCE_VIEW | Allowed | 200 | PASS |
| `POST /users/{userId}/disable` | USER_DISABLE | Refused | 403 | PASS |
| `GET /users/{userId}/downline` | USER_VIEW, REFERRAL_TREE_MANAGE | Refused | 403 | PASS |
| `POST /users/{userId}/enable` | USER_DISABLE | Refused | 403 | PASS |
| `GET /users/{userId}/kyc` | KYC_REVIEW | Refused | 403 | PASS |
| `POST /users/{userId}/reactivate` | USER_SUSPEND | Refused | 403 | PASS |
| `GET /users/{userId}/referral-mode` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `PUT /users/{userId}/referral-mode` | WITHDRAWAL_PROCESS | Refused | 403 | PASS |
| `GET /users/{userId}/referrals` | USER_VIEW | Refused | 403 | PASS |
| `POST /users/{userId}/referrer` | REFERRAL_TREE_MANAGE | Refused | 403 | PASS |
| `GET /users/{userId}/roles` | ROLE_VIEW | Refused | 403 | PASS |
| `PUT /users/{userId}/roles` | USER_ROLE_ASSIGN | Refused | 403 | PASS |
| `POST /users/{userId}/suspend` | USER_SUSPEND | Refused | 403 | PASS |
| `POST /users/{userId}/temporary-password` | USER_ROLE_ASSIGN | Refused | 403 | PASS |

### withdrawal-batches (0 of 5 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawal-batches` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `GET /withdrawal-batches/{batchId}` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `GET /withdrawal-batches/{batchId}/file` | WITHDRAWAL_PROCESS | Refused | 403 | PASS |
| `GET /withdrawal-batches/{batchId}/files` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `GET /withdrawal-batches/{batchId}/files/{fileId}` | WITHDRAWAL_PROCESS | Refused | 403 | PASS |

### withdrawal-runs (0 of 2 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawal-runs` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `POST /withdrawal-runs` | WITHDRAWAL_PROCESS | Refused | 403 | PASS |

### withdrawals (0 of 3 allowed)

| Call | Needs | Should be | HTTP | Result |
|---|---|---|---|---|
| `GET /withdrawals` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `GET /withdrawals/{withdrawalId}` | WITHDRAWAL_VIEW | Refused | 403 | PASS |
| `POST /withdrawals/{withdrawalId}/resolve` | WITHDRAWAL_PROCESS | Refused | 403 | PASS |
