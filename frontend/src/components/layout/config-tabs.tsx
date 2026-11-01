"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { MenuLink } from "@/components/layout/account-menu";

/** The areas of Platform config. */
export function ConfigTabs({ tabs }: { tabs: readonly MenuLink[] }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Platform config" className="flex flex-wrap gap-x-6 gap-y-1 border-b border-border text-sm">
      {tabs.map((tab) => {
        const active = pathname === tab.href;
        return (
          <Link key={tab.href} href={tab.href} aria-current={active ? "page" : undefined}
            className={`-mb-px border-b-2 py-2 ${active
              ? "border-gold font-medium text-foreground"
              : "border-transparent text-muted hover:text-foreground"}`}>
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
