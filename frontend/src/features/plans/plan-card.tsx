import { ArrowRight, CalendarClock } from "lucide-react";
import Link from "next/link";
import { StatusBadge } from "@/components/ui/status-badge";
import { yearsLabel } from "@/features/orders/lease-years";
import type { PublicPlan } from "@/types/plan";
import { formatMoney, formatPercent } from "@/utils/money";
import { PRODUCT_STATUS_LABEL } from "./labels";

/** Grid columns for a list of plan cards: four to a row when that fills the rows evenly, otherwise three. */
export function planGridClass(count: number): string {
  return count % 4 === 0 ? "grid gap-6 sm:grid-cols-2 xl:grid-cols-4" : "grid gap-6 sm:grid-cols-2 xl:grid-cols-3";
}

/** The plan with the highest return, when exactly one plan has it: that card is marked "Highest return". */
export function highestReturnCode(plans: Pick<PublicPlan, "code" | "annualReturnPercent">[]): string | undefined {
  if (plans.length < 2) return undefined;
  const top = Math.max(...plans.map((p) => p.annualReturnPercent));
  const leaders = plans.filter((p) => p.annualReturnPercent === top);
  return leaders.length === 1 ? leaders[0]?.code : undefined;
}

/** A plan's terms: the return first, then the price, then what the smallest investment pays. */
export function PlanCard({ plan, href, action, highlight = false }: {
  plan: PublicPlan;
  href: string;
  action: string;
  /** Marks the plan with the highest return. */
  highlight?: boolean;
}) {
  const money = (amount: number) => ({ amount: amount.toFixed(2), currency: plan.unitPrice.currency });
  const minimum = Number(plan.unitPrice.amount) * plan.minUnits;
  // Rounded the way the backend rounds it, so this matches what the order will pay.
  const monthly = Math.round((minimum * plan.annualReturnPercent) / 12) / 100;
  const total = monthly * plan.tenureYears * 12;
  return (
    <article className={`group relative flex w-full flex-col overflow-hidden rounded-2xl border bg-surface shadow-sm transition duration-300 hover:-translate-y-1 hover:shadow-xl focus-within:ring-2 focus-within:ring-gold ${
      highlight ? "border-gold/60 ring-1 ring-gold/30" : "border-border hover:border-gold/40"}`}>
      {/* ---- header: tenure, name and the return */}
      <header className="relative isolate overflow-hidden bg-ink px-6 pt-5 pb-6 text-on-ink">
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_100%_0%,var(--gold)_0%,transparent_60%)] opacity-35" />
        <span aria-hidden="true"
          className="absolute -right-2 -bottom-6 -z-10 font-display text-[7.5rem] leading-none font-bold text-on-ink/[0.06] select-none">
          {plan.tenureYears}Y
        </span>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-on-ink/10 px-2.5 py-1 text-xs font-medium text-on-ink/85">
            <CalendarClock className="size-3.5" aria-hidden="true" /> {yearsLabel(plan.tenureYears)}
          </span>
          {plan.status !== "OPEN" ? (
            <StatusBadge tone="neutral">{PRODUCT_STATUS_LABEL[plan.status]}</StatusBadge>
          ) : highlight && (
            <span className="rounded-full bg-gold-on-ink px-2.5 py-1 text-xs font-semibold text-ink">Highest return</span>
          )}
        </div>
        <h2 className="mt-4 font-display text-lg font-semibold tracking-wide">
          <Link href={href} className="after:absolute after:inset-0 focus-visible:outline-none">{plan.title}</Link>
        </h2>
        <p className="mt-1 flex items-baseline gap-2">
          <span className="font-display text-5xl font-semibold tracking-tight text-gold-on-ink tabular-nums">
            {formatPercent(plan.annualReturnPercent)}
          </span>
        </p>
        <p className="mt-1 text-sm text-on-ink/70">a year, paid every month</p>
      </header>

      {/* ---- body: terms and what the minimum pays */}
      <div className="flex flex-1 flex-col gap-5 p-6">
        <p className="line-clamp-3 text-sm leading-relaxed text-muted sm:min-h-[4.3rem]">{plan.summary}</p>
        <dl className="divide-y divide-border border-y border-border text-sm">
          <Term label="Price per unit" value={formatMoney(plan.unitPrice)} />
          <Term label="Minimum units" value={String(plan.minUnits)} />
          <Term label="Starts at" value={formatMoney(money(minimum))} />
        </dl>
        <div className="rounded-xl bg-gold/[0.07] p-4">
          <p className="text-xs font-medium tracking-wide text-gold-text uppercase">On the minimum</p>
          <p className="mt-1.5 font-display text-2xl font-semibold tabular-nums">
            {formatMoney(money(monthly))}<span className="ml-1 text-sm font-normal text-muted">/ month</span>
          </p>
          <p className="mt-0.5 text-xs text-muted">{formatMoney(money(total))} over {yearsLabel(plan.tenureYears)}</p>
        </div>
        <span className="mt-auto inline-flex items-center justify-center gap-2 rounded-lg bg-gold px-4 py-2.5 text-sm font-semibold text-on-gold transition-colors group-hover:bg-gold-hover">
          {action}
          <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
        </span>
      </div>
    </article>
  );
}

function Term({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2.5">
      <dt className="text-muted">{label}</dt>
      <dd className="font-semibold tabular-nums">{value}</dd>
    </div>
  );
}
