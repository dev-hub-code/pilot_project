import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ArticleEditor } from "@/features/admin/article-editor";
import { articleStateAction, saveArticleAction } from "@/features/admin/knowledge-base-actions";
import { SupportShell } from "@/features/admin/support-shell";
import { ArticleBody } from "@/features/knowledge-base/article-body";
import { ActionButton } from "@/features/orders/action-button";
import { Permission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Article, ArticleFeedbackStats, ArticleSummary, ArticleVersion, KbCategory, KbPage } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Edit article" };

export default async function EditArticlePage({ params, searchParams }: PageProps<"/admin/support/knowledge-base/[id]">) {
  const denied = await deniedUnless(Permission.KNOWLEDGE_BASE_MANAGE, Permission.SYSTEM_SETTINGS_MANAGE);
  if (denied) return denied;
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const shown = Number.parseInt(String((await searchParams).version ?? ""), 10);
  let article: Article;
  try {
    article = await authFetch<Article>(`/api/v1/admin/knowledge-base/articles/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const [categories, candidates, versions, feedback] = await Promise.all([
    authFetch<KbCategory[]>("/api/v1/admin/knowledge-base/categories"),
    authFetch<KbPage<ArticleSummary>>("/api/v1/admin/knowledge-base/articles?status=PUBLISHED&size=100"),
    authFetch<ArticleVersion[]>(`/api/v1/admin/knowledge-base/articles/${id}/versions`),
    authFetch<ArticleFeedbackStats>(`/api/v1/admin/knowledge-base/articles/${id}/feedback`),
  ]);
  const a = article.summary;
  const old = versions.find((v) => v.version === shown && v.version !== article.currentVersion);
  return (
    <SupportShell active="knowledge-base">
      <Link href="/admin/support/knowledge-base" className="text-sm text-muted hover:underline">Knowledge base</Link>
      <PageHeader title={a.title}
        description={`Version ${article.currentVersion} · by ${article.authorName}, last edited by ${article.updatedByName}${a.status === "PUBLISHED" && a.visibility !== "INTERNAL" ? ` · live at /support/articles/${a.slug}` : ""}`}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge tone={a.status === "PUBLISHED" ? "success" : a.status === "DRAFT" ? "warning" : "neutral"}>{a.status.charAt(0) + a.status.slice(1).toLowerCase()}</StatusBadge>
            {a.status !== "PUBLISHED" && <ActionButton action={articleStateAction.bind(null, a.id, "publish")} label="Publish" />}
            {a.status === "PUBLISHED" && <ActionButton action={articleStateAction.bind(null, a.id, "unpublish")} label="Unpublish" variant="secondary" />}
            {a.status !== "ARCHIVED" && <ActionButton action={articleStateAction.bind(null, a.id, "archive")} label="Archive" variant="secondary"
              confirm="Archive this article? Readers can no longer open it; you can publish it again later." />}
          </div>
        } />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_20rem]">
        <Card><ArticleEditor action={saveArticleAction.bind(null, a.id, article.currentVersion)} article={article} categories={categories} candidates={candidates.content} /></Card>
        <div className="space-y-6">
          <Card title="Reader feedback">
            <p className="text-sm"><span className="font-semibold">{feedback.helpful}</span> found it helpful · <span className="font-semibold">{feedback.notHelpful}</span> did not · {a.views} views</p>
            {feedback.comments.length > 0 && (
              <ul className="mt-3 space-y-2 text-sm">
                {feedback.comments.map((c, i) => (
                  <li key={i} className="border-l-2 border-border pl-2">
                    <span className="text-muted">{c.helpful ? "Helpful" : "Not helpful"} · {formatDateTime(c.createdAt)}</span>
                    <p>{c.comment}</p>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title="Versions">
            <ol className="space-y-2 text-sm">
              {versions.map((v) => (
                <li key={v.version}>
                  {v.version === article.currentVersion ? <span className="font-medium">v{v.version} (current)</span>
                    : <Link href={`/admin/support/knowledge-base/${a.id}?version=${v.version}`} className="text-gold-text hover:underline">v{v.version}</Link>}
                  <span className="block text-xs text-muted">{v.editedByName} · {formatDateTime(v.createdAt)}</span>
                </li>
              ))}
            </ol>
          </Card>
        </div>
      </div>
      {old && (
        <Card title={`Version ${old.version}: ${old.title}`} description={`${old.editedByName} · ${formatDateTime(old.createdAt)}. To restore it, copy its text into the editor and save.`}>
          <p className="mb-3 text-muted">{old.summary}</p>
          <ArticleBody body={old.body} />
        </Card>
      )}
    </SupportShell>
  );
}
