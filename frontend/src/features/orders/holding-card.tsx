import Link from "next/link";
import { ArrowRight, CalendarRange, Download, FileSignature, Repeat } from "lucide-react";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { formatPeriod, leasePercent } from "@/features/earnings/labels";
import { yearsLabel } from "@/features/orders/lease-years";
import type { LeaseAgreement } from "@/types/agreement";
import type { LeaseRent } from "@/types/earning";
import type { Holding, HoldingStatus } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";

export const HOLDING_LABEL: Record<HoldingStatus, string> = {
  AWAITING_ACCEPTANCE: "Accept agreement",
  ACTIVE: "Running",
  MATURED: "Term completed",
};

const HOLDING_TONE: Record<HoldingStatus, StatusTone> = {
  AWAITING_ACCEPTANCE: "warning",
  ACTIVE: "success",
  MATURED: "neutral",
};

/** One investment in the portfolio: what was invested, what it pays, how far its term has come, and its agreement. */
export function HoldingCard({ holding: h, lease, agreement }: {
  holding: Holding;
  lease?: LeaseRent;
  agreement?: LeaseAgreement;
}) {
  const awaiting = h.status === "AWAITING_ACCEPTANCE";
  const percent = lease ? leasePercent(lease) : 0;
  const term = h.tenureMonths % 12 === 0 ? yearsLabel(h.tenureMonths / 12) : `${h.tenureMonths} months`;
  return (
    <article className={`group flex h-full flex-col overflow-hidden rounded-2xl border bg-surface shadow-sm transition duration-300 hover:-translate-y-0.5 hover:shadow-lg ${
      awaiting ? "border-amber-300/70 dark:border-amber-500/40" : "border-border hover:border-gold/40"}`}>
      {/* ---- header: plan, status and the amount */}
      <header className="relative isolate overflow-hidden bg-ink px-5 pt-4 pb-5 text-on-ink">
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_100%_0%,var(--gold)_0%,transparent_60%)] opacity-30" />
        <div className="flex items-start justify-between gap-3">
          <h2 className="font-display text-base font-semibold tracking-wide">
            <Link href={`/invest/${h.productId}`} className="underline-offset-4 hover:underline">{h.productTitle}</Link>
          </h2>
          <StatusBadge tone={HOLDING_TONE[h.status]}>{HOLDING_LABEL[h.status]}</StatusBadge>
        </div>
        <p className="mt-4 text-xs text-on-ink/60">Invested</p>
        <p className="font-display text-3xl font-semibold tracking-tight tabular-nums">{formatMoney(h.amount)}</p>
        <p className="mt-0.5 text-xs text-gold-on-ink">{formatPercent(h.monthlyRentPercent * 12)} a year · {term}</p>
      </header>

      {/* ---- body */}
      <div className="flex flex-1 flex-col gap-4 p-5">
        <dl className="grid grid-cols-2 gap-3 text-sm">
          <div className="rounded-xl bg-background p-3">
            <dt className="flex items-center gap-1.5 text-xs text-muted"><Repeat aria-hidden="true" className="size-3.5" /> Paid monthly</dt>
            <dd className="mt-1 font-semibold tabular-nums">{formatMoney(h.monthlyPayout)}</dd>
            {Number(h.monthlyCapitalReturn.amount) > 0 && (
              <dd className="mt-0.5 text-[11px] text-muted">{formatMoney(h.monthlyRent)} returns + {formatMoney(h.monthlyCapitalReturn)} capital</dd>
            )}
          </div>
          <div className="rounded-xl bg-background p-3">
            <dt className="flex items-center gap-1.5 text-xs text-muted"><CalendarRange aria-hidden="true" className="size-3.5" /> Term</dt>
            <dd className="mt-1 text-xs leading-snug font-medium">
              {h.leaseStartsOn && h.leaseEndsOn ? formatPeriod(h.leaseStartsOn, h.leaseEndsOn) : "Starts when you accept the agreement"}
            </dd>
          </div>
        </dl>

        {lease ? (
          <div className="space-y-2">
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="text-muted">Returns credited</span>
              <span className="rounded-full bg-gold/10 px-2 py-0.5 text-xs font-semibold text-gold-text tabular-nums">{Math.round(percent)}%</span>
            </div>
            <ProgressBar percent={percent} label={`${h.productTitle} term progress`} />
            <p className="text-xs text-muted tabular-nums">
              <span className="font-semibold text-foreground">{formatMoney(lease.rentCredited)}</span> of {formatMoney(lease.totalRent)}
            </p>
          </div>
        ) : awaiting && (
          <p className="rounded-xl bg-amber-50 p-3 text-xs leading-relaxed text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
            Accept your investment agreement to start the term. Returns begin at the next full hour after you accept.
          </p>
        )}

        <div className="mt-auto pt-1">
          {awaiting ? (
            <Link href={`/portfolio/${h.id}/agreement`}
              className="flex min-h-11 items-center justify-center gap-2 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
              <FileSignature aria-hidden="true" className="size-4" /> Accept agreement
              <ArrowRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
          ) : agreement && (
            <div className="space-y-1.5">
              <a href={`/portfolio/agreements/${h.id}`} download title={`Investment agreement ${agreement.agreementNumber}`}
                className="flex min-h-11 items-center justify-center gap-2 rounded-lg border border-border px-4 text-sm font-medium transition-colors hover:border-gold hover:text-gold-text">
                <Download aria-hidden="true" className="size-4" /> Download agreement
              </a>
              <p className="text-center text-[11px] text-muted tabular-nums">Agreement {agreement.agreementNumber}</p>
            </div>
          )}
        </div>
      </div>
    </article>
  );
}
