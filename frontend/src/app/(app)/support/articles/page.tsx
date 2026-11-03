import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ArticleList } from "@/features/knowledge-base/article-list";
import { authFetch } from "@/lib/server/auth/session";
import type { ArticleSummary, KbCategory, KbPage } from "@/types/support";

export const metadata: Metadata = { title: "Help articles" };

export default async function ArticlesPage({ searchParams }: PageProps<"/support/articles">) {
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.slice(0, 200) : "";
  const category = typeof params.category === "string" ? params.category : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "20", sort: "popular" });
  if (q) query.set("q", q);
  if (category) query.set("category", category);
  const [results, categories] = await Promise.all([
    authFetch<KbPage<ArticleSummary>>(`/api/v1/support/knowledge-base/articles?${query}`),
    authFetch<KbCategory[]>("/api/v1/support/knowledge-base/categories"),
  ]);
  const current = categories.find((c) => c.code === category);
  const pages = Math.ceil(results.totalElements / results.size);
  const link = (p: number) => `/support/articles?${new URLSearchParams({ ...(q ? { q } : {}), ...(category ? { category } : {}), page: String(p) })}`;
  return (
    <div className="space-y-6">
      <Link href="/support" className="text-sm text-muted hover:underline">Help &amp; support</Link>
      <PageHeader title={current ? current.name : q ? `Results for “${q}”` : "Help articles"}
        description={current?.description ?? `${results.totalElements} article${results.totalElements === 1 ? "" : "s"}`} />
      <form action="/support/articles" className="flex flex-wrap gap-3">
        {category && <input type="hidden" name="category" value={category} />}
        <label htmlFor="kb-q" className="sr-only">Search</label>
        <input id="kb-q" name="q" type="search" defaultValue={q} maxLength={200} placeholder="Search help articles"
          className="min-w-0 flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        <button type="submit" className="rounded-lg bg-gold px-4 py-2 text-sm font-medium text-white hover:bg-gold/90">Search</button>
      </form>
      <Card>
        <ArticleList articles={results.content} base="/support/articles"
          empty="No article matches. Try other words, or ask our support team." />
      </Card>
      {pages > 1 && (
        <nav className="flex gap-3 text-sm" aria-label="Pages">
          {page > 0 && <Link href={link(page - 1)} className="text-gold-text hover:underline">Previous</Link>}
          <span className="text-muted">Page {page + 1} of {pages}</span>
          {page + 1 < pages && <Link href={link(page + 1)} className="text-gold-text hover:underline">Next</Link>}
        </nav>
      )}
      <p className="text-sm text-muted">Didn&apos;t find it? <Link href="/support/new" className="text-gold-text hover:underline">Ask our support team</Link>.</p>
    </div>
  );
}
