import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { ACCOUNT_LABEL } from "@/features/earnings/labels";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { PageResponse } from "@/types/api";
import type { LedgerAccount, LedgerEntry } from "@/types/earning";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Ledger account" };

export default async function LedgerAccountPage({ params, searchParams }: PageProps<"/admin/ledger/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  let account: LedgerAccount;
  try {
    account = await authFetch<LedgerAccount>(`/api/v1/admin/ledger/accounts/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const entries = await authFetch<PageResponse<LedgerEntry>>(`/api/v1/admin/ledger/accounts/${id}/entries?page=${page}&size=50`);

  return (
    <div className="space-y-6">
      <PageHeader title={ACCOUNT_LABEL[account.accountType]}
        description={`${account.balance.currency} · balance ${formatMoney(account.balance)} (${account.normalBalance.toLowerCase()} side)`}
        actions={account.ownerUserId ? (
          <Link href={`/admin/users/${account.ownerUserId}`} className="text-sm text-gold-text hover:underline">View investor</Link>
        ) : undefined} />
      {entries.content.length === 0 ? (
        <EmptyState title="No entries" />
      ) : (
        <DataTable columns={["When", "Transaction", "Description", "Debit", "Credit"]}>
          {entries.content.map((e) => (
            <tr key={e.id}>
              <Cell className="text-muted">{formatDateTime(e.createdAt)}</Cell>
              <Cell>{humanize(e.transactionType)}</Cell>
              <Cell className="max-w-md truncate">
                {e.transactionType === "RENTAL_DISTRIBUTION" || e.transactionType === "REFERRAL_COMMISSION"
                  ? <Link href={`/admin/rentals/${e.reference}`} className="text-gold-text hover:underline">{e.description}</Link>
                  : e.description}
              </Cell>
              <Cell className="tabular-nums">{e.direction === "DEBIT" ? formatMoney(e.amount) : ""}</Cell>
              <Cell className="tabular-nums">{e.direction === "CREDIT" ? formatMoney(e.amount) : ""}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={entries} basePath={`/admin/ledger/${account.id}`} params={{}} />
    </div>
  );
}
