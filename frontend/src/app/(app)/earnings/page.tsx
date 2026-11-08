import type { Metadata } from "next";
import Link from "next/link";
import { ArrowDownToLine, ArrowRight, Coins, FileText, Network, Undo2, Wallet } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import { type StatTile, StatTiles } from "@/features/dashboard/stat-tiles";
import { EARNINGS_BALANCE_LABEL, LEASE_STATUS_LABEL, LEASE_TONE, leaseNote, leasePercent, nextCreditNote } from "@/features/earnings/labels";
import { authFetch } from "@/lib/server/auth/session";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import type { EarningsSummary } from "@/types/earning";
import type { ReferralOverview } from "@/types/referral";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Earnings" };

export default async function EarningsPage() {
  const [summary, rentCredit, referrals] = await Promise.all([
    authFetch<EarningsSummary>("/api/v1/earnings/summary"),
    getRentCredit(),
    authFetch<ReferralOverview>("/api/v1/referrals/me").catch(() => null),
  ]);
  // Only online investors request withdrawals; network investors are paid when our team makes one.
  const canWithdraw = referrals?.scheme === "ONLINE";
  const credit = creditLabel(rentCredit);
  // Plans pay rent only; capital shows only for units bought before that.
  const hasCapital = summary.capitalReturned.length > 0 || summary.leases.some((l) => Number(l.capitalTotal.amount) > 0);
  const totals = (list: EarningsSummary["balances"]) => (list.length === 0 ? "—" : list.map((m) => formatMoney(m)).join(" · "));
  const wallet = summary.wallet.filter((w) => Number(w.amount.amount) > 0);

  const tiles: StatTile[] = [
    { label: "Returns received", value: totals(summary.rentPaid), icon: Coins, note: "Credited over all your terms" },
    ...(hasCapital ? [{ label: "Capital returned", value: totals(summary.capitalReturned), icon: Undo2, note: "On units bought before plans became returns-only" }] : []),
    { label: "Referral earnings", value: totals(summary.referralEarned), icon: Network, href: "/referrals", note: "By month and level on Referrals" },
    { label: "Total withdrawn", value: totals(summary.totalWithdrawn), icon: ArrowDownToLine, href: "/withdrawals",
      note: summary.withdrawalInProgress.length > 0 ? `${totals(summary.withdrawalInProgress)} in progress` : "Paid out to you so far" },
  ];

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="earnings-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover object-[center_65%] saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_50%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.1fr_1fr] lg:items-end lg:p-10">
          <div className="space-y-3">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Earnings</p>
            <h1 id="earnings-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
              Your <span className="text-gold-on-ink">returns</span>
            </h1>
            <p className="max-w-lg text-sm leading-relaxed text-on-ink/75">
              Each of your investments earns returns throughout its term, credited to your wallet {credit}. Your wallet is
              paid out when our team makes a withdrawal (see Withdrawals).
            </p>
            <div className="flex flex-wrap gap-3 pt-3">
              {canWithdraw && (
                <Link href="/withdrawals"
                  className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
                  Withdraw <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
                </Link>
              )}
              <Link href="/statements"
                className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-on-ink/20 bg-on-ink/10 px-5 text-sm font-semibold text-on-ink backdrop-blur-md transition duration-300 hover:-translate-y-0.5 hover:bg-on-ink/20">
                <FileText aria-hidden="true" className="size-4" /> Statements
              </Link>
            </div>
          </div>

          <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-5 backdrop-blur-md sm:p-6 lg:justify-self-end lg:min-w-96">
            <p className="flex items-center gap-2 text-sm text-on-ink/70">
              <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-on-ink/10 text-gold-on-ink">
                <Wallet className="size-4" />
              </span>
              Wallet balance
            </p>
            <p className="mt-3 font-display text-4xl font-semibold tracking-tight tabular-nums sm:text-5xl">{totals(summary.balances)}</p>
            <p className="mt-2 flex items-center gap-2 text-xs text-on-ink/65">
              <span aria-hidden="true" className="relative flex size-2">
                <span className="absolute inline-flex size-full rounded-full bg-gold-on-ink opacity-70 motion-safe:animate-ping" />
                <span className="relative inline-flex size-2 rounded-full bg-gold-on-ink" />
              </span>
              {nextCreditNote(summary.nextCreditAt, credit)}
            </p>
            {wallet.length > 1 && (
              <dl className="mt-4 grid gap-2 border-t border-on-ink/15 pt-4 text-xs">
                {wallet.map((w) => (
                  <div key={`${w.balance}-${w.amount.currency}`} className="flex items-center justify-between gap-4">
                    <dt className="text-on-ink/65">{EARNINGS_BALANCE_LABEL[w.balance]}</dt>
                    <dd className="font-semibold tabular-nums">{formatMoney(w.amount)}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        </div>
      </section>

      <StatTiles tiles={tiles} label="Earnings summary" />

      {/* ------------------------------------------------------- returns by investment */}
      <section aria-labelledby="by-investment" className="rounded-3xl border border-border bg-surface p-5 shadow-sm sm:p-7">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 id="by-investment" className="text-lg font-semibold tracking-tight">Returns by investment</h2>
            <p className="mt-0.5 text-sm text-muted">What each term pays a month, and how much of it has been credited</p>
          </div>
          <Link href="/portfolio" className="inline-flex min-h-6 items-center gap-1 text-sm font-medium text-gold-text hover:underline">
            Portfolio <ArrowRight aria-hidden="true" className="size-3.5" />
          </Link>
        </div>
        {summary.leases.length === 0 ? (
          <div className="mt-5">
            <EmptyState title="No terms running yet" description="Your term starts once you accept your investment agreement." />
          </div>
        ) : (
          <ul className="mt-5 grid gap-3 lg:grid-cols-2">
            {summary.leases.map((lease) => {
              const percent = leasePercent(lease);
              const title = lease.productTitle ?? "Investment";
              return (
                <li key={lease.holdingId}
                  className="space-y-3 rounded-2xl border border-border/70 bg-background/60 p-4 text-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/30 hover:bg-background hover:shadow-md sm:p-5">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="font-semibold">{title}</p>
                      <p className="text-xs text-muted">{leaseNote(lease)}</p>
                    </div>
                    <StatusBadge tone={LEASE_TONE[lease.status]}>{LEASE_STATUS_LABEL[lease.status]}</StatusBadge>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <p className="text-xs text-muted">Monthly returns</p>
                      <p className="font-display text-lg font-semibold tabular-nums">{formatMoney(lease.monthlyRent)}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-xs text-muted">Credited so far</p>
                      <p className="font-display text-lg font-semibold text-gold-text tabular-nums">{formatMoney(lease.rentCredited)}</p>
                    </div>
                  </div>
                  <ProgressBar percent={percent} label={`${title} term progress`} />
                  <p className="flex justify-between gap-3 text-xs text-muted tabular-nums">
                    <span>{Math.round(percent)}% of the term</span>
                    <span>of {formatMoney(lease.totalRent)}</span>
                  </p>
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}
