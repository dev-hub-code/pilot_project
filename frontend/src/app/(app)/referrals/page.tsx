import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { DownlineTree } from "@/features/referrals/downline-tree";
import { ShareLink } from "@/features/referrals/share-link";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Downline, ReferralEarning, ReferralMonth, ReferralOverview } from "@/types/referral";
import { formatDate, formatMonth } from "@/utils/format";
import { CURRENCY } from "@/lib/currency";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referrals" };

export default async function ReferralsPage({ searchParams }: PageProps<"/referrals">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [overview, downline, monthly, history] = await Promise.all([
    authFetch<ReferralOverview>("/api/v1/referrals/me"),
    authFetch<Downline>("/api/v1/referrals/downline"),
    authFetch<ReferralMonth[]>("/api/v1/referrals/monthly"),
    authFetch<PageResponse<ReferralEarning>>(`/api/v1/referrals/earnings?page=${page}&size=20`),
  ]);
  // Worked example from the level-1 rate in force, which admins set.
  const level1 = overview.levels.find((l) => l.level === 1);
  const example = level1 && level1.ratePercent > 0
    ? `${formatPercent(level1.ratePercent, level1.ratePercent % 1 === 0 ? 0 : 2)} of ${formatMoney({ amount: "100000", currency: CURRENCY })} is ${formatMoney({ amount: (1000 * level1.ratePercent).toFixed(2), currency: CURRENCY })} a month at level 1`
    : null;
  const earned = overview.totalEarned.length === 0 ? "—" : overview.totalEarned.map((m) => formatMoney(m)).join(" · ");

  return (
    <div className="space-y-6">
      <PageHeader title="Referrals"
        description="Invite investors. Every month of their lease you earn a percentage of what they invested, and of what the people they invite invest, four levels deep." />
      {!overview.eligible && overview.ineligibleReason && (
        <Notice tone="warning">{overview.ineligibleReason}. Until then, commissions on your referrals are not paid to you.</Notice>
      )}

      <Card title="Your referral link" description={overview.referredBy ? `You were referred by ${overview.referredBy}.` : undefined}>
        <div className="space-y-3">
          <ShareLink code={overview.code} />
          <p className="text-sm text-muted">Or share your code: <span className="font-mono text-base font-semibold tracking-widest text-foreground">{overview.code}</span></p>
        </div>
      </Card>

      <section className="grid gap-px bg-border sm:grid-cols-5" aria-label="Referral summary">
        <div className="space-y-2 bg-surface p-6 sm:col-span-1">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Commission earned</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{earned}</p>
        </div>
        {overview.levels.map((l) => (
          <Link key={l.level} href={`/referrals/levels/${l.level}`} className="group space-y-2 bg-surface p-6 hover:bg-background">
            <p className="text-xs uppercase tracking-[0.1em] text-muted">Level {l.level} · {formatPercent(l.ratePercent, l.ratePercent % 1 === 0 ? 0 : 2)}</p>
            <p className="font-display text-3xl font-semibold tabular-nums">{l.members}</p>
            <p className="text-xs text-muted">
              {l.level === 1 ? "invited by you" : `invited by level ${l.level - 1}`}
              <span className="ml-1 text-gold-text group-hover:underline">· View members →</span>
            </p>
          </Link>
        ))}
      </section>
      <p className="text-xs text-muted">
        Each level&apos;s rate is a percentage of the referred investor&apos;s investment, paid to you every month of its tenure by SeaLease{example ? ` (e.g. ${example})` : ""}. Your referrals&apos;
        own income is never reduced. Rates in force since {formatDate(overview.ratesEffectiveFrom)}.
      </p>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Your network</h2>
        {downline.members.length === 0 ? (
          <EmptyState title="No referrals yet" description="People who sign up with your link appear here." />
        ) : (
          <>
            <DownlineTree members={downline.members} />
            {downline.truncated && <p className="text-xs text-muted">Showing the earliest {downline.members.length} members.</p>}
          </>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Monthly earnings by level</h2>
        {monthly.length === 0 ? (
          <p className="text-sm text-muted">Each month&apos;s commission appears here, split by level, and is added to your wallet as it is paid.</p>
        ) : (
          <DataTable columns={["Month", ...overview.levels.map((l) => `Level ${l.level}`), "Total"]}>
            {monthly.map((m) => (
              <tr key={`${m.month}-${m.total.currency}`} className="hover:bg-background">
                <Cell>{formatMonth(m.month)}</Cell>
                {m.levels.map((amount, i) => (
                  <Cell key={i} className={`tabular-nums ${Number(amount.amount) === 0 ? "text-muted" : ""}`}>{formatMoney(amount)}</Cell>
                ))}
                <Cell className="tabular-nums font-medium">{formatMoney(m.total)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Commission history</h2>
        {history.content.length === 0 ? (
          <p className="text-sm text-muted">Commission is paid every month, alongside each payout on your referrals&apos; containers.</p>
        ) : (
          <>
            <DataTable columns={["Paid", "From", "Level", "Plan", "Their investment", "Rate", "Commission"]}>
              {history.content.map((e) => (
                <tr key={e.id} className="hover:bg-background">
                  <Cell className="text-muted">{formatDate(e.paidAt)}</Cell>
                  <Cell>{e.sourceName}</Cell>
                  <Cell className="tabular-nums">{e.level}</Cell>
                  <Cell><span className="font-mono">{e.productCode}</span> <span className="text-xs text-muted">payout {e.installmentNumber}</span></Cell>
                  <Cell className="tabular-nums">{formatMoney(e.base)}</Cell>
                  <Cell className="tabular-nums text-muted">{formatPercent(e.ratePercent, 3)}</Cell>
                  <Cell className="tabular-nums font-medium">{formatMoney(e.amount)}</Cell>
                </tr>
              ))}
            </DataTable>
            <Pagination page={history} basePath="/referrals" params={{}} />
          </>
        )}
      </section>
    </div>
  );
}
