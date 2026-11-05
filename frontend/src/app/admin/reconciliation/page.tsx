import type { Metadata } from "next";
import Link from "next/link";
import { CheckCircle2, TriangleAlert } from "lucide-react";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Cell, DataTable } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { StatGrid } from "@/components/ui/stat-grid";
import { StatusBadge } from "@/components/ui/status-badge";
import { METHOD_LABEL } from "@/features/orders/labels";
import { WITHDRAWAL_STATUS_LABEL } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { ReconciliationReport, ReconciliationRun } from "@/types/reconciliation";
import { formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Reconciliation" };

const OUTCOME_LABEL: Record<ReconciliationReport["payoutEvents"][number]["outcome"], string> = {
  NEEDS_RECONCILIATION: "Needs checking",
  AMOUNT_MISMATCH: "Amount differs",
  UNKNOWN_WITHDRAWAL: "Unknown withdrawal",
};

export default async function ReconciliationPage() {
  const denied = await deniedUnlessInMenu("/admin/reconciliation");
  if (denied) return denied;
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const [report, runs] = await Promise.all([
    authFetch<ReconciliationReport>("/api/v1/admin/reconciliation"),
    authFetch<ReconciliationRun[]>("/api/v1/admin/reconciliation/runs?limit=14"),
  ]);
  const balanced = report.trialBalance.every((t) => t.balanced);
  const transitMatches = report.inTransit.every((t) => t.matches);
  const lastRun = runs[0];

  return (
    <div className="space-y-6">
      <PageHeader title="Reconciliation"
        description="What needs finance's attention, and whether the ledger agrees with the payments and withdrawals it records. Checked live each time you open this page, and once a day automatically." />

      <section className={`flex items-start gap-3 rounded-xl border p-5 ${report.issues === 0
        ? "border-emerald-600/20 bg-emerald-50 dark:bg-emerald-500/10" : "border-amber-600/20 bg-amber-50 dark:bg-amber-500/10"}`}>
        {report.issues === 0
          ? <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-700 dark:text-emerald-400" aria-hidden="true" />
          : <TriangleAlert className="mt-0.5 size-5 shrink-0 text-amber-700 dark:text-amber-400" aria-hidden="true" />}
        <div>
          <p className="font-semibold">{report.issues === 0 ? "Everything reconciles" : `${report.issues} ${report.issues === 1 ? "item needs" : "items need"} attention`}</p>
          <p className="text-sm text-muted">Checked {formatDateTime(report.generatedAt)}</p>
        </div>
      </section>

      <StatGrid label="Checks" stats={[
        { label: "Needs attention", value: report.issues },
        { label: "Trial balance", value: balanced ? "Balanced" : "Out of balance", note: "Debits equal credits in every currency" },
        { label: "Withdrawals in transit", value: transitMatches ? "Agrees" : "Differs", note: "Ledger against open withdrawals" },
        { label: "Last daily check", value: lastRun ? lastRun.issues : "—",
          note: lastRun ? `items on ${formatDateTime(lastRun.runAt)}` : "Not run yet: it runs every morning" },
      ]} />

      <Section title="Unmatched gateway receipts" count={report.unmatchedReceipts.length}
        description="Money taken by the gateway for a reference that is none of ours."
        action={<Link href="/admin/payments/unmatched" className="text-sm text-gold-text hover:underline">Resolve receipts</Link>}>
        <DataTable columns={["Received", "Gateway payment", "Paid against", "Amount"]}>
          {report.unmatchedReceipts.map((r) => (
            <tr key={r.id}>
              <Cell className="text-muted">{formatDateTime(r.receivedAt)}</Cell>
              <Cell className="font-mono text-xs">{r.gatewayPaymentId}</Cell>
              <Cell className="font-mono text-xs">{r.providerReference}</Cell>
              <Cell className="tabular-nums">{r.amount ? formatMoney(r.amount) : "—"}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section title="Payout events to check" count={report.payoutEvents.length}
        description="Messages from the payout provider (last 30 days) that could not be applied automatically: compare them with the bank statement.">
        <DataTable columns={["Received", "Event", "Outcome", "Withdrawal", "Bank reference"]}>
          {report.payoutEvents.map((e) => (
            <tr key={e.id}>
              <Cell className="text-muted">{formatDateTime(e.receivedAt)}</Cell>
              <Cell className="text-xs"><span className="font-mono">{e.eventType}</span><span className="block font-mono text-muted">{e.eventId}</span></Cell>
              <Cell><StatusBadge tone={e.outcome === "AMOUNT_MISMATCH" ? "danger" : "warning"}>{OUTCOME_LABEL[e.outcome]}</StatusBadge></Cell>
              <Cell>
                {e.withdrawalReference ? (
                  <>
                    {can(Permission.WITHDRAWAL_VIEW)
                      ? <Link href={`/admin/withdrawals?q=${encodeURIComponent(e.withdrawalReference)}`} className="font-mono text-gold-text hover:underline">{e.withdrawalReference}</Link>
                      : <span className="font-mono">{e.withdrawalReference}</span>}
                    {e.withdrawalStatus && <span className="block text-xs text-muted">{WITHDRAWAL_STATUS_LABEL[e.withdrawalStatus]}</span>}
                  </>
                ) : <span className="text-muted">None of ours</span>}
              </Cell>
              <Cell className="font-mono text-xs">{e.providerReference ?? "—"}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section title="Withdrawals stuck in payment" count={report.stuckWithdrawals.length}
        description="Handed to the bank or provider more than 3 days ago, and still neither paid nor failed.">
        <DataTable columns={["Withdrawal", "Status", "Amount", "Since"]}>
          {report.stuckWithdrawals.map((w) => (
            <tr key={w.id}>
              <Cell>
                {can(Permission.WITHDRAWAL_VIEW)
                  ? <Link href={`/admin/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link>
                  : <span className="font-mono">{w.reference}</span>}
              </Cell>
              <Cell>{WITHDRAWAL_STATUS_LABEL[w.status]}</Cell>
              <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
              <Cell className="text-muted">{formatDateTime(w.since)}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section title="Refunds overdue" count={report.pendingRefunds.length}
        description="Money received that must go back to the investor, still not refunded after 3 days."
        action={<Link href="/admin/payments?view=refunds" className="text-sm text-gold-text hover:underline">Record refunds</Link>}>
        <DataTable columns={["Received", "Order", "Paid by", "Amount"]}>
          {report.pendingRefunds.map((p) => (
            <tr key={p.paymentId}>
              <Cell className="text-muted">{formatDateTime(p.since)}</Cell>
              <Cell>
                {can(Permission.ORDER_VIEW)
                  ? <Link href={`/admin/orders/${p.orderId}`} className="font-mono text-xs text-gold-text hover:underline">{p.orderId}</Link>
                  : <span className="font-mono text-xs">{p.orderId}</span>}
              </Cell>
              <Cell>{METHOD_LABEL[p.method]}</Cell>
              <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section title="Deposits missing from the ledger" count={report.unpostedDeposits.length}
        description="Payments settled (or refunded) without their ledger posting. This should never happen: tell the technical team.">
        <DataTable columns={["Settled", "Payment", "Status", "Amount", "Missing"]}>
          {report.unpostedDeposits.map((d) => (
            <tr key={d.paymentId}>
              <Cell className="text-muted">{formatDateTime(d.settledAt)}</Cell>
              <Cell className="font-mono text-xs">{d.paymentId}</Cell>
              <Cell>{humanize(d.status)}</Cell>
              <Cell className="tabular-nums">{formatMoney(d.amount)}</Cell>
              <Cell>{d.missing === "DEPOSIT_RECEIVED" ? "Money received" : d.missing === "REINVESTMENT" ? "Reinvestment" : "Refund"}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <div className="grid gap-6 lg:grid-cols-2">
        <Section title="Withdrawals in transit" count={report.inTransit.filter((t) => !t.matches).length}
          description="What the ledger holds for withdrawals on their way, against the open withdrawals that reserved it." always>
          {report.inTransit.length === 0 ? <p className="text-sm text-muted">No withdrawals have been made yet.</p> : <DataTable columns={["Currency", "Ledger", "Open withdrawals", ""]}>
            {report.inTransit.map((t) => (
              <tr key={t.currency}>
                <Cell>{t.currency}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.ledger)}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.openWithdrawals)}</Cell>
                <Cell><StatusBadge tone={t.matches ? "success" : "danger"}>{t.matches ? "Agrees" : "Differs"}</StatusBadge></Cell>
              </tr>
            ))}
          </DataTable>}
        </Section>

        <Section title="Trial balance" count={report.trialBalance.filter((t) => !t.balanced).length}
          description="Total debits and credits over the whole ledger." always
          action={can(Permission.LEDGER_VIEW) || can(Permission.FINANCE_VIEW)
            ? <Link href="/admin/ledger" className="text-sm text-gold-text hover:underline">Open the ledger</Link> : undefined}>
          <DataTable columns={["Currency", "Debits", "Credits", ""]}>
            {report.trialBalance.map((t) => (
              <tr key={t.currency}>
                <Cell>{t.currency}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.debits)}</Cell>
                <Cell className="tabular-nums">{formatMoney(t.credits)}</Cell>
                <Cell><StatusBadge tone={t.balanced ? "success" : "danger"}>{t.balanced ? "Balanced" : "Out of balance"}</StatusBadge></Cell>
              </tr>
            ))}
          </DataTable>
        </Section>
      </div>

      <section className="space-y-3" aria-label="Daily checks">
        <h2 className="text-lg font-semibold tracking-tight">Daily checks</h2>
        {runs.length === 0 ? <p className="text-sm text-muted">The first daily check runs tomorrow morning.</p> : (
          <ol className="flex flex-wrap gap-2">
            {runs.map((r) => (
              <li key={r.id} className="rounded-lg border border-border bg-surface px-3 py-2 text-sm">
                <span className="text-muted">{formatDateTime(r.runAt)}</span>{" · "}
                <span className={r.issues === 0 ? "text-emerald-700 dark:text-emerald-400" : "font-medium text-amber-700 dark:text-amber-400"}>
                  {r.issues === 0 ? "All clear" : `${r.issues} to check`}
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}

/** One check: its rows when something needs attention, otherwise a single "all clear" line ({@code always}: the rows regardless). */
function Section({ title, description, count, action, always = false, children }: {
  title: string;
  description: string;
  count: number;
  action?: React.ReactNode;
  always?: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3 rounded-xl border border-border bg-surface p-5 shadow-sm" aria-label={title}>
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold tracking-tight">
            {title}
            <StatusBadge tone={count === 0 ? "success" : "warning"}>{count === 0 ? "Clear" : count}</StatusBadge>
          </h2>
          <p className="mt-0.5 text-sm text-muted">{description}</p>
        </div>
        {(count > 0 || always) && action}
      </header>
      {count > 0 || always ? children : <p className="text-sm text-muted">Nothing to check.</p>}
    </section>
  );
}
