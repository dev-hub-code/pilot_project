"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export interface SubNavItem {
  href: string;
  label: string;
}

/** Horizontal tabs; the active tab is the longest matching prefix. */
export function SubNav({ items, label }: { items: readonly SubNavItem[]; label: string }) {
  const pathname = usePathname();
  const active = items
    .filter((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];

  return (
    <nav aria-label={label} className="overflow-x-auto border-b border-border">
      <ul className="flex gap-6 text-sm">
        {items.map((item) => {
          const isActive = item === active;
          return (
            <li key={item.href}>
              <Link
                href={item.href}
                aria-current={isActive ? "page" : undefined}
                className={`inline-block whitespace-nowrap border-b-2 pb-3 ${
                  isActive ? "border-gold font-medium text-foreground" : "border-transparent text-muted hover:text-foreground"
                }`}
              >
                {item.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
