import Link from "next/link";
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
  const linkClass = "rounded-full border border-foreground/70 px-4 py-1.5 hover:bg-foreground hover:text-surface";
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3 text-sm">
      <span className="text-muted">
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} total
      </span>
      <span className="flex gap-2">
        {!page.first && <Link className={linkClass} href={href(page.page - 1)}>Previous</Link>}
        {!page.last && <Link className={linkClass} href={href(page.page + 1)}>Next</Link>}
      </span>
    </nav>
  );
}
