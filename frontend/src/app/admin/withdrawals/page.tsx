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
  // A search (a whole reference, or the investor's ID or email) covers every status, latest first.
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  // Oldest first: the queue is worked in order of arrival.
  const withdrawals = await authFetch<PageResponse<Withdrawal>>(`/api/v1/admin/withdrawals?${new URLSearchParams(q
    ? { q, page: String(page), size: "25", sort: "createdAt,desc" }
    : { status, page: String(page), size: "25", sort: "createdAt,asc" })}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawals"
        description="Withdrawals are made by withdrawal runs (Withdrawal files): each run has a bank sheet, and cash at the office is marked collected by hand."
        actions={<LinkButton href="/admin/withdrawals/batches" variant="secondary">Withdrawal files</LinkButton>} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
          {FILTERS.map((f) => (
            <Link key={f} href={`/admin/withdrawals?status=${f}`} aria-current={!q && f === status ? "page" : undefined}
              className={`rounded-full border px-3 py-1 ${!q && f === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
              {WITHDRAWAL_STATUS_LABEL[f]}
            </Link>
          ))}
        </nav>
        <form className="flex gap-2" action="/admin/withdrawals" role="search">
          <input name="q" defaultValue={q} placeholder="Reference, investor ID or email"
            aria-label="Search withdrawals by reference, or the investor's ID or email"
            className="h-9 w-72 rounded-none border border-border bg-surface px-3 text-sm outline-none focus:border-gold" />
        </form>
      </div>
      {withdrawals.content.length === 0 ? (
        <EmptyState title={q ? "No withdrawals found" : "Nothing here"}
          description={q ? `Nothing matches “${q}” exactly.` : status === "FAILED" ? "No failed payments are waiting to be resolved." : undefined} />
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
                  ? <>Cash{w.status === "APPROVED" && <span className="block text-xs text-gold-text">To hand over</span>}</>
                  : <>{w.bankHolderName} <span className="text-xs text-muted">{w.bankName} {w.bankAccountMasked}</span></>}
              </Cell>
              <Cell>{w.origin === "SCHEDULED" ? "Withdrawal run" : w.origin === "OFFICE" ? "At the office" : "Investor request"}</Cell>
              <Cell><StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={withdrawals} basePath="/admin/withdrawals" params={q ? { q } : { status }} />
    </div>
  );
}
