import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelOrderAction, startPaymentAction } from "@/features/orders/actions";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";
import { DepositForm } from "@/features/orders/deposit-form";
import { DEPOSIT_MODE_LABEL, DEPOSIT_REFERENCE_LABEL, METHOD_LABEL, ORDER_STATUS_LABEL, ORDER_TONE, PAYMENT_TONE } from "@/features/orders/labels";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Order, Payment } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Order" };

export default async function OrderPage({ params }: PageProps<"/orders/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  let order: Order;
  let payments: Payment[];
  try {
    [order, payments] = await Promise.all([
      authFetch<Order>(`/api/v1/orders/${id}`),
      authFetch<Payment[]>(`/api/v1/orders/${id}/payments`),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const pending = payments.find((p) => p.status === "PENDING");
  const awaitingVerification = pending?.deposit != null;
  const rejected = payments.find((p) => p.method === "BANK_TRANSFER" && p.status === "FAILED" && p.deposit);
  const refunds = payments.filter((p) => p.status === "REFUND_REQUIRED" || p.status === "REFUNDED");
  // Rendered fresh per request: a new key per intent, reused by retries of that same form.
  const key = () => crypto.randomUUID();

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/orders" className="hover:text-foreground">Orders</Link> / <span className="font-mono">{order.orderNumber}</span>
      </nav>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-mono text-3xl font-semibold tracking-tight">{order.orderNumber}</h1>
          <p className="text-sm text-muted">Placed {formatDateTime(order.createdAt)}</p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={ORDER_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusBadge>
          {order.status === "CONFIRMED" && <LinkButton href={`/orders/${order.id}/invoice`} variant="secondary">View invoice</LinkButton>}
        </div>
      </div>

      {order.status === "CONFIRMED" && (
        <Notice tone="success">
          Your investment is confirmed ({formatDateTime(order.confirmedAt)}). Your containers are listed below and in your <Link href="/portfolio" className="underline">portfolio</Link>; their leases and monthly payouts have started.
        </Notice>
      )}
      {(order.status === "EXPIRED" || order.status === "CANCELLED") && (
        <Notice tone="warning">{order.closeReason ?? ORDER_STATUS_LABEL[order.status]}. The reserved containers were released.</Notice>
      )}
      {order.status === "PENDING_PAYMENT" && awaitingVerification && pending?.deposit && (
        <Notice tone="info">
          We received your payment details ({DEPOSIT_MODE_LABEL[pending.deposit.mode]},{" "}
          {DEPOSIT_REFERENCE_LABEL[pending.deposit.mode]}: <span className="font-mono">{pending.deposit.reference}</span>).
          Your order is confirmed once our finance team has verified the payment. Your containers stay reserved until {formatDateTime(order.expiresAt)}.
        </Notice>
      )}
      {order.status === "PENDING_PAYMENT" && !pending && rejected && (
        <Notice tone="warning">
          We could not verify your payment <span className="font-mono">{rejected.deposit?.reference}</span>: {rejected.failureReason}. Please pay again below.
        </Notice>
      )}
      {refunds.map((p) => (
        <Notice key={p.id} tone="warning">
          {formatMoney(p.amount)} paid by {METHOD_LABEL[p.method].toLowerCase()} could not be applied to this order
          {p.status === "REFUNDED" ? ` and was refunded on ${formatDateTime(p.refundedAt)}.` : "; it will be refunded to you."}
        </Notice>
      ))}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <DataTable columns={["Plan", "Container", "Price", "Paid monthly", "Tenure"]}>
            {order.items.map((item) => (
              <tr key={item.id}>
                <Cell>
                  <Link href={`/marketplace/${item.productId}`} className="font-mono text-gold-text hover:underline">{item.productCode}</Link>
                  <span className="block max-w-xs truncate text-xs text-muted">{item.productTitle}</span>
                </Cell>
                <Cell>
                  {item.containerNumber
                    ? <span className="font-mono font-medium">{item.containerNumber}</span>
                    : <span className="text-muted">Assigned once paid</span>}
                  <span className="block text-xs text-muted">{CONTAINER_TYPE_LABEL[item.containerType]}</span>
                </Cell>
                <Cell className="tabular-nums">{formatMoney(item.amount)}</Cell>
                <Cell className="tabular-nums">{formatMoney(item.monthlyPayout)}</Cell>
                <Cell className="tabular-nums">{item.tenureMonths} months</Cell>
              </tr>
            ))}
          </DataTable>

          {payments.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold tracking-tight">Payments</h2>
              <DataTable columns={["Started", "Method", "Reference", "Amount", "Status"]}>
                {payments.map((p) => (
                  <tr key={p.id}>
                    <Cell className="text-muted">{formatDateTime(p.createdAt)}</Cell>
                    <Cell>
                      {METHOD_LABEL[p.method]}
                      {p.deposit && (
                        <span className="block text-xs text-muted">
                          {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>
                        </span>
                      )}
                    </Cell>
                    <Cell className="font-mono text-xs">{p.providerReference}</Cell>
                    <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
                    <Cell>
                      <StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge>
                      {p.failureReason && <span className="block text-xs text-muted">{p.failureReason}</span>}
                    </Cell>
                  </tr>
                ))}
              </DataTable>
            </section>
          )}
        </div>

        <aside className="space-y-6">
          <Card title="Total">
            <p className="font-display text-3xl font-semibold tabular-nums">{formatMoney(order.total)}</p>
            {order.status === "PENDING_PAYMENT" && (
              <p className="mt-2 text-sm text-muted">
                {awaitingVerification
                  ? `Payment being verified. Reserved until ${formatDateTime(order.expiresAt)}.`
                  : `Pay by ${formatDateTime(order.expiresAt)}, or the reservation lapses.`}
              </p>
            )}
          </Card>

          {order.status === "PENDING_PAYMENT" && (
            <Card title="Pay">
              <div className="space-y-6">
                {pending?.bankTransfer && <DepositForm key={pending.id} orderId={order.id} payment={pending} />}
                {pending?.method !== "BANK_TRANSFER" && (
                  <ActionButton action={startPaymentAction.bind(null, order.id, "BANK_TRANSFER", key())}
                    label="Pay by bank (online transfer, cheque or cash deposit)" className="w-full" />
                )}
                <div className="border-t border-border pt-4">
                  <ActionButton action={cancelOrderAction.bind(null, order.id)} label="Cancel order" variant="quiet"
                    confirm={awaitingVerification
                      ? `Cancel ${order.orderNumber}? Your reserved containers are released, and money you already paid is refunded once it arrives.`
                      : `Cancel ${order.orderNumber}? Your reserved containers are released.`} className="w-full" />
                </div>
              </div>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
