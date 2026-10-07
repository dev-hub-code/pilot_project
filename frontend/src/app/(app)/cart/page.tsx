import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { CartLineForm } from "@/features/orders/cart-line-form";
import { CheckoutForm } from "@/features/orders/checkout-form";
import { authFetch } from "@/lib/server/auth/session";
import type { Cart } from "@/types/order";
import { formatMoney, formatPercent } from "@/utils/money";
import { CONTAINER_TYPE_LABEL } from "@/features/marketplace/labels";

export const metadata: Metadata = { title: "Cart" };

export default async function CartPage() {
  const cart = await authFetch<Cart>("/api/v1/cart");
  // One key per rendered checkout: a double submit or retry of this form places one order.
  const idempotencyKey = crypto.randomUUID();

  if (cart.items.length === 0) {
    return (
      <div className="space-y-6">
        <PageHeader title="Cart" />
        <EmptyState title="Your cart is empty" description="Choose a plan in the marketplace and add containers." />
        <LinkButton href="/marketplace">Browse the marketplace</LinkButton>
      </div>
    );
  }

  const terms = cart.items.filter((line) => line.productCode).map((line) => ({
    productId: line.productId,
    code: line.productCode as string,
    title: line.productTitle,
  }));

  return (
    <div className="space-y-6">
      <PageHeader title="Cart" description="Review your containers and accept the terms. Availability is checked again when you place the order." />
      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <ul className="space-y-4">
          {cart.items.map((line) => (
            <li key={line.productId} className="space-y-4 border border-border bg-surface p-6">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  {line.productCode && <p className="font-mono text-xs text-muted">{line.productCode}</p>}
                  <h2 className="text-lg font-semibold tracking-tight">
                    <Link href={`/marketplace/${line.productId}`} className="hover:underline">{line.productTitle}</Link>
                  </h2>
                  {line.containerType && (
                    <p className="text-xs text-muted">
                      {line.quantity} × {CONTAINER_TYPE_LABEL[line.containerType]} container{line.quantity === 1 ? "" : "s"} at {formatMoney(line.pricePerContainer)}
                    </p>
                  )}
                </div>
                <p className="font-display text-2xl font-semibold tabular-nums">{formatMoney(line.amount)}</p>
              </div>
              {line.monthlyPayout && (
                <dl className="grid gap-4 text-sm sm:grid-cols-3">
                  <Fact label="Paid to you monthly" value={formatMoney(line.monthlyPayout)} />
                  <Fact label="Rent + capital back" value={`${formatPercent(line.monthlyRentPercent)} + ${formatPercent(line.monthlyCapitalReturnPercent)} a month`} />
                  <Fact label={`Total over ${line.tenureMonths} months`} value={formatMoney(line.totalPayout)} />
                </dl>
              )}
              {line.problems.length > 0 && (
                <ul className="space-y-1 border-l-4 border-amber-400 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-500/10 dark:text-amber-300">
                  {line.problems.map((problem) => <li key={problem}>{problem}</li>)}
                </ul>
              )}
              <CartLineForm productId={line.productId} quantity={line.quantity} />
            </li>
          ))}
        </ul>

        <aside className="lg:sticky lg:top-6 lg:self-start">
          <Card title="Checkout">
            <dl className="mb-6 flex items-baseline justify-between border-b border-border pb-4">
              <dt className="text-sm text-muted">Total</dt>
              <dd className="font-display text-3xl font-semibold tabular-nums">{formatMoney(cart.total)}</dd>
            </dl>
            <CheckoutForm idempotencyKey={idempotencyKey} terms={terms} total={formatMoney(cart.total)} disabled={!cart.checkoutReady} />
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium tabular-nums">{value}</dd>
    </div>
  );
}
