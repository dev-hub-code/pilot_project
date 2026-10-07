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
    { href: "/admin/kyc", label: "KYC reviews", show: can(Permission.KYC_REVIEW) },
    { href: "/admin/bank-accounts", label: "Bank accounts", show: can(Permission.BANK_ACCOUNT_VERIFY) },
    { href: "/admin/containers", label: "Containers", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/products", label: "Offerings", show: can(Permission.INVESTMENT_VIEW) },
    { href: "/admin/orders", label: "Orders", show: can(Permission.ORDER_VIEW) },
    { href: "/admin/payments", label: "Payments", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/rentals", label: "Rentals",
      show: can(Permission.FINANCE_VIEW) || can(Permission.RENTAL_RECORD) || can(Permission.RENTAL_APPROVE) },
    { href: "/admin/ledger", label: "Ledger", show: can(Permission.FINANCE_VIEW) },
    { href: "/admin/withdrawals", label: "Withdrawals", show: can(Permission.WITHDRAWAL_VIEW) },
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
