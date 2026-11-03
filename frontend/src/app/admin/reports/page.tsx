import type { Metadata } from "next";
import Link from "next/link";
import { BarList } from "@/components/charts/bar-list";
import { ColumnChart, type ColumnDatum } from "@/components/charts/column-chart";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { loadCatalog } from "@/features/analytics/catalog";
import { ReportsShell } from "@/features/analytics/reports-shell";
import { PeriodForm } from "@/features/reports/period-form";
import { thisMonth } from "@/features/reports/report-preview";
import { authFetch } from "@/lib/server/auth/session";
import type { Kpi, Overview } from "@/types/analytics";
import { formatDate, formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Reports & Analytics" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TRENDS = [
  { key: "investedCapital", label: "Capital invested" },
  { key: "returnsCredited", label: "Returns credited" },
  { key: "capitalReturned", label: "Capital returned" },
  { key: "withdrawalsPaid", label: "Withdrawals paid" },
  { key: "referralCommission", label: "Referral commission" },
] as const;
/** Container statuses in their lifecycle order (not by count), so the list reads the same every time. */
const CONTAINER_ORDER = ["AVAILABLE", "RESERVED", "ON_LEASE", "MAINTENANCE", "RETIRED"];
const shortMonth = new Intl.DateTimeFormat("en-GB", { month: "short", timeZone: "UTC" });
const longMonth = new Intl.DateTimeFormat("en-GB", { month: "long", year: "numeric", timeZone: "UTC" });

function value(kpi: Kpi, currency: string, v = kpi.value): string {
  if (v === null) return "—";
  if (kpi.kind === "MONEY") return formatMoney({ amount: String(v), currency });
  if (kpi.kind === "PERCENT") return `${v}%`;
  return new Intl.NumberFormat("en-IN").format(Number(v));
}

function Change({ kpi, currency }: { kpi: Kpi; currency: string }) {
  if (kpi.previous === null) return null;
  const change = kpi.change === null ? null : Number(kpi.change);
  const direction = change === null ? null : change > 0 ? "up" : change < 0 ? "down" : "flat";
  return (
    <p className="text-xs text-muted">
      {direction === null ? "Nothing in the previous period"
        : <>
          {direction === "flat" ? "Unchanged" : <><span aria-hidden>{direction === "up" ? "▲" : "▼"}</span> {direction === "up" ? "Up" : "Down"} {Math.abs(change!).toFixed(1)}%</>}
          {" "}· previously {value(kpi, currency, kpi.previous)}
        </>}
    </p>
  );
}

function KpiCard({ kpi, currency, href }: { kpi: Kpi; currency: string; href: string | null }) {
  const body = (
    <>
      <p className="text-sm text-muted">{kpi.label}</p>
      <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{value(kpi, currency)}</p>
      <Change kpi={kpi} currency={currency} />
      {kpi.hint && <p className="text-xs text-muted">{kpi.hint}</p>}
    </>
  );
  const box = "block space-y-1.5 rounded-xl border border-border bg-surface p-4 shadow-sm";
  return href
    ? <Link href={href} className={`${box} hover:border-foreground/40`} title={`Open the report behind ${kpi.label.toLowerCase()}`}>{body}</Link>
    : <div className={box}>{body}</div>;
}

export default async function ReportsOverviewPage({ searchParams }: PageProps<"/admin/reports">) {
  const denied = await deniedUnlessInMenu("/admin/reports");
  if (denied) return denied;
  const params = await searchParams;
  const preset = thisMonth();
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : preset.from;
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : preset.to;
  const [overview, catalog] = await Promise.all([
    authFetch<Overview>(`/api/v1/admin/analytics/overview?${new URLSearchParams({ from, to })}`),
    loadCatalog(),
  ]);
  const open = new Map(catalog.flatMap((c) => c.reports.map((r) => [r.key, r] as const)));
  const link = (kpi: Kpi, withPeriod: boolean) => {
    const report = open.get(kpi.report);
    if (!report) return null;
    return `/admin/reports/${report.key}${withPeriod && report.usesPeriod ? `?${new URLSearchParams({ from, to })}` : ""}`;
  };
  const currency = overview.currency;
  const columns = (key: string): ColumnDatum[] => overview.trends.map((point) => {
    const date = new Date(`${point.month}-01T00:00:00Z`);
    // Geometry only: the chart scales bars by these numbers; amounts shown come from the decimal strings.
    return { label: date.getUTCMonth() === 0 ? `${shortMonth.format(date)} ${date.getUTCFullYear()}` : shortMonth.format(date),
      title: longMonth.format(date), values: { [key]: Number(point.values[key] ?? 0) } };
  });
  const containers = CONTAINER_ORDER.map((status) => ({
    label: humanize(status), value: overview.containers.find((s) => s.key === status)?.count ?? 0,
  }));

  return (
    <ReportsShell active="overview">
      <PageHeader title="Reports & Analytics"
        description="Investments, earnings, payments, withdrawals, referrals, containers, operations and compliance: live from the platform's records and the ledger." />
      <PeriodForm action="/admin/reports" from={from} to={to} />

      <section aria-labelledby="period-heading" className="space-y-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="period-heading" className="text-lg font-semibold tracking-tight">{formatDate(from)} – {formatDate(to)}</h2>
          <p className="text-xs text-muted">Compared with {formatDate(overview.previousFrom)} – {formatDate(overview.previousTo)}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {overview.period.map((kpi) => <KpiCard key={kpi.key} kpi={kpi} currency={currency} href={link(kpi, true)} />)}
        </div>
      </section>

      <section aria-labelledby="now-heading" className="space-y-3">
        <h2 id="now-heading" className="text-lg font-semibold tracking-tight">As things stand</h2>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {overview.now.map((kpi) => <KpiCard key={kpi.key} kpi={kpi} currency={currency} href={link(kpi, false)} />)}
        </div>
      </section>

      <section aria-labelledby="trends-heading" className="space-y-3">
        <h2 id="trends-heading" className="text-lg font-semibold tracking-tight">Last 12 months</h2>
        <p className="text-sm text-muted">One chart per figure, each on its own scale: capital invested is client money, not revenue.</p>
        <div className="grid gap-4 xl:grid-cols-2">
          {TRENDS.map((trend) => (
            <Card key={trend.key} title={trend.label}>
              <ColumnChart label={`${trend.label} by month`} currency={currency} currentIndex={overview.trends.length - 1}
                series={[{ key: trend.key, label: trend.label, fill: "fill-chart-1", swatch: "bg-chart-1" }]}
                data={columns(trend.key)} />
            </Card>
          ))}
          <Card title="Containers by status" description="The whole fleet, as it stands">
            <BarList label="Containers by status" items={containers} />
          </Card>
        </div>
      </section>
      <p className="text-xs text-muted">Figures as of {formatDateTime(overview.generatedAt)}. Select a figure to open the rows behind it.</p>
    </ReportsShell>
  );
}
