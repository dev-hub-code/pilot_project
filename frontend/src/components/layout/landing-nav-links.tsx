"use client";

import { useEffect, useState } from "react";
import type { MenuLink } from "./account-menu";

/**
 * The landing page's in-page links from `xl` up. Hovering a link fills a soft glass pill behind it and draws an
 * underline out from its centre; the link for the section on screen keeps that state, so readers see where they are.
 */
export function LandingNavLinks({ links }: { links: readonly MenuLink[] }) {
  const [active, setActive] = useState<string | null>(null);

  useEffect(() => {
    const sections = links
      .map((link) => document.querySelector<HTMLElement>(link.href))
      .filter((el): el is HTMLElement => el !== null);
    if (sections.length === 0) return;
    // A section counts as current while it crosses a band just below the header.
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) setActive(`#${entry.target.id}`);
      }
    }, { rootMargin: "-30% 0px -60% 0px" });
    sections.forEach((section) => observer.observe(section));
    const clearAtTop = () => { if (window.scrollY < 200) setActive(null); };
    window.addEventListener("scroll", clearAtTop, { passive: true });
    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", clearAtTop);
    };
  }, [links]);

  return (
    <ul className="hidden items-center gap-1 rounded-full p-1 text-sm xl:mx-auto xl:flex">
      {links.map((link) => {
        const current = active === link.href;
        return (
          <li key={link.href}>
            {/* Plain anchors: <Link> skips the scroll when the URL already has that hash. */}
            <a href={link.href} aria-current={current ? "location" : undefined}
              className={`group relative block whitespace-nowrap rounded-full px-4 py-2 font-medium transition-colors duration-200 hover:bg-glass-strong hover:text-foreground hover:shadow-sm focus-visible:ring-2 focus-visible:ring-gold focus-visible:outline-none ${
                current ? "bg-glass-strong text-foreground shadow-sm" : "text-muted"}`}>
              {link.label}
              <span aria-hidden="true" className={`absolute inset-x-4 bottom-1 h-0.5 origin-center rounded-full bg-gold transition-transform duration-300 ease-out group-hover:scale-x-100 ${
                current ? "scale-x-100" : "scale-x-0"}`} />
            </a>
          </li>
        );
      })}
    </ul>
  );
}
