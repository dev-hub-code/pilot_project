import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { EARNINGS_BALANCE_LABEL } from "@/features/earnings/labels";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Withdrawal, WithdrawalStatus } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

const FILTERS: readonly WithdrawalStatus[] = ["FAILED", "APPROVED", "BATCHED", "PROCESSING", "PAID", "RETURNED", "REJECTED"];

export default async function AdminWithdrawalsPage({ searchParams }: PageProps<"/admin/withdrawals">) {
  await requireStaff();
  const params = await searchParams;
  // Failed payments first: they wait on finance.
  const status = FILTERS.find((f) => f === params.status) ?? "FAILED";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  // Oldest first: the queue is worked in order of arrival.
  const withdrawals = await authFetch<PageResponse<Withdrawal>>(
    `/api/v1/admin/withdrawals?${new URLSearchParams({ status, page: String(page), size: "25", sort: "createdAt,asc" })}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals"
        description="Bank withdrawals are made each month in the rent and referral files. Cash is withdrawn at the office from the investor's page."
        actions={<LinkButton href="/admin/withdrawals/batches" variant="secondary">Withdrawal files</LinkButton>} />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <Link key={f} href={`/admin/withdrawals?status=${f}`} aria-current={f === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${f === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {WITHDRAWAL_STATUS_LABEL[f]}
          </Link>
        ))}
      </nav>
      {withdrawals.content.length === 0 ? (
        <EmptyState title="Nothing here" description={status === "FAILED" ? "No failed payments are waiting to be resolved." : undefined} />
      ) : (
        <DataTable columns={["Made", "Reference", "Amount", "From", "To", "By", "Status"]}>
          {withdrawals.content.map((w) => (
            <tr key={w.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(w.createdAt)}</Cell>
              <Cell><Link href={`/admin/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link></Cell>
              <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
              <Cell>{EARNINGS_BALANCE_LABEL[w.balance]}</Cell>
              <Cell>
                {w.payoutMethod === "OFFICE_CASH"
                  ? <>Cash at the office{w.status === "APPROVED" && <span className="block text-xs text-gold-text">To hand over</span>}</>
                  : <>{w.bankHolderName} <span className="text-xs text-muted">{w.bankName} {w.bankAccountMasked}</span></>}
              </Cell>
              <Cell>{w.origin === "SCHEDULED" ? "Monthly run" : w.origin === "OFFICE" ? "At the office" : "Investor request"}</Cell>
              <Cell><StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={withdrawals} basePath="/admin/withdrawals" params={{ status }} />
    </div>
  );
}
