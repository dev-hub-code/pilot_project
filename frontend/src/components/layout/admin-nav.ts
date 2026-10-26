import { Permission, hasPermission } from "@/lib/permissions";
import type { MenuLink } from "@/components/layout/account-menu";

/** The admin area's pages the viewer may open. Navigation mirrors permissions for convenience only; the API enforces them. */
export function adminNavLinks(permissions: readonly string[]): MenuLink[] {
  const can = (permission: string) => hasPermission(permissions, permission);
  return [
    { href: "/admin", label: "Overview", show: true },
    { href: "/admin/users", label: "Users", show: can(Permission.USER_VIEW) },
    { href: "/admin/leads", label: "Leads", show: can(Permission.LEAD_VIEW) },
    { href: "/admin/support", label: "Support",
      show: can(Permission.SUPPORT_TICKET_VIEW) || can(Permission.FINANCE_TICKET_HANDLE) },
    { href: "/admin/kyc", label: "KYC reviews", show: can(Permission.KYC_REVIEW) },
    { href: "/admin/bank-accounts", label: "Bank accounts", show: can(Permission.BANK_ACCOUNT_VERIFY) },
    { href: "/admin/container-assignments", label: "Assign containers", show: can(Permission.CONTAINER_ASSIGN) },
    { href: "/admin/products", label: "Containers", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/orders", label: "Orders", show: can(Permission.ORDER_VIEW) },
    { href: "/admin/agreement-reviews", label: "Agreement reviews", show: can(Permission.AGREEMENT_REVIEW) },
    { href: "/admin/agreement", label: "Lease agreement", show: can(Permission.AGREEMENT_TEMPLATE_MANAGE) },
    { href: "/admin/payments", label: "Payments", show: can(Permission.FINANCE_VIEW) || can(Permission.PAYMENT_CONFIRM) || can(Permission.PAYMENT_SEARCH) },
    { href: "/admin/company-bank-accounts", label: "Company accounts",
      show: can(Permission.FINANCE_VIEW) || can(Permission.COMPANY_BANK_ACCOUNT_MANAGE)
        || can(Permission.COMPANY_BANK_ACCOUNT_VIEW) },
    { href: "/admin/payouts", label: "Rent", show: can(Permission.FINANCE_VIEW) || can(Permission.PAYOUT_PROCESS) },
    { href: "/admin/ledger", label: "Ledger", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/withdrawals", label: "Withdrawals", show: can(Permission.WITHDRAWAL_VIEW) },
    { href: "/admin/withdrawal-runs", label: "Generate withdrawals", show: can(Permission.WITHDRAWAL_PROCESS) },
    { href: "/admin/finance-tasks", label: "Finance tasks",
      show: can(Permission.WITHDRAWAL_PROCESS) || can(Permission.FINANCE_TASK_PROCESS) },
    { href: "/admin/reports", label: "Reports", show: can(Permission.REPORT_VIEW) || can(Permission.REPORT_GENERATE) },
    { href: "/admin/referrals", label: "Referrals",
      show: can(Permission.FINANCE_VIEW) || can(Permission.REFERRAL_CONFIG_MANAGE) },
    { href: "/admin/roles", label: "Roles", show: can(Permission.ROLE_VIEW) },
    { href: "/admin/security", label: "Security", show: can(Permission.AUDIT_VIEW) },
    { href: "/admin/audit", label: "Audit log", show: can(Permission.AUDIT_VIEW) },
  ].filter((item) => item.show).map(({ href, label }) => ({ href, label }));
}
