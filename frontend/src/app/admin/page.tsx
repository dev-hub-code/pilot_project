import type { Metadata } from "next";
import Link from "next/link";
import { BarList } from "@/components/charts/bar-list";
import { ColumnChart, type ColumnSeries } from "@/components/charts/column-chart";
import { Card } from "@/components/ui/card";
import { DataTable, Cell } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { monthColumns } from "@/features/dashboard/months";
import { CURRENCY } from "@/lib/currency";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";
import type { DashboardCharts, KpiTile } from "@/types/report";
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

  const [tiles, charts, pendingKyc, pendingBanks, roles, toAssign] = await Promise.all([
    authFetch<KpiTile[]>("/api/v1/admin/dashboard"),
    authFetch<DashboardCharts>("/api/v1/admin/dashboard/charts"),
    can(Permission.KYC_REVIEW) ? authFetch<PageResponse<unknown>>("/api/v1/admin/kyc?size=1") : null,
    can(Permission.BANK_ACCOUNT_VERIFY) ? authFetch<PageResponse<unknown>>("/api/v1/admin/bank-accounts?size=1") : null,
    can(Permission.ROLE_VIEW) ? authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=50") : null,
    can(Permission.CONTAINER_ASSIGN)
      ? authFetch<{ pending: PageResponse<unknown> }>("/api/v1/admin/container-assignments?size=1").then((q) => q.pending)
      : null,
  ]);

  return (
    <div className="space-y-8">
      <PageHeader title="Administration" description={`Signed in as ${session.roles.join(", ")}`} />

      {tiles.length > 0 && (
        <section className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-4" aria-label="Key figures">
          {tiles.map((tile) => (
            <Link key={tile.key} href={tile.link} className="space-y-2 bg-surface p-5 hover:bg-background">
              <p className="text-xs uppercase tracking-[0.1em] text-muted">{tile.label}</p>
              {tile.values.map((value) => (
                <p key={value} className="font-display text-2xl font-semibold tabular-nums">{value}</p>
              ))}
              <p className="text-xs text-muted">{tile.note}</p>
            </Link>
          ))}
        </section>
      )}

      {(charts.payouts || charts.pipeline) && (
        <section className="grid gap-6 lg:grid-cols-3" aria-label="Payouts and leads">
          {charts.payouts && (
            <Card title="Paid to investors" description="Each month, by source" className="min-w-0 lg:col-span-2">
              {charts.payouts.length === 0 ? (
                <EmptyState title="Nothing credited yet" description="Rent and commission credited to investors appear here by month." />
              ) : (
                <ColumnChart series={PAYOUT_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Paid to investors by month"
                  data={monthColumns(charts.payouts, PAST, 0, CURRENCY, (m) => ({
                    rent: m.rent, capital: m.capital, referral: m.referral,
                  }))} />
              )}
            </Card>
          )}
          {charts.pipeline && (
            <Card title="Lead pipeline" description="Leads you can see, by stage"
              className={charts.payouts ? "min-w-0" : "min-w-0 lg:col-span-3"}>
              <BarList label="Leads by stage" fill="fill-chart-2"
                items={charts.pipeline.map((p) => ({ label: humanize(p.stage), value: p.leads }))} />
            </Card>
          )}
        </section>
      )}

      {(charts.invested || charts.investors) && (
        <section className="grid gap-6 lg:grid-cols-2" aria-label="Growth">
          {charts.invested && (
            <Card title="Capital invested" description="Containers confirmed each month" className="min-w-0">
              {charts.invested.length === 0 ? (
                <EmptyState title="No investments yet" description="Capital appears here once orders are paid." />
              ) : (
                <ColumnChart series={INVESTED_SERIES} currency={CURRENCY} currentIndex={-PAST} label="Capital invested by month"
                  data={monthColumns(charts.invested, PAST, 0, CURRENCY, (m) => ({ amount: m.amount }))} />
              )}
            </Card>
          )}
          {charts.investors && (
            <Card title="New investors" description="Accounts registered each month" className="min-w-0">
              {charts.investors.length === 0 ? (
                <EmptyState title="No new investors" description="Nobody has registered in the last twelve months." />
              ) : (
                <ColumnChart series={INVESTOR_SERIES} currentIndex={-PAST} label="New investors by month"
                  data={monthColumns(charts.investors, PAST, 0, CURRENCY, (m) => ({ count: m.count }))} />
              )}
            </Card>
          )}
        </section>
      )}

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Work queues">
        {pendingKyc && <QueueCard href="/admin/kyc" label="KYC awaiting review" count={pendingKyc.totalElements} />}
        {pendingBanks && (
          <QueueCard href="/admin/bank-accounts" label="Bank accounts to verify" count={pendingBanks.totalElements} />
        )}
        {toAssign && (
          <QueueCard href="/admin/container-assignments" label="Paid investments awaiting a container" count={toAssign.totalElements} />
        )}
      </section>

      {roles && (
        <section className="space-y-3">
          <h2 className="font-medium">Roles &amp; permissions</h2>
          <DataTable columns={["Role", "Description", "Permissions", "Type"]}>
            {roles.content.map((role) => (
              <tr key={role.id}>
                <Cell className="font-mono text-xs font-medium">{role.name}</Cell>
                <Cell className="text-muted">{role.description}</Cell>
                <Cell className="tabular-nums">{role.permissions.length}</Cell>
                <Cell>
                  <StatusBadge tone={role.system ? "neutral" : "success"}>{role.system ? "System" : "Custom"}</StatusBadge>
                </Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}
    </div>
  );
}

function QueueCard({ href, label, count }: { href: string; label: string; count: number }) {
  return (
    <Link href={href} className="border border-border bg-surface p-6 hover:border-brand">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-2 text-3xl font-semibold tabular-nums">{count}</p>
    </Link>
  );
}
