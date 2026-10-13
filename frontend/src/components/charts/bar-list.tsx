export interface BarListItem {
  label: string;
  value: number;
}

/**
 * Horizontal bars, one per row, each with its value written out beside it, so the bars need no axis
 * or tooltip. Drawn with SVG attributes, not inline styles, because the CSP blocks style attributes.
 */
export function BarList({ items, label, fill = "fill-chart-1" }: { items: readonly BarListItem[]; label: string; fill?: string }) {
  const max = Math.max(...items.map((item) => item.value), 0);
  return (
    <ul aria-label={label} className="space-y-3 text-sm">
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[7rem_1fr_3rem] items-center gap-3">
          <span className="truncate text-muted">{item.label}</span>
          <svg aria-hidden="true" className="block h-2.5 w-full">
            <rect width="100%" height="100%" rx="4" className="fill-border/60" />
            {item.value > 0 && <rect width={`${(item.value / max) * 100}%`} height="100%" rx="4" className={fill} />}
          </svg>
          <span className="text-right font-medium tabular-nums">{item.value}</span>
        </li>
      ))}
    </ul>
  );
}
