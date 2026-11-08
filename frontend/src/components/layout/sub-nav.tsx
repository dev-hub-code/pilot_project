"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export interface SubNavItem {
  href: string;
  label: string;
  icon?: React.ReactNode;
}

/** Tabs as a row of pills, wrapping onto more lines on narrow screens; the active tab is the longest matching prefix. */
export function SubNav({ items, label }: { items: readonly SubNavItem[]; label: string }) {
  const pathname = usePathname();
  const active = items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];

  return (
    <nav aria-label={label}>
      <ul className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
        {items.map((item) => {
          const isActive = item === active;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={isActive ? "page" : undefined}
                className={`group inline-flex items-center gap-2 rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${
                  isActive ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold" : "text-muted hover:bg-background hover:text-foreground"
                }`}
              >
                {item.icon && <span aria-hidden="true" className="flex [&>svg]:size-4">{item.icon}</span>}
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
