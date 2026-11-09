import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowLeftRight, ArrowRight, CalendarCheck, CheckCircle2, FileWarning, Hourglass, type LucideIcon, RadioTower, ReceiptText, Scale,
  TriangleAlert, Undo2,
} from "lucide-react";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Cell, DataTable } from "@/components/ui/data-table";
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
  const denied = await deniedUnlessInMenu("/platform/reconciliation");
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
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="recon-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <div aria-hidden="true" className={`absolute -top-24 -right-24 -z-10 size-80 rounded-full blur-3xl ${report.issues === 0 ? "bg-emerald-400/25" : "bg-amber-400/30"}`} />
        <div aria-hidden="true" className="absolute -bottom-32 -left-20 -z-10 size-72 rounded-full bg-gold/15 blur-3xl" />
        <div className="space-y-6 p-6 sm:p-8 lg:p-10">
          <div className="flex flex-wrap items-center gap-5">
            <span aria-hidden="true" className={`flex size-14 shrink-0 items-center justify-center rounded-2xl ring-1 ${report.issues === 0
              ? "bg-emerald-500/15 text-emerald-300 ring-emerald-400/30" : "bg-amber-500/15 text-amber-300 ring-amber-400/30"}`}>
              {report.issues === 0 ? <CheckCircle2 className="size-7" /> : <TriangleAlert className="size-7" />}
            </span>
            <div className="min-w-0 flex-1 space-y-1">
              <p className="text-xs font-medium tracking-[0.14em] text-on-ink/60 uppercase">Reconciliation</p>
              <h1 id="recon-title" className="text-2xl font-semibold tracking-tight sm:text-3xl">
                {report.issues === 0 ? "Everything reconciles"
                  : <><span className="text-amber-300">{report.issues}</span> {report.issues === 1 ? "item needs" : "items need"} attention</>}
              </h1>
              <p className="max-w-3xl text-sm text-on-ink/70">
                Whether the ledger agrees with the payments and withdrawals it records. Checked live {formatDateTime(report.generatedAt)}, and once a day automatically.
              </p>
            </div>
          </div>
          <dl className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <BannerTile icon={TriangleAlert} label="Needs attention" good={report.issues === 0}
              value={report.issues.toLocaleString("en-IN")} note="Items across every check" />
            <BannerTile icon={Scale} label="Trial balance" good={balanced}
              value={balanced ? "Balanced" : "Out of balance"} note="Debits equal credits in every currency" />
            <BannerTile icon={ArrowLeftRight} label="Withdrawals in transit" good={transitMatches}
              value={transitMatches ? "Agrees" : "Differs"} note="Ledger against open withdrawals" />
            <BannerTile icon={CalendarCheck} label="Last daily check" good={!lastRun || lastRun.issues === 0}
              value={lastRun ? (lastRun.issues === 0 ? "All clear" : `${lastRun.issues} to check`) : "—"}
              note={lastRun ? formatDateTime(lastRun.runAt) : "Not run yet: it runs every morning"} />
          </dl>
        </div>
      </section>

      <Section icon={ReceiptText} title="Unmatched gateway receipts" count={report.unmatchedReceipts.length}
        description="Money taken by the gateway for a reference that is none of ours."
        action={<ActionLink href="/platform/payments/unmatched">Resolve receipts</ActionLink>}>
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

      <Section icon={RadioTower} title="Payout events to check" count={report.payoutEvents.length}
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
                      ? <Link href={`/platform/withdrawals?q=${encodeURIComponent(e.withdrawalReference)}`} className="font-mono text-gold-text hover:underline">{e.withdrawalReference}</Link>
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

      <Section icon={Hourglass} title="Withdrawals stuck in payment" count={report.stuckWithdrawals.length}
        description="Handed to the bank or provider more than 3 days ago, and still neither paid nor failed.">
        <DataTable columns={["Withdrawal", "Status", "Amount", "Since"]}>
          {report.stuckWithdrawals.map((w) => (
            <tr key={w.id}>
              <Cell>
                {can(Permission.WITHDRAWAL_VIEW)
                  ? <Link href={`/platform/withdrawals/${w.id}`} className="font-mono text-gold-text hover:underline">{w.reference}</Link>
                  : <span className="font-mono">{w.reference}</span>}
              </Cell>
              <Cell>{WITHDRAWAL_STATUS_LABEL[w.status]}</Cell>
              <Cell className="tabular-nums">{formatMoney(w.amount)}</Cell>
              <Cell className="text-muted">{formatDateTime(w.since)}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section icon={Undo2} title="Refunds overdue" count={report.pendingRefunds.length}
        description="Money received that must go back to the investor, still not refunded after 3 days."
        action={<ActionLink href="/platform/payments?view=refunds">Record refunds</ActionLink>}>
        <DataTable columns={["Received", "Order", "Paid by", "Amount"]}>
          {report.pendingRefunds.map((p) => (
            <tr key={p.paymentId}>
              <Cell className="text-muted">{formatDateTime(p.since)}</Cell>
              <Cell>
                {can(Permission.ORDER_VIEW)
                  ? <Link href={`/platform/orders/${p.orderId}`} className="font-mono text-xs text-gold-text hover:underline">{p.orderId}</Link>
                  : <span className="font-mono text-xs">{p.orderId}</span>}
              </Cell>
              <Cell>{METHOD_LABEL[p.method]}</Cell>
              <Cell className="tabular-nums">{formatMoney(p.amount)}</Cell>
            </tr>
          ))}
        </DataTable>
      </Section>

      <Section icon={FileWarning} title="Deposits missing from the ledger" count={report.unpostedDeposits.length}
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
        <Section icon={ArrowLeftRight} title="Withdrawals in transit" count={report.inTransit.filter((t) => !t.matches).length}
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

        <Section icon={Scale} title="Trial balance" count={report.trialBalance.filter((t) => !t.balanced).length}
          description="Total debits and credits over the whole ledger." always
          action={can(Permission.LEDGER_VIEW) || can(Permission.FINANCE_VIEW)
            ? <ActionLink href="/platform/ledger">Open the ledger</ActionLink> : undefined}>
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

      <section className="space-y-4 rounded-2xl border border-border bg-surface p-5 shadow-sm sm:p-6" aria-labelledby="runs-title">
        <h2 id="runs-title" className="flex items-center gap-3 text-lg font-semibold tracking-tight">
          <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
            <CalendarCheck className="size-[18px]" />
          </span>
          Daily checks
          <span className="text-sm font-normal text-muted">last {runs.length}</span>
        </h2>
        {runs.length === 0 ? <p className="text-sm text-muted">The first daily check runs tomorrow morning.</p> : (
          <ol className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-7">
            {runs.map((r) => (
              <li key={r.id} className={`flex items-center gap-2.5 rounded-xl border px-3 py-2.5 text-sm ${r.issues === 0
                ? "border-emerald-500/20 bg-emerald-500/5" : "border-amber-500/30 bg-amber-500/10"}`}>
                {r.issues === 0
                  ? <CheckCircle2 aria-hidden="true" className="size-4 shrink-0 text-emerald-700 dark:text-emerald-400" />
                  : <TriangleAlert aria-hidden="true" className="size-4 shrink-0 text-amber-700 dark:text-amber-400" />}
                <span className="min-w-0">
                  <span className={`block font-medium ${r.issues === 0 ? "text-emerald-700 dark:text-emerald-400" : "text-amber-700 dark:text-amber-400"}`}>
                    {r.issues === 0 ? "All clear" : `${r.issues} to check`}
                  </span>
                  <span className="block truncate text-xs text-muted">{formatDateTime(r.runAt)}</span>
                </span>
              </li>
            ))}
          </ol>
        )}
      </section>
    </div>
  );
}

/** A headline check inside the banner, green when it passes and amber when it does not. */
function BannerTile({ icon: Icon, label, value, note, good }: { icon: LucideIcon; label: string; value: string; note: string; good: boolean }) {
  return (
    <div className="rounded-2xl border border-on-ink/15 bg-on-ink/[0.08] p-4 backdrop-blur-md">
      <dt className="flex items-center gap-2 text-xs text-on-ink/60">
        <Icon aria-hidden="true" className={`size-4 ${good ? "text-emerald-300" : "text-amber-300"}`} /> {label}
      </dt>
      <dd className={`mt-1.5 font-display text-xl font-semibold tracking-tight tabular-nums ${good ? "" : "text-amber-300"}`}>{value}</dd>
      <dd className="mt-0.5 truncate text-xs text-on-ink/55">{note}</dd>
    </div>
  );
}

function ActionLink({ href, children }: { href: string; children: React.ReactNode }) {
  return (
    <Link href={href}
      className="group inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm font-medium transition-colors hover:border-gold hover:text-gold-text">
      {children}
      <ArrowRight aria-hidden="true" className="size-3.5 transition-transform group-hover:translate-x-0.5" />
    </Link>
  );
}

/** One check: its rows when something needs attention, otherwise a single "all clear" line ({@code always}: the rows regardless). */
function Section({ icon: Icon, title, description, count, action, always = false, children }: {
  icon: LucideIcon;
  title: string;
  description: string;
  count: number;
  action?: React.ReactNode;
  always?: boolean;
  children: React.ReactNode;
}) {
  const clear = count === 0;
  const expanded = !clear || always;
  return (
    <section aria-label={title}
      className={`relative overflow-hidden rounded-2xl border bg-surface shadow-sm ${clear ? "border-border" : "border-amber-500/40"} ${expanded ? "space-y-4 p-5 sm:p-6" : "p-4"}`}>
      {!clear && <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-amber-500" />}
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-1 items-start gap-3">
          <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${clear
            ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"
            : "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"}`}>
            <Icon className="size-5" />
          </span>
          <div className="min-w-0">
            <h2 className="flex flex-wrap items-center gap-2 font-semibold tracking-tight sm:text-lg">
              {title}
              <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-semibold ${clear
                ? "bg-emerald-500/10 text-emerald-700 dark:text-emerald-400" : "bg-amber-500/15 text-amber-700 dark:text-amber-300"}`}>
                {clear ? <><CheckCircle2 aria-hidden="true" className="size-3" /> Clear</> : count.toLocaleString("en-IN")}
              </span>
            </h2>
            <p className="mt-0.5 text-sm text-muted">{description}</p>
          </div>
        </div>
        {expanded && action}
      </header>
      {expanded && children}
    </section>
  );
}
