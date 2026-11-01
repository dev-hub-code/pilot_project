import "server-only";
import { headers } from "next/headers";
import type { PageResponse } from "@/types/api";
import type { PublicPlan } from "@/types/plan";
import { backendFetch } from "./backend-client";
import { forwardedClientHeaders } from "./request-context";

/** The open online plans for the home page; empty when the backend cannot be reached, so the page still renders. */
export async function getPublicPlans(): Promise<PublicPlan[]> {
  try {
    return await backendFetch<PublicPlan[]>("/api/v1/public/plans");
  } catch {
    return [];
  }
}

/**
 * Online plans from the backend's open API, a page at a time. The visitor's address is passed on, so its per-visitor
 * limit applies to them.
 */
export async function openPlans(query: URLSearchParams): Promise<PageResponse<PublicPlan>> {
  return backendFetch(`/api/v1/open/plans?${query}`, { headers: forwardedClientHeaders(await headers()) });
}
