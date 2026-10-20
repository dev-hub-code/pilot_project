import type { Metadata } from "next";
import Link from "next/link";
import { CardField, CardList, CardListItem } from "@/components/ui/card-list";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelOrderAction } from "@/features/orders/actions";
import { ORDER_STATUS_LABEL, ORDER_TONE, containersSummary } from "@/features/orders/labels";
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
        <>
          <CardList label="Orders">
            {orders.content.map((o) => (
              <CardListItem key={o.id}>
                <div className="flex items-start justify-between gap-3">
                  <Link href={`/orders/${o.id}`} className="card-link font-mono font-medium text-gold-text hover:underline">{o.orderNumber}</Link>
                  <StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge>
                </div>
                <dl className="grid grid-cols-2 gap-3">
                  <CardField label="Total"><span className="font-medium tabular-nums">{formatMoney(o.total)}</span></CardField>
                  <CardField label="Placed"><span className="text-muted">{formatDateTime(o.createdAt)}</span></CardField>
                  <CardField label="Containers" className="col-span-2">{containersSummary(o.items)}</CardField>
                </dl>
                <div className="flex items-center justify-between gap-3">
                  <Link href={`/orders/${o.id}`} className="text-gold-text hover:underline">View order →</Link>
                  {o.status === "PENDING_PAYMENT" && (
                    <ActionButton action={cancelOrderAction.bind(null, o.id)} label="Cancel" variant="quiet" pendingLabel="Cancelling…"
                      confirm={`Cancel ${o.orderNumber}? If you have already paid for it, the money is refunded to you once it arrives.`} />
                  )}
                </div>
              </CardListItem>
            ))}
          </CardList>
          <DataTable columns={["Order", "Placed", "Containers", "Total", "Status", ""]} className="hidden sm:block">
            {orders.content.map((o) => (
              <tr key={o.id} className="hover:bg-background">
                <Cell><Link href={`/orders/${o.id}`} className="card-link font-mono font-medium text-gold-text hover:underline">{o.orderNumber}</Link></Cell>
                <Cell className="text-muted">{formatDateTime(o.createdAt)}</Cell>
                <Cell className="max-w-xs truncate">{containersSummary(o.items)}</Cell>
                <Cell className="tabular-nums">{formatMoney(o.total)}</Cell>
                <Cell><StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge></Cell>
                <Cell>
                  {o.status === "PENDING_PAYMENT" && (
                    <ActionButton action={cancelOrderAction.bind(null, o.id)} label="Cancel" variant="quiet" pendingLabel="Cancelling…"
                      confirm={`Cancel ${o.orderNumber}? If you have already paid for it, the money is refunded to you once it arrives.`} />
                  )}
                </Cell>
              </tr>
            ))}
          </DataTable>
        </>
      )}
      <Pagination page={orders} basePath="/orders" params={{}} />
    </div>
  );
}
