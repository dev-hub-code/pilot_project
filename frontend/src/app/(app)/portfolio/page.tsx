import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { formatPeriod } from "@/features/earnings/labels";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { EarningsSummary } from "@/types/earning";
import type { HoldingStatus, Portfolio } from "@/types/order";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Portfolio" };

const HOLDING_LABEL: Record<HoldingStatus, string> = {
  AWAITING_CONTAINER: "Awaiting container",
  ACTIVE: "On lease",
  MATURED: "Lease completed",
};

const HOLDING_TONE: Record<HoldingStatus, StatusTone> = {
  AWAITING_CONTAINER: "warning",
  ACTIVE: "success",
  MATURED: "neutral",
};

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
          <EmptyState title="No containers yet" description="Your investments appear here once your payment is confirmed. Our team then assigns each its container." />
          <LinkButton href="/marketplace">Browse the marketplace</LinkButton>
        </div>
      ) : (
        <DataTable columns={["Container", "Plan", "Invested", "Paid monthly", "Lease", "Payouts received", "Status"]}>
          {portfolio.holdings.map((h) => {
            const p = progress.get(h.id);
            return (
              <tr key={h.id} className="hover:bg-background">
                <Cell>
                  {h.container ? (
                    <>
                      <span className="font-mono font-medium">{h.container.containerNumber}</span>
                      <span className="block text-xs text-muted">{CONTAINER_TYPE_LABEL[h.container.containerType]}{h.container.currentLocation ? ` · ${h.container.currentLocation}` : ""}</span>
                    </>
                  ) : (
                    <>
                      <span className="text-muted">Being assigned</span>
                      <span className="block text-xs text-muted">Our team assigns your container</span>
                    </>
                  )}
                </Cell>
                <Cell>
                  <Link href={`/marketplace/${h.productId}`} className="font-mono text-gold-text hover:underline">{h.productCode}</Link>
                  <span className="block max-w-xs truncate text-xs text-muted">{h.productTitle}</span>
                </Cell>
                <Cell className="tabular-nums">{formatMoney(h.amount)}</Cell>
                <Cell className="tabular-nums">
                  {formatMoney(h.monthlyPayout)}
                  {Number(h.monthlyCapitalReturn.amount) > 0 && (
                    <span className="block text-xs text-muted">{formatMoney(h.monthlyRent)} rent + {formatMoney(h.monthlyCapitalReturn)} capital</span>
                  )}
                </Cell>
                <Cell>
                  {h.tenureMonths} months
                  <span className="block text-xs text-muted">
                    {h.leaseStartsOn && h.leaseEndsOn ? formatPeriod(h.leaseStartsOn, h.leaseEndsOn) : "Starts when your container is assigned"}
                  </span>
                </Cell>
                <Cell className="tabular-nums">
                  {p ? `${p.paid} of ${p.installments}` : "—"}
                  {p && <span className="block text-xs text-muted">{formatMoney(p.received)} received</span>}
                </Cell>
                <Cell>
                  <StatusBadge tone={HOLDING_TONE[h.status]}>{HOLDING_LABEL[h.status]}</StatusBadge>
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <p className="text-xs text-muted">
        Once your payment is confirmed, our team assigns each investment its container; the lease starts that day. Payouts are credited to your wallet on the payout day each month from then on (a part month is paid by the day). See{" "}
        <Link href="/earnings" className="underline">Earnings</Link> for the full schedule.
      </p>
    </div>
  );
}
