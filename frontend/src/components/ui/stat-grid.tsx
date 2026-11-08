import type { ReactNode } from "react";

export interface Stat {
  label: string;
  value: ReactNode;
  note?: ReactNode;
}

/** The figures a page opens with, the most important first. */
export function StatGrid({ stats, label }: { stats: readonly Stat[]; label: string }) {
  const columns = stats.length === 4 ? "grid-cols-2 xl:grid-cols-4" : "sm:grid-cols-3";
  return (
    <section aria-label={label} className={`grid gap-4 ${columns}`}>
      {stats.map((stat) => (
        <div key={stat.label} className="group relative space-y-1.5 overflow-hidden rounded-2xl border border-border bg-surface p-4 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-md sm:p-5">
          <span aria-hidden="true" className="absolute inset-x-0 top-0 h-0.5 bg-gradient-to-r from-gold to-transparent opacity-60 transition-opacity group-hover:opacity-100" />
          <p className="text-sm text-muted">{stat.label}</p>
          <p className="font-display text-2xl font-semibold sm:text-3xl tracking-tight tabular-nums">{stat.value}</p>
          {stat.note && <p className="text-xs text-muted">{stat.note}</p>}
        </div>
      ))}
    </section>
  );
}
