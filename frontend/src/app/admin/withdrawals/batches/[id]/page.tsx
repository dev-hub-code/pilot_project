import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import {
  BATCH_TONE, PAYOUT_METHOD_LABEL, WITHDRAWAL_KIND_LABEL, WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE, formatPeriod,
} from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { BatchFile, PayoutBatchDetail } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { formatPhone } from "@/lib/phone";

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
  // Cash at the office is handed over by hand, without a sheet.
  const cash = b.payoutMethod === "OFFICE_CASH" && !b.period;
  const investors = new Map(detail.investors.map((i) => [i.userId, i]));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href="/admin/withdrawals/batches" className="text-sm text-muted hover:underline">Withdrawal files</Link>
          <h1 className="text-3xl font-semibold tracking-tight"><span className="font-mono">{b.reference}</span> · {formatMoney(b.total)}</h1>
          <p className="text-sm text-muted">
            {b.period ? `${b.kind ? WITHDRAWAL_KIND_LABEL[b.kind] : "Monthly"} withdrawals for ${formatPeriod(b.period)} · `
              : `${b.kind ? `${WITHDRAWAL_KIND_LABEL[b.kind]} withdrawals` : "Rent and referral withdrawals"} · ${PAYOUT_METHOD_LABEL[b.payoutMethod]} · `}
            {b.itemCount} payment(s) · created {formatDateTime(b.createdAt)}{b.sentAt ? ` · sent ${formatDateTime(b.sentAt)}` : ""}
          </p>
        </div>
        <StatusBadge tone={BATCH_TONE[b.status]}>{humanize(b.status)}</StatusBadge>
      </div>

      {b.status !== "CLOSED" && b.status !== "CANCELLED" && (
        <Card title={cash ? "Hand over at the office" : "Pay from the bank"}
          description={cash
            ? "There is no sheet for cash. When an investor comes to collect, check their ID against the user code, hand over the cash and mark the task collected."
            : "Pay each row from the bank, then mark each withdrawal paid (or failed) on Finance tasks."}>
          <div className="flex flex-wrap items-center gap-4 text-sm">
            {!cash && canProcess && downloadable && (
              <a href={`/admin/withdrawals/batches/${b.id}/file`} className="font-medium text-gold-text hover:underline" download>
                Download {files.find((f) => f.kind === "PAYMENT")?.filename ?? `${b.reference} sheet`}
              </a>
            )}
            {canProcess && (
              <Link href={`/admin/finance-tasks?view=OPEN&method=${b.payoutMethod}`} className="font-medium text-gold-text hover:underline">
                Update on Finance tasks →
              </Link>
            )}
          </div>
        </Card>
      )}
      {b.status === "CLOSED" && (
        <Notice tone="success">
          Every payment in this {cash ? "batch" : "file"} has been settled ({b.paid} {cash ? "collected" : "paid"}, {b.failed} {cash ? "not collected" : "failed"}).
        </Notice>
      )}

      {/* Cash has no sheet; a cash batch made before that keeps its old one, not offered any more. */}
      {!cash && files.length > 0 && (
        <section className="space-y-3">
          <h2 className="font-medium">Files</h2>
          <DataTable columns={["Kept", "File", "Kind", "Paid / failed", ""]}>
            {files.map((f) => (
              <tr key={f.id}>
                <Cell className="text-muted">{formatDateTime(f.createdAt)}</Cell>
                <Cell className="font-mono text-xs">{f.filename}</Cell>
                <Cell>{f.kind === "PAYMENT" ? "Bank sheet" : "Uploaded after paying (before uploads were removed)"}</Cell>
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

      <DataTable columns={["Reference", "Investor", cash ? "Phone" : "Account", "Amount", "Status"]}>
        {items.map((w) => (
          <tr key={w.id} className="align-top">
            <Cell><Link href={`/admin/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link></Cell>
            <Cell>
              <Link href={`/admin/users/${w.userId}`} className="font-mono text-xs text-gold-text hover:underline">
                {investors.get(w.userId)?.userCode ?? "Investor"}
              </Link>
              <span className="block">{cash ? investors.get(w.userId)?.name : w.bankHolderName}</span>
            </Cell>
            <Cell className="text-muted">{cash ? formatPhone(investors.get(w.userId)?.phone ?? "—") : `${w.bankName ?? ""} ${w.bankAccountMasked ?? ""}`}</Cell>
            <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
            <Cell>
              <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
              {w.failureReason && <span className="block max-w-xs truncate text-xs text-muted">{w.failureReason}</span>}
              {w.status === "FAILED" && (
                <Link href={`/admin/withdrawals/${w.id}`} className="block text-xs text-gold-text hover:underline">Resolve</Link>
              )}
            </Cell>
          </tr>
        ))}
      </DataTable>
    </div>
  );
}
