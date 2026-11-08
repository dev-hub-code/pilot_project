"use client";

import { useEffect, useState } from "react";

/**
 * Keeps the landing page's menu bar at the top while the page scrolls. At the top it sits clear on the page's sheen;
 * once the page has scrolled it gets a frosted glass background so it reads over everything below.
 */
export function LandingHeader({ children }: { children: React.ReactNode }) {
  const [scrolled, setScrolled] = useState(false);
  useEffect(() => {
    const update = () => setScrolled(window.scrollY > 24);
    update();
    window.addEventListener("scroll", update, { passive: true });
    return () => window.removeEventListener("scroll", update);
  }, []);
  // Zero height: the bar overlays the hero instead of pushing it down.
  return (
    <div className="sticky top-0 z-40 h-0">
      <header className={`relative flex items-center justify-between gap-6 border-b px-5 text-foreground transition-[background-color,padding,box-shadow,border-color] duration-300 sm:px-10 lg:px-14 ${
        scrolled ? "border-glass-border bg-glass-strong py-3 shadow-lg shadow-ink/10 backdrop-blur-xl lg:py-4" : "border-transparent py-6 lg:py-7"}`}>
        {children}
      </header>
    </div>
  );
}
