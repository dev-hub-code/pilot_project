import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { cancelRatesAction } from "@/features/admin/referral-actions";
import { ScheduleRatesForm } from "@/features/admin/referral-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { RateVersion } from "@/types/referral";
import { formatDateTime, humanize } from "@/utils/format";
import { formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Referral rates" };

export default async function ReferralRatesPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.FINANCE_VIEW) && !can(Permission.REFERRAL_CONFIG_MANAGE)) {
    return <EmptyState title="Not available" description="Referral rates are for finance and admins." />;
  }
  const versions = await authFetch<RateVersion[]>("/api/v1/admin/referral-rates");
  const current = versions.find((v) => v.state === "IN_FORCE");
  const canManage = can(Permission.REFERRAL_CONFIG_MANAGE);

  return (
    <div className="space-y-6">
      <section className="space-y-3" aria-label="Rate versions">
        <h2 className="text-lg font-semibold tracking-tight">Rates</h2>
        <DataTable columns={["From", "Level 1", "Level 2", "Level 3", "Level 4", "One-time", "Reason", "State", ""]}>
          {versions.map((v) => (
            <tr key={v.id}>
              <Cell className="text-muted">{formatDateTime(v.effectiveFrom)}</Cell>
              {v.percents.map((p, i) => <Cell key={i} className="tabular-nums">{formatPercent(p, 3)}</Cell>)}
              <Cell className="tabular-nums">{formatPercent(v.oneTimePercent, 3)}</Cell>
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
          <ScheduleRatesForm current={current?.percents ?? []} currentOneTime={current?.oneTimePercent} />
        </Card>
      )}
    </div>
  );
}
