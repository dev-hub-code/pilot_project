import type { Metadata } from "next";
import { CalendarClock, Check, CircleDollarSign, Clock, Coins, FileSignature, Hourglass, Package, ReceiptText, ShieldCheck, TrendingUp, type LucideIcon } from "lucide-react";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { CardField, CardList, CardListItem } from "@/components/ui/card-list";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { overCheckoutLimit } from "@/features/orders/checkout-limit";
import { cancelOrderAction, startPaymentAction } from "@/features/orders/actions";
import { BalancePaymentForm } from "@/features/orders/balance-payment-form";
import { PayOption } from "@/features/orders/pay-option";
import { DepositForm } from "@/features/orders/deposit-form";
import { RazorpayCheckout } from "@/features/orders/razorpay-checkout";
import { ReceiptForm } from "@/features/orders/receipt-form";
import { DEPOSIT_MODE_LABEL, DEPOSIT_REFERENCE_LABEL, METHOD_LABEL, ORDER_STATUS_LABEL, ORDER_TONE, PAYMENT_TONE } from "@/features/orders/labels";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isDemoAccount } from "@/lib/server/demo";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Money } from "@/types/plan";
import type { Order, OrderItem, Payment, PaymentMethod, PaymentMethods } from "@/types/order";
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
  let availableBalance: Money | undefined;
  let checkoutLimit: Money | undefined;
  try {
    [order, payments, demo, { methods, availableBalance, checkoutLimit }] = await Promise.all([
      authFetch<Order>(`/api/v1/orders/${id}`),
      authFetch<Payment[]>(`/api/v1/orders/${id}/payments`),
      isDemoAccount(),
      authFetch<PaymentMethods>("/api/v1/payments/methods"),
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
  // Online investors pay online (unless an admin turned it off), by bank transfer to the company's account, or from
  // their balance; network investors by bank or at the office. Only online investors can pay from a balance.
  const online = methods.includes("WALLET");
  const gateway = methods.includes("GATEWAY");
  // Paid into one of the company's accounts and verified by finance.
  const companyTransfer = methods.includes("BANK_TRANSFER");
  // The gateway takes one payment of at most this much; a larger order is paid another way (the backend refuses it).
  const overLimit = overCheckoutLimit(order.total, checkoutLimit);
  // Online investors may pay from their balance, unless they have told us they paid another way (that is verified
  // first). Whether it covers the order is only a hint here: the backend checks it before spending.
  const balancePayable = methods.includes("WALLET") && availableBalance != null && !awaitingVerification;
  const balanceCovers = availableBalance != null && Number(availableBalance.amount) >= Number(order.total.amount);
  const payable = order.status === "PENDING_PAYMENT";
  const returns = sumMoney(order.items.map((item) => scale(item.monthlyPayout, item.tenureMonths)), order.total);
  const monthly = sumMoney(order.items.map((item) => item.monthlyPayout), order.total);
  // Rendered fresh per request: a new key per intent, reused by retries of that same form.
  const key = () => crypto.randomUUID();

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/orders" className="inline-flex min-h-6 items-center hover:text-foreground">Orders</Link> / <span className="font-mono">{order.orderNumber}</span>
      </nav>

      <section aria-labelledby="order-heading"
        className="relative overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_100%_0%,color-mix(in_oklab,var(--gold)_12%,transparent),transparent_55%)]" />
        <div className="relative flex flex-wrap items-start justify-between gap-6 p-5 sm:p-7">
          <div className="flex min-w-0 items-start gap-4">
            <span aria-hidden="true" className="hidden size-12 shrink-0 items-center justify-center rounded-2xl bg-gold/10 text-gold-text sm:flex">
              <ReceiptText className="size-6" />
            </span>
            <div className="min-w-0 space-y-2">
              <div className="flex flex-wrap items-center gap-3">
                <h1 id="order-heading" className="font-mono text-2xl font-semibold tracking-tight sm:text-3xl">{order.orderNumber}</h1>
                <StatusBadge tone={ORDER_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusBadge>
              </div>
              <p className="flex items-center gap-1.5 text-sm text-muted">
                <CalendarClock aria-hidden="true" className="size-4" /> Placed {formatDateTime(order.createdAt)}
              </p>
            </div>
          </div>
          <div className="space-y-2 text-left sm:text-right">
            <p className="text-xs font-medium tracking-[0.08em] text-muted uppercase">{paid ? "Paid" : "Total"}</p>
            <p className="font-display text-3xl font-semibold tabular-nums sm:text-4xl">{formatMoney(paid?.amount ?? pending?.amount ?? order.total)}</p>
            {payable && (
              <p className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-800 dark:text-amber-200">
                <Clock aria-hidden="true" className="size-3.5" />
                {awaitingVerification ? `Reserved until ${formatDateTime(order.expiresAt)}`
                  : pending?.officeCash ? `Pay at our office by ${formatDateTime(order.expiresAt)}`
                    : `Pay by ${formatDateTime(order.expiresAt)}`}
              </p>
            )}
            {order.status === "CONFIRMED" && !demo && (
              <div><LinkButton href={`/orders/${order.id}/invoice`} variant="secondary">View invoice</LinkButton></div>
            )}
          </div>
        </div>
        {(payable || order.status === "CONFIRMED") && (
          <Progress steps={[
            { label: "Order placed", done: true },
            { label: awaitingVerification ? "Payment being verified" : "Payment", done: order.status === "CONFIRMED", current: payable },
            { label: "Investment confirmed", done: order.status === "CONFIRMED" },
            { label: "Agreement accepted", done: false, current: order.status === "CONFIRMED" },
          ]} />
        )}
      </section>

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
          <Card title="Your investment" description={payable ? "What you are buying with this order." : undefined}>
            <div className="space-y-5">
              {order.items.map((item) => <ItemSummary key={item.id} item={item} />)}
              <dl className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                <Figure icon={CircleDollarSign} label="You invest" value={formatMoney(order.total)} />
                <Figure icon={Coins} label="You receive monthly" value={formatMoney(monthly)} />
                <Figure icon={TrendingUp} label="Paid to you over the term" value={formatMoney(returns)} accent />
              </dl>
              <p className="flex items-start gap-2 text-xs text-muted">
                <ShieldCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-emerald-600 dark:text-emerald-400" />
                Fixed for the whole term and paid to your wallet every month, under an agreement in your name.
              </p>
            </div>
          </Card>

          {payments.length > 0 && (
            <section className="space-y-3">
              <h2 className="text-lg font-semibold tracking-tight">Payment history</h2>
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

        <aside className="order-first space-y-6 lg:order-none lg:sticky lg:top-6 lg:self-start">
          {payable && online && (
            <Card title="Pay" description={awaitingVerification ? "We are checking the payment you told us about."
              : pending ? undefined : "Choose how you would like to pay."}>
              <div className="space-y-5">
                {balancePayable && availableBalance && balanceCovers && (
                  <div className="rounded-xl border border-emerald-500/30 bg-emerald-500/5 p-4">
                    <BalancePaymentForm orderId={order.id} orderNumber={order.orderNumber} total={order.total}
                      available={availableBalance} idempotencyKey={key()} />
                  </div>
                )}
                {/* Paid to our account: the investor says how; finance verifies it. Other ways are closed meanwhile. */}
                {pending?.bankTransfer && <DepositForm key={pending.id} orderId={order.id} payment={pending} />}
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
                {overLimit && checkoutLimit && (
                  <Notice tone="info">
                    Online payment is limited to {formatMoney(checkoutLimit)} per payment.{" "}
                    {companyTransfer
                      ? "Please pay this order by bank transfer."
                      : balancePayable && balanceCovers
                        ? "Please pay this order from your available balance above."
                        : `Please cancel this order and place orders of up to ${formatMoney(checkoutLimit)} each.`}
                  </Notice>
                )}
                {!awaitingVerification && (gateway && !overLimit && pending?.method !== "GATEWAY"
                  || companyTransfer && pending?.method !== "BANK_TRANSFER") && (
                  <div className="space-y-3">
                    {pending && <p className="text-xs font-medium tracking-[0.08em] text-muted uppercase">Or pay another way</p>}
                    {gateway && !overLimit && pending?.method !== "GATEWAY" && (
                      <PayOption action={startPaymentAction.bind(null, order.id, "GATEWAY", key())} icon="online"
                        title="Pay online" badge="Instant" hint="UPI, net banking or card. Confirmed as soon as you pay." />
                    )}
                    {companyTransfer && pending?.method !== "BANK_TRANSFER" && (
                      <PayOption action={startPaymentAction.bind(null, order.id, "BANK_TRANSFER", key())} icon="bank"
                        title="Bank transfer to SeaLease" hint="NEFT, RTGS or IMPS from your bank. Confirmed once our team verifies it." />
                    )}
                  </div>
                )}
                {balancePayable && availableBalance && !balanceCovers && Number(availableBalance.amount) > 0 && (
                  <p className="text-xs text-muted">
                    Your available balance ({formatMoney(availableBalance)}) does not cover this order, so it cannot be
                    paid from your balance.
                  </p>
                )}
                <div className="border-t border-border pt-4">
                  <ActionButton action={cancelOrderAction.bind(null, order.id)} label="Cancel order" variant="quiet"
                    confirm={awaitingVerification
                      ? `Cancel ${order.orderNumber}? Money you already paid is refunded once it arrives.`
                      : `Cancel ${order.orderNumber}? Money that still arrives for it is refunded.`} className="w-full" />
                </div>
              </div>
            </Card>
          )}

          {payable && !online && (
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

/** Where the order is, from placing it to the agreement that starts its returns. */
function Progress({ steps }: { steps: { label: string; done: boolean; current?: boolean }[] }) {
  return (
    <ol className="relative grid grid-cols-2 gap-x-4 gap-y-4 border-t border-border/70 bg-background/40 px-5 py-4 sm:grid-cols-4 sm:px-7">
      {steps.map((step, index) => (
        <li key={step.label} aria-current={step.current ? "step" : undefined} className="flex items-center gap-2.5 text-sm">
          <span aria-hidden="true" className={`flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
            step.done ? "bg-emerald-500 text-white"
              : step.current ? "bg-gold text-on-gold ring-4 ring-gold/15"
                : "bg-border text-muted"}`}>
            {step.done ? <Check className="size-4" /> : step.current && index === 1 ? <Hourglass className="size-3.5" />
              : step.current ? <FileSignature className="size-3.5" /> : index + 1}
          </span>
          <span className={step.done || step.current ? "font-medium" : "text-muted"}>
            {step.label}
            <span className="sr-only">{step.done ? " (done)" : step.current ? " (current)" : ""}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}

/** One plan in the order: what it is, how many units and what it pays. */
function ItemSummary({ item }: { item: OrderItem }) {
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-xl border border-border bg-background/40 p-4">
      <span aria-hidden="true" className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
        <Package className="size-5" />
      </span>
      <div className="min-w-0 flex-1">
        <Link href={`/invest/${item.productId}`} className="font-semibold text-gold-text hover:underline">{item.productTitle}</Link>
        <p className="text-xs text-muted tabular-nums">
          {item.units !== null && item.unitPrice ? `${item.units} units × ${formatMoney(item.unitPrice)} · ` : ""}{item.tenureMonths} months
        </p>
      </div>
      <p className="font-display text-lg font-semibold tabular-nums">{formatMoney(item.amount)}</p>
    </div>
  );
}

function Figure({ icon: Icon, label, value, accent }: { icon: LucideIcon; label: string; value: string; accent?: boolean }) {
  return (
    <div className={`rounded-xl p-4 ring-1 ${accent ? "col-span-2 bg-emerald-500/5 ring-emerald-500/25 sm:col-span-1" : "bg-background/40 ring-border"}`}>
      <dt className="flex items-center gap-1.5 text-xs text-muted">
        <Icon aria-hidden="true" className={`size-3.5 ${accent ? "text-emerald-600 dark:text-emerald-400" : "text-gold-text"}`} /> {label}
      </dt>
      <dd className={`mt-1 font-display text-xl font-semibold tabular-nums ${accent ? "text-emerald-700 dark:text-emerald-300" : ""}`}>{value}</dd>
    </div>
  );
}

function scale(money: Money, times: number): Money {
  return { ...money, amount: (Number(money.amount) * times).toFixed(2) };
}

function sumMoney(amounts: Money[], fallback: Money): Money {
  const total = amounts.reduce((sum, m) => sum + Number(m.amount), 0);
  return { currency: amounts[0]?.currency ?? fallback.currency, amount: total.toFixed(2) };
}
