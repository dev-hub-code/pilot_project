import type { Metadata } from "next";
import Link from "next/link";
import { BarList } from "@/components/charts/bar-list";
import { ColumnChart, type ColumnDatum } from "@/components/charts/column-chart";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { SupportKpiCard, kpiValue } from "@/features/admin/support-kpi";
import { SupportShell } from "@/features/admin/support-shell";
import { PeriodForm } from "@/features/reports/period-form";
import { thisMonth } from "@/features/reports/report-preview";
import {
  CATEGORY_LABEL, CATEGORY_ORDER, CHANNEL_LABEL, PRIORITY_LABEL, PRIORITY_ORDER, STAFF_STATUS_LABEL, STATUS_ORDER, TRIGGER_LABEL,
} from "@/features/support/labels";
import { Permission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { Slice, SupportDashboard, TicketCategory, TicketChannel, TicketPriority, TicketStatus } from "@/types/support";
import { formatDate, formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Support dashboard" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;
const SNAPSHOT = new Set(["open", "unassigned", "breachedOpen", "atRisk", "escalatedOpen", "waiting"]);
/** The API's age bands for open tickets. */
const AGING = [["Under 1 day", "Under a day"], ["1-3 days", "1–3 days"], ["3-7 days", "3–7 days"], ["Over 7 days", "Over 7 days"]] as const;
const dayLabel = new Intl.DateTimeFormat("en-GB", { day: "numeric", timeZone: "UTC" });
const dayTitle = new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

/** Slices in a fixed order (lifecycle, severity), each labelled, so lists read the same every time. */
function ordered<K extends string>(slices: Slice[], order: readonly K[], label: (k: K) => string) {
  return order.map((k) => ({ label: label(k), value: slices.find((s) => s.key === k)?.count ?? 0 }));
}

export default async function SupportDashboardPage({ searchParams }: PageProps<"/admin/support/dashboard">) {
  const denied = await deniedUnless(Permission.SUPPORT_TICKET_VIEW, Permission.SUPPORT_ANALYTICS_VIEW, Permission.REPORT_VIEW);
  if (denied) return denied;
  const params = await searchParams;
  const preset = thisMonth();
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : preset.from;
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : preset.to;
  const d = await authFetch<SupportDashboard>(`/api/v1/admin/support/dashboard?${new URLSearchParams({ from, to })}`);
  const period = d.kpis.filter((k) => !SNAPSHOT.has(k.key));
  const snapshot = d.kpis.filter((k) => SNAPSHOT.has(k.key));
  const columns = (key: "created" | "resolved" | "breaches"): ColumnDatum[] => d.trend.map((p) => {
    const date = new Date(`${p.day}T00:00:00Z`);
    return { label: dayLabel.format(date), title: dayTitle.format(date), values: { [key]: p[key] } };
  });
  const trends = [
    { key: "created", label: "Tickets opened", fill: "fill-chart-1", swatch: "bg-chart-1" },
    { key: "resolved", label: "Tickets resolved", fill: "fill-chart-3", swatch: "bg-chart-3" },
    { key: "breaches", label: "SLA breaches", fill: "fill-chart-2", swatch: "bg-chart-2" },
  ] as const;
  const otherKeys = (slices: Slice[], known: readonly string[]) => slices.filter((s) => !known.includes(s.key));

  return (
    <SupportShell active="dashboard">
      <PageHeader title="Support dashboard"
        description="Live from tickets, SLA records, escalations and customer ratings. Select a figure to open the tickets behind it." />
      <PeriodForm action="/admin/support/dashboard" from={from} to={to} />

      <section aria-labelledby="now-heading" className="space-y-3">
        <h2 id="now-heading" className="text-lg font-semibold tracking-tight">As things stand</h2>
        <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-6">
          {snapshot.map((k) => (
            <Link key={k.key} href={k.drill ? `/admin/support?${k.drill}` : "/admin/support"}
              className="block space-y-1 rounded-xl border border-border bg-surface p-4 shadow-sm hover:border-foreground/40">
              <p className="text-sm text-muted">{k.label}</p>
              <p className="font-display text-2xl font-semibold tabular-nums">{kpiValue(k)}</p>
            </Link>
          ))}
        </div>
      </section>

      <section aria-labelledby="period-heading" className="space-y-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="period-heading" className="text-lg font-semibold tracking-tight">{formatDate(d.from)} – {formatDate(d.to)}</h2>
          <p className="text-xs text-muted">Compared with {formatDate(d.previousFrom)} – {formatDate(d.previousTo)}</p>
        </div>
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {period.map((k) => <SupportKpiCard key={k.key} kpi={{ ...k, drill: k.drill ? `/admin/support?${k.drill}` : null }} />)}
        </div>
        <p className="text-xs text-muted">Response and resolution times count business minutes for business-hours policies; time waiting on the customer is excluded.</p>
      </section>

      <section aria-labelledby="trend-heading" className="space-y-3">
        <h2 id="trend-heading" className="text-lg font-semibold tracking-tight">Day by day</h2>
        <div className="grid gap-4 xl:grid-cols-3">
          {trends.map((t) => (
            <Card key={t.key} title={t.label}>
              <ColumnChart label={`${t.label} per day`} series={[t]} data={columns(t.key)} />
            </Card>
          ))}
        </div>
      </section>

      <section aria-labelledby="mix-heading" className="space-y-3">
        <h2 id="mix-heading" className="text-lg font-semibold tracking-tight">Ticket mix</h2>
        <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
          <Card title="Open by status"><BarList label="Open tickets by status"
            items={ordered(d.byStatus, STATUS_ORDER.filter((s) => s !== "RESOLVED" && s !== "CLOSED"), (k: TicketStatus) => STAFF_STATUS_LABEL[k])} /></Card>
          <Card title="Opened in the period by priority"><BarList label="Tickets by priority"
            items={ordered(d.byPriority, PRIORITY_ORDER, (k: TicketPriority) => PRIORITY_LABEL[k])} /></Card>
          <Card title="Open by age" description="Since the ticket was opened"><BarList label="Open tickets by age"
            items={AGING.map(([k, l]) => ({ label: l, value: d.aging.find((s) => s.key === k)?.count ?? 0 }))} /></Card>
          <Card title="Opened in the period by category">
            {d.byCategory.length === 0 ? <p className="text-sm text-muted">No tickets opened in the period.</p> : (
              <BarList label="Tickets by category"
                items={ordered(d.byCategory, CATEGORY_ORDER, (k: TicketCategory) => CATEGORY_LABEL[k]).filter((i) => i.value > 0)} />
            )}
          </Card>
          <Card title="Opened in the period by channel"><BarList label="Tickets by channel"
            items={[...ordered(d.byChannel, ["PORTAL", "STAFF", "SYSTEM", "EMAIL"] as const, (k: TicketChannel) => CHANNEL_LABEL[k]),
              ...otherKeys(d.byChannel, ["PORTAL", "STAFF", "SYSTEM", "EMAIL"]).map((s) => ({ label: s.key, value: s.count }))]} /></Card>
          <Card title="Ratings in the period"><BarList label="Customer ratings"
            items={[5, 4, 3, 2, 1].map((n) => ({ label: `${n} / 5`, value: d.ratings.find((s) => s.key === String(n))?.count ?? 0 }))} /></Card>
        </div>
      </section>

      <section aria-labelledby="team-heading" className="space-y-3">
        <h2 id="team-heading" className="text-lg font-semibold tracking-tight">Agent workload</h2>
        {d.workload.length === 0 ? <p className="text-sm text-muted">No support or finance agents yet.</p> : (
          <DataTable columns={["Agent", "Open", "In progress", "Waiting", "Breached", "Resolved (7 days)"]}>
            {d.workload.map((w) => (
              <tr key={w.agentId}>
                <Cell>{w.name}<span className="block text-xs text-muted">{w.teams.map((t) => (t === "FINANCE" ? "Finance" : "Support")).join(", ")}</span></Cell>
                <Cell className="tabular-nums">{w.open}</Cell>
                <Cell className="tabular-nums">{w.inProgress}</Cell>
                <Cell className="tabular-nums">{w.waiting}</Cell>
                <Cell className="tabular-nums">{w.breached > 0 ? <StatusBadge tone="danger">{w.breached}</StatusBadge> : 0}</Cell>
                <Cell className="tabular-nums">{w.resolvedRecently}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card title="Recent escalations">
          {d.recentEscalations.length === 0 ? <p className="text-sm text-muted">No escalations in the period.</p> : (
            <ul className="divide-y divide-border text-sm">
              {d.recentEscalations.map((e) => (
                <li key={e.id} className="py-2">
                  <Link href={`/admin/support/${e.ticketId}`} className="font-medium text-gold-text hover:underline">{e.subject}</Link>
                  <span className="block text-xs text-muted"><span className="font-mono">{e.reference}</span> · L{e.level} · {TRIGGER_LABEL[e.trigger]} · {formatDateTime(e.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title="Customer satisfaction by week">
          {d.csat.length === 0 ? <p className="text-sm text-muted">No ratings in the period.</p> : (
            <DataTable columns={["Week of", "Average", "Ratings"]}>
              {d.csat.map((w) => (
                <tr key={w.weekStart}>
                  <Cell>{formatDate(w.weekStart)}</Cell>
                  <Cell className="tabular-nums">{w.average === null ? "—" : `${w.average.toFixed(2)} / 5`}</Cell>
                  <Cell className="tabular-nums">{w.ratings}</Cell>
                </tr>
              ))}
            </DataTable>
          )}
        </Card>
      </div>
      <p className="text-xs text-muted">Dates in {d.timeZone}.</p>
    </SupportShell>
  );
}
