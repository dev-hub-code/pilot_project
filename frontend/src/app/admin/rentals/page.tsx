import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatPeriod, RECEIPT_STATUS_LABEL, RECEIPT_TONE } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DuePeriod, ReceiptStatus, RentalReceipt } from "@/types/earning";
import { formatDate, formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Rentals" };

const VIEWS = [
  { key: "due", label: "Due from lessees" },
  { key: "RECORDED", label: "Awaiting approval" },
  { key: "DISTRIBUTED", label: "Distributed" },
  { key: "REJECTED", label: "Voided" },
] as const;

export default async function RentalsPage({ searchParams }: PageProps<"/admin/rentals">) {
  const session = await requireStaff();
  const params = await searchParams;
  const view = VIEWS.find((v) => v.key === params.view) ?? VIEWS[0];
  const canRecord = hasPermission(session.permissions, Permission.RENTAL_RECORD);

  return (
    <div className="space-y-6">
      <PageHeader title="Rentals" description="Record what lessees paid, approve it, and it is distributed to investors by ownership." />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/rentals?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>
      {view.key === "due" ? <DueList canRecord={canRecord} /> : <Receipts status={view.key} page={params.page} />}
    </div>
  );
}

async function DueList({ canRecord }: { canRecord: boolean }) {
  const due = await authFetch<DuePeriod[]>("/api/v1/admin/rentals/due");
  if (due.length === 0) {
    return <EmptyState title="Nothing outstanding" description="Every rental period that has ended has been recorded." />;
  }
  return (
    <DataTable columns={["Offering", "Period", "Dates", "Due", "Expected", ""]}>
      {due.map((d) => (
        <tr key={`${d.productId}-${d.periodNumber}`} className="hover:bg-background">
          <Cell>
            <Link href={`/admin/products/${d.productId}`} className="font-mono text-gold-text hover:underline">{d.productCode}</Link>
            <span className="block max-w-xs truncate text-xs text-muted">{d.productTitle}</span>
          </Cell>
          <Cell className="tabular-nums">{d.periodNumber} of {d.periodCount}</Cell>
          <Cell className="text-muted">{formatPeriod(d.periodStartsOn, d.dueOn)}</Cell>
          <Cell>
            {formatDate(d.dueOn)}
            {d.daysOverdue > 0 && <span className="block text-xs text-rose-600 dark:text-rose-400">{d.daysOverdue} days overdue</span>}
          </Cell>
          <Cell className="tabular-nums">{formatMoney(d.expectedAmount)}</Cell>
          <Cell>
            {canRecord && (
              <Link href={`/admin/rentals/new?productId=${d.productId}&period=${d.periodNumber}`} className="text-gold-text hover:underline">
                Record payment
              </Link>
            )}
          </Cell>
        </tr>
      ))}
    </DataTable>
  );
}

async function Receipts({ status, page: rawPage }: { status: ReceiptStatus; page: string | string[] | undefined }) {
  const page = Math.max(0, Number.parseInt(String(rawPage ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", status });
  const receipts = await authFetch<PageResponse<RentalReceipt>>(`/api/v1/admin/rentals?${query}`);
  if (receipts.content.length === 0) return <EmptyState title="Nothing here" />;
  return (
    <>
      <DataTable columns={["Recorded", "Offering", "Period", "Received", "Amount", "Reference", "Status"]}>
        {receipts.content.map((r) => (
          <tr key={r.id} className="hover:bg-background">
            <Cell className="text-muted">
              <Link href={`/admin/rentals/${r.id}`} className="text-gold-text hover:underline">{formatDateTime(r.createdAt)}</Link>
            </Cell>
            <Cell><span className="font-mono">{r.productCode}</span></Cell>
            <Cell className="tabular-nums">{r.periodNumber} of {r.periodCount}</Cell>
            <Cell className="text-muted">{formatDate(r.receivedOn)}</Cell>
            <Cell className="tabular-nums">
              {formatMoney(r.amount)}
              {r.amount.amount !== r.expectedAmount.amount && (
                <span className="block text-xs text-muted">expected {formatMoney(r.expectedAmount)}</span>
              )}
            </Cell>
            <Cell className="font-mono text-xs">{r.externalReference}</Cell>
            <Cell><StatusBadge tone={RECEIPT_TONE[r.status]}>{RECEIPT_STATUS_LABEL[r.status]}</StatusBadge></Cell>
          </tr>
        ))}
      </DataTable>
      <Pagination page={receipts} basePath="/admin/rentals" params={{ view: status }} />
    </>
  );
}
