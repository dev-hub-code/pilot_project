import type { Metadata } from "next";
import Link from "next/link";
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
import { formatDate, formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { Activity, ArrowDownRight, ArrowUpRight, BarChart3, CalendarDays, ChevronRight, Minus, type LucideIcon } from "lucide-react";

export const metadata: Metadata = { title: "Reports & Analytics" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TRENDS = [
  { key: "investedCapital", label: "Capital invested" },
  { key: "returnsCredited", label: "Returns credited" },
  { key: "capitalReturned", label: "Capital returned" },
  { key: "withdrawalsPaid", label: "Withdrawals paid" },
  { key: "referralCommission", label: "Referral commission" },
] as const;
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
  if (direction === null) return <p className="text-xs text-muted">Nothing in the previous period</p>;
  // Up or down only, not good or bad: more withdrawals paid is neither.
  const Icon = direction === "up" ? ArrowUpRight : direction === "down" ? ArrowDownRight : Minus;
  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
      <span className={`inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-semibold tabular-nums ${direction === "flat"
        ? "bg-background ring-1 ring-border" : "bg-gold/10 text-gold-text"}`}>
        <Icon aria-hidden="true" className="size-3" />
        {direction === "flat" ? "Unchanged" : `${direction === "up" ? "Up" : "Down"} ${Math.abs(change!).toFixed(1)}%`}
      </span>
      <span>previously {value(kpi, currency, kpi.previous)}</span>
    </p>
  );
}

function KpiCard({ kpi, currency, href }: { kpi: Kpi; currency: string; href: string | null }) {
  const body = (
    <>
      {href && <span aria-hidden="true" className="absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 bg-gold transition-transform duration-500 group-hover:scale-x-100" />}
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted">{kpi.label}</p>
        {href && <ArrowUpRight aria-hidden="true" className="size-4 shrink-0 text-muted transition duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-text" />}
      </div>
      <p className={`font-display text-2xl font-semibold tracking-tight tabular-nums ${kpi.value === null || Number(kpi.value) === 0 ? "text-muted" : ""}`}>{value(kpi, currency)}</p>
      <Change kpi={kpi} currency={currency} />
      {kpi.hint && <p className="text-xs text-muted">{kpi.hint}</p>}
    </>
  );
  const box = "group relative block space-y-2 overflow-hidden rounded-2xl border border-border bg-surface p-5 shadow-sm";
  return href
    ? <Link href={href} className={`${box} transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-lg hover:shadow-gold/10`}
      title={`Open the report behind ${kpi.label.toLowerCase()}`}>{body}</Link>
    : <div className={box}>{body}</div>;
}

export default async function ReportsOverviewPage({ searchParams }: PageProps<"/platform/reports">) {
  const denied = await deniedUnlessInMenu("/platform/reports");
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
    return `/platform/reports/${report.key}${withPeriod && report.usesPeriod ? `?${new URLSearchParams({ from, to })}` : ""}`;
  };
  const currency = overview.currency;
  const columns = (key: string): ColumnDatum[] => overview.trends.map((point) => {
    const date = new Date(`${point.month}-01T00:00:00Z`);
    // Geometry only: the chart scales bars by these numbers; amounts shown come from the decimal strings.
    return { label: date.getUTCMonth() === 0 ? `${shortMonth.format(date)} ${date.getUTCFullYear()}` : shortMonth.format(date),
      title: longMonth.format(date), values: { [key]: Number(point.values[key] ?? 0) } };
  });

  return (
    <ReportsShell active="overview">
      <PageHeader title="Reports & Analytics"
        description="Investments, earnings, payments, withdrawals, referrals, operations and compliance: live from the platform's records and the ledger." />
      <PeriodForm action="/platform/reports" from={from} to={to} />

      <section aria-labelledby="period-heading" className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <SectionTitle id="period-heading" icon={CalendarDays}>{formatDate(from)} – {formatDate(to)}</SectionTitle>
          <p className="rounded-full bg-background px-3 py-1 text-xs text-muted ring-1 ring-border">
            Compared with {formatDate(overview.previousFrom)} – {formatDate(overview.previousTo)}
          </p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {overview.period.map((kpi) => <KpiCard key={kpi.key} kpi={kpi} currency={currency} href={link(kpi, true)} />)}
        </div>
      </section>

      <section aria-labelledby="now-heading" className="space-y-3">
        <SectionTitle id="now-heading" icon={Activity}>As things stand</SectionTitle>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {overview.now.map((kpi) => <KpiCard key={kpi.key} kpi={kpi} currency={currency} href={link(kpi, false)} />)}
        </div>
      </section>

      <section aria-labelledby="trends-heading" className="space-y-3">
        <SectionTitle id="trends-heading" icon={BarChart3}>Last 12 months</SectionTitle>
        <p className="text-sm text-muted">One chart per figure, each on its own scale: capital invested is client money, not revenue.</p>
        <div className="grid gap-4 xl:grid-cols-2">
          {TRENDS.map((trend) => (
            <Card key={trend.key} title={trend.label}>
              <ColumnChart label={`${trend.label} by month`} currency={currency} currentIndex={overview.trends.length - 1}
                series={[{ key: trend.key, label: trend.label, fill: "fill-chart-1", swatch: "bg-chart-1" }]}
                data={columns(trend.key)} />
            </Card>
          ))}
        </div>
      </section>
      <p className="flex items-center gap-1.5 text-xs text-muted">
        <ChevronRight aria-hidden="true" className="size-3.5" />
        Figures as of {formatDateTime(overview.generatedAt)}. Select a figure to open the rows behind it.
      </p>
    </ReportsShell>
  );
}

function SectionTitle({ id, icon: Icon, children }: { id: string; icon: LucideIcon; children: React.ReactNode }) {
  return (
    <h2 id={id} className="flex items-center gap-3 text-lg font-semibold tracking-tight">
      <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
        <Icon className="size-[18px]" />
      </span>
      {children}
    </h2>
  );
}
