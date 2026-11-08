import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, BookOpen, ChevronRight, LifeBuoy, MessageSquareText, MessageSquareWarning, Plus, Search, TrendingUp } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { CATEGORY_LABEL, CUSTOMER_TONE, TICKET_STATUS_LABEL } from "@/features/support/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ArticleSummary, KbCategory, KbPage, Ticket } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Help & support" };

/** Requests that are finished: shown quieter than the ones still open. */
const DONE = new Set(["RESOLVED", "CLOSED"]);

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
  const awaiting = tickets.content.filter((t) => t.status === "WAITING_ON_CUSTOMER");
  // Recently updated, without the ones already listed as popular.
  const popularIds = new Set(popular.content.map((a) => a.id));
  const fresh = recent.content.filter((a) => !popularIds.has(a.id));

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------------ banner + search */}
      <section aria-labelledby="support-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="savannah-cranes" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover object-[center_65%] saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.9)_55%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="space-y-6 p-6 sm:p-8 lg:p-10">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div className="space-y-3">
              <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Help &amp; support</p>
              <h1 id="support-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
                How can we <span className="text-gold-on-ink">help?</span>
              </h1>
              <p className="max-w-lg text-sm leading-relaxed text-on-ink/75">Find an answer in our help articles, or ask our support team.</p>
            </div>
            <Link href="/support/new"
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
              <Plus aria-hidden="true" className="size-4" /> New request
            </Link>
          </div>
          <form action="/support/articles" role="search" className="flex max-w-2xl items-center gap-2 rounded-2xl border border-on-ink/20 bg-on-ink/10 p-1.5 backdrop-blur-md focus-within:border-gold-on-ink/60 focus-within:bg-on-ink/15">
            <label htmlFor="kb-q" className="sr-only">Search help articles</label>
            <Search aria-hidden="true" className="ml-3 size-5 shrink-0 text-on-ink/60" />
            <input id="kb-q" name="q" type="search" placeholder="e.g. withdrawal, KYC, agreement" maxLength={200}
              className="min-w-0 flex-1 bg-transparent px-1 py-2.5 text-sm text-on-ink placeholder:text-on-ink/50 focus:outline-none" />
            <button type="submit"
              className="rounded-xl bg-gold px-5 py-2.5 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
          </form>
          {withArticles.length > 0 && (
            <ul className="flex flex-wrap gap-2 text-sm" aria-label="Browse by topic">
              {withArticles.map((c) => (
                <li key={c.id}>
                  <Link href={`/support/articles?category=${c.code}`}
                    className="inline-flex min-h-9 items-center gap-2 rounded-full border border-on-ink/20 bg-on-ink/[0.06] px-3.5 text-on-ink/90 backdrop-blur-md transition-colors hover:border-on-ink/40 hover:bg-on-ink/15">
                    {c.name} <span className="rounded-full bg-on-ink/15 px-1.5 text-xs tabular-nums">{c.articles}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {/* ---------------------------------------------------- waiting on your reply */}
      {awaiting.length > 0 && (
        <section aria-labelledby="awaiting-title" className="rounded-2xl border border-amber-300/70 bg-amber-50/70 p-5 dark:border-amber-500/30 dark:bg-amber-500/[0.07] sm:p-6">
          <h2 id="awaiting-title" className="flex items-center gap-2 text-sm font-semibold text-amber-900 dark:text-amber-200">
            <MessageSquareWarning aria-hidden="true" className="size-4" />
            {awaiting.length === 1 ? "Our team is waiting for your reply" : `Our team is waiting for your reply on ${awaiting.length} requests`}
          </h2>
          <ul className="mt-4 grid gap-3 md:grid-cols-2">
            {awaiting.map((t) => (
              <li key={t.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-surface p-4 dark:border-amber-500/20">
                <div className="min-w-0 text-sm">
                  <p className="truncate font-semibold">{t.subject}</p>
                  <p className="text-xs text-muted"><span className="font-mono">{t.reference}</span> · {formatDateTime(t.lastMessageAt)}</p>
                </div>
                <Link href={`/support/${t.id}`}
                  className="inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
                  Reply <ArrowRight aria-hidden="true" className="size-4" />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        {/* -------------------------------------------------------------- requests */}
        <section aria-labelledby="requests-heading" className="overflow-hidden rounded-3xl border border-border bg-surface shadow-sm lg:self-start">
          <div className="flex items-center justify-between gap-3 border-b border-border px-5 py-4 sm:px-6">
            <h2 id="requests-heading" className="flex items-center gap-2.5 text-lg font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><MessageSquareText className="size-4" /></span>
              Your requests
            </h2>
            {tickets.totalElements > 0 && <span className="text-sm text-muted tabular-nums">{tickets.totalElements}</span>}
          </div>
          {tickets.content.length === 0 ? (
            <div className="space-y-4 p-6 text-center">
              <EmptyState title="No support requests" description="Open one whenever you need help." />
              <Link href="/support/new" className="inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
                <Plus aria-hidden="true" className="size-4" /> New request
              </Link>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {tickets.content.map((t) => {
                const done = DONE.has(t.status);
                return (
                  <li key={t.id} className="group flex items-center gap-4 px-5 py-4 transition-colors hover:bg-background sm:px-6">
                    <div className="min-w-0 flex-1 space-y-1">
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                        <Link href={`/support/${t.id}`} className={`card-link truncate font-semibold ${done ? "text-muted" : ""}`}>{t.subject}</Link>
                        <StatusBadge tone={CUSTOMER_TONE[t.status]}>{TICKET_STATUS_LABEL[t.status]}</StatusBadge>
                      </div>
                      <p className="text-xs text-muted">
                        {CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` · ${t.subcategoryName}` : ""}{t.relatedLabel ? ` · ${t.relatedLabel}` : ""}
                      </p>
                      <p className="text-xs text-muted"><span className="font-mono">{t.reference}</span> · Updated {formatDateTime(t.lastMessageAt)}</p>
                    </div>
                    <ChevronRight aria-hidden="true" className="size-5 shrink-0 text-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-gold-text" />
                  </li>
                );
              })}
            </ul>
          )}
          {tickets.totalPages > 1 && <div className="border-t border-border px-5 py-3 sm:px-6"><Pagination page={tickets} basePath="/support" params={{}} /></div>}
        </section>

        {/* -------------------------------------------------------------- articles */}
        <div className="space-y-6">
          {popular.content.length > 0 && <ArticleCard title="Popular articles" icon={<TrendingUp className="size-4" />} articles={popular.content} />}
          {fresh.length > 0 && <ArticleCard title="Recently updated" icon={<BookOpen className="size-4" />} articles={fresh} />}
          <div className="relative isolate overflow-hidden rounded-3xl bg-[linear-gradient(140deg,var(--gold),color-mix(in_oklab,var(--gold)_55%,var(--ink)))] p-6 text-on-gold shadow-lg">
            <LifeBuoy aria-hidden="true" className="absolute -right-6 -bottom-6 -z-10 size-32 opacity-15" />
            <p className="font-semibold">Can&apos;t find an answer?</p>
            <p className="mt-1 text-sm opacity-85">Open a request and our support team will get back to you.</p>
            <Link href="/support/new"
              className="group mt-4 inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-on-gold/15 px-4 text-sm font-semibold backdrop-blur-md transition-colors hover:bg-on-gold/25">
              Contact support <ArrowRight aria-hidden="true" className="size-4 transition-transform group-hover:translate-x-0.5" />
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}

function ArticleCard({ title, icon, articles }: { title: string; icon: React.ReactNode; articles: readonly ArticleSummary[] }) {
  return (
    <section className="rounded-3xl border border-border bg-surface p-5 shadow-sm sm:p-6">
      <h2 className="flex items-center gap-2.5 font-semibold tracking-tight">
        <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text">{icon}</span>
        {title}
      </h2>
      <ul className="mt-3 -mx-2">
        {articles.map((a) => (
          <li key={a.id}>
            <Link href={`/support/articles/${a.slug}`} className="group flex items-start gap-3 rounded-xl px-2 py-2.5 transition-colors hover:bg-background">
              <span className="min-w-0 flex-1">
                <span className="block text-sm font-medium group-hover:text-gold-text">{a.title}</span>
                <span className="mt-0.5 line-clamp-2 block text-xs text-muted">{a.summary}</span>
                <span className="mt-1 block text-[11px] font-medium tracking-wide text-gold-text/80 uppercase">{a.categoryName}</span>
              </span>
              <ChevronRight aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted transition-transform group-hover:translate-x-0.5 group-hover:text-gold-text" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
