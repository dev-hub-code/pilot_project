import Link from "next/link";

/**
 * SeaLease mark: a rising gold stroke over a corrugated container — investment lifting the asset.
 * Drawn with currentColor so it adapts to light and dark backgrounds.
 */
export function LogoMark({ className = "size-9" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden="true" focusable="false">
      <polygon points="3,37 17,5 22,5 9,37" className="fill-gold" />
      <rect x="15.5" y="18.5" width="21" height="18" fill="none" stroke="currentColor" strokeWidth="2.5" />
      <path d="M21 21v13M26 21v13M31 21v13" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

export function Logo({ href = "/", tone = "ink", compact = false }: { href?: string; tone?: "ink" | "light"; compact?: boolean }) {
  const colour = tone === "light" ? "text-on-ink" : "text-foreground";
  return (
    <Link href={href} className={`inline-flex items-center gap-2.5 ${colour}`} aria-label="SeaLease home">
      <LogoMark />
      <span className={`font-wordmark text-xl font-light uppercase tracking-[0.32em] ${compact ? "hidden sm:inline" : ""}`}>SeaLease</span>
    </Link>
  );
}
