import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DownlineMember, ReferralEarning } from "@/types/referral";
import { formatDate } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referral member" };

/** The commission ledger from one member of the investor's downline. */
export default async function ReferralMemberPage({ params, searchParams }: PageProps<"/referrals/members/[id]">) {
  const { id } = await params;
  if (!/^m[1-9]\d{0,8}$/.test(id)) notFound();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  let member: DownlineMember;
  let ledger: PageResponse<ReferralEarning>;
  try {
    [member, ledger] = await Promise.all([
      authFetch<DownlineMember>(`/api/v1/referrals/members/${id}`),
      authFetch<PageResponse<ReferralEarning>>(`/api/v1/referrals/members/${id}/earnings?page=${page}&size=20`),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const earned = member.earned.length === 0 ? "—" : member.earned.map((m) => formatMoney(m)).join(" · ");

  return (
    <div className="space-y-6">
      <Link href={`/referrals/levels/${member.level}`} className="text-sm text-muted hover:text-foreground">← Level {member.level}</Link>
      <PageHeader title={member.displayName} description={`Level ${member.level} · joined ${formatDate(member.joinedAt)}`} />

      <section className="grid gap-px bg-border sm:grid-cols-2" aria-label="Commission from this member">
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">You earned from {member.displayName}</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{earned}</p>
        </div>
        <div className="space-y-2 bg-surface p-6">
          <p className="text-xs uppercase tracking-[0.1em] text-muted">Commission payments</p>
          <p className="font-display text-3xl font-semibold tabular-nums">{ledger.totalElements}</p>
        </div>
      </section>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Commission ledger</h2>
        {ledger.content.length === 0 ? (
          <EmptyState title="No commission yet"
            description="Commission is paid every month, alongside each payout on this member's containers." />
        ) : (
          <>
            <DataTable columns={["Paid", "Plan", "Their investment", "Rate", "Commission"]}>
              {ledger.content.map((e) => (
                <tr key={e.id} className="hover:bg-background">
                  <Cell className="text-muted">{formatDate(e.paidAt)}</Cell>
                  <Cell><span className="font-mono">{e.productCode}</span> <span className="text-xs text-muted">payout {e.installmentNumber}</span></Cell>
                  <Cell className="tabular-nums">{formatMoney(e.base)}</Cell>
                  <Cell className="tabular-nums text-muted">{formatPercent(e.ratePercent, 3)}</Cell>
                  <Cell className="tabular-nums font-medium">{formatMoney(e.amount)}</Cell>
                </tr>
              ))}
            </DataTable>
            <Pagination page={ledger} basePath={`/referrals/members/${id}`} params={{}} />
          </>
        )}
      </section>
    </div>
  );
}
