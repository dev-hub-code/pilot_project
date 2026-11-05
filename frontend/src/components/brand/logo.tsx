import Link from "next/link";

/** One block in the mark, drawn in perspective: its front face is 24 wide and 8 high. */
function blockPaths(x: number, y: number) {
  const [length, height, dx, dy] = [24, 8, 5.5, -3.5];
  return {
    top: `M${x} ${y}l${dx} ${dy}h${length}l${-dx} ${-dy}z`,
    end: `M${x + length} ${y}l${dx} ${dy}v${height}l${-dx} ${-dy}z`,
    side: `M${x} ${y}h${length}v${height}h${-length}z`,
  };
}

/** Bottom to top, each set back a little; the top one is gold. */
const STACK = [blockPaths(2, 29), blockPaths(5, 20.5), blockPaths(8, 12)];

/**
 * The mark's shapes in its own coordinates (the blocks span x 1–39, y 7.5–38), for drawing it inside another SVG.
 * @param ink  fill of the lower blocks
 * @param gold fill of the top block; the gold token when omitted
 */
export function LogoMarkShapes({ ink = "currentColor", gold }: { ink?: string; gold?: string }) {
  return STACK.map((c, i) => {
    const fill = i === STACK.length - 1 ? (gold ? { fill: gold } : { className: "fill-gold" }) : { fill: ink };
    return (
      <g key={i} fillRule="evenodd">
        <path {...fill} fillOpacity="0.55" d={c.top} />
        <path {...fill} fillOpacity="0.78" d={c.end} />
        <path {...fill} d={c.side} />
      </g>
    );
  });
}

/**
 * SeaLease mark: three stacked blocks, the gold one on top the investor's own. The others
 * follow currentColor, with lighter top and end faces, so the mark adapts to light and dark backgrounds.
 */
export function LogoMark({ className = "h-9 w-11" }: { className?: string }) {
  return (
    <svg viewBox="1 7.5 38 30.5" className={className} aria-hidden="true" focusable="false">
      <LogoMarkShapes />
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
