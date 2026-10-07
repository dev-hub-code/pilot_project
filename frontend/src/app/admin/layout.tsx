import { AppHeader } from "@/components/layout/app-header";
import { SubNav } from "@/components/layout/sub-nav";
import { Permission, hasPermission } from "@/lib/permissions";
import { requireStaff } from "@/lib/server/auth/session";

export default async function AdminLayout({ children }: LayoutProps<"/admin">) {
  const session = await requireStaff();
  const can = (permission: string) => hasPermission(session.permissions, permission);
  // Navigation mirrors permissions for convenience only; the API enforces them.
  const items = [
    { href: "/admin", label: "Overview", show: true },
    { href: "/admin/users", label: "Users", show: can(Permission.USER_VIEW) },
    { href: "/admin/leads", label: "Leads", show: can(Permission.LEAD_VIEW) },
    { href: "/admin/support", label: "Support", show: can(Permission.SUPPORT_TICKET_VIEW) },
    { href: "/admin/kyc", label: "KYC reviews", show: can(Permission.KYC_REVIEW) },
    { href: "/admin/bank-accounts", label: "Bank accounts", show: can(Permission.BANK_ACCOUNT_VERIFY) },
    { href: "/admin/containers", label: "Containers", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/products", label: "Plans", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/orders", label: "Orders", show: can(Permission.ORDER_VIEW) },
    { href: "/admin/payments", label: "Payments", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/company-bank-accounts", label: "Company accounts",
      show: can(Permission.FINANCE_VIEW) || can(Permission.COMPANY_BANK_ACCOUNT_MANAGE) },
    { href: "/admin/payouts", label: "Payouts", show: can(Permission.FINANCE_VIEW) || can(Permission.PAYOUT_PROCESS) },
    { href: "/admin/ledger", label: "Ledger", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/withdrawals", label: "Withdrawals", show: can(Permission.WITHDRAWAL_VIEW) },
    { href: "/admin/reports", label: "Reports", show: can(Permission.REPORT_VIEW) || can(Permission.REPORT_GENERATE) },
    { href: "/admin/referrals", label: "Referrals",
      show: can(Permission.FINANCE_VIEW) || can(Permission.REFERRAL_CONFIG_MANAGE) },
  ].filter((item) => item.show);

  return (
    <>
      <AppHeader session={session} />
      <main className="mx-auto w-full max-w-7xl flex-1 space-y-8 px-4 py-10 sm:px-6">
        <div className="print:hidden"><SubNav items={items} label="Administration" /></div>
        {children}
      </main>
    </>
  );
}
