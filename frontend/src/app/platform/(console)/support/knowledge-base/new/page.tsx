import type { Metadata } from "next";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ArticleEditor } from "@/features/admin/article-editor";
import { saveArticleAction } from "@/features/admin/knowledge-base-actions";
import { SupportShell } from "@/features/admin/support-shell";
import { Permission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { ArticleSummary, KbCategory, KbPage } from "@/types/support";

export const metadata: Metadata = { title: "New article" };

export default async function NewArticlePage() {
  const denied = await deniedUnless(Permission.KNOWLEDGE_BASE_MANAGE, Permission.SYSTEM_SETTINGS_MANAGE);
  if (denied) return denied;
  const [categories, candidates] = await Promise.all([
    authFetch<KbCategory[]>("/api/v1/admin/knowledge-base/categories"),
    authFetch<KbPage<ArticleSummary>>("/api/v1/admin/knowledge-base/articles?status=PUBLISHED&size=100"),
  ]);
  return (
    <SupportShell active="knowledge-base">
      <PageHeader title="New article" description="Saved as a draft; publish it when it is ready." />
      <Card><ArticleEditor action={saveArticleAction.bind(null, null, 0)} article={null} categories={categories} candidates={candidates.content} /></Card>
    </SupportShell>
  );
}
