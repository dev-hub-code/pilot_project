import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { batchStepAction, markItemFailedAction, markItemPaidAction } from "@/features/admin/withdrawal-actions";
import { ReconcileUploadForm, SettleBatchForm } from "@/features/admin/withdrawal-forms";
import { BATCH_TONE, WITHDRAWAL_KIND_LABEL, WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE, formatPeriod } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { BatchFile, PayoutBatchDetail } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal file" };

export default async function BatchPage({ params }: PageProps<"/admin/withdrawals/batches/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  let detail: PayoutBatchDetail;
  let files: BatchFile[];
  try {
    [detail, files] = await Promise.all([
      authFetch<PayoutBatchDetail>(`/api/v1/admin/withdrawal-batches/${id}`),
      authFetch<BatchFile[]>(`/api/v1/admin/withdrawal-batches/${id}/files`),
    ]);
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
          <Link href="/admin/withdrawals/batches" className="text-sm text-muted hover:underline">Withdrawal files</Link>
          <h1 className="text-3xl font-semibold tracking-tight"><span className="font-mono">{b.reference}</span> · {formatMoney(b.total)}</h1>
          <p className="text-sm text-muted">
            {b.period ? `${b.kind ? WITHDRAWAL_KIND_LABEL[b.kind] : "Monthly"} withdrawals for ${formatPeriod(b.period)} · ` : ""}
            {b.itemCount} payment(s) · created {formatDateTime(b.createdAt)}{b.sentAt ? ` · sent ${formatDateTime(b.sentAt)}` : ""}
          </p>
        </div>
        <StatusBadge tone={BATCH_TONE[b.status]}>{humanize(b.status)}</StatusBadge>
      </div>

      {canProcess && b.status !== "CLOSED" && b.status !== "CANCELLED" && (
        <div className="grid gap-6 lg:grid-cols-3">
          <Card title="1. Download and pay" description="Kept with the batch. Contains full account numbers; every download is recorded.">
            <div className="space-y-3 text-sm">
              {downloadable && (
                <a href={`/admin/withdrawals/batches/${b.id}/file`} className="font-medium text-gold-text hover:underline" download>
                  Download {files.find((f) => f.kind === "PAYMENT")?.filename ?? `${b.reference} file`}
                </a>
              )}
              <p className="text-muted">
                Pay each row from the bank, then fill in <span className="font-medium text-foreground">Status</span> (PAID or
                FAILED) and <span className="font-medium text-foreground">Remarks</span> (the bank reference, or why it failed).
              </p>
            </div>
          </Card>
          <Card title="2. Upload the completed file" description="Paid rows complete. Failed rows are held and get a support ticket assigned to you.">
            <ReconcileUploadForm batchId={b.id} reference={b.reference} />
          </Card>
          <Card title="Or by hand" description={b.status === "CREATED" ? "Without uploading a file." : "For payments the file did not cover."}>
            {b.status === "CREATED" ? (
              <div className="space-y-3">
                <ConfirmForm action={batchStepAction.bind(null, b.id, "sent")} submitLabel="Mark as being paid"
                  confirm={`Mark ${b.reference} as being paid? Then mark each payment paid or failed below.`} />
              </div>
            ) : (
              <SettleBatchForm batchId={b.id} outstanding={b.outstanding} />
            )}
          </Card>
        </div>
      )}
      {b.status === "CLOSED" && <Notice tone="success">Every payment in this file has been settled ({b.paid} paid, {b.failed} failed).</Notice>}

      {files.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-medium">Files</h2>
          <DataTable columns={["Kept", "File", "Kind", "Paid / failed", ""]}>
            {files.map((f) => (
              <tr key={f.id}>
                <Cell className="text-muted">{formatDateTime(f.createdAt)}</Cell>
                <Cell className="font-mono text-xs">{f.filename}</Cell>
                <Cell>{f.kind === "PAYMENT" ? "Withdrawal file" : "Uploaded after paying"}</Cell>
                <Cell className="tabular-nums">{f.kind === "PAYMENT" ? "—" : `${f.paidCount ?? 0} / ${f.failedCount ?? 0}`}</Cell>
                <Cell>
                  {canProcess && (
                    <a href={`/admin/withdrawals/batches/${b.id}/files/${f.id}`} className="text-sm text-gold-text hover:underline" download>
                      Download
                    </a>
                  )}
                </Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}

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
              {w.status === "FAILED" && (
                <Link href={`/admin/withdrawals/${w.id}`} className="block text-xs text-gold-text hover:underline">Resolve</Link>
              )}
            </Cell>
            <Cell>
              {canProcess && b.status === "SENT" && w.status === "PROCESSING" && (
                <div className="min-w-64 space-y-2">
                  <ConfirmForm action={markItemPaidAction.bind(null, b.id, w.id)} submitLabel="Paid"
                    confirm={`Mark ${w.reference} as paid?`} />
                  <details>
                    <summary className="cursor-pointer text-xs text-muted">Bank returned it…</summary>
                    <ReasonForm action={markItemFailedAction.bind(null, b.id, w.id)} label="Reason" submitLabel={`Mark ${w.reference} failed`}
                      confirm={`Mark ${w.reference} as failed? The amount is held and a support ticket is opened for you.`} />
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
