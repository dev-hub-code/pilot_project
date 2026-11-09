import Link from "next/link";
import type { ReactNode } from "react";
import { loadCatalog } from "./catalog";

const item = "relative block rounded-lg px-3 py-1.5 text-sm transition-colors duration-200";
const idle = `${item} text-muted hover:bg-background hover:text-foreground`;
const current = `${item} bg-gold/10 font-medium text-gold-text before:absolute before:inset-y-1.5 before:left-0 before:w-0.5 before:rounded-full before:bg-gold`;

/**
 * The reporting area: the categories and reports this viewer may open beside the page. Categories are collapsible;
 * the one holding the open report starts expanded. On phones the menu folds into one disclosure above the page.
 */
export async function ReportsShell({ active, children }: { active: string; children: ReactNode }) {
  const categories = await loadCatalog();
  const links = (
    <nav aria-label="Reports" className="space-y-1">
      <Link href="/platform/reports" className={active === "overview" ? current : idle} aria-current={active === "overview" ? "page" : undefined}>
        Overview
      </Link>
      {categories.map((category) => {
        const open = category.reports.some((r) => r.key === active);
        return (
          <details key={category.key} open={open} className="group">
            <summary className="flex cursor-pointer list-none items-center justify-between rounded-lg px-3 py-1.5 text-sm font-medium hover:bg-background [&::-webkit-details-marker]:hidden">
              {category.label}
              <span aria-hidden className="text-xs text-muted transition-transform group-open:rotate-90">›</span>
            </summary>
            <ul className="ml-3 mt-1 space-y-0.5 border-l border-border pl-2">
              {category.reports.map((r) => (
                <li key={r.key}>
                  <Link href={`/platform/reports/${r.key}`} className={r.key === active ? current : idle}
                    aria-current={r.key === active ? "page" : undefined}>{r.title}</Link>
                </li>
              ))}
            </ul>
          </details>
        );
      })}
      <div className="mt-3 space-y-1 border-t border-border pt-3">
        <Link href="/platform/reports/documents" className={active === "documents" ? current : idle}>Statements &amp; summaries</Link>
        <Link href="/platform/reports/history" className={active === "history" ? current : idle}>Download history</Link>
      </div>
    </nav>
  );
  return (
    <div className="grid gap-6 lg:grid-cols-[15rem_minmax(0,1fr)]">
      <aside className="lg:sticky lg:top-4 lg:self-start">
        <details className="rounded-2xl border border-border bg-surface p-2 shadow-sm lg:hidden">
          <summary className="cursor-pointer px-3 py-1.5 text-sm font-medium">All reports</summary>
          <div className="mt-2">{links}</div>
        </details>
        <div className="hidden max-h-[calc(100vh-2rem)] overflow-y-auto rounded-2xl border border-border bg-surface p-2 shadow-sm lg:block">
          {links}
        </div>
      </aside>
      <div className="min-w-0 space-y-6">{children}</div>
    </div>
  );
}
