import type { Metadata } from "next";
import Link from "next/link";
import { BarList } from "@/components/charts/bar-list";
import { ColumnChart, type ColumnSeries } from "@/components/charts/column-chart";
import { Card } from "@/components/ui/card";
import { DataTable, Cell } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { DashboardBoard, type Widget } from "@/features/dashboard/board";
import { resetDashboardLayoutAction, saveDashboardLayoutAction } from "@/features/dashboard/layout-actions";
import { monthColumns } from "@/features/dashboard/months";
import { PRIORITY_TONE } from "@/features/support/labels";
import { CURRENCY } from "@/lib/currency";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";
import type { DashboardCharts, DashboardLayout, KpiTile, PriorityRow, Workload, WorkloadCounts } from "@/types/report";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Admin" };

/** The charts cover the last twelve months, this one included. */
const PAST = -11;

const PAYOUT_SERIES: readonly ColumnSeries[] = [
  { key: "rent", label: "Rent", fill: "fill-chart-1", swatch: "bg-chart-1" },
  { key: "capital", label: "Capital returned", fill: "fill-chart-2", swatch: "bg-chart-2" },
  { key: "referral", label: "Referral commission", fill: "fill-chart-3", swatch: "bg-chart-3" },
];
const INVESTED_SERIES: readonly ColumnSeries[] = [
  { key: "amount", label: "Capital invested", fill: "fill-chart-1", swatch: "bg-chart-1" },
];
const INVESTOR_SERIES: readonly ColumnSeries[] = [
  { key: "count", label: "New investors", fill: "fill-chart-2", swatch: "bg-chart-2" },
];

export default async function AdminOverviewPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);

  const [tiles, charts, pendingKyc, pendingBanks, roles, toAssign, toConfirm, workload, layout] = await Promise.all([
    authFetch<KpiTile[]>("/api/v1/admin/dashboard"),
    authFetch<DashboardCharts>("/api/v1/admin/dashboard/charts"),
    can(Permission.KYC_REVIEW) ? authFetch<PageResponse<unknown>>("/api/v1/admin/kyc?size=1") : null,
    can(Permission.BANK_ACCOUNT_VERIFY) ? authFetch<PageResponse<unknown>>("/api/v1/admin/bank-accounts?size=1") : null,
    can(Permission.ROLE_VIEW) ? authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=50") : null,
    can(Permission.CONTAINER_ASSIGN)
      ? authFetch<{ pending: PageResponse<unknown> }>("/api/v1/admin/container-assignments?size=1").then((q) => q.pending)
      : null,
    can(Permission.PAYMENT_CONFIRM) ? authFetch<PageResponse<unknown>>("/api/v1/admin/payments?status=PENDING&size=1") : null,
    authFetch<Workload>("/api/v1/admin/dashboard/workload"),
    authFetch<DashboardLayout>("/api/v1/admin/dashboard/layout"),
  ]);

  // The cards the viewer's roles show (an admin chooses them per role); all of them until one is chosen.
  const allowed = new Set(layout.allowed ?? []);
  const widgets: Widget[] = [];
  const add = (key: string, title: string, span: Widget["span"], node: React.ReactNode) =>
    widgets.push({ key, title, span, node });
  const workloadCards = (prefix: string, group: string, counts: WorkloadCounts, noun: string,
    links: { pending: string; mine: string; done: string }) => {
    add(`${prefix}-pending`, `${group}: all pending`, 1, <QueueCard href={links.pending} group={group}
      label={`All pending ${noun}`} count={counts.pending}
      note={counts.overdue === null ? undefined : `${counts.overdue} overdue`} />);
    add(`${prefix}-mine`, `${group}: assigned to me`, 1, <QueueCard href={links.mine} group={group}
      label="Assigned to me" count={counts.assignedToMe} note="Pending, assigned to you" />);
    add(`${prefix}-today`, `${group}: completed today`, 1, <QueueCard href={links.done} group={group}
      label="Completed today" count={counts.doneToday} note={`${counts.doneTodayByMe} by you`} />);
    add(`${prefix}-month`, `${group}: completed by me this month`, 1, <QueueCard href={links.done} group={group}
      label="Completed by me this month" count={counts.doneThisMonthByMe} note={`${counts.doneThisMonth} by everyone`} />);
  };

  if (workload.supportTickets) {
    workloadCards("support", "Support tickets", workload.supportTickets, "tickets",
      { pending: "/admin/support", mine: "/admin/support?view=mine", done: "/admin/support?view=resolved" });
  }
  if (workload.supportByPriority) {
    add("support-priority", "Support: pending tickets by priority", 4, <PriorityTable rows={workload.supportByPriority} />);
  }
  if (workload.financeTasks) {
    workloadCards("finance-task", "Finance tasks", workload.financeTasks, "tasks",
      { pending: "/admin/finance-tasks", mine: "/admin/finance-tasks", done: "/admin/finance-tasks?view=DONE" });
  }
  if (workload.financeTickets) {
    workloadCards("finance-ticket", "Tickets handed to finance", workload.financeTickets, "tickets",
      { pending: "/admin/support?view=finance", mine: "/admin/support?view=mine", done: "/admin/support?view=resolved" });
  }
  for (const tile of tiles) {
    add(`kpi-${tile.key}`, tile.label, 1, (
      <Link href={tile.link} className="block h-full space-y-2 border border-border bg-surface p-5 hover:border-brand">
        <p className="text-xs uppercase tracking-[0.1em] text-muted">{tile.label}</p>
        {tile.values.map((value) => (
          <p key={value} className="font-display text-2xl font-semibold tabular-nums">{value}</p>
        ))}
        <p className="text-xs text-muted">{tile.note}</p>
      </Link>
    ));
  }
  if (charts.payouts) {
    add("chart-payouts", "Chart: paid to investors", 2, (
      <Card title="Paid to investors" description="Each month, by source" className="h-full min-w-0">
        {charts.payouts.length === 0 ? (
          <EmptyState title="Nothing credited yet" description="Rent and commission credited to investors appear here by month." />
        ) : (
          <ColumnChart series={PAYOUT_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Paid to investors by month"
            data={monthColumns(charts.payouts, PAST, 0, CURRENCY, (m) => ({
              rent: m.rent, capital: m.capital, referral: m.referral,
            }))} />
        )}
      </Card>
    ));
  }
  if (charts.pipeline) {
    add("chart-pipeline", "Chart: lead pipeline", 2, (
      <Card title="Lead pipeline" description="Leads you can see, by stage" className="h-full min-w-0">
        <BarList label="Leads by stage" fill="fill-chart-2"
          items={charts.pipeline.map((p) => ({ label: humanize(p.stage), value: p.leads }))} />
      </Card>
    ));
  }
  if (charts.invested) {
    add("chart-invested", "Chart: capital invested", 2, (
      <Card title="Capital invested" description="Containers confirmed each month" className="h-full min-w-0">
        {charts.invested.length === 0 ? (
          <EmptyState title="No investments yet" description="Capital appears here once orders are paid." />
        ) : (
          <ColumnChart series={INVESTED_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Capital invested by month"
            data={monthColumns(charts.invested, PAST, 0, CURRENCY, (m) => ({ amount: m.amount }))} />
        )}
      </Card>
    ));
  }
  if (charts.investors) {
    add("chart-investors", "Chart: new investors", 2, (
      <Card title="New investors" description="Accounts registered each month" className="h-full min-w-0">
        {charts.investors.length === 0 ? (
          <EmptyState title="No new investors" description="Nobody has registered in the last twelve months." />
        ) : (
          <ColumnChart series={INVESTOR_SERIES} currentIndex={-PAST} label="New investors by month"
            data={monthColumns(charts.investors, PAST, 0, CURRENCY, (m) => ({ count: m.count }))} />
        )}
      </Card>
    ));
  }
  if (pendingKyc) {
    add("queue-kyc", "KYC awaiting review", 1,
      <QueueCard href="/admin/kyc" label="KYC awaiting review" count={pendingKyc.totalElements} />);
  }
  if (pendingBanks) {
    add("queue-banks", "Bank accounts to verify", 1,
      <QueueCard href="/admin/bank-accounts" label="Bank accounts to verify" count={pendingBanks.totalElements} />);
  }
  if (toConfirm) {
    add("queue-payments", "Payments to confirm", 1,
      <QueueCard href="/admin/payments" label="Payments to confirm" count={toConfirm.totalElements} />);
  }
  if (toAssign) {
    add("queue-containers", "Paid investments awaiting a container", 1,
      <QueueCard href="/admin/container-assignments" label="Paid investments awaiting a container" count={toAssign.totalElements} />);
  }
  if (roles) {
    add("roles", "Roles & permissions", 4, (
      <section className="space-y-3">
        <h2 className="font-medium">Roles &amp; permissions</h2>
        <DataTable columns={["Role", "Description", "Permissions", "Type"]}>
          {roles.content.map((role) => (
            <tr key={role.id}>
              <Cell className="font-mono text-xs font-medium">
                <Link href={`/admin/roles/${role.id}`} className="text-gold-text hover:underline">{role.name}</Link>
              </Cell>
              <Cell className="text-muted">{role.description}</Cell>
              <Cell className="tabular-nums">{role.permissions.length}</Cell>
              <Cell>
                <StatusBadge tone={role.system ? "neutral" : "success"}>{role.system ? "System" : "Custom"}</StatusBadge>
              </Cell>
            </tr>
          ))}
        </DataTable>
      </section>
    ));
  }

  return (
    <div className="space-y-8">
      <PageHeader title="Administration" description={`Signed in as ${session.roles.join(", ")}`} />
      <DashboardBoard widgets={layout.allowed ? widgets.filter((w) => allowed.has(w.key)) : widgets} layout={layout} saveAction={saveDashboardLayoutAction}
        resetAction={resetDashboardLayoutAction} />
    </div>
  );
}

function QueueCard({ href, label, count, note, group }: {
  href: string;
  label: string;
  count: number;
  note?: string;
  /** The queue the card belongs to, shown above its label. */
  group?: string;
}) {
  return (
    <Link href={href} className="block h-full border border-border bg-surface p-6 hover:border-brand">
      {group && <p className="text-xs uppercase tracking-[0.1em] text-muted">{group}</p>}
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-2 text-3xl font-semibold tabular-nums">{count}</p>
      {note && <p className="mt-1 text-xs text-muted">{note}</p>}
    </Link>
  );
}

/** Pending support tickets by priority, most urgent first; each count opens the matching tickets. */
function PriorityTable({ rows }: { rows: PriorityRow[] }) {
  const count = (value: number, view: string, priority: string) => value === 0
    ? <span className="text-muted">0</span>
    : <Link href={`/admin/support?view=${view}&priority=${priority}`} className="font-medium text-gold-text hover:underline">{value}</Link>;
  return (
    <section className="space-y-3" aria-label="Support tickets by priority">
      <h2 className="font-medium">Pending tickets by priority</h2>
      <DataTable columns={["Priority", "Pending", "Overdue", "Unassigned", "Assigned to me"]}>
        {rows.map((r) => (
          <tr key={r.priority}>
            <Cell><StatusBadge tone={PRIORITY_TONE[r.priority]}>{humanize(r.priority)}</StatusBadge></Cell>
            <Cell className="tabular-nums">{count(r.pending, "queue", r.priority)}</Cell>
            <Cell className="tabular-nums">{count(r.overdue, "overdue", r.priority)}</Cell>
            <Cell className="tabular-nums">{count(r.unassigned, "unassigned", r.priority)}</Cell>
            <Cell className="tabular-nums">{count(r.assignedToMe, "mine", r.priority)}</Cell>
          </tr>
        ))}
      </DataTable>
    </section>
  );
}
