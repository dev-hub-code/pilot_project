import "server-only";
import { cache } from "react";
import { authFetch } from "@/lib/server/auth/session";
import type { CatalogCategory, CatalogEntry } from "@/types/analytics";

/** The reports this viewer may open (the backend filters by their permissions); fetched once per request. */
export const loadCatalog = cache(() => authFetch<CatalogCategory[]>("/api/v1/admin/analytics/catalog"));

export async function findReport(key: string): Promise<{ category: CatalogCategory; report: CatalogEntry } | null> {
  for (const category of await loadCatalog()) {
    const report = category.reports.find((r) => r.key === key);
    if (report) return { category, report };
  }
  return null;
}
