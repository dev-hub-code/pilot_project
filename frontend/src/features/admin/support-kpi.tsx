import Link from "next/link";
import type { SupportKpi } from "@/types/support";

const count = new Intl.NumberFormat("en-IN");

/** Minutes as "2d 3h", "4h 10m" or "25m". */
export function minutesText(minutes: number): string {
  const total = Math.round(minutes);
  const d = Math.floor(total / 1440);
  const h = Math.floor((total % 1440) / 60);
  const m = total % 60;
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}m` : `${m}m`;
}

export function kpiValue(kpi: SupportKpi, v: number | null = kpi.value): string {
  if (v === null) return "—";
  switch (kpi.unit) {
    case "minutes": return minutesText(v);
    case "percent": return `${v.toFixed(1)}%`;
    case "rating": return `${v.toFixed(2)} / 5`;
    default: return count.format(v);
  }
}

/** The change against the previous period, said as better or worse, not only up or down. */
function Change({ kpi }: { kpi: SupportKpi }) {
  if (kpi.previous === null) return <p className="text-xs text-muted">Nothing to compare in the previous period</p>;
  if (kpi.value === null) return <p className="text-xs text-muted">Previously {kpiValue(kpi, kpi.previous)}</p>;
  const diff = kpi.value - kpi.previous;
  if (Math.abs(diff) < 1e-9) return <p className="text-xs text-muted">Unchanged · previously {kpiValue(kpi, kpi.previous)}</p>;
  const better = diff > 0 === kpi.higherBetter;
  const pct = kpi.previous !== 0 ? ` ${Math.abs((diff / kpi.previous) * 100).toFixed(1)}%` : "";
  return (
    <p className="text-xs text-muted">
      <span className={better ? "text-emerald-700 dark:text-emerald-400" : "text-rose-700 dark:text-rose-400"}>
        <span aria-hidden>{diff > 0 ? "▲" : "▼"}</span> {diff > 0 ? "Up" : "Down"}{pct} ({better ? "better" : "worse"})
      </span>
      {" "}· previously {kpiValue(kpi, kpi.previous)}
    </p>
  );
}

export function SupportKpiCard({ kpi }: { kpi: SupportKpi }) {
  const body = (
    <>
      <p className="text-sm text-muted">{kpi.label}</p>
      <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{kpiValue(kpi)}</p>
      <Change kpi={kpi} />
    </>
  );
  const box = "block space-y-1.5 rounded-xl border border-border bg-surface p-4 shadow-sm";
  return kpi.drill
    ? <Link href={kpi.drill} className={`${box} hover:border-foreground/40`} title={`Open the tickets behind ${kpi.label.toLowerCase()}`}>{body}</Link>
    : <div className={box}>{body}</div>;
}
