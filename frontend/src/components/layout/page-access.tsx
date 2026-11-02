import type { ReactNode } from "react";
import { ADMIN_NAV } from "@/components/layout/admin-nav";
import { EmptyState } from "@/components/ui/empty-state";
import { requireStaff } from "@/lib/server/auth/session";

const notAvailable = <EmptyState title="Not available" description="Your role does not include this page." />;

/** What to show instead of a page the viewer's role does not include, or null. The API enforces this regardless. */
export async function deniedUnless(...permissions: readonly string[]): Promise<ReactNode | null> {
  const session = await requireStaff();
  return permissions.some((p) => session.permissions.includes(p)) ? null : notAvailable;
}

/** The same for a page in the menu, which already says who it is for. */
export async function deniedUnlessInMenu(href: string): Promise<ReactNode | null> {
  const item = ADMIN_NAV.find((entry) => entry.href === href);
  if (!item) throw new Error(`${href} is not in the admin menu`);
  return deniedUnless(...item.requires);
}
