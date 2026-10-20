import type { Metadata } from "next";
import Link from "next/link";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { authFetch } from "@/lib/server/auth/session";
import { getRentCredit } from "@/lib/server/rent-credit";
import type { EarningsSummary } from "@/types/earning";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Earnings" };

export default async function EarningsPage() {
  const [summary, credit] = await Promise.all([authFetch<EarningsSummary>("/api/v1/earnings/summary"), getRentCredit()]);
  // Plans pay rent only; capital shows only for containers bought before that.
  const hasCapital = summary.capitalReturned.length > 0 || summary.leases.some((l) => Number(l.capitalTotal.amount) > 0);
  const totals = (list: EarningsSummary["balances"]) => (list.length === 0 ? "—" : list.map((m) => formatMoney(m)).join(" · "));

  return (
    <div className="space-y-6">
      <PageHeader title="Earnings"
        description={`Each of your containers earns rent throughout its lease, credited to your wallet ${credit.label}. Your wallet is paid out when our team makes a withdrawal (see Withdrawals).`}
        actions={<LinkButton href="/statements" variant="secondary">Statements</LinkButton>} />
      <section className={`grid gap-px bg-border sm:grid-cols-2 ${hasCapital ? "lg:grid-cols-5" : "lg:grid-cols-4"}`} aria-label="Earnings summary">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Wallet balance</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.balances)}</p>
          <Link href="/withdrawals" className="text-sm text-gold-text hover:underline">Withdraw →</Link>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Rent received</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.rentPaid)}</p>
        </div>
        {hasCapital && (
          <div className="space-y-2 bg-surface p-6">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">Capital returned</p>
            <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.capitalReturned)}</p>
          </div>
        )}
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Referral earnings</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.referralEarned)}</p>
          <Link href="/referrals" className="text-sm text-gold-text hover:underline">By month and level →</Link>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Total withdrawn</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{totals(summary.totalWithdrawn)}</p>
          {summary.withdrawalInProgress.length > 0 && (
            <p className="text-xs text-muted">{totals(summary.withdrawalInProgress)} in progress</p>
          )}
        </div>
      </section>
    </div>
  );
}
