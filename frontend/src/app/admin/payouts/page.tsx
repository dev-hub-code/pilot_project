import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { runPayoutsAction } from "@/features/admin/ledger-actions";
import { ActionButton } from "@/features/orders/action-button";
import { PAYOUT_STATUS_LABEL, PAYOUT_TONE, todayUtc } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DuePayouts, Payout } from "@/types/earning";
import { formatDate, formatDateTime, formatPeriod } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payouts" };

type View = { key: string; label: string; query: Record<string, string> };

const VIEWS: readonly [View, ...View[]] = [
  { key: "due", label: "Ended", query: { status: "SCHEDULED", dueBy: "" } },
  { key: "scheduled", label: "In progress and upcoming", query: { status: "SCHEDULED", sort: "dueOn,asc" } },
  { key: "paid", label: "Fully credited", query: { status: "PAID", sort: "dueOn,desc" } },
];

export default async function PayoutsPage({ searchParams }: PageProps<"/admin/payouts">) {
  const session = await requireStaff();
  const params = await searchParams;
  const view = VIEWS.find((v) => v.key === params.view) ?? (params.status === "PAID" ? VIEWS[2]! : VIEWS[0]);
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", ...view.query });
  if (view.key === "due") query.set("dueBy", todayUtc());
  const [payouts, due] = await Promise.all([
    authFetch<PageResponse<Payout>>(`/api/v1/admin/payouts?${query}`),
    authFetch<DuePayouts[]>("/api/v1/admin/payouts/due"),
  ]);
  const dueCount = due.reduce((n, d) => n + d.count, 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Payouts"
        description="Every container's rent is credited to its investor's wallet every day: each day gets that month's rent ÷ its days. Each row is one calendar month of a lease (a part month at each end); it is fully credited when the month ends, and referral commission on it is paid then. Containers bought before plans became rent-only also get part of the price back, the same way." />

      <Card title="Rent not yet credited" description="Days that have ended are credited automatically every hour.">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className="text-sm">
            {dueCount === 0
              ? "Nothing is waiting: every day of rent so far has been credited."
              : due.map((d) => `${d.count} month(s) · ${formatMoney(d.total)}`).join(" · ")}
          </p>
          {dueCount > 0 && hasPermission(session.permissions, Permission.PAYOUT_PROCESS) && (
            <ActionButton action={runPayoutsAction} label="Credit rent now" pendingLabel="Crediting…"
              confirm="Credit every day of rent that has ended to investors' wallets now?" />
          )}
        </div>
      </Card>

      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/payouts?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      {payouts.content.length === 0 ? (
        <EmptyState title="No payouts here" />
      ) : (
        <DataTable columns={["Due", "Investor", "Plan", "Container", "Payout", "Period", "Rent", "Capital", "Total", "Status"]}>
          {payouts.content.map((p) => (
            <tr key={p.id}>
              <Cell className="text-muted">{formatDate(p.dueOn)}</Cell>
              <Cell>{p.userId && <Link href={`/admin/users/${p.userId}`} className="text-gold-text hover:underline">View</Link>}</Cell>
              <Cell className="font-mono text-xs">{p.productCode}</Cell>
              <Cell className="font-mono text-xs">{p.containerNumber}</Cell>
              <Cell className="tabular-nums">{p.installmentNumber} of {p.installmentCount}</Cell>
              <Cell className="whitespace-nowrap text-xs text-muted">
                {formatPeriod(p.periodStart, p.periodEnd)}
                {p.monthShare < 1 && <span className="block">Part month</span>}
              </Cell>
              <Cell className="tabular-nums">{formatMoney(p.rent)}</Cell>
              <Cell className="tabular-nums">{formatMoney(p.capital)}</Cell>
              <Cell className="tabular-nums font-medium">{formatMoney(p.total)}</Cell>
              <Cell>
                <StatusBadge tone={PAYOUT_TONE[p.status]}>{PAYOUT_STATUS_LABEL[p.status]}</StatusBadge>
                {p.paidAt && <span className="block text-xs text-muted">{formatDateTime(p.paidAt)}</span>}
              </Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={payouts} basePath="/admin/payouts" params={{ view: view.key }} />
    </div>
  );
}
