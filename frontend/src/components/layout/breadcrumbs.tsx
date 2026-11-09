"use client";

import { ChevronRight, Home } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { MenuSection } from "@/components/layout/account-menu";
import { ADMIN_NAV } from "@/components/layout/admin-nav";

/** Pages with no menu entry, by path; "*" is any one segment, such as an id. */
const PAGES: [string, string][] = [
  ["/platform/audit/*", "Audit entry"],
  ["/platform/demo-accounts/*", "Demo account"],
  ["/platform/departments/*", "Department details"],
  ["/platform/kyc/*", "KYC submission"],
  ["/platform/leads/new", "New lead"],
  ["/platform/leads/*", "Lead details"],
  ["/platform/ledger/*", "Ledger account"],
  ["/platform/orders/*/invoice", "Invoice"],
  ["/platform/orders/*", "Order details"],
  ["/platform/platform-config", "Platform Configuration"],
  ["/platform/products/new", "New product"],
  ["/platform/products/*/edit", "Edit"],
  ["/platform/products/*", "Product details"],
  ["/platform/roles/new", "New role"],
  ["/platform/roles/*", "Role details"],
  ["/platform/staff/new", "New employee"],
  ["/platform/staff/*", "Employee details"],
  ["/platform/support/dashboard", "Dashboard"],
  ["/platform/support/escalations", "Escalations"],
  ["/platform/support/feedback", "Feedback"],
  ["/platform/support/new", "New ticket"],
  ["/platform/support/knowledge-base/new", "New article"],
  ["/platform/support/knowledge-base/read/*", "Article"],
  ["/platform/support/knowledge-base/*", "Edit article"],
  ["/platform/support/*", "Ticket"],
  ["/platform/users/new", "New user"],
  ["/platform/users/new-investor", "New investor"],
  ["/platform/users/*", "Investor details"],
  ["/platform/withdrawals/batches", "Withdrawal files"],
  ["/platform/withdrawals/batches/*", "Withdrawal file"],
  ["/platform/withdrawals/*", "Withdrawal details"],
];

/** Paths that only group other pages: their crumb goes to the page people know them by. */
const SAME_AS: Record<string, string> = { "/platform/kyc": "/platform/verification" };

const matches = (pattern: string, path: string) => {
  const want = pattern.split("/");
  const have = path.split("/");
  return want.length === have.length && want.every((part, index) => part === "*" || part === have[index]);
};

/** Where an admin page sits: "Dashboard › Orders & Agreements › Order details › Invoice". */
export function Breadcrumbs({ sections }: { sections: readonly MenuSection[] }) {
  const pathname = usePathname();
  if (pathname === "/platform") return null;
  const allowed = new Set(sections.flatMap((section) => section.links).map((link) => link.href));
  const segments = pathname.split("/").filter(Boolean).slice(1);

  const crumbs = segments.flatMap((_, index) => {
    const path = `/platform/${segments.slice(0, index + 1).join("/")}`;
    const href = SAME_AS[path] ?? path;
    const nav = ADMIN_NAV.find((item) => item.href === href);
    const label = nav?.crumb ?? nav?.label ?? PAGES.find(([pattern]) => matches(pattern, href))?.[1];
    if (!label) return [];
    // A menu page this person cannot open is named but not linked.
    return [{ href, label, linked: !nav || allowed.has(href) }];
  });

  return (
    <nav aria-label="Breadcrumb" className="mx-auto w-full max-w-7xl px-4 pt-5 sm:px-6 print:hidden">
      <ol className="flex flex-wrap items-center gap-x-1 gap-y-1 text-sm text-muted">
        <li>
          <Link href="/platform" className="inline-flex min-h-7 items-center gap-1.5 rounded-lg px-2 transition-colors hover:bg-surface hover:text-foreground">
            <Home aria-hidden="true" className="size-3.5" /> Dashboard
          </Link>
        </li>
        {crumbs.map((crumb, index) => (
          <li key={crumb.href} className="flex min-w-0 items-center gap-1">
            <ChevronRight aria-hidden="true" className="size-3.5 shrink-0" />
            {index === crumbs.length - 1
              ? <span aria-current="page" className="rounded-lg bg-surface px-2 py-0.5 font-medium text-foreground shadow-sm ring-1 ring-border">{crumb.label}</span>
              : crumb.linked
                ? <Link href={crumb.href} className="inline-flex min-h-7 items-center rounded-lg px-2 transition-colors hover:bg-surface hover:text-foreground">{crumb.label}</Link>
                : <span>{crumb.label}</span>}
          </li>
        ))}
      </ol>
    </nav>
  );
}
