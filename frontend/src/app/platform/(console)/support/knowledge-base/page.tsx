import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnless } from "@/components/layout/page-access";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge, type StatusTone } from "@/components/ui/status-badge";
import { SupportShell, supportAbilities } from "@/features/admin/support-shell";
import { Permission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { ArticleStatus, ArticleSummary, ArticleVisibility, KbCategory, KbPage } from "@/types/support";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = { title: "Knowledge base" };

const VISIBILITY_LABEL: Record<ArticleVisibility, string> = { PUBLIC: "Public", CUSTOMER: "Customers", INTERNAL: "Internal" };
const STATUS_TONE: Record<ArticleStatus, StatusTone> = { DRAFT: "warning", PUBLISHED: "success", ARCHIVED: "neutral" };

export default async function KnowledgeBasePage({ searchParams }: PageProps<"/platform/support/knowledge-base">) {
  const denied = await deniedUnless(Permission.KNOWLEDGE_BASE_MANAGE, Permission.SYSTEM_SETTINGS_MANAGE,
    Permission.SUPPORT_TICKET_VIEW, Permission.FINANCE_TICKET_HANDLE);
  if (denied) return denied;
  const can = supportAbilities((await requireStaff()).permissions);
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 200) : "";
  const category = typeof params.category === "string" ? params.category : "";
  const status = can.editArticles && typeof params.status === "string" && ["DRAFT", "PUBLISHED", "ARCHIVED"].includes(params.status) ? params.status : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (q) query.set("q", q);
  if (category) query.set("category", category);
  if (status) query.set("status", status);
  // Editors see every draft and archive; others read what is published.
  const [articles, categories] = await Promise.all([
    authFetch<KbPage<ArticleSummary>>(can.editArticles ? `/api/v1/admin/knowledge-base/articles?${query}`
      : `/api/v1/admin/knowledge-base/search?${query}`),
    authFetch<KbCategory[]>("/api/v1/admin/knowledge-base/categories"),
  ]);
  const pages = Math.ceil(articles.totalElements / articles.size);
  const link = (p: number) => `/platform/support/knowledge-base?${new URLSearchParams({ q, category, status, page: String(p) })}`;
  return (
    <SupportShell active="knowledge-base">
      <PageHeader title="Knowledge base" description={can.editArticles
        ? "Help articles for the public help centre, signed-in customers, and employees. Every save keeps a version."
        : "Published articles, including internal ones, to help answer tickets."}
        actions={can.editArticles ? <LinkButton href="/platform/support/knowledge-base/new">New article</LinkButton> : undefined} />
      <form action="/platform/support/knowledge-base" className="grid gap-3 rounded-xl border border-border bg-surface p-4 sm:grid-cols-4">
        <label className="space-y-1 text-sm sm:col-span-2"><span className="block font-medium">Search</span>
          <input name="q" type="search" defaultValue={q} maxLength={200} className="w-full rounded-lg border border-border bg-surface px-3 py-2" /></label>
        <label className="space-y-1 text-sm"><span className="block font-medium">Category</span>
          <select name="category" defaultValue={category} className="w-full rounded-lg border border-border bg-surface px-3 py-2">
            <option value="">Any</option>
            {categories.map((c) => <option key={c.id} value={c.code}>{c.name}</option>)}
          </select></label>
        {can.editArticles && (
          <label className="space-y-1 text-sm"><span className="block font-medium">Status</span>
            <select name="status" defaultValue={status} className="w-full rounded-lg border border-border bg-surface px-3 py-2">
              <option value="">Any</option><option value="DRAFT">Draft</option><option value="PUBLISHED">Published</option><option value="ARCHIVED">Archived</option>
            </select></label>
        )}
        <div className="sm:col-span-4"><Button type="submit" variant="secondary">Filter</Button></div>
      </form>
      {articles.content.length === 0 ? <EmptyState title="No articles" description={q || category || status ? "Nothing matches these filters." : "No article yet."} /> : (
        <DataTable columns={["Article", "Category", "Audience", "Status", "Views", "Helpful", "Updated"]}>
          {articles.content.map((a) => (
            <tr key={a.id} className="hover:bg-background">
              <Cell wrap>
                <Link href={can.editArticles ? `/platform/support/knowledge-base/${a.id}` : `/platform/support/knowledge-base/read/${a.slug}`}
                  className="font-medium text-gold-text hover:underline">{a.title}</Link>
                <span className="block text-xs text-muted">{a.summary}</span>
              </Cell>
              <Cell>{a.categoryName}</Cell>
              <Cell>{VISIBILITY_LABEL[a.visibility]}</Cell>
              <Cell><StatusBadge tone={STATUS_TONE[a.status]}>{a.status.charAt(0) + a.status.slice(1).toLowerCase()}</StatusBadge></Cell>
              <Cell className="tabular-nums">{a.views}</Cell>
              <Cell className="tabular-nums">{a.helpful} / {a.helpful + a.notHelpful}</Cell>
              <Cell className="text-muted">{formatDate(a.updatedAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {pages > 1 && (
        <nav className="flex gap-3 text-sm" aria-label="Pages">
          {page > 0 && <Link href={link(page - 1)} className="text-gold-text hover:underline">Previous</Link>}
          <span className="text-muted">Page {page + 1} of {pages} · {articles.totalElements} articles</span>
          {page + 1 < pages && <Link href={link(page + 1)} className="text-gold-text hover:underline">Next</Link>}
        </nav>
      )}
    </SupportShell>
  );
}
