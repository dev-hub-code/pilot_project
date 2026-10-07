import { formatMoney } from "@/utils/money";

export interface ReturnPart {
  label: string;
  amount: number;
  /** Tailwind fill class for the bar segment, e.g. "fill-chart-1". */
  fill: string;
  /** Tailwind background class for the legend key, e.g. "bg-chart-1". */
  swatch: string;
}

/**
 * What the investor has received so far, as one bar split by source, with every value written out
 * beside its key. Drawn with SVG attributes, not inline styles, because the CSP blocks style attributes.
 */
export function ReturnsBreakdown({ parts, currency }: { parts: readonly ReturnPart[]; currency: string }) {
  const total = parts.reduce((sum, part) => sum + part.amount, 0);
  const money = (amount: number, compact = false) => formatMoney({ amount: amount.toFixed(2), currency }, { compact });
  const visible = parts.filter((part) => part.amount > 0);
  // Each segment's start, as a percentage of the bar.
  const starts = visible.map((_, i) => (visible.slice(0, i).reduce((sum, p) => sum + p.amount, 0) / total) * 100);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs uppercase tracking-[0.1em] text-muted">Received to date</p>
        <p className="mt-1 font-display text-4xl font-semibold">{money(total)}</p>
      </div>

      <svg className="block h-3 w-full" role="img"
        aria-label={visible.map((p) => `${p.label} ${percent(p.amount, total)}`).join(", ") || "Nothing received yet"}>
        <defs>
          <clipPath id="returns-bar">
            <rect width="100%" height="100%" rx="4" />
          </clipPath>
        </defs>
        <g clipPath="url(#returns-bar)">
          <rect width="100%" height="100%" className="fill-border" />
          {visible.map((part, i) => (
            <rect key={part.label} x={`${starts[i]}%`} width={`${(part.amount / total) * 100}%`} height="100%" className={part.fill} />
          ))}
          {/* 2px surface gaps between segments. */}
          {visible.slice(1).map((part, i) => (
            <line key={part.label} x1={`${starts[i + 1]}%`} x2={`${starts[i + 1]}%`} y1="0" y2="100%" className="stroke-surface" strokeWidth={2} />
          ))}
        </g>
      </svg>

      <dl className="space-y-3 text-sm">
        {parts.map((part) => (
          <div key={part.label} className="flex items-center gap-3">
            <dt className="flex flex-1 items-center gap-2.5 text-muted">
              <span aria-hidden="true" className={`size-2.5 rounded-[2px] ${part.swatch}`} />
              {part.label}
            </dt>
            <dd className="font-medium tabular-nums">{money(part.amount)}</dd>
            <dd className="w-12 text-right text-xs text-muted tabular-nums">{percent(part.amount, total)}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

function percent(amount: number, total: number): string {
  if (total <= 0) return "—";
  const share = (amount / total) * 100;
  return amount > 0 && share < 1 ? "<1%" : `${Math.round(share)}%`;
}
