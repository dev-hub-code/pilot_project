"use client";

import { useEffect, useId, useRef, useState } from "react";
import { formatMoney } from "@/utils/money";

export interface ColumnSeries {
  key: string;
  label: string;
  /** Tailwind fill class for the mark, e.g. "fill-chart-1". */
  fill: string;
  /** Tailwind background class for the legend and tooltip key, e.g. "bg-chart-1". */
  swatch: string;
  /** Scheduled (not yet paid) amounts: drawn as a lighter wash of the same hue. */
  scheduled?: boolean;
}

export interface ColumnDatum {
  /** Axis label, e.g. "Oct". */
  label: string;
  /** Tooltip and table heading, e.g. "October 2026". */
  title: string;
  values: Record<string, number>;
}

interface Props {
  series: readonly ColumnSeries[];
  data: readonly ColumnDatum[];
  currency: string;
  /** Marks this column as the current period: a rule at its left edge, labelled "This month". */
  currentIndex?: number;
  /** Accessible name of the chart. */
  label: string;
}

const PLOT_HEIGHT = 220;
const AXIS_BAND = 28;
const TOP = 20;
const LEFT = 56;
const GAP = 2;
const MAX_BAR = 24;
const RADIUS = 4;

/**
 * Stacked columns, one per period. Server-rendered data, measured and drawn on the client so text
 * stays crisp at any width. Every value is also in the table under the chart.
 */
export function ColumnChart({ series, data, currency, currentIndex, label }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [active, setActive] = useState<number | null>(null);
  const tableId = useId();

  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.floor(entry!.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const money = (amount: number, compact = false) => formatMoney({ amount: amount.toFixed(2), currency }, { compact });
  const totals = data.map((d) => series.reduce((sum, s) => sum + (d.values[s.key] ?? 0), 0));
  const ticks = niceTicks(Math.max(...totals, 0));
  const top = ticks.at(-1)!;
  const plotWidth = Math.max(0, width - LEFT);
  const band = data.length > 0 ? plotWidth / data.length : 0;
  const bar = Math.min(MAX_BAR, band * 0.56);
  const y = (value: number) => TOP + PLOT_HEIGHT - (value / top) * PLOT_HEIGHT;
  const labelEvery = band < 34 ? Math.ceil(34 / band) : 1;
  const legend = series.filter((s) => !s.scheduled);
  const hasScheduled = series.some((s) => s.scheduled) && data.some((d) => series.some((s) => s.scheduled && (d.values[s.key] ?? 0) > 0));

  return (
    <figure className="space-y-4">
      <figcaption className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted">
        {legend.map((s) => (
          <span key={s.key} className="inline-flex items-center gap-2">
            <span aria-hidden="true" className={`size-2.5 rounded-[2px] ${s.swatch}`} />
            {s.label}
          </span>
        ))}
        {hasScheduled && (
          <span className="inline-flex items-center gap-2">
            <span aria-hidden="true" className="size-2.5 rounded-[2px] bg-muted/30" />
            Lighter: scheduled
          </span>
        )}
      </figcaption>

      <div ref={box} className="relative h-[268px]" onPointerLeave={() => setActive(null)}>
        {width > 0 && (
          <svg width={width} height={TOP + PLOT_HEIGHT + AXIS_BAND} role="group" aria-label={label} aria-describedby={tableId}
            className="block overflow-visible">
            {ticks.map((tick) => (
              <g key={tick}>
                <line x1={LEFT} x2={width} y1={y(tick)} y2={y(tick)} className="stroke-border" strokeWidth={1}
                  shapeRendering="crispEdges" />
                <text x={LEFT - 10} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-muted text-[11px] tabular-nums">
                  {tick === 0 ? "0" : money(tick, true)}
                </text>
              </g>
            ))}

            {currentIndex !== undefined && currentIndex >= 0 && currentIndex < data.length && (
              <g aria-hidden="true">
                <line x1={LEFT + band * currentIndex} x2={LEFT + band * currentIndex} y1={TOP - 12} y2={TOP + PLOT_HEIGHT}
                  className="stroke-muted/60" strokeWidth={1} shapeRendering="crispEdges" />
                <text x={LEFT + band * currentIndex + 6} y={TOP - 4} className="fill-muted text-[11px]">This month</text>
              </g>
            )}

            {data.map((datum, index) => {
              const centre = LEFT + band * index + band / 2;
              const dimmed = active !== null && active !== index;
              let base = 0;
              const drawn = series.filter((s) => (datum.values[s.key] ?? 0) > 0);
              return (
                <g key={datum.title} className={`transition-opacity duration-150 ${dimmed ? "opacity-45" : ""}`}>
                  {drawn.map((s, i) => {
                    const value = datum.values[s.key]!;
                    const y0 = y(base);
                    base += value;
                    const isTop = i === drawn.length - 1;
                    const y1 = y(base) + (isTop ? 0 : GAP);
                    const height = Math.max(0, y0 - y1);
                    if (height < 0.5) return null;
                    return (
                      <path key={s.key} d={columnPath(centre - bar / 2, y1, bar, height, isTop ? RADIUS : 0)}
                        className={`${s.fill} ${s.scheduled ? "opacity-35" : ""}`} />
                    );
                  })}
                  {index % labelEvery === 0 && (
                    <text x={centre} y={TOP + PLOT_HEIGHT + 18} textAnchor="middle"
                      className={`text-[11px] ${active === index ? "fill-foreground font-medium" : "fill-muted"}`}>
                      {datum.label}
                    </text>
                  )}
                  {/* The hit target is the whole band, not just the painted column. */}
                  <rect x={LEFT + band * index} y={TOP} width={band} height={PLOT_HEIGHT} fill="transparent"
                    tabIndex={0} role="img" aria-label={`${datum.title}: ${money(totals[index]!)}`}
                    className="cursor-default outline-none focus-visible:stroke-gold focus-visible:stroke-2"
                    onPointerEnter={() => setActive(index)} onFocus={() => setActive(index)} onBlur={() => setActive(null)} />
                </g>
              );
            })}
          </svg>
        )}

        {active !== null && width > 0 && (
          <Tooltip x={LEFT + band * active + band / 2} width={width} title={data[active]!.title}
            rows={series.filter((s) => (data[active]!.values[s.key] ?? 0) > 0)
              .map((s) => ({ key: s.key, label: s.scheduled ? `${s.label} (scheduled)` : s.label, swatch: s.swatch,
                faded: !!s.scheduled, value: money(data[active]!.values[s.key]!) }))}
            total={money(totals[active]!)} />
        )}
      </div>

      <details className="text-sm">
        <summary className="cursor-pointer text-xs text-muted hover:text-foreground">Show as table</summary>
        <div className="mt-3 overflow-x-auto">
          <table id={tableId} className="w-full text-left text-xs tabular-nums">
            <thead className="text-muted">
              <tr>
                <th scope="col" className="py-1.5 pr-4 font-medium">Month</th>
                {series.map((s) => (
                  <th key={s.key} scope="col" className="py-1.5 pr-4 text-right font-medium">
                    {s.scheduled ? `${s.label} (scheduled)` : s.label}
                  </th>
                ))}
                <th scope="col" className="py-1.5 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody>
              {data.map((d, i) => (
                <tr key={d.title} className="border-t border-border">
                  <th scope="row" className="py-1.5 pr-4 font-normal">{d.title}</th>
                  {series.map((s) => (
                    <td key={s.key} className="py-1.5 pr-4 text-right">{d.values[s.key] ? money(d.values[s.key]!) : "—"}</td>
                  ))}
                  <td className="py-1.5 text-right font-medium">{totals[i] ? money(totals[i]!) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}

interface TooltipRow {
  key: string;
  label: string;
  swatch: string;
  faded: boolean;
  value: string;
}

/** Rendered only after hydration, so its position is set through the CSSOM, which the CSP allows. */
function Tooltip({ x, width, title, rows, total }: { x: number; width: number; title: string; rows: TooltipRow[]; total: string }) {
  const TIP = 240;
  const left = Math.min(Math.max(0, x - TIP / 2), Math.max(0, width - TIP));
  return (
    <div role="status" className="pointer-events-none absolute top-0 z-10 w-[240px] border border-border bg-surface p-3 text-xs shadow-lg"
      style={{ left }}>
      <p className="mb-2 text-muted">{title}</p>
      <ul className="space-y-1.5">
        {rows.map((row) => (
          <li key={row.key} className="flex items-center gap-2">
            <span aria-hidden="true" className={`h-0.5 w-3 rounded-full ${row.swatch} ${row.faded ? "opacity-40" : ""}`} />
            <span className="font-semibold tabular-nums text-foreground">{row.value}</span>
            <span className="truncate text-muted">{row.label}</span>
          </li>
        ))}
      </ul>
      {rows.length > 1 && (
        <p className="mt-2 flex justify-between border-t border-border pt-2">
          <span className="text-muted">Total</span>
          <span className="font-semibold tabular-nums">{total}</span>
        </p>
      )}
    </div>
  );
}

/** A column with rounded top corners and a square base. */
function columnPath(x: number, y: number, w: number, h: number, r: number): string {
  const radius = Math.min(r, w / 2, h);
  return `M${x},${y + h}V${y + radius}Q${x},${y} ${x + radius},${y}H${x + w - radius}Q${x + w},${y} ${x + w},${y + radius}V${y + h}Z`;
}

/** 0 and up to four round steps (1, 2, 2.5 or 5 × 10ⁿ) that cover max. */
function niceTicks(max: number): number[] {
  if (max <= 0) return [0, 1];
  const raw = max / 4;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= raw)!;
  const count = Math.ceil(max / step);
  return Array.from({ length: count + 1 }, (_, i) => i * step);
}
