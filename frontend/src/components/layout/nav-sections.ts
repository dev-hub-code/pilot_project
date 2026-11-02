import type { MenuSection } from "@/components/layout/account-menu";
import { adminNavSections } from "@/components/layout/admin-nav";
import { hasStaffAccess, isInvestor } from "@/lib/permissions";

/** The site menu for a viewer: an account is either an investor or staff; staff only see their profile and the admin area. */
export function navSections(permissions: readonly string[]): MenuSection[] {
  if (isInvestor(permissions)) {
    return [{ links: [
      { href: "/dashboard", label: "Dashboard", icon: "dashboard" },
      { href: "/invest", label: "Plans", icon: "package" },
      { href: "/portfolio", label: "Portfolio", icon: "chart" },
      { href: "/earnings", label: "Earnings", icon: "coins" },
      { href: "/referrals", label: "Referrals", icon: "network" },
      { href: "/withdrawals", label: "Withdrawals", icon: "wallet" },
      { href: "/support", label: "Support", icon: "life" },
      { href: "/orders", label: "Orders", icon: "file" },
      { href: "/profile", label: "Profile", icon: "user" },
      { href: "/settings", label: "Settings", icon: "settings" },
    ] }];
  }
  return [
    ...(hasStaffAccess(permissions) ? adminNavSections(permissions) : []),
    { label: "Account", links: [
      { href: "/profile", label: "Profile", icon: "user" },
      { href: "/settings", label: "Settings", icon: "settings" },
    ] },
  ];
}
