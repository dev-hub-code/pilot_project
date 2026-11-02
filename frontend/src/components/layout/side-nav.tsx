"use client";

import {
  Award, Banknote, BookOpen, Building2, ChartColumn, Check, ChevronDown, Circle, ClipboardList, Coins, CreditCard,
  FileText, History, KeyRound, LayoutDashboard, LifeBuoy, type LucideIcon, Network, Package, Percent, Settings,
  ShieldCheck, Stamp, Target, User, Users, Wallet,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import type { MenuLink, MenuSection } from "@/components/layout/account-menu";

const ICONS: Record<string, LucideIcon> = {
  badge: Award, bank: Banknote, book: BookOpen, building: Building2, card: CreditCard, chart: ChartColumn, check: Check,
  coins: Coins, dashboard: LayoutDashboard, file: FileText, history: History, key: KeyRound, life: LifeBuoy,
  network: Network, package: Package, percent: Percent, settings: Settings, shield: ShieldCheck, stamp: Stamp,
  target: Target, tasks: ClipboardList, user: User, users: Users, wallet: Wallet,
};

/** The link a path belongs to: the longest match, so "/admin" does not claim every admin page. */
function activeLink(sections: readonly MenuSection[], pathname: string): MenuLink | undefined {
  return sections.flatMap((section) => section.links)
    .filter((link) => pathname === link.href || pathname.startsWith(`${link.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
}

/**
 * The site menu as a left-hand panel on wide screens; on narrower ones it is in the account menu instead. Named
 * groups fold away, and the one holding the current page stays open.
 */
export function SideNav({ sections }: { sections: readonly MenuSection[] }) {
  const pathname = usePathname();
  const active = activeLink(sections, pathname);
  const [closed, setClosed] = useState<ReadonlySet<string>>(new Set());
  const collapsible = sections.length > 1;

  return (
    <aside className="sticky top-20 hidden h-[calc(100vh-5rem)] w-64 shrink-0 overflow-y-auto border-r border-border bg-surface lg:block">
      <nav aria-label="Main" className="py-3 text-sm">
        {sections.map((section, index) => {
          const name = section.label ?? String(index);
          const holdsActive = active !== undefined && section.links.includes(active);
          const open = !collapsible || !section.label || holdsActive || !closed.has(name);
          return (
            <div key={name} className={index > 0 ? "mt-1" : ""}>
              {collapsible && section.label && (
                <button type="button" aria-expanded={open} disabled={holdsActive}
                  onClick={() => setClosed((was) => {
                    const next = new Set(was);
                    if (!next.delete(name)) next.add(name);
                    return next;
                  })}
                  className="flex w-full items-center justify-between px-5 py-1.5 text-[11px] uppercase tracking-[0.1em] text-muted hover:text-foreground">
                  {section.label}
                  <ChevronDown aria-hidden="true" className={`size-3.5 transition-transform ${open ? "" : "-rotate-90"}`} />
                </button>
              )}
              {open && (
                <ul>
                  {section.links.map((link) => {
                    const isActive = link === active;
                    const Icon = link.icon ? (ICONS[link.icon] ?? Circle) : null;
                    return (
                      <li key={`${link.href}-${link.label}`}>
                        <Link href={link.href} aria-current={isActive ? "page" : undefined}
                          className={`flex items-center gap-2.5 border-l-2 px-5 py-1.5 ${isActive
                            ? "border-gold bg-background font-medium text-foreground"
                            : "border-transparent text-muted hover:bg-background hover:text-foreground"}`}>
                          {Icon && <Icon aria-hidden="true" className="size-4 shrink-0" />}
                          {link.label}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          );
        })}
      </nav>
    </aside>
  );
}
