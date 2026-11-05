import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { CardField, CardList, CardListItem } from "@/components/ui/card-list";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { cancelOrderAction, startPaymentAction } from "@/features/orders/actions";
import { DepositForm } from "@/features/orders/deposit-form";
import { RazorpayCheckout } from "@/features/orders/razorpay-checkout";
import { ReceiptForm } from "@/features/orders/receipt-form";
import { DEPOSIT_MODE_LABEL, DEPOSIT_REFERENCE_LABEL, METHOD_LABEL, ORDER_STATUS_LABEL, ORDER_TONE, PAYMENT_TONE } from "@/features/orders/labels";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isDemoAccount } from "@/lib/server/demo";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Order, Payment, PaymentMethod } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Order" };

export default async function OrderPage({ params }: PageProps<"/orders/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  let order: Order;
  let payments: Payment[];
  let demo: boolean;
  let methods: PaymentMethod[];
  try {
    [order, payments, demo, { methods }] = await Promise.all([
      authFetch<Order>(`/api/v1/orders/${id}`),
      authFetch<Payment[]>(`/api/v1/orders/${id}/payments`),
      isDemoAccount(),
      authFetch<{ methods: PaymentMethod[] }>("/api/v1/payments/methods"),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const pending = payments.find((p) => p.status === "PENDING");
  const awaitingVerification = pending?.deposit != null || pending?.receiptNumber != null;
  const rejected = payments.find((p) => p.method === "BANK_TRANSFER" && p.status === "FAILED" && p.deposit);
  const refunds = payments.filter((p) => p.status === "REFUND_REQUIRED" || p.status === "REFUNDED");
  const paid = payments.find((p) => p.status === "SUCCEEDED");
  // Online investors pay through the gateway; network investors by bank or at the office.
  const online = methods.includes("GATEWAY");
  // Rendered fresh per request: a new key per intent, reused by retries of that same form.
  const key = () => crypto.randomUUID();

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/orders" className="inline-flex min-h-6 items-center hover:text-foreground">Orders</Link> / <span className="font-mono">{order.orderNumber}</span>
      </nav>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-mono text-3xl font-semibold tracking-tight">{order.orderNumber}</h1>
          <p className="text-sm text-muted">Placed {formatDateTime(order.createdAt)}</p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={ORDER_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusBadge>
          {/* A demo account's orders are dummies, with no invoice. */}
          {order.status === "CONFIRMED" && !demo && <LinkButton href={`/orders/${order.id}/invoice`} variant="secondary">View invoice</LinkButton>}
        </div>
      </div>

      {order.status === "CONFIRMED" && (
        <Notice tone="success">
          Your investment is confirmed ({formatDateTime(order.confirmedAt)}). Your containers are listed below and in your <Link href="/portfolio" className="underline">portfolio</Link>; each term starts, and earns returns, once you accept its agreement there.
        </Notice>
      )}
      {(order.status === "EXPIRED" || order.status === "CANCELLED") && (
        <Notice tone="warning">{order.closeReason ?? ORDER_STATUS_LABEL[order.status]}. It can no longer be paid.</Notice>
      )}
      {order.status === "PENDING_PAYMENT" && awaitingVerification && pending?.deposit && (
        <Notice tone="info">
          We received your payment details ({DEPOSIT_MODE_LABEL[pending.deposit.mode]},{" "}
          {DEPOSIT_REFERENCE_LABEL[pending.deposit.mode]}: <span className="font-mono">{pending.deposit.reference}</span>).
          Your order is confirmed once our finance team has verified the payment. Please pay by {formatDateTime(order.expiresAt)}.
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
          <CardList label="Plans">
            {order.items.map((item) => (
              <CardListItem key={item.id}>
                <div>
                  <Link href={`/invest/${item.productId}`} className="font-medium text-gold-text hover:underline">{item.productTitle}</Link>
                </div>
                <dl className="grid grid-cols-2 gap-3">
                  {item.units !== null && item.unitPrice && (
                    <CardField label="Units"><span className="tabular-nums">{item.units} × {formatMoney(item.unitPrice)}</span></CardField>
                  )}
                  <CardField label="Amount"><span className="font-medium tabular-nums">{formatMoney(item.amount)}</span></CardField>
                  <CardField label="Returns monthly"><span className="tabular-nums">{formatMoney(item.monthlyPayout)}</span></CardField>
                  <CardField label="Term"><span className="tabular-nums">{item.tenureMonths} months</span></CardField>
                </dl>
              </CardListItem>
            ))}
          </CardList>
          <DataTable columns={["Plan", "Units", "Amount", "Returns monthly", "Term"]} className="hidden sm:block">
            {order.items.map((item) => (
              <tr key={item.id}>
                <Cell>
                  <Link href={`/invest/${item.productId}`} className="inline-flex min-h-6 items-center text-gold-text hover:underline">{item.productTitle}</Link>
                </Cell>
                <Cell className="tabular-nums">{item.units !== null && item.unitPrice ? `${item.units} × ${formatMoney(item.unitPrice)}` : "—"}</Cell>
                <Cell className="tabular-nums font-medium">{formatMoney(item.amount)}</Cell>
                <Cell className="tabular-nums">{formatMoney(item.monthlyPayout)}</Cell>
                <Cell className="tabular-nums">{item.tenureMonths} months</Cell>
              </tr>
            ))}
          </DataTable>

          {payments.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold tracking-tight">Payments</h2>
              <CardList label="Payments">
                {payments.map((p) => (
                  <CardListItem key={p.id}>
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="font-medium">{METHOD_LABEL[p.method]}</p>
                        {p.receiptNumber && (
                          <p className="text-xs text-muted">Receipt · <span className="font-mono">{p.receiptNumber}</span></p>
                        )}
                        {p.deposit && (
                          <p className="text-xs text-muted">
                            {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>
                          </p>
                        )}
                      </div>
                      <StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge>
                    </div>
                    {p.failureReason && <p className="text-xs text-muted">{p.failureReason}</p>}
                    <dl className="grid grid-cols-2 gap-3">
                      <CardField label="Amount"><span className="tabular-nums">{formatMoney(p.amount)}</span></CardField>
                      <CardField label="Started"><span className="text-muted">{formatDateTime(p.createdAt)}</span></CardField>
                      <CardField label="Reference" className="col-span-2"><span className="break-all font-mono text-xs">{p.providerReference}</span></CardField>
                    </dl>
                  </CardListItem>
                ))}
              </CardList>
              <DataTable columns={["Started", "Method", "Reference", "Amount", "Status"]} className="hidden sm:block">
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
            <p className="font-display text-3xl font-semibold tabular-nums">{formatMoney(paid?.amount ?? pending?.amount ?? order.total)}</p>
            {order.status === "PENDING_PAYMENT" && (
              <p className="mt-2 text-sm text-muted">
                {awaitingVerification
                  ? `Payment being verified. Reserved until ${formatDateTime(order.expiresAt)}.`
                  : pending?.virtualAccount
                    ? `Transfer by ${formatDateTime(order.expiresAt)}, or the order lapses.`
                    : pending?.officeCash
                    ? `Pay at our office by ${formatDateTime(order.expiresAt)}, or the order lapses.`
                    : `Pay by ${formatDateTime(order.expiresAt)}, or the order lapses.`}
              </p>
            )}
          </Card>

          {order.status === "PENDING_PAYMENT" && online && (
            <Card title="Pay">
              <div className="space-y-6">
                {pending?.checkout && (
                  <RazorpayCheckout key={pending.id} orderId={order.id} paymentId={pending.id} checkout={pending.checkout} />
                )}
                {pending?.virtualAccount && (
                  <div className="space-y-3 text-sm">
                    <p className="font-medium">Pay by bank transfer</p>
                    <p className="text-xs text-muted">
                      This account is for this order only. Pay by NEFT, RTGS, IMPS or UPI from your own bank.
                    </p>
                    <dl className="space-y-2">
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Amount, exactly</dt>
                        <dd className="font-display text-xl font-semibold tabular-nums">{formatMoney(pending.virtualAccount.amount)}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Account number</dt>
                        <dd className="font-mono text-base break-all">{pending.virtualAccount.accountNumber}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">IFSC</dt>
                        <dd className="font-mono text-base">{pending.virtualAccount.ifsc}</dd></div>
                      {pending.virtualAccount.accountName && (
                        <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Account name</dt>
                          <dd>{pending.virtualAccount.accountName}</dd></div>
                      )}
                      {pending.virtualAccount.bankName && (
                        <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Bank</dt>
                          <dd>{pending.virtualAccount.bankName}</dd></div>
                      )}
                      {pending.virtualAccount.upiId && (
                        <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Or UPI ID</dt>
                          <dd className="font-mono text-base break-all">{pending.virtualAccount.upiId}</dd></div>
                      )}
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Pay by</dt>
                        <dd>{formatDateTime(pending.virtualAccount.payBy)}</dd></div>
                    </dl>
                    <p className="text-xs text-muted">
                      Your order is confirmed automatically when the money arrives: within minutes by IMPS or UPI, the same
                      day by NEFT or RTGS. Reload this page to see it. A different amount is refunded to you.
                    </p>
                  </div>
                )}
                {pending?.method !== "GATEWAY" && (
                  <ActionButton action={startPaymentAction.bind(null, order.id, "GATEWAY", key())}
                    label="Pay online (UPI, net banking or card)" pendingLabel="Preparing payment…" className="w-full" />
                )}
                {pending?.method !== "VIRTUAL_ACCOUNT" && (
                  <ActionButton action={startPaymentAction.bind(null, order.id, "VIRTUAL_ACCOUNT", key())}
                    label="Pay by bank transfer (NEFT, RTGS, IMPS)" pendingLabel="Opening an account…"
                    variant="secondary" className="w-full" />
                )}
                <div className="border-t border-border pt-4">
                  <ActionButton action={cancelOrderAction.bind(null, order.id)} label="Cancel order" variant="quiet"
                    confirm={`Cancel ${order.orderNumber}? Money that still arrives for it is refunded.`} className="w-full" />
                </div>
              </div>
            </Card>
          )}

          {order.status === "PENDING_PAYMENT" && !online && (
            <Card title="Pay">
              <div className="space-y-6">
                {pending?.bankTransfer && <DepositForm key={pending.id} orderId={order.id} payment={pending} />}
                {pending?.officeCash && (
                  <div className="space-y-3 text-sm">
                    <p className="font-medium">Pay in cash</p>
                    <dl className="space-y-2">
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Amount</dt>
                        <dd className="font-display text-xl font-semibold tabular-nums">{formatMoney(pending.officeCash.amount)}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Quote this reference</dt>
                        <dd className="font-mono text-base">{pending.officeCash.reference}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Office</dt>
                        <dd className="whitespace-pre-line">{pending.officeCash.address}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Hours</dt><dd>{pending.officeCash.hours}</dd></div>
                      <div><dt className="text-xs text-muted uppercase tracking-[0.08em]">Pay by</dt><dd>{formatDateTime(pending.officeCash.payBy)}</dd></div>
                    </dl>
                    <p className="text-xs text-muted">
                      Bring the amount in cash and quote the reference. You get a receipt: enter its number below, and your
                      order is confirmed once our finance team records it.
                    </p>
                    <ReceiptForm key={pending.id} orderId={order.id} payment={pending} />
                  </div>
                )}
                {/* Once cash at the office is chosen it stays the way to pay (or the order is cancelled). */}
                {!pending && (
                  <ActionButton action={startPaymentAction.bind(null, order.id, "BANK_TRANSFER", key())}
                    label="Pay now" className="w-full" />
                )}
                {methods.includes("OFFICE_CASH") && pending?.method !== "OFFICE_CASH" && (
                  <ActionButton action={startPaymentAction.bind(null, order.id, "OFFICE_CASH", key())}
                    label="Pay in cash" variant="secondary" className="w-full" />
                )}
                <div className="border-t border-border pt-4">
                  <ActionButton action={cancelOrderAction.bind(null, order.id)} label="Cancel order" variant="quiet"
                    confirm={awaitingVerification
                      ? `Cancel ${order.orderNumber}? Money you already paid is refunded once it arrives.`
                      : `Cancel ${order.orderNumber}?`} className="w-full" />
                </div>
              </div>
            </Card>
          )}
        </aside>
      </div>
    </div>
  );
}
