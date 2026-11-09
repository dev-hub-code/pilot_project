import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ORDER_STATUS_LABEL, ORDER_TONE } from "@/features/orders/labels";
import { Ban, CheckCircle2, Clock, Hourglass, Search } from "lucide-react";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Order, OrderStatus } from "@/types/order";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Orders" };

const STATUSES = Object.keys(ORDER_STATUS_LABEL) as OrderStatus[];

/** Each status's icon and colours, for the tile at the start of an order's row. */
const STATUS_ICON: Record<OrderStatus, { icon: typeof Clock; tile: string }> = {
  PENDING_PAYMENT: { icon: Hourglass, tile: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300" },
  CONFIRMED: { icon: CheckCircle2, tile: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300" },
  EXPIRED: { icon: Clock, tile: "bg-background text-muted ring-1 ring-border" },
  CANCELLED: { icon: Ban, tile: "bg-background text-muted ring-1 ring-border" },
};

/** The plans in an order by name, each once, with the units bought. */
function orderPlans(order: Order): { names: string; units: number } {
  return {
    names: [...new Set(order.items.map((item) => item.productTitle))].join(", "),
    units: order.items.reduce((sum, item) => sum + (item.units ?? 0), 0),
  };
}

export default async function AdminOrdersPage({ searchParams }: PageProps<"/platform/orders">) {
  const denied = await deniedUnlessInMenu("/platform/orders");
  if (denied) return denied;
  const session = await requireStaff();
  // Without browsing (support), orders are found only by a whole order number or the investor's ID or email.
  const searchOnly = !hasPermission(session.permissions, Permission.RECORD_BROWSE);
  const params = await searchParams;
  const status = STATUSES.find((s) => s === params.status);
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (status) query.set("status", status);
  if (q) query.set("q", q);
  const orders = searchOnly && !q ? null : await authFetch<PageResponse<Order>>(`/api/v1/admin/orders?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Orders"
        description={orders
          ? `Investor checkouts and their payment state · ${orders.totalElements.toLocaleString("en-IN")} ${orders.totalElements === 1 ? "order" : "orders"}${status || q ? " match" : ""}.`
          : "Investor checkouts and their payment state."} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter by status" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {[undefined, ...STATUSES].map((s) => {
            const href = (() => {
              const next = new URLSearchParams();
              if (s) next.set("status", s);
              if (q) next.set("q", q);
              const qs = next.toString();
              return qs ? `/platform/orders?${qs}` : "/platform/orders";
            })();
            return (
              <Link key={s ?? "all"} href={href} aria-current={s === status ? "page" : undefined}
                className={`rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${s === status
                  ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                  : "text-muted hover:bg-background hover:text-foreground"}`}>
                {s ? ORDER_STATUS_LABEL[s] : "All"}
              </Link>
            );
          })}
        </nav>
        <form action="/platform/orders" role="search"
          className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:w-auto">
          {status && <input type="hidden" name="status" value={status} />}
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={q} placeholder="Order number, investor ID or email"
            aria-label="Search by order number, or the investor's ID or email"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none sm:w-72" />
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </form>
      </div>
      {!orders ? (
        <EmptyState title="Search for an order"
          description="Enter a whole order number, or the investor's ID (e.g. SL03500) or email to see their orders." />
      ) : orders.content.length === 0 ? (
        <EmptyState title="No orders" description={q && searchOnly ? `Nothing matches “${q}” exactly.` : undefined} />
      ) : (
        <DataTable columns={["Order", "Placed", "Total", "Status"]}>
          {orders.content.map((o) => {
            const { icon: Icon, tile } = STATUS_ICON[o.status];
            const { names, units } = orderPlans(o);
            const closed = o.status === "EXPIRED" || o.status === "CANCELLED";
            return (
              <tr key={o.id} className="group hover:bg-background">
                <Cell wrap className="min-w-[16rem]">
                  <span className="flex items-center gap-3">
                    <span aria-hidden="true" className={`flex size-9 shrink-0 items-center justify-center rounded-lg transition-transform duration-300 group-hover:scale-105 ${tile}`}>
                      <Icon className="size-4" />
                    </span>
                    <span className="min-w-0">
                      <Link href={`/platform/orders/${o.id}`} className="card-link block font-mono font-medium text-gold-text">{o.orderNumber}</Link>
                      <span className="block truncate text-xs text-muted">{names}{units > 0 ? ` · ${units.toLocaleString("en-IN")} ${units === 1 ? "unit" : "units"}` : ""}</span>
                    </span>
                  </span>
                </Cell>
                <Cell className="text-muted">{formatDateTime(o.createdAt)}</Cell>
                <Cell className={`font-semibold tabular-nums ${closed ? "text-muted line-through decoration-1" : ""}`}>{formatMoney(o.total)}</Cell>
                <Cell><StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge></Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      {orders && <Pagination page={orders} basePath="/platform/orders" params={{ status, q: q || undefined }} />}
    </div>
  );
}
