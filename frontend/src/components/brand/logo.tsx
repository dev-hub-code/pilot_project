import Link from "next/link";

/** One container in the mark, seen from the side and its door end: its long side is 24 wide and 8 high. */
function containerPaths(x: number, y: number) {
  const [length, height, dx, dy] = [24, 8, 5.5, -3.5];
  const slot = (sx: number, sy: number) => `M${sx} ${sy}h0.6v5.4h-0.6z`;
  // Corrugation ribs on the long side, and the two locking bars on the doors, are cut out of their faces.
  const ribs = Array.from({ length: 11 }, (_, i) => slot(x + 2.2 + i * 1.9, y + 1.3)).join("");
  const bars = [0.33, 0.66].map((u) => slot(x + length + dx * u - 0.3, y + dy * u + 1.3)).join("");
  return {
    top: `M${x} ${y}l${dx} ${dy}h${length}l${-dx} ${-dy}z`,
    end: `M${x + length} ${y}l${dx} ${dy}v${height}l${-dx} ${-dy}z${bars}`,
    side: `M${x} ${y}h${length}v${height}h${-length}z${ribs}`,
  };
}

/** Bottom to top, each set back a little; the top one is gold. */
const STACK = [containerPaths(2, 29), containerPaths(5, 20.5), containerPaths(8, 12)];

/**
 * SeaLease mark: three shipping containers stacked as in a port, the gold one on top the investor's own. The others
 * follow currentColor, with lighter top and end faces, so the mark adapts to light and dark backgrounds.
 */
export function LogoMark({ className = "h-9 w-11" }: { className?: string }) {
  return (
    <svg viewBox="1 7.5 38 30.5" className={className} aria-hidden="true" focusable="false">
      {STACK.map((c, i) => {
        const fill = i === STACK.length - 1 ? { className: "fill-gold" } : { fill: "currentColor" };
        return (
          <g key={i} fillRule="evenodd">
            <path {...fill} fillOpacity="0.55" d={c.top} />
            <path {...fill} fillOpacity="0.78" d={c.end} />
            <path {...fill} d={c.side} />
          </g>
        );
      })}
    </svg>
  );
}

export function Logo({ href = "/", tone = "ink", compact = false }: { href?: string; tone?: "ink" | "light"; compact?: boolean }) {
  const colour = tone === "light" ? "text-on-ink" : "text-foreground";
  return (
    <Link href={href} className={`inline-flex items-center gap-3 ${colour}`} aria-label="SeaLease home">
      <LogoMark />
      <span className={`font-wordmark text-xl font-semibold uppercase leading-none tracking-[0.28em] ${compact ? "hidden sm:inline" : ""}`}>
        SeaLease
      </span>
    </Link>
  );
}
