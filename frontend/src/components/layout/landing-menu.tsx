"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
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
        className="flex size-10 items-center justify-center rounded-full border border-glass-border bg-glass-strong text-foreground shadow-sm backdrop-blur-md transition hover:scale-105 hover:bg-ink hover:text-on-ink active:scale-95 focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none">
        <svg aria-hidden="true" viewBox="0 0 24 24" className="size-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
          {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
        </svg>
      </button>
      {open && (
        <div id={menuId} className="absolute inset-x-3 top-full z-50 mt-2 overflow-hidden rounded-2xl border border-glass-border bg-glass-strong text-foreground shadow-xl backdrop-blur-xl motion-safe:animate-[menu-in_180ms_ease-out] sm:inset-x-10 sm:left-auto sm:w-72">
          <ul className="p-2 text-sm">
            {links.map((link) => {
              const className = "group relative flex items-center justify-between overflow-hidden rounded-xl px-4 py-3 font-medium text-muted transition-colors duration-200 hover:bg-glass hover:text-foreground focus-visible:bg-glass focus-visible:text-foreground focus-visible:outline-none";
              const content = (
                <>
                  <span aria-hidden="true" className="absolute inset-y-2 left-0 w-1 -translate-x-full rounded-full bg-gold transition-transform duration-200 group-hover:translate-x-0 group-focus-visible:translate-x-0" />
                  <span className="transition-transform duration-200 group-hover:translate-x-1.5">{link.label}</span>
                  <ArrowRight aria-hidden="true" className="size-4 -translate-x-2 text-gold-text opacity-0 transition duration-200 group-hover:translate-x-0 group-hover:opacity-100" />
                </>
              );
              return (
                <li key={link.href}>
                  {/* In-page sections use plain anchors: <Link> skips the scroll when the URL already has that hash. */}
                  {link.href.startsWith("#") ? (
                    <a href={link.href} onClick={() => setOpen(false)} className={className}>{content}</a>
                  ) : (
                    <Link href={link.href} onClick={() => setOpen(false)} className={className}>{content}</Link>
                  )}
                </li>
              );
            })}
          </ul>
          <div className="border-t border-border p-4 sm:hidden">
            <Link href="/register" onClick={() => setOpen(false)}
              className="group flex h-11 items-center justify-center gap-2 rounded-full bg-ink text-sm font-medium text-on-ink transition hover:bg-gold dark:bg-gold dark:text-on-gold">
              Get Started <ArrowRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-1" />
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}
