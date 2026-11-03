import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ArticleList } from "@/features/knowledge-base/article-list";
import { CATEGORY_LABEL, CUSTOMER_TONE, TICKET_STATUS_LABEL } from "@/features/support/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ArticleSummary, KbCategory, KbPage, Ticket } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Help & support" };

export default async function SupportPage({ searchParams }: PageProps<"/support">) {
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const [tickets, categories, popular, recent] = await Promise.all([
    authFetch<PageResponse<Ticket>>(`/api/v1/support/tickets?page=${page}&size=10`),
    authFetch<KbCategory[]>("/api/v1/support/knowledge-base/categories"),
    authFetch<KbPage<ArticleSummary>>("/api/v1/support/knowledge-base/articles?sort=popular&size=5"),
    authFetch<KbPage<ArticleSummary>>("/api/v1/support/knowledge-base/articles?sort=recent&size=5"),
  ]);
  const withArticles = categories.filter((c) => c.articles > 0);
  return (
    <div className="space-y-8">
      <PageHeader title="Help & support" description="Find an answer in our help articles, or ask our support team."
        actions={<LinkButton href="/support/new">New request</LinkButton>} />

      <section aria-labelledby="search-heading" className="space-y-4 rounded-xl border border-border bg-surface p-5">
        <h2 id="search-heading" className="text-lg font-semibold tracking-tight">Search help articles</h2>
        <form action="/support/articles" className="flex flex-wrap gap-3">
          <label htmlFor="kb-q" className="sr-only">Search</label>
          <input id="kb-q" name="q" type="search" placeholder="e.g. withdrawal, KYC, agreement" maxLength={200}
            className="min-w-0 flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
          <button type="submit" className="rounded-lg bg-gold px-4 py-2 text-sm font-medium text-white hover:bg-gold/90">Search</button>
        </form>
        {withArticles.length > 0 && (
          <ul className="flex flex-wrap gap-2 text-sm" aria-label="Browse by topic">
            {withArticles.map((c) => (
              <li key={c.id}>
                <Link href={`/support/articles?category=${c.code}`} className="inline-flex min-h-9 items-center rounded-full border border-border px-3 hover:border-foreground/40">
                  {c.name} <span className="ml-1.5 text-xs text-muted">{c.articles}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {(popular.content.length > 0 || recent.content.length > 0) && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title="Popular articles"><ArticleList articles={popular.content} base="/support/articles" /></Card>
          <Card title="Recently updated"><ArticleList articles={recent.content} base="/support/articles" /></Card>
        </div>
      )}

      <section aria-labelledby="requests-heading" className="space-y-3">
        <h2 id="requests-heading" className="text-lg font-semibold tracking-tight">Your requests</h2>
        {tickets.content.length === 0 ? (
          <EmptyState title="No support requests" description="Open one whenever you need help." />
        ) : (
          <>
            <DataTable columns={["Request", "About", "Status", "Last update"]}>
              {tickets.content.map((t) => (
                <tr key={t.id} className="hover:bg-background">
                  <Cell>
                    <Link href={`/support/${t.id}`} className="card-link font-medium text-gold-text hover:underline">{t.subject}</Link>
                    <span className="block font-mono text-xs text-muted">{t.reference}</span>
                  </Cell>
                  <Cell className="text-muted">{CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` · ${t.subcategoryName}` : ""}{t.relatedLabel ? ` · ${t.relatedLabel}` : ""}</Cell>
                  <Cell><StatusBadge tone={CUSTOMER_TONE[t.status]}>{TICKET_STATUS_LABEL[t.status]}</StatusBadge></Cell>
                  <Cell className="text-muted">{formatDateTime(t.lastMessageAt)}</Cell>
                </tr>
              ))}
            </DataTable>
            <Pagination page={tickets} basePath="/support" params={{}} />
          </>
        )}
      </section>
    </div>
  );
}
