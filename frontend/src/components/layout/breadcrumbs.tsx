"use client";

import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { MenuSection } from "@/components/layout/account-menu";
import { ADMIN_NAV } from "@/components/layout/admin-nav";

/** Pages with no menu entry, by path; "*" is any one segment, such as an id. */
const PAGES: [string, string][] = [
  ["/admin/audit/*", "Audit entry"],
  ["/admin/demo-accounts/*", "Demo account"],
  ["/admin/departments/*", "Department details"],
  ["/admin/kyc/*", "KYC submission"],
  ["/admin/leads/new", "New lead"],
  ["/admin/leads/*", "Lead details"],
  ["/admin/ledger/*", "Ledger account"],
  ["/admin/orders/*/invoice", "Invoice"],
  ["/admin/orders/*", "Order details"],
  ["/admin/platform-config", "Platform Configuration"],
  ["/admin/products/new", "New product"],
  ["/admin/products/*/edit", "Edit"],
  ["/admin/products/*", "Product details"],
  ["/admin/roles/new", "New role"],
  ["/admin/roles/*", "Role details"],
  ["/admin/staff/new", "New employee"],
  ["/admin/staff/*", "Employee details"],
  ["/admin/support/dashboard", "Dashboard"],
  ["/admin/support/escalations", "Escalations"],
  ["/admin/support/feedback", "Feedback"],
  ["/admin/support/new", "New ticket"],
  ["/admin/support/knowledge-base/new", "New article"],
  ["/admin/support/knowledge-base/read/*", "Article"],
  ["/admin/support/knowledge-base/*", "Edit article"],
  ["/admin/support/*", "Ticket"],
  ["/admin/users/new", "New user"],
  ["/admin/users/new-investor", "New investor"],
  ["/admin/users/*", "Investor details"],
  ["/admin/withdrawals/batches", "Withdrawal files"],
  ["/admin/withdrawals/batches/*", "Withdrawal file"],
  ["/admin/withdrawals/*", "Withdrawal details"],
];

/** Paths that only group other pages: their crumb goes to the page people know them by. */
const SAME_AS: Record<string, string> = { "/admin/kyc": "/admin/verification" };

const matches = (pattern: string, path: string) => {
  const want = pattern.split("/");
  const have = path.split("/");
  return want.length === have.length && want.every((part, index) => part === "*" || part === have[index]);
};

/** Where an admin page sits: "Dashboard › Orders & Agreements › Order details › Invoice". */
export function Breadcrumbs({ sections }: { sections: readonly MenuSection[] }) {
  const pathname = usePathname();
  if (pathname === "/admin") return null;
  const allowed = new Set(sections.flatMap((section) => section.links).map((link) => link.href));
  const segments = pathname.split("/").filter(Boolean).slice(1);

  const crumbs = segments.flatMap((_, index) => {
    const path = `/admin/${segments.slice(0, index + 1).join("/")}`;
    const href = SAME_AS[path] ?? path;
    const nav = ADMIN_NAV.find((item) => item.href === href);
    const label = nav?.label ?? PAGES.find(([pattern]) => matches(pattern, href))?.[1];
    if (!label) return [];
    // A menu page this person cannot open is named but not linked.
    return [{ href, label, linked: !nav || allowed.has(href) }];
  });

  return (
    <nav aria-label="Breadcrumb" className="mx-auto w-full max-w-7xl px-4 pt-5 sm:px-6 print:hidden">
      <ol className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm text-muted">
        <li><Link href="/admin" className="inline-flex min-h-6 items-center hover:text-foreground">Dashboard</Link></li>
        {crumbs.map((crumb, index) => (
          <li key={crumb.href} className="flex min-w-0 items-center gap-1">
            <ChevronRight aria-hidden="true" className="size-3.5 shrink-0" />
            {index === crumbs.length - 1
              ? <span aria-current="page" className="font-medium text-foreground">{crumb.label}</span>
              : crumb.linked
                ? <Link href={crumb.href} className="inline-flex min-h-6 items-center hover:text-foreground">{crumb.label}</Link>
                : <span>{crumb.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}
