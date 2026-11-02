import { Permission, hasPermission } from "@/lib/permissions";
import type { MenuLink, MenuSection } from "@/components/layout/account-menu";

/** One entry of the admin menu. Navigation mirrors permissions for convenience only; the API enforces them. */
export interface NavItem {
  id: string;
  label: string;
  href: string;
  icon: string;
  category: string;
  /** Holding any one of these shows the item. */
  requires: readonly string[];
}

const P = Permission;

/** The whole admin menu, in display order. Items and groups a viewer may not open are left out. */
export const ADMIN_NAV: readonly NavItem[] = [
  { id: "dashboard", label: "Dashboard", href: "/admin", icon: "dashboard", category: "Overview", requires: [] },

  { id: "investors", label: "Investor Management", href: "/admin/users", icon: "users", category: "Business Operations", requires: [P.USER_VIEW] },
  { id: "products", label: "Investment Products", href: "/admin/products", icon: "package", category: "Business Operations", requires: [P.INVESTMENT_VIEW] },
  { id: "orders", label: "Orders & Agreements", href: "/admin/orders", icon: "file", category: "Business Operations", requires: [P.ORDER_VIEW] },

  { id: "payments", label: "Payments", href: "/admin/payments", icon: "card", category: "Finance", requires: [P.FINANCE_VIEW, P.PAYMENT_CONFIRM, P.PAYMENT_SEARCH] },
  { id: "payouts", label: "Investor Payouts", href: "/admin/payouts", icon: "coins", category: "Finance", requires: [P.FINANCE_VIEW, P.PAYOUT_PROCESS, P.PAYOUT_VIEW] },
  { id: "withdrawals", label: "Withdrawals", href: "/admin/withdrawals", icon: "wallet", category: "Finance", requires: [P.WITHDRAWAL_VIEW] },
  { id: "finance-tasks", label: "Finance Tasks", href: "/admin/finance-tasks", icon: "tasks", category: "Finance", requires: [P.WITHDRAWAL_PROCESS, P.FINANCE_TASK_PROCESS] },
  { id: "ledger", label: "Financial Ledger", href: "/admin/ledger", icon: "book", category: "Finance", requires: [P.FINANCE_VIEW, P.LEDGER_VIEW] },

  { id: "verification", label: "KYC & Bank Verification", href: "/admin/verification", icon: "check", category: "Compliance", requires: [P.KYC_REVIEW, P.BANK_ACCOUNT_VERIFY] },

  { id: "leads", label: "CRM & Leads", href: "/admin/leads", icon: "target", category: "Customer Operations", requires: [P.LEAD_VIEW] },
  { id: "support", label: "Support Tickets", href: "/admin/support", icon: "life", category: "Customer Operations", requires: [P.SUPPORT_TICKET_VIEW, P.FINANCE_TICKET_HANDLE] },
  { id: "announcements", label: "Announcements", href: "/admin/announcements", icon: "megaphone", category: "Customer Operations", requires: [P.ANNOUNCEMENT_MANAGE] },

  { id: "referrals", label: "Referral Management", href: "/admin/downlines", icon: "network", category: "Partner Management", requires: [P.REFERRAL_TREE_MANAGE] },
  { id: "commissions", label: "Referral Commissions", href: "/admin/referrals", icon: "percent", category: "Partner Management", requires: [P.FINANCE_VIEW] },

  { id: "reports", label: "Reports", href: "/admin/reports", icon: "chart", category: "Analytics", requires: [P.REPORT_VIEW, P.REPORT_GENERATE] },

  { id: "staff", label: "Staff Management", href: "/admin/staff", icon: "badge", category: "Administration", requires: [P.STAFF_ACCOUNT_VIEW, P.USER_ROLE_ASSIGN] },
  { id: "departments", label: "Departments", href: "/admin/departments", icon: "building", category: "Administration", requires: [P.DEPARTMENT_VIEW, P.DEPARTMENT_MANAGE] },
  { id: "roles", label: "Roles", href: "/admin/roles", icon: "shield", category: "Administration", requires: [P.ROLE_VIEW] },
  { id: "permissions", label: "Permissions", href: "/admin/permissions", icon: "key", category: "Administration", requires: [P.ROLE_VIEW] },
  { id: "approvals", label: "Approvals", href: "/admin/approvals", icon: "stamp", category: "Administration", requires: [P.APPROVAL_VIEW, P.APPROVAL_DECIDE] },
  { id: "demo", label: "Demo Accounts", href: "/admin/demo-accounts", icon: "user", category: "Administration", requires: [P.DEMO_ACCOUNT_MANAGE] },

  { id: "config-returns", label: "Return Configuration", href: "/admin/platform-config/returns", icon: "settings", category: "Platform Settings",
    requires: [P.PAYOUT_PROCESS, P.SYSTEM_SETTINGS_MANAGE, P.FINANCE_VIEW] },
  { id: "config-withdrawals", label: "Withdrawal Configuration", href: "/admin/platform-config/withdrawals", icon: "settings", category: "Platform Settings", requires: [P.WITHDRAWAL_PROCESS] },
  { id: "config-referrals", label: "Referral Configuration", href: "/admin/platform-config/referral-rates", icon: "settings", category: "Platform Settings",
    requires: [P.FINANCE_VIEW, P.REFERRAL_CONFIG_MANAGE] },
  { id: "bank-accounts", label: "Company Bank Accounts", href: "/admin/platform-config/company-accounts", icon: "bank", category: "Platform Settings",
    requires: [P.FINANCE_VIEW, P.COMPANY_BANK_ACCOUNT_MANAGE, P.COMPANY_BANK_ACCOUNT_VIEW] },
  { id: "terms", label: "Terms & Conditions", href: "/admin/terms", icon: "file", category: "Platform Settings", requires: [P.TERMS_MANAGE] },
  { id: "agreement", label: "Agreement Templates", href: "/admin/agreement", icon: "file", category: "Platform Settings", requires: [P.AGREEMENT_TEMPLATE_MANAGE] },

  { id: "security", label: "Security Dashboard", href: "/admin/security", icon: "shield", category: "Security", requires: [P.AUDIT_VIEW, P.SECURITY_VIEW] },
  { id: "audit", label: "Audit Logs", href: "/admin/audit", icon: "history", category: "Security", requires: [P.AUDIT_VIEW] },
];

const visible = (item: NavItem, permissions: readonly string[]) =>
  item.requires.length === 0 || item.requires.some((p) => hasPermission(permissions, p));

/** The menu for a viewer, grouped by category; empty categories are left out. */
export function adminNavSections(permissions: readonly string[]): MenuSection[] {
  const sections = new Map<string, MenuLink[]>();
  for (const item of ADMIN_NAV) {
    if (!visible(item, permissions)) continue;
    const links = sections.get(item.category) ?? [];
    links.push({ href: item.href, label: item.label, icon: item.icon });
    sections.set(item.category, links);
  }
  return [...sections].map(([label, links]) => ({ label, links }));
}

/** The tabs of Platform config the viewer may open. */
export function configTabs(permissions: readonly string[]): MenuLink[] {
  const can = (permission: string) => hasPermission(permissions, permission);
  return [
    { href: "/admin/platform-config/withdrawals", label: "Withdrawals", show: can(P.WITHDRAWAL_PROCESS) },
    { href: "/admin/platform-config/returns", label: "Returns credit",
      show: can(P.PAYOUT_PROCESS) || can(P.SYSTEM_SETTINGS_MANAGE) || can(P.FINANCE_VIEW) },
    { href: "/admin/platform-config/referral-rates", label: "Referral rates",
      show: can(P.FINANCE_VIEW) || can(P.REFERRAL_CONFIG_MANAGE) },
    { href: "/admin/platform-config/company-accounts", label: "Company accounts",
      show: can(P.FINANCE_VIEW) || can(P.COMPANY_BANK_ACCOUNT_MANAGE) || can(P.COMPANY_BANK_ACCOUNT_VIEW) },
  ].filter((item) => item.show).map(({ href, label }) => ({ href, label }));
}
