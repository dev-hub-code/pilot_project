import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { ArticleBody } from "@/features/knowledge-base/article-body";
import { ArticleList } from "@/features/knowledge-base/article-list";
import { HelpfulButtons } from "@/features/knowledge-base/helpful-buttons";
import { articleFeedbackAction } from "@/features/support/actions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { Article } from "@/types/support";
import { formatDate } from "@/utils/format";

export const metadata: Metadata = { title: "Help article" };

export default async function ArticlePage({ params }: PageProps<"/support/articles/[slug]">) {
  const { slug } = await params;
  if (!/^[a-z0-9-]{1,120}$/.test(slug)) notFound();
  let article: Article;
  try {
    article = await authFetch<Article>(`/api/v1/support/knowledge-base/articles/${slug}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const a = article.summary;
  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_18rem]">
      <article className="min-w-0 space-y-5">
        <nav className="text-sm text-muted">
          <Link href="/support" className="hover:underline">Help &amp; support</Link> ›{" "}
          <Link href={`/support/articles?category=${a.categoryCode}`} className="hover:underline">{a.categoryName}</Link>
        </nav>
        <header className="space-y-2">
          <h1 className="text-3xl font-semibold tracking-tight">{a.title}</h1>
          <p className="text-muted">{a.summary}</p>
          <p className="text-xs text-muted">Updated {formatDate(a.updatedAt)}</p>
        </header>
        <ArticleBody body={article.body} />
        <div className="border-t border-border pt-5">
          <HelpfulButtons vote={articleFeedbackAction.bind(null, a.slug)} />
        </div>
      </article>
      <aside className="space-y-6">
        <Card title="Related articles">
          <ArticleList articles={article.related} base="/support/articles" empty="No related articles." />
        </Card>
        <Card title="Still need help?">
          <p className="text-sm text-muted">Our support team will get back to you.</p>
          <Link href="/support/new" className="mt-3 inline-flex text-sm font-medium text-gold-text hover:underline">Open a request</Link>
        </Card>
      </aside>
    </div>
  );
}
