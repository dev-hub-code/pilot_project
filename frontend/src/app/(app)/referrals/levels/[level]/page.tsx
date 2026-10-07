import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { authFetch } from "@/lib/server/auth/session";
import type { Downline, ReferralOverview } from "@/types/referral";
import { formatDate } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referral level" };

/** Members of one level of the investor's downline; each opens their commission ledger. */
export default async function ReferralLevelPage({ params }: PageProps<"/referrals/levels/[level]">) {
  const level = Number((await params).level);
  if (!Number.isInteger(level) || level < 1 || level > 4) notFound();
  const [overview, downline] = await Promise.all([
    authFetch<ReferralOverview>("/api/v1/referrals/me"),
    authFetch<Downline>("/api/v1/referrals/downline"),
  ]);
  const rate = overview.levels.find((l) => l.level === level)?.ratePercent;
  const names = new Map(downline.members.map((m) => [m.id, m.displayName]));
  const members = downline.members.filter((m) => m.level === level);

  return (
    <div className="space-y-6">
      <Link href="/referrals" className="text-sm text-muted hover:text-foreground">← Referrals</Link>
      <PageHeader title={`Level ${level}`}
        description={`${level === 1 ? "People you invited" : `People invited by your level ${level - 1}`}. ${
          rate === undefined ? "" : `You earn ${formatPercent(rate, rate % 1 === 0 ? 0 : 2)} of what each invests, every month of its tenure.`}`} />
      {members.length === 0 ? (
        <EmptyState title="No members at this level yet" description="Members appear here as your network grows." />
      ) : (
        <DataTable columns={["Member", "Invited by", "Joined", "You earned"]}>
          {members.map((m) => (
            <tr key={m.id} className="hover:bg-background">
              <Cell>
                <Link href={`/referrals/members/${m.id}`} className="font-medium text-gold-text hover:underline">{m.displayName}</Link>
              </Cell>
              <Cell className="text-muted">{m.parentId ? names.get(m.parentId) ?? "—" : "You"}</Cell>
              <Cell className="text-muted">{formatDate(m.joinedAt)}</Cell>
              <Cell className="tabular-nums">{m.earned.length === 0 ? "—" : m.earned.map((e) => formatMoney(e)).join(" · ")}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {downline.truncated && <p className="text-xs text-muted">Showing members among the earliest {downline.members.length} in your network.</p>}
    </div>
  );
}
