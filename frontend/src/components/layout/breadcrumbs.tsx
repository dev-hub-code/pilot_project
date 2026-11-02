"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { MenuSection } from "@/components/layout/account-menu";

const words = (segment: string) => segment.replaceAll("-", " ").replace(/^./, (c) => c.toUpperCase());
const isId = (segment: string) => /^[0-9a-f-]{32,36}$/i.test(segment);

/** Where an admin page sits: "Dashboard / Departments / Details". Menu names are used where a page has one. */
export function Breadcrumbs({ sections }: { sections: readonly MenuSection[] }) {
  const pathname = usePathname();
  if (pathname === "/admin") return null;
  const links = sections.flatMap((section) => section.links);
  const segments = pathname.split("/").filter(Boolean).slice(1);
  const crumbs = segments.map((segment, index) => {
    const href = `/admin/${segments.slice(0, index + 1).join("/")}`;
    return { href, label: links.find((link) => link.href === href)?.label ?? (isId(segment) ? "Details" : words(segment)) };
  });

  return (
    <nav aria-label="Breadcrumb" className="mx-auto w-full max-w-7xl px-4 pt-3 sm:px-6">
      <ol className="flex flex-wrap items-center gap-1.5 text-xs text-muted">
        <li><Link href="/admin" className="hover:text-foreground">Dashboard</Link></li>
        {crumbs.map((crumb, index) => (
          <li key={crumb.href} className="flex items-center gap-1.5">
            <span aria-hidden="true">/</span>
            {index === crumbs.length - 1
              ? <span aria-current="page" className="text-foreground">{crumb.label}</span>
              : <Link href={crumb.href} className="hover:text-foreground">{crumb.label}</Link>}
          </li>
        ))}
      </ol>
    </nav>
  );
}
