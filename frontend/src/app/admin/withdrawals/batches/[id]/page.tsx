import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { batchStepAction, markItemFailedAction, markItemPaidAction } from "@/features/admin/withdrawal-actions";
import { SettleBatchForm } from "@/features/admin/withdrawal-forms";
import { BATCH_TONE, WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { PayoutBatchDetail } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payout batch" };

export default async function BatchPage({ params }: PageProps<"/admin/withdrawals/batches/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  let detail: PayoutBatchDetail;
  try {
    detail = await authFetch<PayoutBatchDetail>(`/api/v1/admin/withdrawal-batches/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { batch: b, items } = detail;
  const canProcess = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);
  const downloadable = b.status === "CREATED" || b.status === "SENT";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href="/admin/withdrawals/batches" className="text-sm text-muted hover:underline">Payout batches</Link>
          <h1 className="text-3xl font-semibold tracking-tight"><span className="font-mono">{b.reference}</span> · {formatMoney(b.total)}</h1>
          <p className="text-sm text-muted">{b.itemCount} payment(s) · created {formatDateTime(b.createdAt)}{b.sentAt ? ` · sent ${formatDateTime(b.sentAt)}` : ""}</p>
        </div>
        <StatusBadge tone={BATCH_TONE[b.status]}>{humanize(b.status)}</StatusBadge>
      </div>

      {canProcess && b.status !== "CLOSED" && b.status !== "CANCELLED" && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card title="1. Payment file" description="Contains full account numbers. Every download is recorded.">
            {downloadable && (
              <a href={`/admin/withdrawals/batches/${b.id}/file`} className="text-sm font-medium text-gold-text hover:underline" download>
                Download {b.reference}.csv
              </a>
            )}
          </Card>
          {b.status === "CREATED" && (
            <Card title="2. Sent to the bank" description="After uploading the file to the bank.">
              <div className="space-y-3">
                <ConfirmForm action={batchStepAction.bind(null, b.id, "sent")} submitLabel="Mark as sent"
                  confirm={`Mark ${b.reference} as sent to the bank? It can no longer be cancelled.`} />
                <ConfirmForm action={batchStepAction.bind(null, b.id, "cancel")} submitLabel="Cancel batch"
                  confirm={`Cancel ${b.reference}? Its withdrawals return to the approved queue.`} />
              </div>
            </Card>
          )}
          {b.status === "SENT" && (
            <Card title="3. Reconcile" description="Mark each payment once the bank statement confirms it.">
              <SettleBatchForm batchId={b.id} outstanding={b.outstanding} />
            </Card>
          )}
        </div>
      )}
      {b.status === "CLOSED" && <Notice tone="success">Every payment in this batch has been reconciled ({b.paid} paid, {b.failed} failed).</Notice>}

      <DataTable columns={["Reference", "Beneficiary", "Account", "Amount", "Status", ""]}>
        {items.map((w) => (
          <tr key={w.id} className="align-top">
            <Cell><Link href={`/admin/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link></Cell>
            <Cell>{w.bankHolderName}</Cell>
            <Cell className="text-muted">{w.bankName} {w.bankAccountMasked}</Cell>
            <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
            <Cell>
              <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
              {w.failureReason && <span className="block max-w-xs truncate text-xs text-muted">{w.failureReason}</span>}
            </Cell>
            <Cell>
              {canProcess && b.status === "SENT" && w.status === "PROCESSING" && (
                <div className="min-w-64 space-y-2">
                  <ConfirmForm action={markItemPaidAction.bind(null, b.id, w.id)} submitLabel="Paid"
                    confirm={`Mark ${w.reference} as paid?`} />
                  <details>
                    <summary className="cursor-pointer text-xs text-muted">Bank returned it…</summary>
                    <ReasonForm action={markItemFailedAction.bind(null, b.id, w.id)} label="Reason" submitLabel={`Mark ${w.reference} failed`}
                      confirm={`Mark ${w.reference} as failed? The amount returns to the investor's balance.`} />
                  </details>
                </div>
              )}
            </Cell>
          </tr>
        ))}
      </DataTable>
    </div>
  );
}
