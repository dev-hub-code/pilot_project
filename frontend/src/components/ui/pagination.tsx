import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import type { PageResponse } from "@/types/api";

/** Link-based pagination that preserves the current filters. */
export function Pagination({ page, basePath, params }: {
  page: PageResponse<unknown>;
  basePath: string;
  params: Record<string, string | undefined> | object;
}) {
  if (page.totalPages <= 1) return null;
  const href = (target: number) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) if (typeof value === "string" && value) query.set(key, value);
    query.set("page", String(target));
    return `${basePath}?${query.toString()}`;
  };
  const linkClass = "group inline-flex min-h-9 items-center gap-1 rounded-lg border border-border bg-surface px-3.5 font-medium shadow-sm transition-colors hover:border-gold hover:text-gold-text";
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 text-sm">
      <span className="text-muted">
        Page <span className="font-medium text-foreground tabular-nums">{page.page + 1}</span> of {page.totalPages} · {page.totalElements.toLocaleString("en-IN")} total
      </span>
      <span className="flex gap-2">
        {!page.first && <Link className={linkClass} href={href(page.page - 1)}><ChevronLeft aria-hidden="true" className="size-4 transition-transform group-hover:-translate-x-0.5" /> Previous</Link>}
        {!page.last && <Link className={linkClass} href={href(page.page + 1)}>Next <ChevronRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-0.5" /></Link>}
      </span>
    </nav>
  );
}
