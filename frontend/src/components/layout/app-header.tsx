import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { LogoutButton } from "@/features/auth/logout-button";
import { hasStaffAccess, isInvestor } from "@/lib/permissions";
import { authFetch, type Session } from "@/lib/server/auth/session";

export async function AppHeader({ session }: { session: Session }) {
  // The badge is a convenience: a failure here must never break the page.
  const unread = await authFetch<{ unread: number }>("/api/v1/notifications/unread-count").then((r) => r.unread, () => 0);
  // An account is either an investor or staff; staff only see their profile and the admin area.
  const investor = isInvestor(session.permissions);
  const links = investor
    ? [
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
    ]
    : [
      ...(hasStaffAccess(session.permissions) ? [{ href: "/admin", label: "Admin" }] : []),
      { href: "/profile", label: "Profile" },
    ];
  const items = links.map((link) => (
    <li key={link.href}>
      <Link href={link.href} className="whitespace-nowrap text-muted transition-colors hover:text-foreground">
        {link.label}
      </Link>
    </li>
  ));

  return (
    <header className="border-b border-border bg-surface print:hidden">
      <div className="mx-auto flex h-20 w-full max-w-7xl items-center justify-between gap-4 px-4 sm:px-6">
        <Logo href={investor ? "/dashboard" : "/admin"} compact />
        <div className="flex items-center gap-8">
          <nav aria-label="Main" className="hidden md:block">
            <ul className="flex items-center gap-6 text-sm">{items}</ul>
          </nav>
          <Link href="/notifications" className="relative text-muted transition-colors hover:text-foreground"
            aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}>
            <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" className="size-5">
              <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" /><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
            </svg>
            {unread > 0 && (
              <span className="absolute -top-1.5 -right-2 min-w-4 rounded-full bg-gold px-1 text-center text-[10px] leading-4 font-semibold text-ink">
                {unread > 99 ? "99+" : unread}
              </span>
            )}
          </Link>
          <LogoutButton />
        </div>
      </div>
      {/* Narrow screens: links move to their own scrollable row instead of overflowing the header. */}
      <nav aria-label="Main" className="overflow-x-auto border-t border-border md:hidden">
        <ul className="flex gap-6 px-4 py-3 text-sm">{items}</ul>
      </nav>
    </header>
  );
}
