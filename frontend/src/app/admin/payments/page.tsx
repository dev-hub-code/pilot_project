import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { DEPOSIT_MODE_LABEL, METHOD_LABEL, PAYMENT_TONE } from "@/features/orders/labels";
import { PaymentDecision } from "@/features/admin/payment-decision";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Payment, PaymentStatus } from "@/types/order";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Payments" };

type Filter = { key: string; status: PaymentStatus; method?: "BANK_TRANSFER" | "OFFICE_CASH"; label: string };

const FILTERS: readonly [Filter, ...Filter[]] = [
  { key: "bank", status: "PENDING", method: "BANK_TRANSFER", label: "Bank payments to verify" },
  { key: "office", status: "PENDING", method: "OFFICE_CASH", label: "Cash expected at the office" },
  { key: "refunds", status: "REFUND_REQUIRED", label: "Refunds due" },
  { key: "succeeded", status: "SUCCEEDED", label: "Succeeded" },
  { key: "failed", status: "FAILED", label: "Failed" },
];

export default async function PaymentsPage({ searchParams }: PageProps<"/admin/payments">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  // Without finance view (finance itself), only the payments waiting for confirmation are shown, and they are decided here.
  const confirmOnly = !can(Permission.FINANCE_VIEW);
  const canConfirm = can(Permission.PAYMENT_CONFIRM);
  // Support only finds payments by searching: a whole order number, payment reference, or investor ID or email.
  const searchOnly = confirmOnly && !canConfirm;
  const filters = confirmOnly ? FILTERS.filter((f) => f.status === "PENDING") : FILTERS;
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const filter = filters.find((f) => f.key === params.view) ?? FILTERS[0];
  // A search covers every status; the queues apply without one.
  const decide = canConfirm && !q && filter.status === "PENDING";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (q) query.set("q", q);
  else {
    query.set("status", filter.status);
    if (filter.method) query.set("method", filter.method);
  }
  const payments = searchOnly && !q ? null : await authFetch<PageResponse<Payment>>(`/api/v1/admin/payments?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Payments" description={searchOnly
        ? "Find an investor's payments by a whole order number, payment reference, or the investor's ID or email."
        : confirmOnly
        ? "Check bank payments against the statement and record cash received at the office. Assign a payment to yourself before confirming or rejecting it."
        : "Check bank payments against the statement, record cash received at the office, and settle refunds. Assign a payment to yourself before confirming or rejecting it."} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        {!searchOnly && (
          <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
            {filters.map((f) => (
              <Link key={f.key} href={`/admin/payments?view=${f.key}`} aria-current={!q && f === filter ? "page" : undefined}
                className={`rounded-full border px-3 py-1 ${!q && f === filter ? "border-foreground text-foreground" : "border-border text-muted"}`}>
                {f.label}
              </Link>
            ))}
          </nav>
        )}
        <form className="flex gap-2" action="/admin/payments" role="search">
          <input name="q" defaultValue={q} placeholder="Order no., reference, investor ID or email"
            aria-label="Search payments by order number, payment reference, or investor ID or email"
            className="h-9 w-72 rounded-none border border-border bg-surface px-3 text-sm outline-none focus:border-gold" />
        </form>
      </div>
      {!payments ? (
        <EmptyState title="Search for a payment"
          description="Enter a whole order number, a payment reference, or the investor's ID (e.g. SL03500) or email." />
      ) : payments.content.length === 0 ? (
        <EmptyState title={q ? "No payments found" : "Nothing here"}
          description={q ? `Nothing matches “${q}” exactly. Check the number, reference, ID or email.` : undefined} />
      ) : decide ? (
        <ul className="space-y-4">
          {payments.content.map((p) => (
            <li key={p.id} className="space-y-4 border border-border bg-surface p-5">
              <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                <span>
                  <strong>{METHOD_LABEL[p.method]}</strong> · <span className="font-mono">{p.providerReference}</span> ·{" "}
                  {formatMoney(p.amount)} · order{" "}
                  {confirmOnly ? <span className="font-mono">{p.orderNumber ?? p.orderId}</span>
                    : <Link href={`/admin/orders/${p.orderId}`} className="font-mono text-gold-text hover:underline">{p.orderNumber ?? p.orderId}</Link>}
                </span>
                <span className="text-xs text-muted">Started {formatDateTime(p.createdAt)}</span>
              </div>
              <p className="text-xs text-muted">
                {p.deposit ? (
                  <>Investor paid by {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>{" "}
                    into {p.deposit.bankName} {p.deposit.accountNumber}</>
                ) : p.method === "BANK_TRANSFER" ? "The investor has not sent the transfer details yet." : "To pay at the office."}
              </p>
              {p.userId === session.userId
                ? <p className="text-sm text-muted">This is your own order: someone else must confirm it.</p>
                : <PaymentDecision payment={p} viewerId={session.userId} />}
            </li>
          ))}
        </ul>
      ) : (
        <DataTable columns={["Started", "Order", "Method", "Reference", "Investor paid by", "Amount", "Status"]}>
          {payments.content.map((p) => (
            <tr key={p.id} className="hover:bg-background">
              <Cell className="text-muted">{formatDateTime(p.createdAt)}</Cell>
              <Cell>
                {can(Permission.ORDER_VIEW)
                  ? <Link href={`/admin/orders/${p.orderId}`} className="font-mono text-gold-text hover:underline">{p.orderNumber ?? p.orderId}</Link>
                  : <span className="font-mono">{p.orderNumber ?? p.orderId}</span>}
              </Cell>
              <Cell>{METHOD_LABEL[p.method]}</Cell>
              <Cell className="font-mono text-xs">{p.providerReference}</Cell>
              <Cell className="text-xs">
                {p.deposit ? (
                  <>
                    {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>
                    <span className="block text-muted">{p.deposit.bankName} {p.deposit.accountNumber}</span>
                  </>
                ) : <span className="text-muted">{p.method === "BANK_TRANSFER" && p.status === "PENDING" ? "Not submitted yet"
                  : p.method === "OFFICE_CASH" ? (p.externalReference ? `Receipt ${p.externalReference}` : "To pay at the office") : "—"}</span>}
              </Cell>
              <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
              <Cell><StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {payments && <Pagination page={payments} basePath="/admin/payments" params={q ? { q } : { view: filter.key }} />}
    </div>
  );
}
