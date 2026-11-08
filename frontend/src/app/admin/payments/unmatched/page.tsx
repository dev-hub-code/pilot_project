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
import { AlertTriangle, CalendarClock, CheckCircle2, Fingerprint, Link2, Server } from "lucide-react";

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

      <nav aria-label="Filter" className="inline-flex gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/payments/unmatched?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${v === view
              ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
              : "text-muted hover:bg-background hover:text-foreground"}`}>
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
            <li key={r.id} className="relative grid overflow-hidden rounded-2xl border border-amber-300/60 bg-surface shadow-sm transition-shadow duration-300 hover:shadow-md lg:grid-cols-[1.4fr_1fr] dark:border-amber-500/30">
              <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-amber-400" />
              <div className="space-y-4 p-5 pl-6">
                <div className="flex flex-wrap items-center gap-3">
                  <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-xl bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300">
                    <AlertTriangle className="size-5" />
                  </span>
                  <p className="font-display text-2xl font-semibold tracking-tight tabular-nums">{r.amount ? formatMoney(r.amount) : "Amount unknown"}</p>
                  <StatusBadge tone="warning">To resolve</StatusBadge>
                </div>
                <dl className="grid gap-2 text-sm sm:grid-cols-2">
                  <Item label="Received" icon={<CalendarClock />}>{formatDateTime(r.receivedAt)}</Item>
                  <Item label="Gateway" icon={<Server />}>{r.provider}</Item>
                  <Item label="Gateway payment ID" icon={<Fingerprint />}><span className="font-mono text-xs break-all">{r.gatewayPaymentId}</span></Item>
                  <Item label="Paid against (order or virtual account)" icon={<Link2 />}><span className="font-mono text-xs break-all">{r.providerReference}</span></Item>
                </dl>
              </div>
              <div className="border-t border-border bg-background/40 p-5 lg:border-t-0 lg:border-l">
                {canResolve ? (
                  <ResolveUnmatchedForm receiptId={r.id} amount={r.amount ? formatMoney(r.amount) : "this receipt"} />
                ) : (
                  <p className="text-sm text-muted">Finance staff who confirm payments resolve these.</p>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {resolved.length > 0 && (
        <section className="space-y-3" aria-label="Resolved">
          <h2 className="flex items-center gap-3 text-lg font-semibold tracking-tight">
            <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300">
              <CheckCircle2 className="size-[18px]" />
            </span>
            Resolved
            <span className="rounded-full bg-background px-2 py-0.5 text-xs font-medium text-muted tabular-nums ring-1 ring-border">{resolved.length}</span>
          </h2>
          <DataTable columns={["Received", "Gateway payment", "Amount", "Resolved", "How"]}>
            {resolved.map((r) => (
              <tr key={r.id} className="hover:bg-background">
                <Cell className="text-muted">{formatDateTime(r.receivedAt)}</Cell>
                <Cell className="font-mono text-xs">{r.gatewayPaymentId}<span className="block text-muted">{r.providerReference}</span></Cell>
                <Cell className="font-semibold tabular-nums">{r.amount ? formatMoney(r.amount) : "—"}</Cell>
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

function Item({ label, icon, children }: { label: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 gap-3 rounded-xl bg-background/60 p-3">
      <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface text-gold-text shadow-sm [&>svg]:size-4">{icon}</span>
      <div className="min-w-0">
        <dt className="text-xs text-muted">{label}</dt>
        <dd className="mt-0.5 font-medium">{children}</dd>
      </div>
    </div>
  );
}
