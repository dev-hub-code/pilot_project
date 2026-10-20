import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import { runPayoutsAction } from "@/features/admin/ledger-actions";
import { RentCreditCard } from "@/features/admin/rent-credit-card";
import { ActionButton } from "@/features/orders/action-button";
import { LEASE_STATUS_LABEL, LEASE_TONE, leaseNote, leasePercent } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DuePayouts, LeaseRent, LeaseRentStatus } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { getRentCredit } from "@/lib/server/rent-credit";

export const metadata: Metadata = { title: "Rent" };

const VIEWS: readonly { status: LeaseRentStatus; label: string }[] = [
  { status: "RUNNING", label: "Earning now" },
  { status: "COMPLETED", label: "Completed" },
];

export default async function PayoutsPage({ searchParams }: PageProps<"/admin/payouts">) {
  const session = await requireStaff();
  const params = await searchParams;
  const view = VIEWS.find((v) => v.status === params.status) ?? VIEWS[0]!;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", status: view.status });
  const [leases, due, credit] = await Promise.all([
    authFetch<PageResponse<LeaseRent>>(`/api/v1/admin/payouts?${query}`),
    authFetch<DuePayouts[]>("/api/v1/admin/payouts/due"),
    getRentCredit(),
  ]);
  const dueCount = due.reduce((n, d) => n + d.count, 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Rent"
        description={`Every container's rent is credited to its investor's wallet ${credit.label} of its lease, from the full hour after the investor accepts its agreement: the monthly rent × the lease months elapsed, so the whole lease pays exactly monthly rent × tenure. Referral commission on it is credited with it. Containers bought before plans became rent-only also get part of the price back, the same way.`} />

      {(hasPermission(session.permissions, Permission.PAYOUT_PROCESS)
        || hasPermission(session.permissions, Permission.SYSTEM_SETTINGS_MANAGE)
        || hasPermission(session.permissions, Permission.FINANCE_VIEW)) && <RentCreditCard permissions={session.permissions} />}

      <Card title="Rent not yet credited" description="Each interval that has ended is credited automatically within a minute or so.">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <p className="text-sm">
            {dueCount === 0
              ? "Nothing is waiting: all rent due so far has been credited."
              : due.map((d) => `${d.count} lease(s) behind · ${formatMoney(d.total)}`).join(" · ")}
          </p>
          {dueCount > 0 && hasPermission(session.permissions, Permission.PAYOUT_PROCESS) && (
            <ActionButton action={runPayoutsAction} label="Credit rent now" pendingLabel="Crediting…"
              confirm="Credit all rent due so far to investors' wallets now?" />
          )}
        </div>
      </Card>

      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.status} href={`/admin/payouts?status=${v.status}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      {leases.content.length === 0 ? (
        <EmptyState title={view.status === "RUNNING" ? "No leases earning rent" : "No completed leases"} />
      ) : (
        <DataTable columns={["Investor", "Listing", "Container", "Lease", "Monthly rent", "Rent credited", "Capital back", "Progress", "Status"]}>
          {leases.content.map((l) => (
            <tr key={l.holdingId} className="align-top">
              <Cell>{l.userId && <Link href={`/admin/users/${l.userId}`} className="text-gold-text hover:underline">View</Link>}</Cell>
              <Cell className="font-mono text-xs">{l.productCode}</Cell>
              <Cell className="font-mono text-xs">{l.containerNumber}</Cell>
              <Cell className="whitespace-nowrap text-xs text-muted">
                {formatDate(l.startsAt)} – {formatDate(l.endsAt)}
                <span className="block">{l.tenureMonths} months</span>
              </Cell>
              <Cell className="tabular-nums">{formatMoney(l.monthlyRent)}</Cell>
              <Cell className="tabular-nums">
                <span className="font-medium">{formatMoney(l.rentCredited)}</span>
                <span className="block text-xs text-muted">of {formatMoney(l.totalRent)}</span>
              </Cell>
              <Cell className="tabular-nums">
                {formatMoney(l.capitalCredited)}
                <span className="block text-xs text-muted">of {formatMoney(l.capitalTotal)}</span>
              </Cell>
              <Cell className="min-w-36">
                <ProgressBar percent={leasePercent(l)} label={`${l.containerNumber ?? "Container"} lease credited`} />
                <span className="mt-1 block text-xs text-muted">
                  {leaseNote(l)}
                </span>
              </Cell>
              <Cell><StatusBadge tone={LEASE_TONE[l.status]}>{LEASE_STATUS_LABEL[l.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={leases} basePath="/admin/payouts" params={{ status: view.status }} />
    </div>
  );
}
