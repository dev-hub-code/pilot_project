/** Mirrors of the backend's investment plan DTOs. */

/** Amounts are decimal strings at the currency's minor unit ("50000.00"): never parse to float for maths. */
export interface Money {
  amount: string;
  currency: string;
}

export type ProductStatus = "DRAFT" | "OPEN" | "CLOSED" | "CANCELLED";
/** The investor type a plan is offered to. */
export type PlanAudience = "ONLINE" | "NETWORK";

/** A plan's published terms. One tenure for one investor type: investors buy units at the plan's price. */
export interface Plan {
  id: string;
  code: string;
  title: string;
  summary: string;
  status: ProductStatus;
  tenureYears: number;
  /** Returns a year, as a percentage of the investment; paid monthly. */
  annualReturnPercent: number;
  unitPrice: Money;
  /** The fewest units an investor may buy. */
  minUnits: number;
}

/** A plan as visitors see it: the website gets no id. */
export type PublicPlan = Omit<Plan, "id">;

export interface PlanDetail {
  plan: Plan;
  eligibility: { eligible: boolean; reasons: string[] };
}

/** What buying a number of units of a plan costs and pays. */
export interface PlanQuote {
  units: number;
  unitPrice: Money;
  /** Units × the unit price: what the investor pays. */
  amount: Money;
  annualReturnPercent: number;
  monthlyReturns: Money;
  /** Monthly payouts over the tenure. */
  payouts: number;
  /** The returns over the whole tenure. */
  totalReturns: Money;
  valid: boolean;
  problems: string[];
}

/** An investment plan, as staff see it. */
export interface Product {
  id: string;
  code: string;
  status: ProductStatus;
  audience: PlanAudience;
  title: string;
  summary: string;
  currency: string;
  tenureYears: number;
  annualReturnPercent: number;
  /** The price of one unit: the same on every plan. */
  unitPrice: number;
  minUnits: number;
  /** Investments made under the plan. */
  containersSold: number;
  publishedAt: string | null;
  closedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  createdAt: string;
  version: number;
}
