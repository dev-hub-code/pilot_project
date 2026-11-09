import { yearsLabel } from "@/features/orders/lease-years";
import { minimumAmount, planReturns, times, type PlanReturns } from "@/features/plans/returns";
import type { Money, PublicPlan } from "@/types/plan";
import { formatPercent } from "@/utils/money";

/**
 * Figures the landing page states about the plans, all worked out from the live plans (GET /api/v1/public/plans),
 * never typed in: when admins change a plan, every section follows. Null or empty when no plan is open.
 */

/** "15.00%" or "15.00% to 18.00%". */
function range(values: number[], format: (v: number) => string, joiner = " to "): string | null {
  if (values.length === 0) return null;
  const low = Math.min(...values);
  const high = Math.max(...values);
  return low === high ? format(low) : `${format(low)}${joiner}${format(high)}`;
}

export interface PlanFacts {
  count: number;
  /** "15.00% to 18.00%" a year. */
  returns: string | null;
  /** "3 to 9 years". */
  terms: string | null;
  /** The least anyone can invest, and in which plan. */
  cheapest: { plan: PublicPlan; amount: Money } | null;
}

export function planFacts(plans: PublicPlan[]): PlanFacts {
  const cheapest = plans
    .map((plan) => ({ plan, amount: minimumAmount(plan) }))
    .reduce<PlanFacts["cheapest"]>((best, c) => (!best || Number(c.amount.amount) < Number(best.amount.amount) ? c : best), null);
  const years = plans.map((p) => p.tenureYears);
  return {
    count: plans.length,
    returns: range(plans.map((p) => p.annualReturnPercent), (v) => formatPercent(v)),
    terms: years.length === 0 ? null
      : Math.min(...years) === Math.max(...years) ? yearsLabel(years[0] ?? 0) : `${Math.min(...years)} to ${Math.max(...years)} years`,
    cheapest,
  };
}

/** How far into their terms the example investor is. */
export const EXAMPLE_MONTHS_IN = 4;
/** Of those months, how many months of returns the example investor has already withdrawn. */
export const EXAMPLE_MONTHS_WITHDRAWN = 3;
/** Roughly what the example investor put into each plan, before rounding up to whole units and each plan's minimum. */
const EXAMPLE_TARGETS = [250000, 400000];

export interface ExampleHolding {
  plan: PublicPlan;
  units: number;
  returns: PlanReturns;
}

/**
 * A hypothetical investor in up to two of the live plans, for the dashboard preview. Amounts are whole units at
 * least each plan's minimum, so they are investments the platform would accept.
 */
export function exampleHoldings(plans: PublicPlan[]): ExampleHolding[] {
  return plans.slice(0, 2).map((plan, i) => {
    const price = Number(plan.unitPrice.amount);
    const units = Math.max(plan.minUnits, Math.ceil((EXAMPLE_TARGETS[i] ?? 0) / price));
    return { plan, units, returns: planReturns(plan, times(plan.unitPrice, units)) };
  });
}
