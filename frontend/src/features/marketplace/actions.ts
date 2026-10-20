"use server";

import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { ReturnProjection } from "@/types/marketplace";

/** The return calculator's figures for {@code containers} of a plan over {@code years}; null when they cannot be had. */
export async function projectReturnAction(productId: string, containers: number, years: number): Promise<ReturnProjection | null> {
  if (!isUuid(productId) || !Number.isInteger(containers) || containers < 1 || containers > 50
    || !Number.isInteger(years) || years < 1 || years > 30) {
    return null;
  }
  try {
    return await authFetch<ReturnProjection>(`/api/v1/marketplace/${productId}/projection?containers=${containers}&years=${years}`);
  } catch {
    return null;
  }
}
