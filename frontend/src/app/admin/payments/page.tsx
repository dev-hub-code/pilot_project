import { deniedUnlessInMenu } from "@/components/layout/page-access";
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
import {
  Ban, Banknote, CheckCircle2, CreditCard, Landmark, Layers, type LucideIcon, Receipt, RotateCcw, Search, Smartphone, Undo2, Wallet, XCircle,
} from "lucide-react";
import type { PaymentMethod } from "@/types/order";

export const metadata: Metadata = { title: "Payments" };

type Filter = { key: string; status?: PaymentStatus; method?: "BANK_TRANSFER" | "OFFICE_CASH"; label: string; icon: LucideIcon };

/** "All" (every status) leads for finance; staff who only confirm start on their queue. */
const ALL: Filter = { key: "all", label: "All", icon: Layers };

const FILTERS: readonly [Filter, ...Filter[]] = [
  { key: "bank", status: "PENDING", method: "BANK_TRANSFER", label: "Bank payments to verify", icon: Landmark },
  { key: "office", status: "PENDING", method: "OFFICE_CASH", label: "Cash expected", icon: Banknote },
  { key: "refunds", status: "REFUND_REQUIRED", label: "Refunds due", icon: RotateCcw },
  { key: "succeeded", status: "SUCCEEDED", label: "Succeeded", icon: CheckCircle2 },
  { key: "failed", status: "FAILED", label: "Failed", icon: XCircle },
  { key: "cancelled", status: "CANCELLED", label: "Cancelled", icon: Ban },
  { key: "refunded", status: "REFUNDED", label: "Refunded", icon: Undo2 },
];

/** Each payment method's icon. */
const METHOD_ICON: Record<PaymentMethod, LucideIcon> = {
  BANK_TRANSFER: Landmark,
  OFFICE_CASH: Banknote,
  CARD: CreditCard,
  GATEWAY: Smartphone,
  VIRTUAL_ACCOUNT: Landmark,
  WALLET: Wallet,
};

export default async function PaymentsPage({ searchParams }: PageProps<"/admin/payments">) {
  const denied = await deniedUnlessInMenu("/admin/payments");
  if (denied) return denied;
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  // Without finance view (finance itself), only the payments waiting for confirmation are shown, and they are decided here.
  const confirmOnly = !can(Permission.FINANCE_VIEW);
  const canConfirm = can(Permission.PAYMENT_CONFIRM);
  // Support only finds payments by searching: a whole order number, payment reference, or investor ID or email.
  const searchOnly = confirmOnly && !canConfirm;
  const filters = confirmOnly ? FILTERS.filter((f) => f.status === "PENDING") : [ALL, ...FILTERS];
  const params = await searchParams;
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const filter = filters.find((f) => f.key === params.view) ?? filters[0] ?? FILTERS[0];
  // A search covers every status; the queues apply without one.
  const decide = canConfirm && !q && filter.status === "PENDING";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (q) query.set("q", q);
  else {
    if (filter.status) query.set("status", filter.status);
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
      <div className="space-y-3">
        {!searchOnly && (
          // One row: on narrow screens it scrolls sideways rather than wrapping.
          <nav aria-label="Filter" className="flex max-w-full gap-1 overflow-x-auto rounded-xl border border-border bg-surface p-1 text-sm shadow-sm [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
            {filters.map((f) => {
              const on = !q && f === filter;
              const Icon = f.icon;
              return (
                <Link key={f.key} href={`/admin/payments?view=${f.key}`} aria-current={on ? "page" : undefined}
                  className={`inline-flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${on
                    ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                    : "text-muted hover:bg-background hover:text-foreground"}`}>
                  <Icon aria-hidden="true" className="size-4" /> {f.label}
                </Link>
              );
            })}
          </nav>
        )}
        <form action="/admin/payments" role="search"
          className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:max-w-md">
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={q} placeholder="Order no., reference, investor ID or email"
            aria-label="Search payments by order number, payment reference, or investor ID or email"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
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
          {payments.content.map((p) => {
            const MethodIcon = METHOD_ICON[p.method];
            return (
            <li key={p.id} className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm transition-shadow duration-300 hover:shadow-md">
              <div className="flex flex-wrap items-center gap-4 border-b border-border/70 bg-background/40 px-5 py-4">
                <span aria-hidden="true" className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
                  <MethodIcon className="size-5" />
                </span>
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-semibold">{METHOD_LABEL[p.method]}</p>
                  <p className="text-xs text-muted">
                    <span className="font-mono">{p.providerReference}</span> · order{" "}
                    {confirmOnly ? <span className="font-mono">{p.orderNumber ?? p.orderId}</span>
                      : <Link href={`/admin/orders/${p.orderId}`} className="font-mono text-gold-text hover:underline">{p.orderNumber ?? p.orderId}</Link>}
                    {" "}· started {formatDateTime(p.createdAt)}
                  </p>
                </div>
                <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{formatMoney(p.amount)}</p>
              </div>
              <div className="space-y-4 p-5">
              <p className="flex items-start gap-2 rounded-xl bg-background/70 px-3 py-2.5 text-xs text-muted">
                <Receipt aria-hidden="true" className="mt-px size-4 shrink-0 text-gold-text" />
                <span>
                {p.deposit ? (
                  <>Investor paid by {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>{" "}
                    into {p.deposit.bankName} {p.deposit.accountNumber}</>
                ) : p.method === "BANK_TRANSFER" ? "The investor has not sent the transfer details yet."
                  : p.receiptNumber ? <>Investor paid at the office · receipt <span className="font-mono">{p.receiptNumber}</span></>
                  : "To pay at the office."}
                </span>
              </p>
              {p.userId === session.userId
                ? <p className="text-sm text-muted">This is your own order: someone else must confirm it.</p>
                : <PaymentDecision payment={p} viewerId={session.userId} />}
              </div>
            </li>
            );
          })}
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
              <Cell>
                {(() => {
                  const MethodIcon = METHOD_ICON[p.method];
                  return (
                    <span className="inline-flex items-center gap-2">
                      <span aria-hidden="true" className="flex size-7 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><MethodIcon className="size-3.5" /></span>
                      {METHOD_LABEL[p.method]}
                    </span>
                  );
                })()}
              </Cell>
              <Cell className="font-mono text-xs">{p.providerReference}</Cell>
              <Cell className="text-xs">
                {p.deposit ? (
                  <>
                    {DEPOSIT_MODE_LABEL[p.deposit.mode]} · <span className="font-mono">{p.deposit.reference}</span>
                    <span className="block text-muted">{p.deposit.bankName} {p.deposit.accountNumber}</span>
                  </>
                ) : <span className="text-muted">{p.method === "BANK_TRANSFER" && p.status === "PENDING" ? "Not submitted yet"
                  : p.method === "OFFICE_CASH" ? (p.externalReference ?? p.receiptNumber ? `Receipt ${p.externalReference ?? p.receiptNumber}` : "To pay at the office") : "—"}</span>}
              </Cell>
              <Cell className="font-semibold tabular-nums">{formatMoney(p.amount)}</Cell>
              <Cell><StatusBadge tone={PAYMENT_TONE[p.status]}>{humanize(p.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {payments && <Pagination page={payments} basePath="/admin/payments" params={q ? { q } : { view: filter.key }} />}
    </div>
  );
}
