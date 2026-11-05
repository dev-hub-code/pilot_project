import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { buttonBase, buttonVariants } from "@/components/ui/button";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatGrid } from "@/components/ui/stat-grid";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { AgreementPrompt } from "@/features/agreement/agreement-prompt";
import { formatPeriod, leasePercent, nextCreditNote } from "@/features/earnings/labels";
import { yearsLabel } from "@/features/orders/lease-years";
import { CURRENCY } from "@/lib/currency";
import { authFetch } from "@/lib/server/auth/session";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { LeaseAgreement } from "@/types/agreement";
import type { EarningsSummary } from "@/types/earning";
import type { HoldingStatus, Portfolio } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";

import { investmentsHeld, holdingBreakdown } from "@/features/orders/holding-summary";

export const metadata: Metadata = { title: "Portfolio" };

const HOLDING_LABEL: Record<HoldingStatus, string> = {
  AWAITING_ACCEPTANCE: "Accept agreement",
  ACTIVE: "Running",
  MATURED: "Term completed",
};

const HOLDING_TONE: Record<HoldingStatus, StatusTone> = {
  AWAITING_ACCEPTANCE: "warning",
  ACTIVE: "success",
  MATURED: "neutral",
};

export default async function PortfolioPage({ searchParams }: PageProps<"/portfolio">) {
  const accepted = (await searchParams).accepted;
  const credit = creditLabel(await getRentCredit());
  const [portfolio, earnings, agreementList] = await Promise.all([
    authFetch<Portfolio>("/api/v1/portfolio"),
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    authFetch<LeaseAgreement[]>("/api/v1/agreements"),
  ]);
  const agreements = new Map(agreementList.map((a) => [a.holdingId, a]));
  const leases = new Map(earnings.leases.map((l) => [l.holdingId, l]));
  const rent = earnings.wallet.filter((w) => w.balance !== "REFERRAL").map((w) => w.amount);
  const rentWallet = rent.length === 0 ? "—"
    : formatMoney({ amount: rent.reduce((sum, m) => sum + Number(m.amount), 0).toFixed(2), currency: CURRENCY });

  return (
    <div className="space-y-6">
      <PageHeader title="Portfolio" description="Your investments, their terms and the returns they earn." />
      {typeof accepted === "string" && agreements.get(accepted)?.acceptedAt && (
        <Notice tone="success">
          Agreement {agreements.get(accepted)?.agreementNumber} accepted. Your term starts at the next full hour, and returns
          is credited to your wallet {credit} from then on. You can download the agreement from the investment below.
        </Notice>
      )}
      <AgreementPrompt holdings={portfolio.holdings} />
      <StatGrid label="Portfolio summary" stats={[
        { label: "Total invested", value: portfolio.totalsByCurrency.length === 0 ? "—" : portfolio.totalsByCurrency.map((m) => formatMoney(m)).join(" · ") },
        { label: "Investments", value: investmentsHeld(portfolio.holdings), note: holdingBreakdown(portfolio.holdings) || "None yet" },
        { label: "Returns wallet", value: rentWallet, note: nextCreditNote(earnings.nextCreditAt, credit) },
      ]} />

      {portfolio.holdings.length === 0 ? (
        <div className="space-y-4">
          <EmptyState title="No investments yet" description="Your investments appear here once your payment is confirmed." />
          <LinkButton href="/invest">See the plans</LinkButton>
        </div>
      ) : (
        // Six columns that fit the page: secondary details sit under each value and wrap, instead of more columns.
        <DataTable columns={["Plan", "Invested", "Paid monthly", "Term", "Returns credited", "Status"]}>
          {portfolio.holdings.map((h) => {
            const lease = leases.get(h.id);
            const agreement = agreements.get(h.id);
            return (
              <tr key={h.id} className="hover:bg-background">
                <Cell wrap className="min-w-[12rem]">
                  <Link href={`/invest/${h.productId}`} className="font-medium text-gold-text hover:underline">{h.productTitle}</Link>
                </Cell>
                <Cell className="tabular-nums">
                  {formatMoney(h.amount)}
                  <span className="block text-xs text-muted">{formatPercent(h.monthlyRentPercent * 12)} a year</span>
                </Cell>
                <Cell wrap className="tabular-nums">
                  <span className="whitespace-nowrap">{formatMoney(h.monthlyPayout)}</span>
                  {Number(h.monthlyCapitalReturn.amount) > 0 && (
                    <span className="block text-xs text-muted">{formatMoney(h.monthlyRent)} returns + {formatMoney(h.monthlyCapitalReturn)} capital</span>
                  )}
                </Cell>
                <Cell wrap className="min-w-[9rem]">
                  <span className="whitespace-nowrap">{h.tenureMonths % 12 === 0 ? yearsLabel(h.tenureMonths / 12) : `${h.tenureMonths} months`}</span>
                  <span className="block text-xs text-muted">
                    {h.leaseStartsOn && h.leaseEndsOn ? formatPeriod(h.leaseStartsOn, h.leaseEndsOn)
                      : "Starts when you accept the agreement"}
                  </span>
                </Cell>
                <Cell className="tabular-nums">
                  {lease ? formatMoney(lease.rentCredited) : "—"}
                  {lease && <span className="block text-xs text-muted">{Math.round(leasePercent(lease))}% of {formatMoney(lease.totalRent)}</span>}
                  {lease && <div className="mt-1.5 w-28"><ProgressBar percent={leasePercent(lease)} label={`${h.productTitle} term progress`} /></div>}
                </Cell>
                <Cell>
                  <div className="flex flex-col items-stretch gap-2 lg:items-start">
                    <StatusBadge tone={HOLDING_TONE[h.status]}>{HOLDING_LABEL[h.status]}</StatusBadge>
                    {h.status === "AWAITING_ACCEPTANCE" ? (
                      <LinkButton href={`/portfolio/${h.id}/agreement`} className="w-full lg:w-auto">Accept agreement</LinkButton>
                    ) : agreement && (
                      <a href={`/portfolio/agreements/${h.id}`} download
                        className={`${buttonBase} ${buttonVariants.quiet} w-full lg:w-auto`}
                        title={`Investment agreement ${agreement.agreementNumber}`}>
                        Download PDF
                      </a>
                    )}
                  </div>
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <p className="text-xs text-muted">
        Once your payment is confirmed, your investment agreement is issued. When you accept the agreement, the term starts at the next full hour and returns are credited to your wallet {credit} from then on. See{" "}
        <Link href="/earnings" className="underline">Earnings</Link> for each term&apos;s returns.
      </p>
    </div>
  );
}
