import Link from "next/link";
import type { ArticleSummary } from "@/types/support";

/** A list of help articles linking to {@code base}/{slug}. */
export function ArticleList({ articles, base, empty = "No articles yet." }: { articles: ArticleSummary[]; base: string; empty?: string }) {
  if (articles.length === 0) return <p className="text-sm text-muted">{empty}</p>;
  return (
    <ul className="divide-y divide-border">
      {articles.map((a) => (
        <li key={a.id} className="py-3">
          <Link href={`${base}/${a.slug}`} className="font-medium text-gold-text hover:underline">{a.title}</Link>
          <p className="text-sm text-muted">{a.summary}</p>
          <p className="mt-1 text-xs text-muted">{a.categoryName}</p>
        </li>
      ))}
    </ul>
  );
}
