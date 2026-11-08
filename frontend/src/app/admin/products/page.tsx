import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { AUDIENCE_LABEL, PRODUCT_STATUS_LABEL, PRODUCT_TONE } from "@/features/plans/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Product, ProductStatus } from "@/types/plan";
import { yearsLabel } from "@/features/orders/lease-years";
import { formatMoney, formatPercent } from "@/utils/money";
import { ArrowUpRight, Globe, Network, Plus } from "lucide-react";
import type { PlanAudience } from "@/types/plan";

export const metadata: Metadata = { title: "Investment plans" };

const STATUSES: ProductStatus[] = ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];

const AUDIENCE_ICON: Record<PlanAudience, typeof Globe> = { ONLINE: Globe, NETWORK: Network };

export default async function ProductsPage({ searchParams }: PageProps<"/admin/products">) {
  const denied = await deniedUnlessInMenu("/admin/products");
  if (denied) return denied;
  const session = await requireStaff();
  const params = await searchParams;
  const status = STATUSES.find((s) => s === params.status);
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (status) query.set("status", status);
  const products = await authFetch<PageResponse<Product>>(`/api/v1/admin/investment-products?${query}`);
  // Each investor type sees only its own plans, so they are listed apart: online first.
  const groups = (["ONLINE", "NETWORK"] as const)
    .map((audience) => ({ audience, plans: products.content.filter((p) => p.audience === audience) }))
    .filter((g) => g.plans.length > 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Investment plans" description="One plan per tenure for each investor type, with its annual return and minimum units. One unit costs ₹500 on every plan. Online and network investors each see only their own plans."
        actions={hasPermission(session.permissions, Permission.INVESTMENT_CREATE)
          ? <LinkButton href="/admin/products/new" className="gap-2"><Plus aria-hidden="true" className="size-4" /> New plan</LinkButton>
          : undefined} />
      <nav aria-label="Filter by status" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
        {[undefined, ...STATUSES].map((s) => (
          <Link key={s ?? "all"} href={s ? `/admin/products?status=${s}` : "/admin/products"} aria-current={s === status ? "page" : undefined}
            className={`rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${s === status
              ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
              : "text-muted hover:bg-background hover:text-foreground"}`}>
            {s ? PRODUCT_STATUS_LABEL[s] : "All"}
          </Link>
        ))}
      </nav>
      {products.content.length === 0 ? (
        <EmptyState title="No plans" description={status ? "No plans have this status." : undefined} />
      ) : groups.map(({ audience, plans }) => {
        const Icon = AUDIENCE_ICON[audience];
        return (
          <section key={audience} aria-labelledby={`plans-${audience}`} className="space-y-4">
            <h2 id={`plans-${audience}`} className="flex items-center gap-3 text-lg font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <Icon className="size-[18px]" />
              </span>
              {AUDIENCE_LABEL[audience]}
              <span className="rounded-full bg-background px-2 py-0.5 text-xs font-medium text-muted tabular-nums ring-1 ring-border">{plans.length}</span>
              <span aria-hidden="true" className="h-px flex-1 bg-gradient-to-r from-border to-transparent" />
            </h2>
            <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {plans.map((p) => {
                const live = p.status === "OPEN";
                return (
                  <li key={p.id}
                    className={`group relative flex flex-col overflow-hidden rounded-2xl border bg-surface shadow-sm transition duration-300 hover:-translate-y-0.5 hover:shadow-lg ${live ? "border-border hover:border-gold/40" : "border-border/70"}`}>
                    <div className={`relative isolate flex items-start gap-4 overflow-hidden px-5 pt-5 pb-4 ${live ? "bg-ink text-on-ink" : "bg-background text-foreground"}`}>
                      {live && <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[radial-gradient(circle_at_100%_0%,var(--gold)_0%,transparent_60%)] opacity-30" />}
                      <span aria-hidden="true"
                        className={`flex size-14 shrink-0 flex-col items-center justify-center rounded-2xl font-display leading-none font-semibold ${live ? "bg-on-ink/10 text-gold-on-ink" : "bg-surface text-muted ring-1 ring-border"}`}>
                        <span className="text-xl">{p.tenureYears}</span>
                        <span className="mt-0.5 text-[10px] tracking-wider uppercase">{p.tenureYears === 1 ? "year" : "years"}</span>
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-2">
                          <Link href={`/admin/products/${p.id}`} className="card-link truncate font-display font-semibold tracking-wide">{p.title}</Link>
                          <ArrowUpRight aria-hidden="true" className={`size-4 shrink-0 transition duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 ${live ? "text-on-ink/60" : "text-muted"}`} />
                        </div>
                        <p className={`mt-0.5 font-mono text-xs ${live ? "text-on-ink/60" : "text-muted"}`}>{p.code}</p>
                        <p className={`mt-2 font-display text-3xl font-semibold tracking-tight tabular-nums ${live ? "text-gold-on-ink" : ""}`}>
                          {formatPercent(p.annualReturnPercent)}
                          <span className={`ml-1.5 text-xs font-normal ${live ? "text-on-ink/60" : "text-muted"}`}>a year</span>
                        </p>
                      </div>
                    </div>
                    <dl className="grid flex-1 grid-cols-3 divide-x divide-border border-t border-border text-center text-sm">
                      <div className="px-2 py-3">
                        <dt className="text-[11px] text-muted">Min. units</dt>
                        <dd className="mt-0.5 font-semibold tabular-nums">{p.minUnits}</dd>
                      </div>
                      <div className="px-2 py-3">
                        <dt className="text-[11px] text-muted">Starts at</dt>
                        <dd className="mt-0.5 font-semibold tabular-nums">
                          {formatMoney({ amount: (p.unitPrice * p.minUnits).toFixed(2), currency: p.currency }, { compact: true })}
                        </dd>
                      </div>
                      <div className="px-2 py-3">
                        <dt className="text-[11px] text-muted">Investments</dt>
                        <dd className="mt-0.5 font-semibold tabular-nums">{p.unitsSold.toLocaleString("en-IN")}</dd>
                      </div>
                    </dl>
                    <div className="flex items-center justify-between gap-2 border-t border-border px-5 py-3 text-xs text-muted">
                      <span>{yearsLabel(p.tenureYears)} · {AUDIENCE_LABEL[p.audience]}</span>
                      <StatusBadge tone={PRODUCT_TONE[p.status]}>{PRODUCT_STATUS_LABEL[p.status]}</StatusBadge>
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        );
      })}
      <Pagination page={products} basePath="/admin/products" params={{ status }} />
    </div>
  );
}
