import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { LinkButton } from "@/components/ui/link-button";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmTransferForm, RefundForm } from "@/features/admin/payment-forms";
import { METHOD_LABEL, ORDER_STATUS_LABEL, ORDER_TONE, PAYMENT_TONE } from "@/features/orders/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Order, Payment } from "@/types/order";
import type { AdminUserDetail } from "@/types/user";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Order" };

export default async function AdminOrderPage({ params }: PageProps<"/admin/orders/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  let order: Order;
  let payments: Payment[];
  try {
    [order, payments] = await Promise.all([
      authFetch<Order>(`/api/v1/admin/orders/${id}`),
      authFetch<Payment[]>(`/api/v1/admin/orders/${id}/payments`),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const ownOrder = order.userId === session.userId;
  const investor = can(Permission.USER_VIEW)
    ? await authFetch<AdminUserDetail>(`/api/v1/admin/users/${order.userId}`).then((u) => u.summary).catch(() => null)
    : null;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="font-mono text-3xl font-semibold tracking-tight">{order.orderNumber}</h1>
          <p className="text-sm text-muted">
            Placed {formatDateTime(order.createdAt)} by{" "}
            {investor
              ? <Link href={`/admin/users/${order.userId}`} className="text-gold-text hover:underline">
                {investor.firstName} {investor.lastName} ({investor.email})
              </Link>
              : <span className="font-mono">{order.userId}</span>}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <StatusBadge tone={ORDER_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusBadge>
          {order.status === "CONFIRMED" && <LinkButton href={`/admin/orders/${order.id}/invoice`} variant="secondary">Invoice</LinkButton>}
        </div>
      </div>

      <Card title="Summary">
        <dl className="grid gap-4 text-sm sm:grid-cols-4">
          <Item label="Total">{formatMoney(order.total)}</Item>
          <Item label="Payment due by">{formatDateTime(order.expiresAt)}</Item>
          <Item label="Confirmed">{formatDateTime(order.confirmedAt)}</Item>
          <Item label="Closed">{order.closedAt ? `${formatDateTime(order.closedAt)} · ${order.closeReason}` : "—"}</Item>
        </dl>
      </Card>

      <DataTable columns={["Offering", "Amount", "Ownership", "Terms version"]}>
        {order.items.map((item) => (
          <tr key={item.productId}>
            <Cell><Link href={`/admin/products/${item.productId}`} className="font-mono text-gold-text hover:underline">{item.productCode}</Link></Cell>
            <Cell className="tabular-nums">{formatMoney(item.amount)}</Cell>
            <Cell className="tabular-nums">{formatPercent(item.ownershipPercent, 4)}</Cell>
            <Cell>{item.termsVersion}</Cell>
          </tr>
        ))}
      </DataTable>

      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Payments</h2>
        {payments.length === 0 ? (
          <p className="text-sm text-muted">No payment started yet.</p>
        ) : (
          <ul className="space-y-4">
            {payments.map((p) => (
              <li key={p.id} className="space-y-4 border border-border bg-surface p-5">
                <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                  <span>
                    <strong>{METHOD_LABEL[p.method]}</strong> · <span className="font-mono">{p.providerReference}</span> · {formatMoney(p.amount)}
                  </span>
                  <StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge>
                </div>
                <dl className="grid gap-3 text-xs text-muted sm:grid-cols-3">
                  <Item label="Started">{formatDateTime(p.createdAt)}</Item>
                  <Item label="Settled">{formatDateTime(p.settledAt)}</Item>
                  <Item label="Bank / gateway reference">{p.externalReference ?? "—"}</Item>
                  {p.failureReason && <Item label="Note">{p.failureReason}</Item>}
                  {p.refundedAt && <Item label="Refunded">{`${formatDateTime(p.refundedAt)} · ${p.refundReference}`}</Item>}
                </dl>
                {p.method === "BANK_TRANSFER" && (p.status === "PENDING" || p.status === "CANCELLED") && can(Permission.PAYMENT_CONFIRM) && !ownOrder && (
                  <ConfirmTransferForm paymentId={p.id} amountDue={p.amount.amount} currency={p.amount.currency} />
                )}
                {p.status === "REFUND_REQUIRED" && can(Permission.FINANCE_ADJUST) && !ownOrder && <RefundForm paymentId={p.id} />}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-[11px] tracking-[0.1em] text-muted uppercase">{label}</dt>
      <dd className="mt-1 font-medium text-foreground">{children}</dd>
    </div>
  );
}
