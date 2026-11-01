import { Permission, hasPermission } from "@/lib/permissions";
import type { MenuLink } from "@/components/layout/account-menu";

/** The tabs of Platform config the viewer may open. */
export function configTabs(permissions: readonly string[]): MenuLink[] {
  const can = (permission: string) => hasPermission(permissions, permission);
  return [
    { href: "/admin/platform-config/withdrawals", label: "Withdrawals", show: can(Permission.WITHDRAWAL_PROCESS) },
    { href: "/admin/platform-config/returns", label: "Returns credit",
      show: can(Permission.PAYOUT_PROCESS) || can(Permission.SYSTEM_SETTINGS_MANAGE) || can(Permission.FINANCE_VIEW) },
    { href: "/admin/platform-config/referral-rates", label: "Referral rates",
      show: can(Permission.FINANCE_VIEW) || can(Permission.REFERRAL_CONFIG_MANAGE) },
    { href: "/admin/platform-config/company-accounts", label: "Company accounts",
      show: can(Permission.FINANCE_VIEW) || can(Permission.COMPANY_BANK_ACCOUNT_MANAGE)
        || can(Permission.COMPANY_BANK_ACCOUNT_VIEW) },
  ].filter((item) => item.show).map(({ href, label }) => ({ href, label }));
}

/** The admin area's pages the viewer may open. Navigation mirrors permissions for convenience only; the API enforces them. */
export function adminNavLinks(permissions: readonly string[]): MenuLink[] {
  const can = (permission: string) => hasPermission(permissions, permission);
  return [
    { href: "/admin", label: "Overview", show: true },
    { href: "/admin/users", label: "Users", show: can(Permission.USER_VIEW) },
    { href: "/admin/leads", label: "Leads", show: can(Permission.LEAD_VIEW) },
    { href: "/admin/support", label: "Support",
      show: can(Permission.SUPPORT_TICKET_VIEW) || can(Permission.FINANCE_TICKET_HANDLE) },
    { href: "/admin/verification", label: "Verification",
      show: can(Permission.KYC_REVIEW) || can(Permission.BANK_ACCOUNT_VERIFY) },
    { href: "/admin/products", label: "Investment plans", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/orders", label: "Orders", show: can(Permission.ORDER_VIEW) },
    { href: "/admin/agreement", label: "Investment agreement", show: can(Permission.AGREEMENT_TEMPLATE_MANAGE) },
    { href: "/admin/terms", label: "Terms & conditions", show: can(Permission.TERMS_MANAGE) },
    { href: "/admin/payments", label: "Payments", show: can(Permission.FINANCE_VIEW) || can(Permission.PAYMENT_CONFIRM) || can(Permission.PAYMENT_SEARCH) },
    { href: "/admin/payouts", label: "Returns", show: can(Permission.FINANCE_VIEW) || can(Permission.PAYOUT_PROCESS) },
    { href: "/admin/ledger", label: "Ledger", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/withdrawals", label: "Withdrawals", show: can(Permission.WITHDRAWAL_VIEW) },
    { href: "/admin/finance-tasks", label: "Finance tasks",
      show: can(Permission.WITHDRAWAL_PROCESS) || can(Permission.FINANCE_TASK_PROCESS) },
    { href: "/admin/reports", label: "Reports", show: can(Permission.REPORT_VIEW) || can(Permission.REPORT_GENERATE) },
    { href: "/admin/referrals", label: "Referrals", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/downlines", label: "Downlines", show: can(Permission.REFERRAL_TREE_MANAGE) },
    { href: "/admin/demo-accounts", label: "Demo accounts", show: can(Permission.DEMO_ACCOUNT_MANAGE) },
    { href: "/admin/platform-config", label: "Platform config", show: configTabs(permissions).length > 0 },
    { href: "/admin/roles", label: "Roles", show: can(Permission.ROLE_VIEW) },
    { href: "/admin/security", label: "Security", show: can(Permission.AUDIT_VIEW) },
    { href: "/admin/audit", label: "Audit log", show: can(Permission.AUDIT_VIEW) },
  ].filter((item) => item.show).map(({ href, label }) => ({ href, label }));
}
