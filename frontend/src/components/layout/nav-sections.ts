import type { MenuSection } from "@/components/layout/account-menu";
import { adminNavSections } from "@/components/layout/admin-nav";
import { hasStaffAccess, isInvestor } from "@/lib/permissions";

/** The site menu for a viewer: an account is either an investor or staff; staff only see their profile and the admin area. */
export function navSections(permissions: readonly string[]): MenuSection[] {
  if (isInvestor(permissions)) {
    return [{ links: [
      { href: "/dashboard", label: "Dashboard" },
      { href: "/invest", label: "Plans" },
      { href: "/portfolio", label: "Portfolio" },
      { href: "/earnings", label: "Earnings" },
      { href: "/referrals", label: "Referrals" },
      { href: "/withdrawals", label: "Withdrawals" },
      { href: "/support", label: "Support" },
      { href: "/orders", label: "Orders" },
      { href: "/profile", label: "Profile" },
      { href: "/settings", label: "Settings" },
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
