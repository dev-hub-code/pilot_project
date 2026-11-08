import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, Ban, CheckCircle2, ChevronRight, Clock, FileText, Hourglass, Receipt } from "lucide-react";
import { PortPhoto } from "@/components/brand/port-photo";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelOrderAction } from "@/features/orders/actions";
import { ORDER_STATUS_LABEL, ORDER_TONE } from "@/features/orders/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Order, OrderStatus } from "@/types/order";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Orders" };

/** Each status's icon and its colours, for the tile at the start of an order's row. */
const STATUS_ICON: Record<OrderStatus, { icon: typeof Clock; tile: string }> = {
  PENDING_PAYMENT: { icon: Hourglass, tile: "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300" },
  CONFIRMED: { icon: CheckCircle2, tile: "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300" },
  EXPIRED: { icon: Clock, tile: "bg-background text-muted" },
  CANCELLED: { icon: Ban, tile: "bg-background text-muted" },
};

/** The plans in an order by name, each once. */
const planNames = (order: Order) => [...new Set(order.items.map((item) => item.productTitle))].join(", ");

const units = (order: Order) => {
  const total = order.items.reduce((sum, item) => sum + (item.units ?? 0), 0);
  return total > 0 ? `${total} ${total === 1 ? "unit" : "units"}` : null;
};

export default async function OrdersPage({ searchParams }: PageProps<"/orders">) {
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const orders = await authFetch<PageResponse<Order>>(`/api/v1/orders?page=${page}&size=20`);
  const pending = orders.content.filter((o) => o.status === "PENDING_PAYMENT");

  return (
    <div className="space-y-8">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="orders-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <PortPhoto name="terminal-above" alt="" eager sizes="(min-width: 1280px) 1216px, 100vw"
          className="absolute inset-0 -z-20 size-full object-cover saturate-[0.8]" />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-[linear-gradient(100deg,var(--ink)_0%,rgb(11_42_85/0.92)_50%,rgb(11_42_85/0.6)_100%)]" />
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div className="flex flex-wrap items-end justify-between gap-6 p-6 sm:p-8 lg:p-10">
          <div className="space-y-3">
            <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Orders</p>
            <h1 id="orders-title" className="text-3xl font-semibold tracking-tight sm:text-4xl">
              Your <span className="text-gold-on-ink">orders</span>
            </h1>
            <p className="max-w-lg text-sm leading-relaxed text-on-ink/75">Every checkout, its payment status and invoice.</p>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            {orders.totalElements > 0 && (
              <span className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-on-ink/20 bg-on-ink/10 px-4 text-sm backdrop-blur-md">
                <Receipt aria-hidden="true" className="size-4 text-gold-on-ink" />
                <span className="font-semibold tabular-nums">{orders.totalElements}</span> {orders.totalElements === 1 ? "order" : "orders"}
              </span>
            )}
            <Link href="/invest"
              className="group inline-flex min-h-11 items-center gap-2 rounded-xl bg-on-ink px-5 text-sm font-semibold text-ink shadow-lg transition duration-300 hover:-translate-y-0.5 hover:bg-gold-on-ink">
              New investment <ArrowRight aria-hidden="true" className="size-4 transition-transform duration-300 group-hover:translate-x-1" />
            </Link>
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------- awaiting payment */}
      {pending.length > 0 && (
        <section aria-labelledby="pending-title" className="rounded-2xl border border-amber-300/70 bg-amber-50/70 p-5 dark:border-amber-500/30 dark:bg-amber-500/[0.07] sm:p-6">
          <h2 id="pending-title" className="flex items-center gap-2 text-sm font-semibold text-amber-900 dark:text-amber-200">
            <Hourglass aria-hidden="true" className="size-4" />
            {pending.length === 1 ? "1 order is waiting for payment" : `${pending.length} orders are waiting for payment`}
          </h2>
          <ul className="mt-4 grid gap-3 md:grid-cols-2">
            {pending.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-surface p-4 dark:border-amber-500/20">
                <div className="min-w-0 text-sm">
                  <p className="font-semibold">{planNames(o)} · <span className="tabular-nums">{formatMoney(o.total)}</span></p>
                  <p className="text-xs text-muted">{o.orderNumber} · pay by {formatDateTime(o.expiresAt)}</p>
                </div>
                <Link href={`/orders/${o.id}`}
                  className="inline-flex min-h-10 items-center gap-1.5 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
                  Pay now <ArrowRight aria-hidden="true" className="size-4" />
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* ------------------------------------------------------------------ orders */}
      {orders.content.length === 0 ? (
        <div className="space-y-4 rounded-3xl border border-border bg-surface p-8 text-center shadow-sm">
          <EmptyState title="No orders yet" description="Orders appear here once you invest in a plan." />
          <Link href="/invest"
            className="inline-flex min-h-11 items-center gap-2 rounded-lg bg-gold px-5 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">
            See the plans <ArrowRight aria-hidden="true" className="size-4" />
          </Link>
        </div>
      ) : (
        <section aria-label="Orders" className="overflow-hidden rounded-3xl border border-border bg-surface shadow-sm">
          <ul className="divide-y divide-border">
            {orders.content.map((o) => {
              const { icon: Icon, tile } = STATUS_ICON[o.status];
              const closed = o.status === "EXPIRED" || o.status === "CANCELLED";
              const count = units(o);
              return (
                <li key={o.id} className="group flex flex-wrap items-center gap-4 px-4 py-4 transition-colors hover:bg-background sm:flex-nowrap sm:px-6">
                  <span aria-hidden="true" className={`flex size-11 shrink-0 items-center justify-center rounded-xl transition-transform duration-300 group-hover:scale-105 ${tile}`}>
                    <Icon className="size-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <Link href={`/orders/${o.id}`} className={`card-link block truncate font-semibold ${closed ? "text-muted" : ""}`}>
                      {planNames(o)}
                    </Link>
                    <p className="mt-0.5 text-xs text-muted sm:truncate">
                      <span className="font-mono">{o.orderNumber}</span> · {formatDateTime(o.createdAt)}{count ? ` · ${count}` : ""}
                    </p>
                  </div>
                  <div className="flex w-full items-center justify-between gap-4 pl-15 sm:w-auto sm:justify-end sm:pl-0">
                    <div className="text-left sm:text-right">
                      <p className={`font-display text-lg font-semibold tabular-nums ${closed ? "text-muted line-through decoration-1" : ""}`}>{formatMoney(o.total)}</p>
                      <StatusBadge tone={ORDER_TONE[o.status]}>{ORDER_STATUS_LABEL[o.status]}</StatusBadge>
                    </div>
                    <div className="flex items-center gap-2">
                      {o.status === "CONFIRMED" && (
                        <Link href={`/orders/${o.id}/invoice`} title={`Invoice for ${o.orderNumber}`}
                          className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-border bg-surface px-3 text-xs font-medium transition-colors hover:border-gold hover:text-gold-text">
                          <FileText aria-hidden="true" className="size-3.5" /> Invoice
                        </Link>
                      )}
                      {o.status === "PENDING_PAYMENT" && (
                        <ActionButton action={cancelOrderAction.bind(null, o.id)} label="Cancel" variant="quiet" pendingLabel="Cancelling…"
                          confirm={`Cancel ${o.orderNumber}? If you have already paid for it, the money is refunded to you once it arrives.`} />
                      )}
                      <ChevronRight aria-hidden="true" className="hidden size-5 text-muted transition-transform duration-300 group-hover:translate-x-1 group-hover:text-gold-text sm:block" />
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>
      )}
      <Pagination page={orders} basePath="/orders" params={{}} />
    </div>
  );
}
