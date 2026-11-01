"use server";

import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { PlanQuote } from "@/types/plan";

/** What buying {@code units} units of a plan costs and pays; null when it cannot be worked out. */
export async function quotePlanAction(productId: string, units: number): Promise<PlanQuote | null> {
  if (!isUuid(productId) || !Number.isInteger(units) || units < 1 || units > 100_000_000) return null;
  try {
    return await authFetch<PlanQuote>(`/api/v1/plans/${productId}/quote?units=${units}`);
  } catch {
    return null;
  }
}
