import type { Money, PublicPlan } from "@/types/plan";

/**
 * Plan returns, worked out exactly as the backend does (ProductTerms.monthlyRent), so every figure on the website
 * matches the order the investor will see:
 *   monthly rate  = annual % ÷ 12, rounded half-up to 8 decimal places
 *   monthly       = amount × monthly rate ÷ 100, rounded half-up to 4 decimals and then to the paisa
 *   over the term = monthly × 12 × tenure years
 * The amount invested is not paid back: the returns over the term are all an investor receives.
 * Integer maths in paise throughout, never binary floats.
 */

const RATE_SCALE = 100_000_000n; // 8 decimal places

/** a ÷ b, rounded half-up (a, b ≥ 0). */
const divideHalfUp = (a: bigint, b: bigint) => (a + b / 2n) / b;

/** "50000.00" → 5000000n paise. */
function toPaise(amount: string): bigint {
  const [whole = "0", fraction = ""] = amount.split(".");
  return BigInt(whole) * 100n + BigInt((fraction + "00").slice(0, 2));
}

function fromPaise(paise: bigint, currency: string): Money {
  const whole = paise / 100n;
  const fraction = (paise % 100n).toString().padStart(2, "0");
  return { amount: `${whole}.${fraction}`, currency };
}

/** Annual % (at most 2 decimals, e.g. 15.5) ÷ 12, at 8 decimals, as the backend keeps it. */
function monthlyRate(annualReturnPercent: number): bigint {
  const hundredths = BigInt(Math.round(annualReturnPercent * 100));
  return divideHalfUp(hundredths * RATE_SCALE, 1200n);
}

export interface PlanReturns {
  /** What the investor pays. */
  amount: Money;
  /** Returns for one full month. */
  monthly: Money;
  months: number;
  /** Every month's returns added up: all the investor receives, since the amount is not paid back. */
  overTerm: Money;
  /** overTerm as a whole percentage of the amount, e.g. 45 for a 3-year plan at 15%. */
  overTermPercent: number;
}

/** The returns on `amount` under a plan's terms. */
export function planReturns(plan: Pick<PublicPlan, "annualReturnPercent" | "tenureYears">, amount: Money): PlanReturns {
  const paise = toPaise(amount.amount);
  // Like Money: first to 4 decimal places of a rupee, then to the paisa, both half-up.
  const tenThousandths = divideHalfUp(paise * monthlyRate(plan.annualReturnPercent), RATE_SCALE);
  const monthly = divideHalfUp(tenThousandths, 100n);
  const months = plan.tenureYears * 12;
  const overTerm = monthly * BigInt(months);
  return {
    amount,
    monthly: fromPaise(monthly, amount.currency),
    months,
    overTerm: fromPaise(overTerm, amount.currency),
    overTermPercent: paise === 0n ? 0 : Number(divideHalfUp(overTerm * 100n, paise)),
  };
}

/** The smallest amount a plan accepts: its minimum units at its unit price. */
export function minimumAmount(plan: Pick<PublicPlan, "unitPrice" | "minUnits">): Money {
  return fromPaise(toPaise(plan.unitPrice.amount) * BigInt(plan.minUnits), plan.unitPrice.currency);
}

/** A whole-rupee amount as Money, for examples: 250000 → { amount: "250000.00", currency }. */
export function money(rupees: number, currency = "INR"): Money {
  return fromPaise(BigInt(Math.round(rupees)) * 100n, currency);
}

/** Adds amounts in the same currency. */
export function sum(amounts: Money[], currency = "INR"): Money {
  return fromPaise(amounts.reduce((total, m) => total + toPaise(m.amount), 0n), amounts[0]?.currency ?? currency);
}

/** `m` times `n` (n a whole number). */
export function times(m: Money, n: number): Money {
  return fromPaise(toPaise(m.amount) * BigInt(n), m.currency);
}
