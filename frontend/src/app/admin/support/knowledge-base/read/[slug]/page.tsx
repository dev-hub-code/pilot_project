import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { SupportShell } from "@/features/admin/support-shell";
import { ArticleBody } from "@/features/knowledge-base/article-body";
import { ArticleList } from "@/features/knowledge-base/article-list";
import { Permission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { Article } from "@/types/support";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = { title: "Article" };

export default async function StaffArticlePage({ params }: PageProps<"/admin/support/knowledge-base/read/[slug]">) {
  const denied = await deniedUnless(Permission.KNOWLEDGE_BASE_MANAGE, Permission.SYSTEM_SETTINGS_MANAGE,
    Permission.SUPPORT_TICKET_VIEW, Permission.FINANCE_TICKET_HANDLE);
  if (denied) return denied;
  const { slug } = await params;
  if (!/^[a-z0-9-]{1,120}$/.test(slug)) notFound();
  let article: Article;
  try {
    article = await authFetch<Article>(`/api/v1/admin/knowledge-base/read/${slug}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const a = article.summary;
  return (
    <SupportShell active="knowledge-base">
      <div className="grid gap-8 lg:grid-cols-[1fr_18rem]">
        <article className="min-w-0 space-y-4">
          <Link href="/admin/support/knowledge-base" className="text-sm text-muted hover:underline">Knowledge base</Link>
          <h1 className="text-3xl font-semibold tracking-tight">{a.title}</h1>
          <p className="text-muted">{a.summary}</p>
          <p className="text-xs text-muted">{a.categoryName} · {a.visibility === "INTERNAL" ? "Internal: do not share with customers" : `Customers can read it at /support/articles/${a.slug}`} · updated {formatDate(a.updatedAt)}</p>
          <ArticleBody body={article.body} />
        </article>
        <Card title="Related"><ArticleList articles={article.related} base="/admin/support/knowledge-base/read" empty="None." /></Card>
      </div>
    </SupportShell>
  );
}
