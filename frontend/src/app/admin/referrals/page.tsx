import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, Coins, Globe, Network, Percent, Repeat, Settings2, UserRound } from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { RateVersion, ReferralEarning } from "@/types/referral";
import { formatDateTime, formatMonth } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referrals" };

/** Levels 1-4 from nearest to furthest, each a shade lighter. */
const LEVEL_TILE = [
  "bg-ink text-on-ink dark:bg-gold dark:text-on-gold",
  "bg-gold text-on-gold",
  "bg-gold/20 text-gold-text",
  "bg-gold/10 text-gold-text",
];

export default async function AdminReferralsPage({ searchParams }: PageProps<"/admin/referrals">) {
  const denied = await deniedUnlessInMenu("/admin/referrals");
  if (denied) return denied;
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const canSeeRates = can(Permission.FINANCE_VIEW) || can(Permission.REFERRAL_CONFIG_MANAGE);
  const [commissions, versions] = await Promise.all([
    can(Permission.FINANCE_VIEW)
      ? authFetch<PageResponse<ReferralEarning>>(`/api/v1/admin/referral-earnings?page=${page}&size=25`)
      : Promise.resolve(null),
    // The rates are a convenience here: their own page is where they are managed.
    canSeeRates ? authFetch<RateVersion[]>("/api/v1/admin/referral-rates").catch(() => null) : Promise.resolve(null),
  ]);
  const rates = versions?.find((v) => v.state === "IN_FORCE") ?? null;

  return (
    <div className="space-y-6">
      <PageHeader title="Referrals"
        description="How investors earn for the people they bring in, and the commissions paid so far."
        actions={(
          <LinkButton href="/admin/platform-config/referral-rates" variant="secondary" className="gap-2">
            <Settings2 aria-hidden="true" className="size-4" /> Referral rates
          </LinkButton>
        )} />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <section aria-labelledby="network-title" className="rounded-2xl border border-border bg-surface p-5 shadow-sm sm:p-6">
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
              <Network className="size-5" />
            </span>
            <div className="min-w-0">
              <h2 id="network-title" className="font-semibold tracking-tight">Network investors</h2>
              <p className="mt-0.5 text-sm text-muted">
                Earn the four level rates, as a percentage of each referred investor&apos;s investment, every month of its tenure.
              </p>
            </div>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="rounded-xl bg-background/60 p-3 ring-1 ring-border">
                <p className="flex items-center gap-2 text-xs text-muted">
                  <span aria-hidden="true" className={`flex size-5 items-center justify-center rounded-md text-[10px] font-bold ${LEVEL_TILE[i]}`}>{i + 1}</span>
                  Level {i + 1}
                </p>
                <p className="mt-1 font-display text-xl font-semibold tracking-tight tabular-nums">
                  {rates?.percents[i] !== undefined ? formatPercent(rates.percents[i], 3) : "—"}
                </p>
                <p className="text-[11px] text-muted">monthly</p>
              </div>
            ))}
          </div>
        </section>

        <section aria-labelledby="online-title" className="flex flex-col rounded-2xl border border-border bg-surface p-5 shadow-sm sm:p-6">
          <div className="flex items-start gap-3">
            <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
              <Globe className="size-5" />
            </span>
            <div className="min-w-0">
              <h2 id="online-title" className="font-semibold tracking-tight">Online investors</h2>
              <p className="mt-0.5 text-sm text-muted">
                Earn the one-time rate once, on the first order of each person they refer directly, when its term starts.
              </p>
            </div>
          </div>
          <div className="mt-4 rounded-xl bg-background/60 p-3 ring-1 ring-border sm:mt-auto">
            <p className="flex items-center gap-2 text-xs text-muted"><Percent aria-hidden="true" className="size-3.5 text-gold-text" /> One-time rate</p>
            <p className="mt-1 font-display text-xl font-semibold tracking-tight tabular-nums">{rates ? formatPercent(rates.oneTimePercent, 3) : "—"}</p>
            <p className="text-[11px] text-muted">once per direct referral</p>
          </div>
        </section>
      </div>
      {rates && <p className="-mt-2 text-xs text-muted">Rates in force since {formatDateTime(rates.effectiveFrom)}.</p>}

      {commissions && (
        <section className="space-y-4" aria-labelledby="paid-title">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="paid-title" className="flex items-center gap-3 text-lg font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <Coins className="size-[18px]" />
              </span>
              Commissions paid
            </h2>
            {commissions.totalElements > 0 && (
              <p className="text-sm text-muted">{commissions.totalElements.toLocaleString("en-IN")} {commissions.totalElements === 1 ? "payment" : "payments"}</p>
            )}
          </div>
          {commissions.content.length === 0 ? (
            <EmptyState title="No commissions yet" description="They are paid monthly, alongside each payout to a referred investor." />
          ) : (
            <>
              {/* A list rather than an eight-column table, so it never scrolls sideways. */}
              <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
                {commissions.content.map((c) => (
                  <li key={c.id}
                    className="grid gap-3 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
                    <div className="flex min-w-0 items-center gap-3">
                      <span aria-label={`Level ${c.level}`}
                        className={`flex size-10 shrink-0 flex-col items-center justify-center rounded-xl leading-none ${LEVEL_TILE[c.level - 1] ?? "bg-background text-muted ring-1 ring-border"}`}>
                        <span className="text-[9px] font-medium uppercase opacity-80">Lvl</span>
                        <span className="text-sm font-bold">{c.level}</span>
                      </span>
                      <div className="min-w-0">
                        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                          <span className="text-muted">From</span>
                          {c.sourceUserId
                            ? <Link href={`/admin/users/${c.sourceUserId}`} className="font-medium hover:text-gold-text hover:underline">{c.sourceName}</Link>
                            : <span className="font-medium">{c.sourceName ?? "—"}</span>}
                          {c.beneficiaryUserId && (
                            <>
                              <ArrowRight aria-hidden="true" className="size-3.5 text-muted" />
                              <Link href={`/admin/users/${c.beneficiaryUserId}`}
                                className="inline-flex items-center gap-1 rounded-full bg-gold/10 px-2 py-0.5 text-xs font-medium text-gold-text hover:bg-gold/20">
                                <UserRound aria-hidden="true" className="size-3" /> Paid to investor
                              </Link>
                            </>
                          )}
                        </p>
                        <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                          {c.productCode && <span className="font-mono text-foreground">{c.productCode}</span>}
                          <span className="inline-flex items-center gap-1"><Repeat aria-hidden="true" className="size-3" />Returns in {formatMonth(c.month)}</span>
                          <span>Paid {formatDateTime(c.paidAt)}</span>
                        </p>
                      </div>
                    </div>
                    <div className="pl-[3.25rem] text-left sm:pl-0 sm:text-right">
                      <p className="text-base font-semibold tabular-nums">{formatMoney(c.amount)}</p>
                      <p className="text-xs text-muted tabular-nums">{formatPercent(c.ratePercent, 3)} of {formatMoney(c.base)}</p>
                    </div>
                  </li>
                ))}
              </ul>
              <Pagination page={commissions} basePath="/admin/referrals" params={{}} />
            </>
          )}
        </section>
      )}
    </div>
  );
}
