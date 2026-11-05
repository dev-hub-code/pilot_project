"use client";

import {
  Award, Banknote, BookOpen, Building2, ChartColumn, Check, ChevronDown, Circle, ClipboardList, Coins, CreditCard,
  FileText, History, Inbox, KeyRound, LayoutDashboard, LifeBuoy, type LucideIcon, Mail, Megaphone, Network, Package, PanelLeftClose, PanelLeftOpen, Percent, Settings,
  Scale, ShieldCheck, Stamp, Target, User, Users, Wallet,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState, useSyncExternalStore } from "react";
import type { MenuLink, MenuSection } from "@/components/layout/account-menu";

const ICONS: Record<string, LucideIcon> = {
  badge: Award, bank: Banknote, book: BookOpen, building: Building2, card: CreditCard, chart: ChartColumn, check: Check,
  coins: Coins, dashboard: LayoutDashboard, file: FileText, history: History, inbox: Inbox, key: KeyRound, life: LifeBuoy, mail: Mail, megaphone: Megaphone,
  network: Network, package: Package, percent: Percent, scale: Scale, settings: Settings, shield: ShieldCheck, stamp: Stamp,
  target: Target, tasks: ClipboardList, user: User, users: Users, wallet: Wallet,
};

/** The link a path belongs to: the longest match, so "/admin" does not claim every admin page. */
function activeLink(sections: readonly MenuSection[], pathname: string): MenuLink | undefined {
  return sections.flatMap((section) => section.links)
    .filter((link) => pathname === link.href || pathname.startsWith(`${link.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];
}

const KEY = "sidebar-collapsed";
const CHANGED = "sidebar-collapsed-change";

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(KEY) === "1";
  } catch {
    return false; // Storage can be blocked; the panel then starts open.
  }
}

function writeCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(KEY, collapsed ? "1" : "0");
  } catch {
    // The choice just lasts until the next page load.
  }
  window.dispatchEvent(new Event(CHANGED));
}

function subscribe(onChange: () => void) {
  window.addEventListener(CHANGED, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CHANGED, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** The menu's groups and links. `rail` shows icons only; `roomy` gives links a touch-sized height. */
export function NavLinks({ sections, rail = false, roomy = false, onNavigate }: {
  sections: readonly MenuSection[];
  rail?: boolean;
  roomy?: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const active = activeLink(sections, pathname);
  const [closed, setClosed] = useState<ReadonlySet<string>>(new Set());
  const collapsible = sections.length > 1;

  return (
    <nav aria-label="Main" className="py-3 text-sm">
      {sections.map((section, index) => {
        const name = section.label ?? String(index);
        const foldable = collapsible && !rail && Boolean(section.label);
        const open = !foldable || !closed.has(name);
        return (
          <div key={name} className={index > 0 ? "mt-1" : ""}>
            {foldable && (
              <button type="button" aria-expanded={open}
                onClick={() => setClosed((was) => {
                  const next = new Set(was);
                  if (!next.delete(name)) next.add(name);
                  return next;
                })}
                className={`flex w-full items-center justify-between px-5 text-[11px] uppercase tracking-[0.1em] text-muted hover:text-foreground ${roomy ? "min-h-11" : "py-1.5"}`}>
                {section.label}
                <ChevronDown aria-hidden="true" className={`size-3.5 transition-transform ${open ? "" : "-rotate-90"}`} />
              </button>
            )}
            {open && (
              <ul>
                {section.links.map((link) => {
                  const isActive = link === active;
                  const Icon = link.icon ? (ICONS[link.icon] ?? Circle) : rail ? Circle : null;
                  return (
                    <li key={`${link.href}-${link.label}`}>
                      <Link href={link.href} aria-current={isActive ? "page" : undefined} onClick={onNavigate}
                        aria-label={rail ? link.label : undefined} title={rail ? link.label : undefined}
                        className={`flex items-center gap-2.5 border-l-2 ${rail ? "justify-center py-2.5" : `px-5 ${roomy ? "min-h-11" : "py-1.5"}`} ${isActive
                          ? "border-gold bg-background font-medium text-foreground"
                          : "border-transparent text-muted hover:bg-background hover:text-foreground"}`}>
                        {Icon && <Icon aria-hidden="true" className="size-4 shrink-0" />}
                        {!rail && link.label}
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
  );
}

/**
 * The site menu as a left-hand panel on wide screens, which collapses to a rail of icons and remembers that on this
 * device; narrower screens open it from the header instead (see MobileNav).
 */
export function SideNav({ sections }: { sections: readonly MenuSection[] }) {
  const collapsed = useSyncExternalStore(subscribe, readCollapsed, () => false);
  const Toggle = collapsed ? PanelLeftOpen : PanelLeftClose;
  const label = collapsed ? "Expand menu" : "Collapse menu";

  return (
    <aside className={`sticky top-20 hidden h-[calc(100vh-5rem)] shrink-0 overflow-y-auto border-r border-border bg-surface transition-[width] lg:block print:hidden ${collapsed ? "w-16" : "w-64"}`}>
      <div className={`flex border-b border-border p-2 ${collapsed ? "justify-center" : "justify-end"}`}>
        <button type="button" aria-label={label} aria-expanded={!collapsed} title={label} onClick={() => writeCollapsed(!collapsed)}
          className="flex size-9 items-center justify-center rounded-lg text-muted hover:bg-background hover:text-foreground">
          <Toggle aria-hidden="true" className="size-5" />
        </button>
      </div>
      <NavLinks sections={sections} rail={collapsed} />
    </aside>
  );
}
