import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { cancelRatesAction } from "@/features/admin/referral-actions";
import { ScheduleRatesForm } from "@/features/admin/referral-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { RateVersion, ReferralEarning } from "@/types/referral";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referrals" };

export default async function AdminReferralsPage({ searchParams }: PageProps<"/admin/referrals">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [versions, commissions] = await Promise.all([
    authFetch<RateVersion[]>("/api/v1/admin/referral-rates"),
    can(Permission.FINANCE_VIEW)
      ? authFetch<PageResponse<ReferralEarning>>(`/api/v1/admin/referral-earnings?page=${page}&size=25`)
      : null,
  ]);
  const current = versions.find((v) => v.state === "IN_FORCE");
  const canManage = can(Permission.REFERRAL_CONFIG_MANAGE);

  return (
    <div className="space-y-6">
      <PageHeader title="Referrals"
        description="Commission rates for four referral levels, as a percentage of each referred investor's investment, paid every month of its tenure." />

      <section className="space-y-3" aria-label="Rate versions">
        <h2 className="text-lg font-semibold tracking-tight">Rates</h2>
        <DataTable columns={["From", "Level 1", "Level 2", "Level 3", "Level 4", "Reason", "State", ""]}>
          {versions.map((v) => (
            <tr key={v.id}>
              <Cell className="text-muted">{formatDateTime(v.effectiveFrom)}</Cell>
              {v.percents.map((p, i) => <Cell key={i} className="tabular-nums">{formatPercent(p, 3)}</Cell>)}
              <Cell className="max-w-xs truncate">{v.reason}</Cell>
              <Cell><StatusBadge tone={toneFor(v.state)}>{humanize(v.state)}</StatusBadge></Cell>
              <Cell>
                {v.state === "SCHEDULED" && canManage && (
                  <ConfirmForm action={cancelRatesAction.bind(null, v.id)} submitLabel="Cancel"
                    confirm={`Cancel the rates scheduled for ${formatDateTime(v.effectiveFrom)}?`} />
                )}
              </Cell>
            </tr>
          ))}
        </DataTable>
      </section>

      {canManage && (
        <Card title="Schedule new rates"
          description="Rates in force are never edited: new rates start now or later and apply to payouts made from then on.">
          <ScheduleRatesForm current={current?.percents ?? []} />
        </Card>
      )}

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
                    <Cell><span className="font-mono">{c.productCode}</span> <span className="text-xs text-muted">payout {c.installmentNumber}</span></Cell>
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
