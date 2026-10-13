import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { DEPOSIT_MODE_LABEL, METHOD_LABEL, PAYMENT_TONE } from "@/features/orders/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Payment, PaymentStatus } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payments" };

type Filter = { key: string; status: PaymentStatus; method?: "BANK_TRANSFER" | "OFFICE_CASH"; label: string };

const FILTERS: readonly [Filter, ...Filter[]] = [
  { key: "bank", status: "PENDING", method: "BANK_TRANSFER", label: "Bank payments to verify" },
  { key: "office", status: "PENDING", method: "OFFICE_CASH", label: "Cash expected at the office" },
  { key: "refunds", status: "REFUND_REQUIRED", label: "Refunds due" },
  { key: "succeeded", status: "SUCCEEDED", label: "Succeeded" },
  { key: "failed", status: "FAILED", label: "Failed" },
];

export default async function PaymentsPage({ searchParams }: PageProps<"/admin/payments">) {
  await requireStaff();
  const params = await searchParams;
  const filter = FILTERS.find((f) => f.key === params.view) ?? FILTERS[0];
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", status: filter.status });
  if (filter.method) query.set("method", filter.method);
  const payments = await authFetch<PageResponse<Payment>>(`/api/v1/admin/payments?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Payments" description="Check bank payments against the statement, record cash received at the office, and settle refunds. Open an order to confirm or reject its payment." />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {FILTERS.map((f) => (
          <Link key={f.key} href={`/admin/payments?view=${f.key}`} aria-current={f === filter ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${f === filter ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {f.label}
          </Link>
        ))}
      </nav>
      {payments.content.length === 0 ? (
        <EmptyState title="Nothing here" />
      ) : (
        <DataTable columns={["Started", "Order", "Method", "Reference", "Investor paid by", "Amount", "Status"]}>
          {payments.content.map((p) => (
            <tr key={p.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(p.createdAt)}</Cell>
              <Cell><Link href={`/admin/orders/${p.orderId}`} className="font-mono text-gold-text hover:underline">{p.orderNumber ?? p.orderId}</Link></Cell>
              <Cell>{METHOD_LABEL[p.method]}</Cell>
              <Cell className="font-mono text-xs">{p.providerReference}</Cell>
              <Cell className="text-xs">
                {p.deposit ? (
                  <>
                    {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>
                    <span className="block text-muted">{p.deposit.bankName} {p.deposit.accountNumber}</span>
                  </>
                ) : <span className="text-muted">{p.method === "BANK_TRANSFER" && p.status === "PENDING" ? "Not submitted yet"
                  : p.method === "OFFICE_CASH" ? (p.externalReference ? `Receipt ${p.externalReference}` : "To pay at the office") : "—"}</span>}
              </Cell>
              <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
              <Cell><StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={payments} basePath="/admin/payments" params={{ view: filter.key }} />
    </div>
  );
}
