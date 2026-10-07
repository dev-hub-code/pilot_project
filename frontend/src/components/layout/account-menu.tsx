"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { logoutAction } from "@/features/auth/actions";

export interface MenuLink {
  href: string;
  label: string;
}

/** The avatar in the header: opens the site menu as a vertical list, with sign-out at the bottom. */
export function AccountMenu({ links, name, email }: { links: readonly MenuLink[]; name: string; email: string | null }) {
  const [open, setOpen] = useState(false);
  const pathname = usePathname();
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  // Close on outside click and Escape; following a link closes it too.
  useEffect(() => {
    if (!open) return;
    const onPointer = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setOpen(false);
        button.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const active = links
    .filter((link) => pathname === link.href || pathname.startsWith(`${link.href}/`))
    .sort((a, b) => b.href.length - a.href.length)[0];

  return (
    <div ref={root} className="relative">
      <button ref={button} type="button" onClick={() => setOpen((o) => !o)}
        aria-expanded={open} aria-controls={menuId} aria-label={`Menu for ${name}`}
        className="flex size-10 items-center justify-center rounded-full border border-border bg-gold text-sm font-semibold text-ink transition-shadow hover:ring-2 hover:ring-gold/40 focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
        {initials(name)}
      </button>
      {open && (
        <div id={menuId} className="absolute right-0 z-50 mt-2 w-64 max-w-[calc(100vw-2rem)] border border-border bg-surface shadow-lg">
          <div className="border-b border-border px-4 py-3">
            <p className="truncate text-sm font-medium">{name}</p>
            {email && <p className="truncate text-xs text-muted">{email}</p>}
          </div>
          <nav aria-label="Main">
            <ul className="max-h-[70vh] overflow-y-auto py-1 text-sm">
              {links.map((link) => {
                const isActive = link === active;
                return (
                  <li key={link.href}>
                    <Link href={link.href} aria-current={isActive ? "page" : undefined} onClick={() => setOpen(false)}
                      className={`block border-l-2 px-4 py-2 ${isActive
                        ? "border-gold bg-background font-medium text-foreground"
                        : "border-transparent text-muted hover:bg-background hover:text-foreground"}`}>
                      {link.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </nav>
          <form action={logoutAction} className="border-t border-border">
            <button type="submit" className="block w-full px-4 py-3 text-left text-sm text-muted hover:bg-background hover:text-foreground">
              Sign out
            </button>
          </form>
        </div>
      )}
    </div>
  );
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const letters = parts.length > 1 ? parts[0]![0]! + parts.at(-1)![0]! : (parts[0] ?? "?").slice(0, 2);
  return letters.toUpperCase();
}
