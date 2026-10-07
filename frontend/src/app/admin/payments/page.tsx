import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { METHOD_LABEL, PAYMENT_TONE } from "@/features/orders/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Payment, PaymentStatus } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payments" };

type Filter = { status: PaymentStatus; method?: "BANK_TRANSFER"; label: string };

const FILTERS: readonly [Filter, ...Filter[]] = [
  { status: "PENDING", method: "BANK_TRANSFER", label: "Transfers to match" },
  { status: "REFUND_REQUIRED", label: "Refunds due" },
  { status: "SUCCEEDED", label: "Succeeded" },
  { status: "FAILED", label: "Failed" },
];

export default async function PaymentsPage({ searchParams }: PageProps<"/admin/payments">) {
  await requireStaff();
  const params = await searchParams;
  const filter = FILTERS.find((f) => f.status === params.status) ?? FILTERS[0];
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", status: filter.status });
  if (filter.method) query.set("method", filter.method);
  const payments = await authFetch<PageResponse<Payment>>(`/api/v1/admin/payments?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Payments" description="Match incoming bank transfers and settle refunds. Open an order to act on its payment." />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <Link key={f.status} href={`/admin/payments?status=${f.status}`} aria-current={f === filter ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${f === filter ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {f.label}
          </Link>
        ))}
      </nav>
      {payments.content.length === 0 ? (
        <EmptyState title="Nothing here" />
      ) : (
        <DataTable columns={["Started", "Order", "Method", "Reference", "Amount", "Status"]}>
          {payments.content.map((p) => (
            <tr key={p.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(p.createdAt)}</Cell>
              <Cell><Link href={`/admin/orders/${p.orderId}`} className="font-mono text-gold-text hover:underline">{p.orderNumber ?? p.orderId}</Link></Cell>
              <Cell>{METHOD_LABEL[p.method]}</Cell>
              <Cell className="font-mono text-xs">{p.providerReference}</Cell>
              <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
              <Cell><StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={payments} basePath="/admin/payments" params={{ status: filter.status }} />
    </div>
  );
}
