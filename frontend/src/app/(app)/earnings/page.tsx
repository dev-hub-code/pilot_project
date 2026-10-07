import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { formatPeriod } from "@/features/earnings/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Earning, EarningsSummary } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Earnings" };

export default async function EarningsPage({ searchParams }: PageProps<"/earnings">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [summary, history] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<PageResponse<Earning>>(`/api/v1/earnings?page=${page}&size=20`),
  ]);
  const totals = (list: EarningsSummary["balances"]) => (list.length === 0 ? "—" : list.map((m) => formatMoney(m)).join(" · "));

  return (
    <div className="space-y-6">
      <PageHeader title="Earnings" description="Rental income from your containers, credited after each lessee payment is received and checked." />
      <section className="grid gap-px bg-border sm:grid-cols-2" aria-label="Earnings summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Available balance</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{totals(summary.balances)}</p>
          <Link href="/withdrawals" className="text-sm text-gold-text hover:underline">Withdraw to your bank account →</Link>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Rental income to date</p>
          <p className="font-display text-4xl font-semibold tabular-nums">{totals(summary.totalEarned)}</p>
          <p className="text-xs text-muted">After management fees</p>
        </div>
      </section>

      {history.content.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title="No rental income yet"
            description="Once a container you own is on lease, your share of each rental payment appears here." />
          <LinkButton href="/portfolio" variant="secondary">View portfolio</LinkButton>
        </div>
      ) : (
        <>
          <DataTable columns={["Credited", "Offering", "Rental period", "Your share", "Gross", "Fee", "Net"]}>
            {history.content.map((e) => (
              <tr key={e.id} className="hover:bg-background">
                <Cell className="text-muted">{formatDate(e.paidAt)}</Cell>
                <Cell>
                  <Link href={`/marketplace/${e.productId}`} className="font-mono text-gold-text hover:underline">{e.productCode}</Link>
                  <span className="block max-w-xs truncate text-xs text-muted">{e.productTitle}</span>
                </Cell>
                <Cell>
                  Period {e.periodNumber}
                  <span className="block text-xs text-muted">{formatPeriod(e.periodStartsOn, e.periodEndsOn)}</span>
                </Cell>
                <Cell className="tabular-nums">{formatPercent(e.ownershipPercent, 4)}</Cell>
                <Cell className="tabular-nums">{formatMoney(e.gross)}</Cell>
                <Cell className="tabular-nums text-muted">−{formatMoney(e.fee)}</Cell>
                <Cell className="tabular-nums font-medium">{formatMoney(e.net)}</Cell>
              </tr>
            ))}
          </DataTable>
          <Pagination page={history} basePath="/earnings" params={{}} />
        </>
      )}
    </div>
  );
}
