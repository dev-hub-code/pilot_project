"use client";

import Link from "next/link";
import { useEffect, useId, useRef, useState } from "react";
import type { MenuLink } from "./account-menu";

/** The home page's menu below the `xl` breakpoint: a toggle that opens the links as a panel under the header. */
export function LandingMenu({ links }: { links: readonly MenuLink[] }) {
  const [open, setOpen] = useState(false);
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

  return (
    <div ref={root} className="xl:hidden">
      <button ref={button} type="button" onClick={() => setOpen((o) => !o)}
        aria-expanded={open} aria-controls={menuId} aria-label={open ? "Close menu" : "Open menu"}
        className="flex size-10 items-center justify-center rounded-full border border-on-ink/25 bg-ink/35 text-on-ink backdrop-blur-md transition-colors hover:bg-on-ink hover:text-ink focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
        <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
          {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
        </svg>
      </button>
      {open && (
        <div id={menuId} className="absolute inset-x-3 top-full z-50 mt-1 border border-border bg-surface text-foreground shadow-lg sm:inset-x-10 sm:left-auto sm:w-72">
          <ul className="py-2 text-sm">
            {links.map((link) => {
              const className = "block px-5 py-3 text-muted transition-colors hover:bg-background hover:text-foreground";
              return (
                <li key={link.href}>
                  {/* In-page sections use plain anchors: <Link> skips the scroll when the URL already has that hash. */}
                  {link.href.startsWith("#") ? (
                    <a href={link.href} onClick={() => setOpen(false)} className={className}>{link.label}</a>
                  ) : (
                    <Link href={link.href} onClick={() => setOpen(false)} className={className}>{link.label}</Link>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="border-t border-border p-4 sm:hidden">
            <Link href="/register" onClick={() => setOpen(false)}
              className="flex h-11 items-center justify-center rounded-full bg-ink text-sm text-on-ink">
              Register
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
