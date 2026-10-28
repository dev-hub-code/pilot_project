"use client";

import { useEffect, useState } from "react";

/**
 * Keeps the landing page's menu bar at the top while the page scrolls. Over the hero photo it stays clear; once the
 * page has scrolled it gets a dark frosted background so it reads over the light sections below.
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
      <header className={`relative flex items-center justify-between gap-6 px-5 text-on-ink transition-[background-color,padding,box-shadow] duration-300 sm:px-10 lg:px-14 ${
        scrolled ? "bg-ink/90 py-3 shadow-lg shadow-ink/20 backdrop-blur-md lg:py-4" : "py-6 lg:py-8"}`}>
        {children}
      </header>
    </div>
  );
}
