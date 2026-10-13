import { Logo } from "@/components/brand/logo";
import { AccountMenu, type MenuSection } from "@/components/layout/account-menu";
import { adminNavLinks } from "@/components/layout/admin-nav";
import { NotificationBell } from "@/components/layout/notification-bell";
import { hasStaffAccess, isInvestor } from "@/lib/permissions";
import { authFetch, type Session } from "@/lib/server/auth/session";
import type { Profile } from "@/types/user";

export async function AppHeader({ session }: { session: Session }) {
  // The badge is a convenience: a failure here must never break the page.
  const [unread, me] = await Promise.all([
    authFetch<{ unread: number }>("/api/v1/notifications/unread-count").then((r) => r.unread, () => 0),
    authFetch<Profile>("/api/v1/users/me").then((p) => p, () => null),
  ]);
  const name = me ? `${me.firstName} ${me.lastName}`.trim() || me.email : "Account";
  // An account is either an investor or staff; staff only see their profile and the admin area.
  const investor = isInvestor(session.permissions);
  const sections: MenuSection[] = investor
    ? [{ links: [
      { href: "/dashboard", label: "Dashboard" },
      { href: "/marketplace", label: "Marketplace" },
      { href: "/portfolio", label: "Portfolio" },
      { href: "/earnings", label: "Earnings" },
      { href: "/referrals", label: "Referrals" },
      { href: "/withdrawals", label: "Withdrawals" },
      { href: "/support", label: "Support" },
      { href: "/orders", label: "Orders" },
      { href: "/cart", label: "Cart" },
      { href: "/profile", label: "Profile" },
      { href: "/settings", label: "Settings" },
    ] }]
    : [
      ...(hasStaffAccess(session.permissions) ? [{ label: "Administration", links: adminNavLinks(session.permissions) }] : []),
      { label: "Account", links: [
        { href: "/profile", label: "Profile" },
        { href: "/settings", label: "Settings" },
      ] },
    ];

  return (
    <header className="border-b border-border bg-surface print:hidden">
      <div className="mx-auto flex h-20 w-full max-w-7xl items-center justify-between gap-4 px-4 sm:px-6">
        <Logo href={investor ? "/dashboard" : "/admin"} compact />
        <div className="flex items-center gap-4">
          <NotificationBell initial={unread} />
          <AccountMenu sections={sections} name={name} email={me?.email ?? null} />
        </div>
      </div>
    </header>
  );
}
