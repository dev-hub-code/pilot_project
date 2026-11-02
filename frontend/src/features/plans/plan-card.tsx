import Link from "next/link";
import { StatusBadge } from "@/components/ui/status-badge";
import { yearsLabel } from "@/features/orders/lease-years";
import type { PublicPlan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";
import { PRODUCT_STATUS_LABEL } from "./labels";

/** A plan's terms: the return first, then the price, then what the smallest investment pays. */
export function PlanCard({ plan, href, action }: { plan: PublicPlan; href: string; action: string }) {
  const money = (amount: number) => ({ amount: amount.toFixed(2), currency: plan.unitPrice.currency });
  const minimum = Number(plan.unitPrice.amount) * plan.minUnits;
  // Rounded the way the backend rounds it, so this matches what the order will pay.
  const monthly = Math.round((minimum * plan.annualReturnPercent) / 12) / 100;
  return (
    <article className="relative flex w-full flex-col gap-5 rounded-xl border border-border bg-surface p-6">
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-xl font-semibold tracking-tight">
            <Link href={href} className="after:absolute after:inset-0">{plan.title}</Link>
          </h2>
          {plan.status !== "OPEN" && <StatusBadge tone="neutral">{PRODUCT_STATUS_LABEL[plan.status]}</StatusBadge>}
        </div>
        <p className="text-sm text-muted">{plan.summary}</p>
      </div>
      <div>
        <p className="font-display text-4xl font-semibold tracking-tight text-gold-text tabular-nums">{formatPercent(plan.annualReturnPercent)}</p>
        <p className="text-sm text-muted">a year, paid monthly for {yearsLabel(plan.tenureYears)}</p>
      </div>
      <dl className="grid grid-cols-2 gap-4">
        <Term label="Price of 1 unit" value={formatMoney(plan.unitPrice)} />
        <Term label="Minimum" value={plan.minUnits === 1 ? "1 unit" : `${plan.minUnits} units`} />
      </dl>
      <p className="rounded-lg bg-background p-3 text-sm">
        Invest {formatMoney(money(minimum))} and receive <span className="font-semibold">{formatMoney(money(monthly))} a month</span>,
        {" "}{formatMoney(money(monthly * plan.tenureYears * 12))} over the term.
      </p>
      <p className="mt-auto text-sm font-medium text-gold-text">{action} →</p>
    </article>
  );
}

function Term({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 font-semibold">{value}</dd>
    </div>
  );
}
