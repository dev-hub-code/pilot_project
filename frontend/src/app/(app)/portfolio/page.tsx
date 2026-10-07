import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatPeriod } from "@/features/earnings/labels";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { EarningsSummary } from "@/types/earning";
import type { Portfolio } from "@/types/order";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Portfolio" };

export default async function PortfolioPage() {
  const [portfolio, earnings] = await Promise.all([
    authFetch<Portfolio>("/api/v1/portfolio"),
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
  ]);
  const progress = new Map(earnings.holdings.map((h) => [h.holdingId, h]));

  return (
    <div className="space-y-6">
      <PageHeader title="Portfolio" description="The containers you own, their leases and their monthly payouts." />
      <section className="grid gap-px bg-border sm:grid-cols-3" aria-label="Portfolio summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Total invested</p>
          <p className="font-display text-4xl font-semibold tabular-nums">
            {portfolio.totalsByCurrency.length === 0 ? "—" : portfolio.totalsByCurrency.map((m) => formatMoney(m)).join(" · ")}
          </p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Containers on lease</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{portfolio.activeHoldings}</p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Next payout</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{earnings.nextPayout ? formatMoney(earnings.nextPayout.total) : "—"}</p>
          {earnings.nextPayout && <p className="text-xs text-muted">on {formatDate(earnings.nextPayout.dueOn)}</p>}
        </div>
      </section>

      {portfolio.holdings.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title="No containers yet" description="Containers are assigned to you here once your payment is confirmed." />
          <LinkButton href="/marketplace">Browse the marketplace</LinkButton>
        </div>
      ) : (
        <DataTable columns={["Container", "Plan", "Invested", "Paid monthly", "Lease", "Payouts received", "Status"]}>
          {portfolio.holdings.map((h) => {
            const p = progress.get(h.id);
            return (
              <tr key={h.id} className="hover:bg-background">
                <Cell>
                  <span className="font-mono font-medium">{h.container.containerNumber}</span>
                  <span className="block text-xs text-muted">{CONTAINER_TYPE_LABEL[h.container.containerType]} · {h.container.currentLocation}</span>
                </Cell>
                <Cell>
                  <Link href={`/marketplace/${h.productId}`} className="font-mono text-gold-text hover:underline">{h.productCode}</Link>
                  <span className="block max-w-xs truncate text-xs text-muted">{h.productTitle}</span>
                </Cell>
                <Cell className="tabular-nums">{formatMoney(h.amount)}</Cell>
                <Cell className="tabular-nums">
                  {formatMoney(h.monthlyPayout)}
                  <span className="block text-xs text-muted">{formatMoney(h.monthlyRent)} rent + {formatMoney(h.monthlyCapitalReturn)} capital</span>
                </Cell>
                <Cell>
                  {h.tenureMonths} months
                  <span className="block text-xs text-muted">{formatPeriod(h.leaseStartsOn, h.leaseEndsOn)}</span>
                </Cell>
                <Cell className="tabular-nums">
                  {p ? `${p.paid} of ${p.installments}` : "—"}
                  {p && <span className="block text-xs text-muted">{formatMoney(p.received)} received</span>}
                </Cell>
                <Cell>
                  <StatusBadge tone={h.status === "ACTIVE" ? "success" : "neutral"}>{h.status === "ACTIVE" ? "On lease" : "Lease completed"}</StatusBadge>
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <p className="text-xs text-muted">
        Payouts are credited to your wallet every month, starting one month after your payment was confirmed. See{" "}
        <Link href="/earnings" className="underline">Earnings</Link> for the full schedule.
      </p>
    </div>
  );
}
