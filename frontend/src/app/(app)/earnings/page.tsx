import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { PAYOUT_STATUS_LABEL, PAYOUT_TONE } from "@/features/earnings/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { EarningsSummary, Payout, PayoutStatus } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Earnings" };

const VIEWS: readonly { status: PayoutStatus; label: string; sort: string }[] = [
  { status: "PAID", label: "Received", sort: "dueOn,desc" },
  { status: "SCHEDULED", label: "Upcoming", sort: "dueOn,asc" },
];

export default async function EarningsPage({ searchParams }: PageProps<"/earnings">) {
  const params = await searchParams;
  const view = VIEWS.find((v) => v.status === params.status) ?? VIEWS[0]!;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const [summary, payouts] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<PageResponse<Payout>>(`/api/v1/earnings?status=${view.status}&sort=${view.sort}&page=${page}&size=20`),
  ]);
  const totals = (list: EarningsSummary["balances"]) => (list.length === 0 ? "—" : list.map((m) => formatMoney(m)).join(" · "));

  return (
    <div className="space-y-6">
      <PageHeader title="Earnings" description="Every month each of your containers pays you rent plus part of your capital back, straight into your wallet."
        actions={<LinkButton href="/statements" variant="secondary">Statements</LinkButton>} />
      <section className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-5" aria-label="Earnings summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Wallet balance</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.balances)}</p>
          <Link href="/withdrawals" className="text-sm text-gold-text hover:underline">Withdraw to your bank account →</Link>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Rent received</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.rentPaid)}</p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Capital returned</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.capitalReturned)}</p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Referral earnings</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.referralEarned)}</p>
          <Link href="/referrals" className="text-sm text-gold-text hover:underline">By month and level →</Link>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Next payout</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{summary.nextPayout ? formatMoney(summary.nextPayout.total) : "—"}</p>
          {summary.nextPayout && <p className="text-xs text-muted">on {formatDate(summary.nextPayout.dueOn)}</p>}
        </div>
      </section>

      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.status} href={`/earnings?status=${v.status}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      {payouts.content.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title={view.status === "PAID" ? "No payouts yet" : "Nothing scheduled"}
            description="Each container you buy pays you every month for its lease, starting one month after your payment is confirmed." />
          <LinkButton href="/portfolio" variant="secondary">View portfolio</LinkButton>
        </div>
      ) : (
        <>
          <DataTable columns={[view.status === "PAID" ? "Paid" : "Due", "Container", "Payout", "Rent", "Capital back", "Total", "Status"]}>
            {payouts.content.map((p) => (
              <tr key={p.id} className="hover:bg-background">
                <Cell className="text-muted">{formatDate(p.paidAt ?? p.dueOn)}</Cell>
                <Cell>
                  <span className="font-mono">{p.containerNumber}</span>
                  <span className="block text-xs text-muted">{p.productCode}</span>
                </Cell>
                <Cell className="tabular-nums">{p.installmentNumber} of {p.installmentCount}</Cell>
                <Cell className="tabular-nums">{formatMoney(p.rent)}</Cell>
                <Cell className="tabular-nums">{formatMoney(p.capital)}</Cell>
                <Cell className="tabular-nums font-medium">{formatMoney(p.total)}</Cell>
                <Cell><StatusBadge tone={PAYOUT_TONE[p.status]}>{PAYOUT_STATUS_LABEL[p.status]}</StatusBadge></Cell>
              </tr>
            ))}
          </DataTable>
          <Pagination page={payouts} basePath="/earnings" params={{ status: view.status }} />
        </>
      )}
    </div>
  );
}
