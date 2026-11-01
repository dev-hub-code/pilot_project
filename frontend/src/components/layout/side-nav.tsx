"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { MenuSection } from "@/components/layout/account-menu";

/** The site menu as a left-hand panel on wide screens; on narrower ones it is in the account menu instead. */
export function SideNav({ sections }: { sections: readonly MenuSection[] }) {
  const pathname = usePathname();
  const active = sections.flatMap((section) => section.links)
    .filter((link) => pathname === link.href || pathname.startsWith(`${link.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];

  return (
    <aside className="sticky top-20 hidden h-[calc(100vh-5rem)] w-60 shrink-0 overflow-y-auto border-r border-border bg-surface lg:block print:hidden">
      <nav aria-label="Main" className="py-4 text-sm">
        {sections.map((section, index) => (
          <div key={section.label ?? index} className={index > 0 ? "mt-3 border-t border-border pt-3" : ""}>
            {sections.length > 1 && section.label && (
              <p className="px-5 pb-1 text-[11px] uppercase tracking-[0.1em] text-muted">{section.label}</p>
            )}
            <ul>
              {section.links.map((link) => {
                const isActive = link === active;
                return (
                  <li key={link.href}>
                    <Link href={link.href} aria-current={isActive ? "page" : undefined}
                      className={`block border-l-2 px-5 py-1.5 ${isActive
                        ? "border-gold bg-background font-medium text-foreground"
                        : "border-transparent text-muted hover:bg-background hover:text-foreground"}`}>
                      {link.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>
    </aside>
  );
}
