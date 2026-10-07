import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelOrderAction, simulatePaymentAction, startPaymentAction } from "@/features/orders/actions";
import { METHOD_LABEL, ORDER_STATUS_LABEL, ORDER_TONE, PAYMENT_TONE } from "@/features/orders/labels";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Order, Payment } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney, formatPercent, FREQUENCY_LABEL } from "@/utils/money";

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
          Your investment is confirmed ({formatDateTime(order.confirmedAt)}). It now appears in your <Link href="/portfolio" className="underline">portfolio</Link>.
        </Notice>
      )}
      {(order.status === "EXPIRED" || order.status === "CANCELLED") && (
        <Notice tone="warning">{order.closeReason ?? ORDER_STATUS_LABEL[order.status]}. The reserved capacity was released.</Notice>
      )}
      {refunds.map((p) => (
        <Notice key={p.id} tone="warning">
          {formatMoney(p.amount)} paid by {METHOD_LABEL[p.method].toLowerCase()} could not be applied to this order
          {p.status === "REFUNDED" ? ` and was refunded on ${formatDateTime(p.refundedAt)}.` : "; it will be refunded to you."}
        </Notice>
      ))}

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <DataTable columns={["Offering", "Amount", "Ownership", "Expected rental", "Terms"]}>
            {order.items.map((item) => (
              <tr key={item.productId}>
                <Cell>
                  <Link href={`/marketplace/${item.productId}`} className="font-mono text-gold-text hover:underline">{item.productCode}</Link>
                  <span className="block max-w-xs truncate text-xs text-muted">{item.productTitle}</span>
                </Cell>
                <Cell className="tabular-nums">{formatMoney(item.amount)}</Cell>
                <Cell className="tabular-nums">{formatPercent(item.ownershipPercent, 4)}</Cell>
                <Cell className="tabular-nums">{formatMoney(item.rentalPerPayment)}/{FREQUENCY_LABEL[item.rentalFrequency]}</Cell>
                <Cell>v{item.termsVersion}</Cell>
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
                    <Cell>{METHOD_LABEL[p.method]}</Cell>
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
              <p className="mt-2 text-sm text-muted">Pay by {formatDateTime(order.expiresAt)}, or the reservation lapses.</p>
            )}
          </Card>

          {order.status === "PENDING_PAYMENT" && (
            <Card title="Pay">
              <div className="space-y-6">
                {pending?.bankTransfer && (
                  <div className="space-y-3">
                    <p className="text-sm">Transfer exactly <strong>{formatMoney(pending.bankTransfer.amount)}</strong> to:</p>
                    <dl className="grid gap-2 text-sm">
                      <Row label="Beneficiary" value={pending.bankTransfer.beneficiaryName} />
                      <Row label="IBAN" value={pending.bankTransfer.iban} mono />
                      <Row label="BIC" value={pending.bankTransfer.bic} mono />
                      <Row label="Bank" value={pending.bankTransfer.bankName} />
                      <Row label="Reference" value={pending.bankTransfer.reference} mono />
                    </dl>
                    <Notice tone="info">Quote the reference exactly. We confirm your order when the money arrives.</Notice>
                  </div>
                )}
                {pending?.method === "CARD" && (
                  <div className="space-y-3">
                    <p className="text-sm">Card payment <span className="font-mono text-xs">{pending.providerReference}</span> is waiting for the card issuer.</p>
                    {pending.simulated && (
                      <div className="space-y-3 border border-dashed border-border p-4">
                        <p className="text-xs uppercase tracking-[0.08em] text-muted">Development: simulated card gateway</p>
                        <ActionButton action={simulatePaymentAction.bind(null, order.id, pending.id, "SUCCEEDED")} label="Simulate approved card" className="w-full" />
                        <ActionButton action={simulatePaymentAction.bind(null, order.id, pending.id, "FAILED")} label="Simulate declined card" variant="quiet" className="w-full" />
                      </div>
                    )}
                  </div>
                )}
                <div className="space-y-3">
                  {pending?.method !== "BANK_TRANSFER" && (
                    <ActionButton action={startPaymentAction.bind(null, order.id, "BANK_TRANSFER", key())} label="Pay by bank transfer"
                      variant={pending ? "quiet" : "primary"} className="w-full" />
                  )}
                  {pending?.method !== "CARD" && (
                    <ActionButton action={startPaymentAction.bind(null, order.id, "CARD", key())} label="Pay by card"
                      variant="quiet" className="w-full" />
                  )}
                </div>
                <div className="border-t border-border pt-4">
                  <ActionButton action={cancelOrderAction.bind(null, order.id)} label="Cancel order" variant="quiet"
                    confirm={`Cancel ${order.orderNumber}? Your reserved share is released.`} className="w-full" />
                </div>
              </div>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted">{label}</dt>
      <dd className={`text-right font-medium ${mono ? "font-mono" : ""}`}>{value}</dd>
    </div>
  );
}
