import { Permission, hasPermission } from "@/lib/permissions";
import type { MenuLink, MenuSection } from "@/components/layout/account-menu";

/** One entry of the admin menu. Navigation mirrors permissions for convenience only; the API enforces them. */
export interface NavItem {
  id: string;
  label: string;
  href: string;
  icon: string;
  category: string;
  /** The name in breadcrumbs, when the menu label alone would be unclear there. */
  crumb?: string;
  /** Holding any one of these shows the item. */
  requires: readonly string[];
}

const P = Permission;

/** The whole admin menu, in display order. Items and groups a viewer may not open are left out. */
export const ADMIN_NAV: readonly NavItem[] = [
  { id: "dashboard", label: "Dashboard", href: "/platform", icon: "dashboard", category: "Overview", requires: [] },

  { id: "invite-investor", label: "Invite Investor", href: "/platform/invite-investor", icon: "mail", category: "Business Operations", requires: [] },
  { id: "investors", label: "Investor Management", href: "/platform/users", icon: "users", category: "Business Operations", requires: [P.USER_VIEW] },
  { id: "products", label: "Investment Products", href: "/platform/products", icon: "package", category: "Business Operations", requires: [P.INVESTMENT_VIEW] },
  { id: "orders", label: "Orders & Agreements", href: "/platform/orders", icon: "file", category: "Business Operations", requires: [P.ORDER_VIEW] },

  { id: "payments", label: "Payments", href: "/platform/payments", icon: "card", category: "Finance", requires: [P.FINANCE_VIEW, P.PAYMENT_CONFIRM, P.PAYMENT_SEARCH] },
  { id: "unmatched-receipts", label: "Unmatched Receipts", href: "/platform/payments/unmatched", icon: "inbox", category: "Finance", requires: [P.FINANCE_VIEW, P.PAYMENT_CONFIRM] },
  { id: "payouts", label: "Investor Payouts", href: "/platform/payouts", icon: "coins", category: "Finance", requires: [P.FINANCE_VIEW, P.PAYOUT_PROCESS, P.PAYOUT_VIEW] },
  { id: "withdrawals", label: "Withdrawals", href: "/platform/withdrawals", icon: "wallet", category: "Finance", requires: [P.WITHDRAWAL_VIEW] },
  { id: "finance-tasks", label: "Finance Tasks", href: "/platform/finance-tasks", icon: "tasks", category: "Finance", requires: [P.WITHDRAWAL_PROCESS, P.FINANCE_TASK_PROCESS] },
  { id: "ledger", label: "Financial Ledger", href: "/platform/ledger", icon: "book", category: "Finance", requires: [P.FINANCE_VIEW, P.LEDGER_VIEW] },
  { id: "reconciliation", label: "Reconciliation", href: "/platform/reconciliation", icon: "scale", category: "Finance", requires: [P.FINANCE_VIEW] },

  { id: "verification", label: "KYC & Bank Verification", href: "/platform/verification", icon: "check", category: "Compliance", requires: [P.KYC_REVIEW, P.BANK_ACCOUNT_VERIFY] },

  { id: "leads", label: "CRM & Leads", href: "/platform/leads", icon: "target", category: "Customer Operations", requires: [P.LEAD_VIEW] },
  { id: "support", label: "Support Tickets", href: "/platform/support", icon: "life", category: "Customer Operations", requires: [P.SUPPORT_TICKET_VIEW, P.FINANCE_TICKET_HANDLE] },
  // Reached from the support area too. System settings managers may use both pages as well.
  { id: "knowledge-base", label: "Knowledge Base", href: "/platform/support/knowledge-base", icon: "book", category: "Customer Operations", requires: [P.KNOWLEDGE_BASE_MANAGE, P.SYSTEM_SETTINGS_MANAGE] },
  { id: "support-settings", label: "Support Settings", href: "/platform/support/settings", icon: "settings", category: "Customer Operations", requires: [P.SUPPORT_CONFIG_MANAGE, P.SYSTEM_SETTINGS_MANAGE] },
  { id: "announcements", label: "Announcements", href: "/platform/announcements", icon: "megaphone", category: "Customer Operations", requires: [P.ANNOUNCEMENT_MANAGE] },

  { id: "referrals", label: "Referral Management", href: "/platform/downlines", icon: "network", category: "Partner Management", requires: [P.REFERRAL_TREE_MANAGE] },
  { id: "commissions", label: "Referral Commissions", href: "/platform/referrals", icon: "percent", category: "Partner Management", requires: [P.FINANCE_VIEW] },

  { id: "reports", label: "Reports", href: "/platform/reports", icon: "chart", category: "Analytics", requires: [P.REPORT_VIEW, P.REPORT_GENERATE] },

  { id: "staff", label: "Staff Management", href: "/platform/staff", icon: "badge", category: "Administration", requires: [P.STAFF_ACCOUNT_VIEW, P.USER_ROLE_ASSIGN] },
  { id: "departments", label: "Departments", href: "/platform/departments", icon: "building", category: "Administration", requires: [P.DEPARTMENT_VIEW, P.DEPARTMENT_MANAGE] },
  { id: "roles", label: "Roles", href: "/platform/roles", icon: "shield", category: "Administration", requires: [P.ROLE_VIEW] },
  { id: "permissions", label: "Permissions", href: "/platform/permissions", icon: "key", category: "Administration", requires: [P.ROLE_VIEW] },
  { id: "approvals", label: "Approvals", href: "/platform/approvals", icon: "stamp", category: "Administration", requires: [P.APPROVAL_VIEW, P.APPROVAL_DECIDE] },
  { id: "demo", label: "Demo Accounts", href: "/platform/demo-accounts", icon: "user", category: "Administration", requires: [P.DEMO_ACCOUNT_MANAGE] },

  // The same pages, in the same order, as the tabs of Platform config (configTabs below). The overview shows to
  // anyone who may open any of them.
  { id: "config-overview", label: "Overview", crumb: "Platform Configuration", href: "/platform/platform-config", icon: "dashboard", category: "Platform Settings",
    requires: [P.WITHDRAWAL_PROCESS, P.PAYOUT_PROCESS, P.SYSTEM_SETTINGS_MANAGE, P.FINANCE_VIEW, P.REFERRAL_CONFIG_MANAGE,
      P.COMPANY_BANK_ACCOUNT_MANAGE, P.COMPANY_BANK_ACCOUNT_VIEW, P.TERMS_MANAGE, P.AGREEMENT_TEMPLATE_MANAGE] },
  { id: "config-withdrawals", label: "Withdrawal Configuration", href: "/platform/platform-config/withdrawals", icon: "settings", category: "Platform Settings", requires: [P.WITHDRAWAL_PROCESS] },
  { id: "config-returns", label: "Return Configuration", href: "/platform/platform-config/returns", icon: "settings", category: "Platform Settings",
    requires: [P.PAYOUT_PROCESS, P.SYSTEM_SETTINGS_MANAGE, P.FINANCE_VIEW] },
  { id: "config-referrals", label: "Referral Configuration", href: "/platform/platform-config/referral-rates", icon: "settings", category: "Platform Settings",
    requires: [P.FINANCE_VIEW, P.REFERRAL_CONFIG_MANAGE] },
  { id: "payment-gateway", label: "Payment Gateway", href: "/platform/platform-config/payment-gateway", icon: "card", category: "Platform Settings",
    requires: [P.SYSTEM_SETTINGS_MANAGE, P.FINANCE_VIEW] },
  { id: "bank-accounts", label: "Company Bank Accounts", href: "/platform/platform-config/company-accounts", icon: "bank", category: "Platform Settings",
    requires: [P.FINANCE_VIEW, P.COMPANY_BANK_ACCOUNT_MANAGE, P.COMPANY_BANK_ACCOUNT_VIEW] },
  { id: "terms", label: "Terms & Conditions", href: "/platform/platform-config/terms", icon: "file", category: "Platform Settings", requires: [P.TERMS_MANAGE] },
  { id: "agreement", label: "Agreement Templates", href: "/platform/platform-config/agreement", icon: "file", category: "Platform Settings", requires: [P.AGREEMENT_TEMPLATE_MANAGE] },

  { id: "security", label: "Security Dashboard", href: "/platform/security", icon: "shield", category: "Security", requires: [P.AUDIT_VIEW, P.SECURITY_VIEW] },
  { id: "audit", label: "Audit Logs", href: "/platform/audit", icon: "history", category: "Security", requires: [P.AUDIT_VIEW] },
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
    { href: "/platform/platform-config/withdrawals", label: "Withdrawals", show: can(P.WITHDRAWAL_PROCESS) },
    { href: "/platform/platform-config/returns", label: "Returns credit",
      show: can(P.PAYOUT_PROCESS) || can(P.SYSTEM_SETTINGS_MANAGE) || can(P.FINANCE_VIEW) },
    { href: "/platform/platform-config/referral-rates", label: "Referral rates",
      show: can(P.FINANCE_VIEW) || can(P.REFERRAL_CONFIG_MANAGE) },
    { href: "/platform/platform-config/payment-gateway", label: "Payment gateway",
      show: can(P.SYSTEM_SETTINGS_MANAGE) || can(P.FINANCE_VIEW) },
    { href: "/platform/platform-config/company-accounts", label: "Company accounts",
      show: can(P.FINANCE_VIEW) || can(P.COMPANY_BANK_ACCOUNT_MANAGE) || can(P.COMPANY_BANK_ACCOUNT_VIEW) },
    { href: "/platform/platform-config/terms", label: "Terms", show: can(P.TERMS_MANAGE) },
    { href: "/platform/platform-config/agreement", label: "Agreement", show: can(P.AGREEMENT_TEMPLATE_MANAGE) },
  ].filter((item) => item.show).map(({ href, label }) => ({ href, label }));
}
