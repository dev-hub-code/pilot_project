import "server-only";
import type { Money } from "@/types/marketplace";
import { backendFetch } from "./backend-client";

/** The platform's track record, computed by the backend from its books. */
export interface PlatformStats {
  paidToInvestors: Money;
  /** Containers whose rent has started being credited. */
  containersEarning: number;
  investors: number;
  containers: number;
}

/** Null when the backend cannot be reached, so the home page still renders without the figures. */
export async function getPlatformStats(): Promise<PlatformStats | null> {
  try {
    return await backendFetch<PlatformStats>("/api/v1/public/stats");
  } catch {
    return null;
  }
}
