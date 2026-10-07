import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ORDER_STATUS_LABEL, ORDER_TONE } from "@/features/orders/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Order, OrderStatus } from "@/types/order";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Orders" };

const STATUSES = Object.keys(ORDER_STATUS_LABEL) as OrderStatus[];

export default async function AdminOrdersPage({ searchParams }: PageProps<"/admin/orders">) {
  await requireStaff();
  const params = await searchParams;
  const status = STATUSES.find((s) => s === params.status);
  const q = typeof params.q === "string" ? params.q.slice(0, 30) : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (status) query.set("status", status);
  if (q) query.set("q", q);
  const orders = await authFetch<PageResponse<Order>>(`/api/v1/admin/orders?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Orders" description="Investor checkouts and their payment state." />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter by status" className="flex flex-wrap gap-2 text-sm">
          {[undefined, ...STATUSES].map((s) => (
            <Link key={s ?? "all"} href={s ? `/admin/orders?status=${s}` : "/admin/orders"} aria-current={s === status ? "page" : undefined}
              className={`rounded-full border px-3 py-1 ${s === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
              {s ? ORDER_STATUS_LABEL[s] : "All"}
            </Link>
          ))}
        </nav>
        <form className="flex gap-2" action="/admin/orders">
          {status && <input type="hidden" name="status" value={status} />}
          <input name="q" defaultValue={q} placeholder="Order number" aria-label="Search by order number"
            className="h-9 w-48 rounded-none border border-border bg-surface px-3 text-sm outline-none focus:border-gold" />
        </form>
      </div>
      {orders.content.length === 0 ? (
        <EmptyState title="No orders" />
      ) : (
        <DataTable columns={["Order", "Placed", "Offerings", "Total", "Status"]}>
          {orders.content.map((o) => (
            <tr key={o.id} className="hover:bg-background">
              <Cell><Link href={`/admin/orders/${o.id}`} className="font-mono font-medium text-gold-text hover:underline">{o.orderNumber}</Link></Cell>
              <Cell className="text-muted">{formatDateTime(o.createdAt)}</Cell>
              <Cell className="max-w-xs truncate">{o.items.map((i) => i.productCode).join(", ")}</Cell>
              <Cell className="tabular-nums">{formatMoney(o.total)}</Cell>
              <Cell><StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={orders} basePath="/admin/orders" params={{ status, q: q || undefined }} />
    </div>
  );
}
