import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { approveRentalAction, rejectRentalAction } from "@/features/admin/rental-actions";
import { formatPeriod, RECEIPT_STATUS_LABEL, RECEIPT_TONE } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { RentalReceiptDetail } from "@/types/earning";
import { formatDate, formatDateTime } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Rental payment" };

export default async function RentalReceiptPage({ params }: PageProps<"/admin/rentals/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  let detail: RentalReceiptDetail;
  try {
    detail = await authFetch<RentalReceiptDetail>(`/api/v1/admin/rentals/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const r = detail.receipt;
  const recordedByMe = r.recordedBy === session.userId;
  const canApprove = hasPermission(session.permissions, Permission.RENTAL_APPROVE);
  const canVoid = canApprove || (recordedByMe && hasPermission(session.permissions, Permission.RENTAL_RECORD));
  const pending = r.status === "RECORDED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href={`/admin/products/${r.productId}`} className="font-mono text-sm text-gold-text hover:underline">{r.productCode}</Link>
          <h1 className="text-3xl font-semibold tracking-tight">Rent for period {r.periodNumber} of {r.periodCount}</h1>
          <p className="text-sm text-muted">{r.productTitle} · {formatPeriod(r.periodStartsOn, r.periodEndsOn)}</p>
        </div>
        <StatusBadge tone={RECEIPT_TONE[r.status]}>{RECEIPT_STATUS_LABEL[r.status]}</StatusBadge>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Card title="Payment">
            <dl className="grid gap-4 text-sm sm:grid-cols-3">
              <Item label="Amount received">{formatMoney(r.amount)}</Item>
              <Item label="Expected">{formatMoney(r.expectedAmount)}</Item>
              <Item label="Received on">{formatDate(r.receivedOn)}</Item>
              <Item label="Bank reference"><span className="font-mono">{r.externalReference}</span></Item>
              <Item label="Management fee">{formatPercent(r.managementFeePercent)}</Item>
              <Item label="Recorded">{formatDateTime(r.createdAt)}{recordedByMe ? " (by you)" : ""}</Item>
              {r.note && <div className="sm:col-span-3"><Item label="Note">{r.note}</Item></div>}
              {r.decidedAt && <Item label={r.status === "REJECTED" ? "Voided" : "Approved"}>{formatDateTime(r.decidedAt)}</Item>}
              {r.rejectionReason && <div className="sm:col-span-2"><Item label="Reason">{r.rejectionReason}</Item></div>}
            </dl>
          </Card>

          {detail.distribution.length > 0 || detail.retained ? (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold tracking-tight">{detail.preview ? "Distribution on approval" : "Distribution"}</h2>
              {detail.preview && <Notice>Preview from the current holdings. Nothing has been paid out yet.</Notice>}
              <dl className="grid gap-px border border-border bg-border text-sm sm:grid-cols-3">
                <Summary label="To investors" value={formatMoney(detail.toInvestors)} />
                <Summary label="Management fees" value={formatMoney(detail.fees)} />
                <Summary label="Retained by platform" value={formatMoney(detail.retained)} />
              </dl>
              {detail.distribution.length > 0 && (
                <DataTable columns={["Investor", "Ownership", "Gross share", "Fee", "Net credited"]}>
                  {detail.distribution.map((line) => (
                    <tr key={line.holdingId}>
                      <Cell>
                        <Link href={`/admin/users/${line.userId}`} className="font-mono text-xs text-gold-text hover:underline">{line.userId}</Link>
                      </Cell>
                      <Cell className="tabular-nums">{formatPercent(line.ownershipPercent, 4)}</Cell>
                      <Cell className="tabular-nums">{formatMoney(line.gross)}</Cell>
                      <Cell className="tabular-nums text-muted">{formatMoney(line.fee)}</Cell>
                      <Cell className="tabular-nums font-medium">{formatMoney(line.net)}</Cell>
                    </tr>
                  ))}
                </DataTable>
              )}
            </section>
          ) : null}
        </div>

        {pending && (canApprove || canVoid) && (
          <div className="space-y-6">
            {canApprove && (
              <Card title="Approve & distribute" description="Credits each investor's earnings and posts the ledger.">
                {recordedByMe ? (
                  <Notice tone="warning">You recorded this payment, so someone else must approve it.</Notice>
                ) : (
                  <ConfirmForm action={approveRentalAction.bind(null, r.id)} submitLabel="Approve & distribute"
                    confirm={`Distribute ${formatMoney(r.amount)} to the investors of ${r.productCode}? This cannot be undone.`} />
                )}
              </Card>
            )}
            {canVoid && (
              <Card title="Void" description="Wrong period, amount or reference? Void it and record it again.">
                <ReasonForm action={rejectRentalAction.bind(null, r.id)} label="Reason" submitLabel="Void payment"
                  confirm="Void this rental payment?" />
              </Card>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div className="space-y-1 bg-surface p-4">
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="font-display text-2xl font-semibold tabular-nums">{value}</dd>
    </div>
  );
}
