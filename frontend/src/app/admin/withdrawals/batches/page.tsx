import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { generateWithdrawalsAction } from "@/features/admin/withdrawal-actions";
import { WithdrawalMinimumCard } from "@/features/admin/withdrawal-minimum-card";
import { ActionButton } from "@/features/orders/action-button";
import { BATCH_TONE, PAYOUT_METHOD_LABEL, WITHDRAWAL_KIND_LABEL, formatPeriod } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PayoutBatch, WithdrawalRun } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal files" };

export default async function BatchesPage({ searchParams }: PageProps<"/admin/withdrawals/batches">) {
  const session = await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [batches, runs] = await Promise.all([
    authFetch<PageResponse<PayoutBatch>>(`/api/v1/admin/withdrawal-batches?page=${page}&size=25`),
    authFetch<WithdrawalRun[]>("/api/v1/admin/withdrawal-runs"),
  ]);
  const canProcess = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawal files"
        description="Generate rent withdrawals and referral withdrawals separately: each moves every investor's wallet of that kind into withdrawal in progress, as a bank sheet for finance to pay and a cash batch handed over at the office." />

      <Card title="Generate withdrawals"
        description="Each run takes every eligible investor's balance of its kind up to now; the other kind stays in their wallet for its own run. Rent: containers paid by bank go on the bank sheet, containers paid in cash at the office to the cash batch. Referral: by bank or in cash, as each investor chose for the year. Paid amounts then move to the investor's total withdrawn.">
        {canProcess ? (
          <div className="flex flex-wrap gap-3">
            <ActionButton action={generateWithdrawalsAction.bind(null, "RENT")} label="Generate rent withdrawals"
              pendingLabel="Generating…"
              confirm="Withdraw every eligible investor's rent now? Bank withdrawals go on a bank sheet, cash on a batch handed over at the office. Referral is not included." />
            <ActionButton action={generateWithdrawalsAction.bind(null, "REFERRAL")} label="Generate referral withdrawals"
              pendingLabel="Generating…" variant="secondary"
              confirm="Withdraw every eligible investor's referral commission now? Bank withdrawals go on a bank sheet, cash on a batch handed over at the office. Rent is not included." />
          </div>
        ) : (
          <p className="text-sm text-muted">Generating withdrawals needs the process-withdrawals permission.</p>
        )}
        <p className="mt-4 text-sm text-muted">
          Each withdrawal then becomes a task on <Link href="/admin/finance-tasks" className="text-gold-text hover:underline">Finance tasks</Link>,
          where finance marks it paid (or collected at the office), or failed with the reason. Bank batches also have an
          Excel sheet to pay from.
        </p>
      </Card>

      <WithdrawalMinimumCard permissions={session.permissions} />

      {runs.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-medium">Runs</h2>
          <DataTable columns={["Run", "Kind", "Generated", "Bank sheet", "Cash at office", "Left out"]}>
            {runs.map((run) => (
              <tr key={run.id} className="align-top">
                <Cell className="font-mono font-medium">{run.reference}</Cell>
                <Cell>{run.kind ? WITHDRAWAL_KIND_LABEL[run.kind] : "Rent and referral"}</Cell>
                <Cell className="text-muted">{formatDateTime(run.runAt)}</Cell>
                <Cell><Sheet batchId={run.bankBatchId} reference={run.bankBatchReference} investors={run.bankInvestors} total={run.bankTotal} /></Cell>
                <Cell><Sheet batchId={run.cashBatchId} reference={run.cashBatchReference} investors={run.cashInvestors} total={run.cashTotal} /></Cell>
                <Cell>
                  {run.skipped.length === 0 ? <span className="text-muted">None</span> : (
                    <details>
                      <summary className="cursor-pointer text-sm">{run.skipped.length} investor(s)</summary>
                      <ul className="mt-2 space-y-1 text-xs">
                        {run.skipped.map((skip, i) => (
                          <li key={`${skip.userId}-${i}`}>
                            <Link href={`/admin/users/${skip.userId}`} className="font-mono text-gold-text hover:underline">{skip.userCode ?? "Investor"}</Link>
                            <span className="text-muted"> · {PAYOUT_METHOD_LABEL[skip.payoutMethod]} · {skip.reason}</span>
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
        <DataTable columns={["Created", "Batch", "Sheet", "Items", "Total", "Paid / failed / outstanding", "Status"]}>
          {batches.content.map((b) => (
            <tr key={b.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(b.createdAt)}</Cell>
              <Cell><Link href={`/admin/withdrawals/batches/${b.id}`} className="font-mono text-gold-text hover:underline">{b.reference}</Link></Cell>
              <Cell className="text-muted">
                {b.period ? `${b.kind ? WITHDRAWAL_KIND_LABEL[b.kind] : ""} ${formatPeriod(b.period)}`.trim()
                  : `${b.kind ? `${WITHDRAWAL_KIND_LABEL[b.kind]} · ` : ""}${PAYOUT_METHOD_LABEL[b.payoutMethod]}`}
              </Cell>
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

function Sheet({ batchId, reference, investors, total }: {
  batchId: string | null;
  reference: string | null;
  investors: number;
  total: WithdrawalRun["bankTotal"];
}) {
  if (!batchId) return <span className="text-muted">Nobody</span>;
  return (
    <>
      <Link href={`/admin/withdrawals/batches/${batchId}`} className="font-mono text-gold-text hover:underline">{reference}</Link>
      <span className="block text-xs text-muted">{investors} investor(s) · {formatMoney(total)}</span>
    </>
  );
}
