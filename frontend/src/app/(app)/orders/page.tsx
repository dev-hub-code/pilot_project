import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ORDER_STATUS_LABEL, ORDER_TONE } from "@/features/orders/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Order } from "@/types/order";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Orders" };

export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const orders = await authFetch<PageResponse<Order>>(`/api/v1/orders?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Orders" description="Every checkout, its payment status and invoice." />
      {orders.content.length === 0 ? (
        <EmptyState title="No orders yet" description="Orders appear here once you check out your cart." />
      ) : (
        <DataTable columns={["Order", "Placed", "Offerings", "Total", "Status"]}>
          {orders.content.map((o) => (
            <tr key={o.id} className="hover:bg-background">
              <Cell><Link href={`/orders/${o.id}`} className="font-mono font-medium text-gold-text hover:underline">{o.orderNumber}</Link></Cell>
              <Cell className="text-muted">{formatDateTime(o.createdAt)}</Cell>
              <Cell className="max-w-xs truncate">{o.items.map((i) => i.productCode).join(", ")}</Cell>
              <Cell className="tabular-nums">{formatMoney(o.total)}</Cell>
              <Cell><StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={orders} basePath="/orders" params={{}} />
    </div>
  );
}
