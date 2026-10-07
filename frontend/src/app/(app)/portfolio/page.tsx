import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { authFetch } from "@/lib/server/auth/session";
import type { Portfolio } from "@/types/order";
import { formatDate, humanize } from "@/utils/format";
import { formatMoney, formatPercent, FREQUENCY_LABEL } from "@/utils/money";

export const metadata: Metadata = { title: "Portfolio" };

export default async function PortfolioPage() {
  const portfolio = await authFetch<Portfolio>("/api/v1/portfolio");

  return (
    <div className="space-y-6">
      <PageHeader title="Portfolio" description="Your confirmed container investments." />
      <section className="grid gap-px bg-border sm:grid-cols-2" aria-label="Portfolio summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Total invested</p>
          <p className="font-display text-4xl font-semibold tabular-nums">
            {portfolio.totalsByCurrency.length === 0 ? "—" : portfolio.totalsByCurrency.map((m) => formatMoney(m)).join(" · ")}
          </p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Active investments</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{portfolio.activeHoldings}</p>
        </div>
      </section>

      {portfolio.holdings.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title="No investments yet" description="Confirmed investments appear here once their payment arrives." />
          <LinkButton href="/marketplace">Browse the marketplace</LinkButton>
        </div>
      ) : (
        <DataTable columns={["Offering", "Invested", "Ownership", "Expected rental", "Term", "Since", "Status"]}>
          {portfolio.holdings.map((h) => (
            <tr key={h.id} className="hover:bg-background">
              <Cell>
                <Link href={`/marketplace/${h.productId}`} className="font-mono text-gold-text hover:underline">{h.productCode}</Link>
                <span className="block max-w-xs truncate text-xs text-muted">{h.productTitle}</span>
              </Cell>
              <Cell className="tabular-nums">{formatMoney(h.amount)}</Cell>
              <Cell className="tabular-nums">{formatPercent(h.ownershipPercent, 4)}</Cell>
              <Cell className="tabular-nums">{formatMoney(h.expectedRentalPerPayment)}/{FREQUENCY_LABEL[h.rentalFrequency]}</Cell>
              <Cell>{h.durationMonths} months</Cell>
              <Cell className="text-muted">{formatDate(h.confirmedAt)}</Cell>
              <Cell><StatusBadge tone={toneFor(h.status)}>{humanize(h.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <p className="text-xs text-muted">Expected rental assumes the lessee pays as forecast. Rental income starts being paid out in a later release.</p>
    </div>
  );
}
