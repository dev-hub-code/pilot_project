import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ReferralEarning } from "@/types/referral";
import { formatDateTime, formatMonth } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referrals" };

export default async function AdminReferralsPage({ searchParams }: PageProps<"/admin/referrals">) {
  const denied = await deniedUnlessInMenu("/admin/referrals");
  if (denied) return denied;
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const commissions = can(Permission.FINANCE_VIEW)
    ? await authFetch<PageResponse<ReferralEarning>>(`/api/v1/admin/referral-earnings?page=${page}&size=25`)
    : null;

  return (
    <div className="space-y-6">
      <PageHeader title="Referrals"
        description="Network investors earn the four level rates, as a percentage of each referred investor's investment, every month of its tenure. Online investors earn the one-time rate once, on the first order of each person they refer directly, when its term starts."
        actions={<LinkButton href="/admin/platform-config/referral-rates" variant="secondary">Referral rates</LinkButton>} />

      {commissions && (
        <section className="space-y-3" aria-label="Commissions">
          <h2 className="text-lg font-semibold tracking-tight">Commissions paid</h2>
          {commissions.content.length === 0 ? (
            <EmptyState title="No commissions yet" description="They are paid monthly, alongside each payout to a referred investor." />
          ) : (
            <>
              <DataTable columns={["Paid", "To", "From", "Level", "Plan", "Investment", "Rate", "Commission"]}>
                {commissions.content.map((c) => (
                  <tr key={c.id}>
                    <Cell className="text-muted">{formatDateTime(c.paidAt)}</Cell>
                    <Cell>
                      {c.beneficiaryUserId && (
                        <Link href={`/admin/users/${c.beneficiaryUserId}`} className="font-mono text-xs text-gold-text hover:underline">
                          {c.beneficiaryUserId.slice(0, 8)}…
                        </Link>
                      )}
                    </Cell>
                    <Cell>
                      {c.sourceUserId ? <Link href={`/admin/users/${c.sourceUserId}`} className="hover:underline">{c.sourceName}</Link> : c.sourceName}
                    </Cell>
                    <Cell className="tabular-nums">{c.level}</Cell>
                    <Cell><span className="font-mono">{c.productCode}</span> <span className="text-xs text-muted">returns in {formatMonth(c.month)}</span></Cell>
                    <Cell className="tabular-nums">{formatMoney(c.base)}</Cell>
                    <Cell className="tabular-nums text-muted">{formatPercent(c.ratePercent, 3)}</Cell>
                    <Cell className="tabular-nums font-medium">{formatMoney(c.amount)}</Cell>
                  </tr>
                ))}
              </DataTable>
              <Pagination page={commissions} basePath="/admin/referrals" params={{}} />
            </>
          )}
        </section>
      )}
    </div>
  );
}
