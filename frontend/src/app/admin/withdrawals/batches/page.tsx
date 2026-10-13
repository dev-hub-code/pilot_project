import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { runWithdrawalsNowAction } from "@/features/admin/withdrawal-actions";
import { WithdrawalDaysForm } from "@/features/admin/withdrawal-forms";
import { ActionButton } from "@/features/orders/action-button";
import { BATCH_TONE, WITHDRAWAL_KIND_LABEL, formatPeriod } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PayoutBatch, ScheduledRun, WithdrawalKind, WithdrawalSettings } from "@/types/withdrawal";
import { formatDate, formatDateTime, humanize, ordinal } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal files" };

const KINDS: readonly WithdrawalKind[] = ["RENT", "REFERRAL"];

export default async function BatchesPage({ searchParams }: PageProps<"/admin/withdrawals/batches">) {
  const session = await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [batches, settings, runs] = await Promise.all([
    authFetch<PageResponse<PayoutBatch>>(`/api/v1/admin/withdrawal-batches?page=${page}&size=25`),
    authFetch<WithdrawalSettings>("/api/v1/admin/settings/withdrawals"),
    authFetch<ScheduledRun[]>("/api/v1/admin/withdrawal-runs"),
  ]);
  const thisMonth = new Date().toISOString().slice(0, 7);
  const ran = (kind: WithdrawalKind) => runs.some((run) => run.period === thisMonth && run.kind === kind);
  const day = { RENT: settings.rentWithdrawalDay, REFERRAL: settings.referralWithdrawalDay };
  const nextFile = { RENT: settings.nextRentFileOn, REFERRAL: settings.nextReferralFileOn };
  const canProcess = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawal files"
        description="Each month the rent and referral bank withdrawals are made into an Excel file for finance to pay by hand." />

      <Card title="Monthly withdrawals"
        description="The day before each withdrawal day, every eligible investor's whole balance is withdrawn to their verified primary bank account and the month's file is made. Finance pays it on the day and uploads it back with each row's status. Cash at the office is withdrawn from the same days.">
        <div className="space-y-6">
          <div className="grid gap-px bg-border sm:grid-cols-2">
            {KINDS.map((kind) => (
              <div key={kind} className="space-y-3 bg-surface p-5">
                <p className="text-xs uppercase tracking-[0.1em] text-muted">{WITHDRAWAL_KIND_LABEL[kind]}</p>
                <dl className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <dt className="text-muted">Withdrawal day</dt>
                    <dd className="mt-1 font-medium">{ordinal(day[kind])} of the month</dd>
                  </div>
                  <div>
                    <dt className="text-muted">{ran(kind) ? "Next file" : "This month's file"}</dt>
                    <dd className="mt-1 font-medium">{ran(kind) ? formatDate(nextFile[kind]) : `Due ${formatDate(nextFile[kind])}`}</dd>
                  </div>
                </dl>
                {canProcess && !ran(kind) && (
                  <ActionButton action={runWithdrawalsNowAction.bind(null, kind)} label={`Make this month's ${WITHDRAWAL_KIND_LABEL[kind].toLowerCase()} file now`}
                    variant="secondary" pendingLabel="Making withdrawals…"
                    confirm={`Withdraw every eligible investor's ${WITHDRAWAL_KIND_LABEL[kind].toLowerCase()} balance now and make this month's file? It will not run again this month.`} />
                )}
              </div>
            ))}
          </div>
          <div className="flex flex-wrap items-end justify-between gap-6">
            {hasPermission(session.permissions, Permission.SYSTEM_SETTINGS_MANAGE)
              ? <WithdrawalDaysForm rentDay={settings.rentWithdrawalDay} referralDay={settings.referralWithdrawalDay} />
              : <p className="text-xs text-muted">Changing the days needs the system settings permission.</p>}
            <p className="text-xs text-muted">Days last changed {formatDateTime(settings.updatedAt)}</p>
          </div>
        </div>
      </Card>

      {runs.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-medium">Monthly runs</h2>
          <DataTable columns={["Month", "Kind", "Ran", "Withdrawals", "Total", "Batch", "Skipped"]}>
            {runs.map((run) => (
              <tr key={run.id} className="align-top">
                <Cell className="font-medium">{formatPeriod(run.period)}</Cell>
                <Cell>{WITHDRAWAL_KIND_LABEL[run.kind]}</Cell>
                <Cell className="text-muted">{formatDateTime(run.runAt)}{run.triggeredBy ? " (by hand)" : ""}</Cell>
                <Cell className="tabular-nums">{run.withdrawalCount}</Cell>
                <Cell className="tabular-nums">{formatMoney(run.total)}</Cell>
                <Cell>
                  {run.batchId
                    ? <Link href={`/admin/withdrawals/batches/${run.batchId}`} className="font-mono text-gold-text hover:underline">{run.batchReference}</Link>
                    : <span className="text-muted">—</span>}
                </Cell>
                <Cell>
                  {run.skipped.length === 0 ? <span className="text-muted">None</span> : (
                    <details>
                      <summary className="cursor-pointer text-sm">{run.skipped.length} investor(s)</summary>
                      <ul className="mt-2 space-y-1 text-xs">
                        {run.skipped.map((skip) => (
                          <li key={skip.userId}>
                            <Link href={`/admin/users/${skip.userId}`} className="text-gold-text hover:underline">Investor</Link>
                            <span className="text-muted"> · {skip.reason}</span>
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                </Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}
      {batches.content.length === 0 ? (
        <EmptyState title="No withdrawal files yet" />
      ) : (
        <DataTable columns={["Created", "Batch", "File", "Items", "Total", "Paid / failed / outstanding", "Status"]}>
          {batches.content.map((b) => (
            <tr key={b.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(b.createdAt)}</Cell>
              <Cell><Link href={`/admin/withdrawals/batches/${b.id}`} className="font-mono text-gold-text hover:underline">{b.reference}</Link></Cell>
              <Cell className="text-muted">{b.period ? `${b.kind ? WITHDRAWAL_KIND_LABEL[b.kind] : ""} ${formatPeriod(b.period)}`.trim() : "By hand"}</Cell>
              <Cell className="tabular-nums">{b.itemCount}</Cell>
              <Cell className="tabular-nums">{formatMoney(b.total)}</Cell>
              <Cell className="tabular-nums">{b.paid} / {b.failed} / {b.outstanding}</Cell>
              <Cell><StatusBadge tone={BATCH_TONE[b.status]}>{humanize(b.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={batches} basePath="/admin/withdrawals/batches" params={{}} />
    </div>
  );
}
