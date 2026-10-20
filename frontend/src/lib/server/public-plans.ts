import "server-only";
import type { Money } from "@/types/marketplace";
import { backendFetch } from "./backend-client";

/** An open container listing's published terms, as the backend gives them to the website. No rent: it is set by each
 * order's total value. */
export interface PublicPlan {
  code: string;
  title: string;
  summary: string | null;
  containerType: string;
  containerTypeLabel: string;
  price: Money;
  minTenureYears: number;
  maxTenureYears: number;
}

/** Empty when the backend cannot be reached, so the home page still renders without the plans. */
export async function getPublicPlans(): Promise<PublicPlan[]> {
  try {
    return await backendFetch<PublicPlan[]>("/api/v1/public/plans");
  } catch {
    return [];
  }
}
