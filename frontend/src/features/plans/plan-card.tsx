import Link from "next/link";
import { StatusBadge } from "@/components/ui/status-badge";
import { yearsLabel } from "@/features/orders/lease-years";
import type { PublicPlan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";
import { PRODUCT_STATUS_LABEL } from "./labels";

/** A plan's terms: the tenure and monthly return first, then the unit price and the fewest units to buy. */
export function PlanCard({ plan, href, action }: { plan: PublicPlan; href: string; action: string }) {
  return (
    <article className="relative flex w-full flex-col gap-5 border border-border bg-surface p-6">
      <div className="space-y-2">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-xl font-semibold tracking-tight">
            <Link href={href} className="after:absolute after:inset-0">{plan.title}</Link>
          </h2>
          {plan.status !== "OPEN" && <StatusBadge tone="neutral">{PRODUCT_STATUS_LABEL[plan.status]}</StatusBadge>}
        </div>
        <p className="text-sm text-muted">{plan.summary}</p>
      </div>
      <dl className="grid grid-cols-2 gap-4">
        <Term label="Tenure" value={yearsLabel(plan.tenureYears)} />
        <Term label="Returns a year" value={formatPercent(plan.annualReturnPercent)} emphasis />
        <Term label="Price of 1 unit" value={formatMoney(plan.unitPrice)} />
        <Term label="Minimum" value={plan.minUnits === 1 ? "1 unit" : `${plan.minUnits} units`} />
      </dl>
      <p className="mt-auto text-sm font-medium text-gold-text">{action} →</p>
    </article>
  );
}

function Term({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className={`mt-1 font-semibold ${emphasis ? "font-display text-2xl text-gold-text" : ""}`}>{value}</dd>
    </div>
  );
}
