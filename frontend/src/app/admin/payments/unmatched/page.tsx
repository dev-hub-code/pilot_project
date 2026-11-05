import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatGrid } from "@/components/ui/stat-grid";
import { StatusBadge } from "@/components/ui/status-badge";
import { ResolveUnmatchedForm } from "@/features/admin/reconciliation-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { UnmatchedReceipt } from "@/types/reconciliation";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Unmatched receipts" };

const VIEWS = [
  { key: "open", label: "To resolve" },
  { key: "all", label: "All" },
] as const;

export default async function UnmatchedReceiptsPage({ searchParams }: PageProps<"/admin/payments/unmatched">) {
  const denied = await deniedUnlessInMenu("/admin/payments/unmatched");
  if (denied) return denied;
  const session = await requireStaff();
  const canResolve = hasPermission(session.permissions, Permission.PAYMENT_CONFIRM);
  const params = await searchParams;
  const view = VIEWS.find((v) => v.key === params.view) ?? VIEWS[0];
  // Open ones are listed first either way; "all" adds the resolved ones after them.
  const receipts = await authFetch<UnmatchedReceipt[]>(`/api/v1/admin/payments/unmatched?open=${view.key === "open"}`);
  const open = receipts.filter((r) => !r.resolvedAt);
  const resolved = receipts.filter((r) => r.resolvedAt);
  const oldest = open.reduce<string | null>((min, r) => (min === null || r.receivedAt < min ? r.receivedAt : min), null);

  return (
    <div className="space-y-6">
      <PageHeader title="Unmatched receipts"
        description="Money the payment gateway took for an order or virtual account that matches none of our payments. Trace each one (usually a refund to the sender) and record how it was resolved." />

      <StatGrid label="Unmatched receipts" stats={[
        { label: "To resolve", value: open.length, note: open.length === 0 ? "Nothing waiting" : "Each is money held with no payment behind it" },
        { label: "Oldest waiting", value: oldest ? formatDateTime(oldest) : "—" },
        { label: "Shown", value: receipts.length, note: view.key === "all" ? "Open first, then the most recently resolved" : "Open only" },
      ]} />

      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/payments/unmatched?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      {receipts.length === 0 && (
        <EmptyState title={view.key === "open" ? "Nothing to resolve" : "No unmatched receipts"}
          description="Every payment the gateway reported belonged to one of our payments." />
      )}

      {open.length > 0 && (
        <ul className="space-y-4" aria-label="To resolve">
          {open.map((r) => (
            <li key={r.id} className="grid gap-5 rounded-xl border border-border bg-surface p-5 shadow-sm lg:grid-cols-[1.4fr_1fr]">
              <div className="space-y-3">
                <div className="flex flex-wrap items-center gap-3">
                  <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{r.amount ? formatMoney(r.amount) : "Amount unknown"}</p>
                  <StatusBadge tone="warning">To resolve</StatusBadge>
                </div>
                <dl className="grid gap-3 text-sm sm:grid-cols-2">
                  <Item label="Received">{formatDateTime(r.receivedAt)}</Item>
                  <Item label="Gateway">{r.provider}</Item>
                  <Item label="Gateway payment ID"><span className="font-mono text-xs">{r.gatewayPaymentId}</span></Item>
                  <Item label="Paid against (order or virtual account)"><span className="font-mono text-xs">{r.providerReference}</span></Item>
                </dl>
              </div>
              {canResolve ? (
                <ResolveUnmatchedForm receiptId={r.id} amount={r.amount ? formatMoney(r.amount) : "this receipt"} />
              ) : (
                <p className="text-sm text-muted">Finance staff who confirm payments resolve these.</p>
              )}
            </li>
          ))}
        </ul>
      )}

      {resolved.length > 0 && (
        <section className="space-y-3" aria-label="Resolved">
          <h2 className="text-lg font-semibold tracking-tight">Resolved</h2>
          <DataTable columns={["Received", "Gateway payment", "Amount", "Resolved", "How"]}>
            {resolved.map((r) => (
              <tr key={r.id} className="hover:bg-background">
                <Cell className="text-muted">{formatDateTime(r.receivedAt)}</Cell>
                <Cell className="font-mono text-xs">{r.gatewayPaymentId}<span className="block text-muted">{r.providerReference}</span></Cell>
                <Cell className="tabular-nums">{r.amount ? formatMoney(r.amount) : "—"}</Cell>
                <Cell className="text-muted">{formatDateTime(r.resolvedAt)}</Cell>
                <Cell className="max-w-sm whitespace-pre-line text-sm">{r.resolution}</Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}
    </div>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1 font-medium">{children}</dd>
    </div>
  );
}
