import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { CreateBatchForm } from "@/features/admin/withdrawal-forms";
import { BATCH_TONE } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PayoutBatch, Withdrawal } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payout batches" };

export default async function BatchesPage({ searchParams }: PageProps<"/admin/withdrawals/batches">) {
  const session = await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const [batches, approved] = await Promise.all([
    authFetch<PageResponse<PayoutBatch>>(`/api/v1/admin/withdrawal-batches?page=${page}&size=25`),
    authFetch<PageResponse<Withdrawal>>("/api/v1/admin/withdrawals?status=APPROVED&size=500"),
  ]);
  const waiting = new Map<string, number>();
  for (const w of approved.content) waiting.set(w.amount.currency, (waiting.get(w.amount.currency) ?? 0) + 1);
  const canProcess = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);

  return (
    <div className="space-y-6">
      <PageHeader title="Payout batches" description="Approved withdrawals are paid by bank file, one batch per currency." />
      {canProcess && (
        <Card title="New batch">
          {waiting.size === 0 ? (
            <p className="text-sm text-muted">No approved withdrawals are waiting to be paid.</p>
          ) : (
            <CreateBatchForm currencies={[...waiting].map(([currency, count]) => ({ value: currency, label: `${currency} · ${count} waiting` }))} />
          )}
        </Card>
      )}
      {batches.content.length === 0 ? (
        <EmptyState title="No batches yet" />
      ) : (
        <DataTable columns={["Created", "Batch", "Items", "Total", "Paid / failed / outstanding", "Status"]}>
          {batches.content.map((b) => (
            <tr key={b.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(b.createdAt)}</Cell>
              <Cell><Link href={`/admin/withdrawals/batches/${b.id}`} className="font-mono text-gold-text hover:underline">{b.reference}</Link></Cell>
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
