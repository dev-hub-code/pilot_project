import Link from "next/link";
import { Logo } from "@/components/brand/logo";
import { LogoutButton } from "@/features/auth/logout-button";
import { Permission, hasPermission, hasStaffAccess } from "@/lib/permissions";
import type { Session } from "@/lib/server/auth/session";

export function AppHeader({ session }: { session: Session }) {
  const investor = hasPermission(session.permissions, Permission.INVESTOR_PORTAL);
  const links = [
    { href: "/dashboard", label: "Dashboard" },
    { href: "/marketplace", label: "Marketplace" },
    ...(investor
      ? [
        { href: "/portfolio", label: "Portfolio" },
        { href: "/earnings", label: "Earnings" },
        { href: "/orders", label: "Orders" },
        { href: "/cart", label: "Cart" },
      ]
      : []),
    { href: "/profile", label: "Profile" },
    ...(hasStaffAccess(session.permissions) ? [{ href: "/admin", label: "Admin" }] : []),
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
        <Logo href="/dashboard" compact />
        <div className="flex items-center gap-8">
          <nav aria-label="Main" className="hidden md:block">
            <ul className="flex items-center gap-6 text-sm">{items}</ul>
          </nav>
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
