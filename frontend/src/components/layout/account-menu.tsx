"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useId, useRef, useState } from "react";
import { EmailText } from "@/components/ui/email-text";

export interface MenuLink {
  href: string;
  label: string;
}

/** A group of links in the menu; the heading is shown when the menu has more than one group. */
export interface MenuSection {
  label?: string;
  links: readonly MenuLink[];
}

/** The avatar in the header: opens the site menu as a vertical list, with sign-out at the bottom. */
export function AccountMenu({ sections, name, email, userCode }: {
  sections: readonly MenuSection[];
  name: string;
  email: string | null;
  /** The readable ID ("SL03500"), shown so the user can quote it to us. */
  userCode: string | null;
}) {
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

  const active = sections.flatMap((section) => section.links)
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
            {email && <p className="truncate text-xs text-muted"><EmailText>{email}</EmailText></p>}
            {userCode && <p className="mt-1 font-mono text-xs text-muted">ID {userCode}</p>}
          </div>
          <nav aria-label="Main" className="max-h-[75vh] overflow-y-auto py-1 text-sm">
            {sections.map((section, index) => (
              <div key={section.label ?? index} className={index > 0 ? "mt-1 border-t border-border pt-1" : ""}>
                {sections.length > 1 && section.label && (
                  <p id={`${menuId}-${index}`} className="px-4 pt-2 pb-1 text-[11px] uppercase tracking-[0.1em] text-muted">
                    {section.label}
                  </p>
                )}
                <ul aria-labelledby={sections.length > 1 && section.label ? `${menuId}-${index}` : undefined}>
                  {section.links.map((link) => {
                    const isActive = link === active;
                    return (
                      <li key={link.href}>
                        <Link href={link.href} aria-current={isActive ? "page" : undefined} onClick={() => setOpen(false)}
                          className={`block border-l-2 px-4 py-1.5 ${isActive
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
          {/* A full page request (see app/logout/route.ts), so no signed-in page stays in the browser. */}
          <form action="/logout" method="post" className="border-t border-border">
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
