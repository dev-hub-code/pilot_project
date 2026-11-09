"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutGrid, Settings } from "lucide-react";
import { CONFIG_SECTIONS } from "@/features/admin/config-sections";
import type { MenuLink } from "@/components/layout/account-menu";


/** The areas of Platform config. */
export function ConfigTabs({ tabs }: { tabs: readonly MenuLink[] }) {
  const pathname = usePathname();
  return (
    <nav aria-label="Platform config" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
        <Link href="/platform/platform-config" aria-current={pathname === "/platform/platform-config" ? "page" : undefined}
        className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${pathname === "/platform/platform-config"
          ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
          : "text-muted hover:bg-background hover:text-foreground"}`}>
        <LayoutGrid aria-hidden="true" className="size-4" /> Overview
      </Link>
      {tabs.map((tab) => {
          const active = pathname === tab.href;
          const Icon = CONFIG_SECTIONS[tab.href]?.icon ?? Settings;
          return (
            <Link key={tab.href} href={tab.href} aria-current={active ? "page" : undefined}
              className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${active
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              <Icon aria-hidden="true" className="size-4" /> {tab.label}
            </Link>
          );
        })}
    </nav>
  );
}
