import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { BATCH_TONE, PAYOUT_METHOD_LABEL, WITHDRAWAL_KIND_LABEL, formatPeriod } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { PayoutBatch } from "@/types/withdrawal";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawal files" };

export default async function BatchesPage({ searchParams }: PageProps<"/admin/withdrawals/batches">) {
  const session = await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const batches = await authFetch<PageResponse<PayoutBatch>>(`/api/v1/admin/withdrawal-batches?page=${page}&size=25`);
  const canGenerate = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawal files"
        description="The bank sheets and cash batches each withdrawal run makes, for finance to pay and hand over at the office."
        actions={canGenerate ? <LinkButton href="/admin/withdrawal-runs" variant="secondary">Generate withdrawals</LinkButton> : undefined} />

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

