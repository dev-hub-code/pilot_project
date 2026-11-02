"use client";

import { useEffect, useId, useRef, useState } from "react";
import { EmailText } from "@/components/ui/email-text";

export interface MenuLink {
  href: string;
  label: string;
  /** A name from the side panel's icon set. */
  icon?: string;
}

/** A group of links in the menu; the heading is shown when the menu has more than one group. */
export interface MenuSection {
  label?: string;
  links: readonly MenuLink[];
}

/**
 * The avatar in the header: who is signed in and sign-out.
 */
export function AccountMenu({ name, email, userCode, demo = false }: {
  name: string;
  email: string | null;
  /** The readable ID ("SL03500"), shown so the user can quote it to us. */
  userCode: string | null;
  /** A demo account: a dark circle around the avatar marks it. */
  demo?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menuId = useId();

  // Close on outside click and Escape.
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


  return (
    <div ref={root} className="relative">
      <button ref={button} type="button" onClick={() => setOpen((o) => !o)}
        aria-expanded={open} aria-controls={menuId} aria-label={`Menu for ${name}`}
        className={`flex size-10 items-center justify-center rounded-full bg-gold text-sm font-semibold text-on-gold transition-shadow hover:ring-2 hover:ring-gold/40 focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none ${demo ? "border-[3px] border-ink dark:border-foreground" : "border border-border"}`}>
        {initials(name)}
      </button>
      {open && (
        <div id={menuId} className="absolute right-0 z-50 mt-2 w-64 max-w-[calc(100vw-2rem)] border border-border bg-surface shadow-lg">
          <div className="border-b border-border px-4 py-3">
            <p className="truncate text-sm font-medium">{name}</p>
            {email && <p className="truncate text-xs text-muted"><EmailText>{email}</EmailText></p>}
            {userCode && <p className="mt-1 font-mono text-xs text-muted">ID {userCode}</p>}
          </div>
          {/* A full page request (see app/logout/route.ts), so no signed-in page stays in the browser. */}
          <form action="/logout" method="post" className="">
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
