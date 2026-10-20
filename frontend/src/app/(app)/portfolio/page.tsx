import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { formatPeriod, leasePercent, nextCreditNote } from "@/features/earnings/labels";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { getRentCredit } from "@/lib/server/rent-credit";
import type { LeaseAgreement } from "@/types/agreement";
import type { EarningsSummary } from "@/types/earning";
import type { HoldingStatus, Portfolio } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";

import { containersOwned, holdingBreakdown } from "@/features/orders/holding-summary";

export const metadata: Metadata = { title: "Portfolio" };

const HOLDING_LABEL: Record<HoldingStatus, string> = {
  AWAITING_CONTAINER: "Awaiting container",
  AWAITING_ACCEPTANCE: "Accept agreement",
  ACTIVE: "On lease",
  MATURED: "Lease completed",
};

const HOLDING_TONE: Record<HoldingStatus, StatusTone> = {
  AWAITING_CONTAINER: "warning",
  AWAITING_ACCEPTANCE: "warning",
  ACTIVE: "success",
  MATURED: "neutral",
};

export default async function PortfolioPage() {
  const credit = await getRentCredit();
  const [portfolio, earnings, agreementList] = await Promise.all([
    authFetch<Portfolio>("/api/v1/portfolio"),
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<LeaseAgreement[]>("/api/v1/agreements"),
  ]);
  const agreements = new Map(agreementList.map((a) => [a.holdingId, a]));
  const toAccept = portfolio.holdings.filter((h) => h.status === "AWAITING_ACCEPTANCE");
  const firstToAccept = toAccept[0];
  const leases = new Map(earnings.leases.map((l) => [l.holdingId, l]));
  const rent = earnings.wallet.filter((w) => w.balance !== "REFERRAL").map((w) => w.amount);
  const rentWallet = rent.length === 0 ? "—"
    : formatMoney({ amount: rent.reduce((sum, m) => sum + Number(m.amount), 0).toFixed(2), currency: CURRENCY });

  return (
    <div className="space-y-6">
      <PageHeader title="Portfolio" description="The containers you own, their leases and the rent they earn." />
      {firstToAccept && (
        <Notice tone="warning">
          <strong>{toAccept.length === 1 ? "A lease agreement is" : `${toAccept.length} lease agreements are`} waiting for you.</strong>{" "}
          Your container{toAccept.length === 1 ? " has" : "s have"} been assigned. Rent starts once you accept the lease
          agreement: <Link href={`/portfolio/${firstToAccept.id}/agreement`} className="font-medium underline">review and accept</Link>.
        </Notice>
      )}
      <section className="grid gap-px bg-border sm:grid-cols-3" aria-label="Portfolio summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Total invested</p>
          <p className="font-display text-4xl font-semibold tabular-nums">
            {portfolio.totalsByCurrency.length === 0 ? "—" : portfolio.totalsByCurrency.map((m) => formatMoney(m)).join(" · ")}
          </p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Containers</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{containersOwned(portfolio.holdings)}</p>
          <p className="text-xs text-muted">{holdingBreakdown(portfolio.holdings) || "None yet"}</p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Rent wallet</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{rentWallet}</p>
          <p className="text-xs text-muted">{nextCreditNote(earnings.nextCreditAt, credit.label)}</p>
        </div>
      </section>

      {portfolio.holdings.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title="No containers yet" description="Your investments appear here once your payment is confirmed. Our team then assigns each its container." />
          <LinkButton href="/marketplace">Browse the marketplace</LinkButton>
        </div>
      ) : (
        <DataTable columns={["Container", "Type", "Invested", "Rent", "Paid monthly", "Lease", "Rent credited", "Status"]}>
          {portfolio.holdings.map((h) => {
            const lease = leases.get(h.id);
            const agreement = agreements.get(h.id);
            return (
              <tr key={h.id} className="hover:bg-background">
                <Cell>
                  {h.container ? (
                    <>
                      <span className="font-mono font-medium">{h.container.containerNumber}</span>
                      <span className="block text-xs text-muted">{CONTAINER_TYPE_LABEL[h.container.containerType]}{h.container.currentLocation ? ` · ${h.container.currentLocation}` : ""}</span>
                      {h.status === "AWAITING_ACCEPTANCE" ? (
                        <Link href={`/portfolio/${h.id}/agreement`} className="block text-xs font-medium text-gold-text hover:underline">
                          Review and accept agreement
                        </Link>
                      ) : agreement && (
                        <Link href={`/portfolio/${h.id}/agreement`} className="block text-xs text-gold-text hover:underline"
                          title={`Lease agreement ${agreement.agreementNumber}`}>
                          Lease agreement
                        </Link>
                      )}
                    </>
                  ) : (
                    <>
                      <span className="text-muted">Being assigned</span>
                      <span className="block text-xs text-muted">Our team assigns your container</span>
                    </>
                  )}
                </Cell>
                <Cell>
                  <Link href={`/marketplace/${h.productId}`} className="text-gold-text hover:underline">{h.productTitle}</Link>
                </Cell>
                <Cell className="tabular-nums">{formatMoney(h.amount)}</Cell>
                <Cell className="tabular-nums">
                  {formatPercent(h.monthlyRentPercent)}
                  <span className="block text-xs text-muted">a month</span>
                </Cell>
                <Cell className="tabular-nums">
                  {formatMoney(h.monthlyPayout)}
                  {Number(h.monthlyCapitalReturn.amount) > 0 && (
                    <span className="block text-xs text-muted">{formatMoney(h.monthlyRent)} rent + {formatMoney(h.monthlyCapitalReturn)} capital</span>
                  )}
                </Cell>
                <Cell>
                  {h.tenureMonths} months
                  <span className="block text-xs text-muted">
                    {h.leaseStartsOn && h.leaseEndsOn ? formatPeriod(h.leaseStartsOn, h.leaseEndsOn)
                      : h.status === "AWAITING_ACCEPTANCE" ? "Starts when you accept the agreement" : "Starts when your container is assigned"}
                  </span>
                </Cell>
                <Cell className="tabular-nums">
                  {lease ? formatMoney(lease.rentCredited) : "—"}
                  {lease && <span className="block text-xs text-muted">{Math.round(leasePercent(lease))}% of {formatMoney(lease.totalRent)}</span>}
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
        Once your payment is confirmed, our team assigns each investment its container and issues its lease agreement. When you accept the agreement, the lease starts at the next full hour and rent is credited to your wallet every hour from then on. See{" "}
        <Link href="/earnings" className="underline">Earnings</Link> for each lease&apos;s rent.
      </p>
    </div>
  );
}
