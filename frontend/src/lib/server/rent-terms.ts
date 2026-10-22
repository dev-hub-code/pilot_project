import "server-only";
import type { MinimumOrder, Money, RentTier, RentTiers } from "@/types/marketplace";
import { backendFetch } from "./backend-client";

/** The published rent bands (rate by order value) and the minimum order, for the website. */
export interface RentTerms {
  tiers: RentTier[];
  /** Null when there is no minimum. */
  minimumOrder: Money | null;
}

/** Null when the backend cannot be reached, so the home page still renders with general wording. */
export async function getRentTerms(): Promise<RentTerms | null> {
  try {
    const [tiers, minimum] = await Promise.all([
      backendFetch<RentTiers>("/api/v1/public/rent-tiers"),
      backendFetch<MinimumOrder>("/api/v1/public/minimum-order"),
    ]);
    if (tiers.tiers.length === 0) return null;
    return { tiers: tiers.tiers, minimumOrder: Number(minimum.amount.amount) > 0 ? minimum.amount : null };
  } catch {
    return null;
  }
}

/** The band an order of this total falls in; null below the first band. */
export function tierFor(terms: RentTerms, total: number): RentTier | null {
  return terms.tiers.find((t) => total >= Number(t.fromAmount.amount)
    && (t.toAmount === null || total <= Number(t.toAmount.amount))) ?? null;
}
