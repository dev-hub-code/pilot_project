import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Withdrawal, WithdrawalStatus } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

const FILTERS: readonly WithdrawalStatus[] = ["PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "PAID", "FAILED", "REJECTED"];

export default async function AdminWithdrawalsPage({ searchParams }: PageProps<"/admin/withdrawals">) {
  await requireStaff();
  const params = await searchParams;
  const status = FILTERS.find((f) => f === params.status) ?? "PENDING_APPROVAL";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  // Oldest first: the queue is worked in order of arrival.
  const withdrawals = await authFetch<PageResponse<Withdrawal>>(
    `/api/v1/admin/withdrawals?${new URLSearchParams({ status, page: String(page), size: "25", sort: "createdAt,asc" })}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals" description="Approve requests, then pay approved withdrawals in batches."
        actions={<LinkButton href="/admin/withdrawals/batches" variant="secondary">Payout batches</LinkButton>} />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <Link key={f} href={`/admin/withdrawals?status=${f}`} aria-current={f === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${f === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {WITHDRAWAL_STATUS_LABEL[f]}
          </Link>
        ))}
      </nav>
      {withdrawals.content.length === 0 ? (
        <EmptyState title="Nothing here" />
      ) : (
        <DataTable columns={["Requested", "Reference", "Amount", "To", "Approvals", "Status"]}>
          {withdrawals.content.map((w) => (
            <tr key={w.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(w.createdAt)}</Cell>
              <Cell><Link href={`/admin/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link></Cell>
              <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
              <Cell>{w.bankHolderName} <span className="text-xs text-muted">{w.bankName} {w.bankAccountMasked}</span></Cell>
              <Cell className="tabular-nums">{w.approvals} of {w.requiredApprovals}</Cell>
              <Cell><StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={withdrawals} basePath="/admin/withdrawals" params={{ status }} />
    </div>
  );
}
