import type { Metadata } from "next";
import Link from "next/link";
import {
  AlertTriangle, Banknote, CheckCircle2, FileSpreadsheet, Handshake, History, Landmark, ListChecks, type LucideIcon, TrendingUp, UserX, Wallet,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { HowItWorks } from "@/features/admin/config-ui";
import { generateWithdrawalsAction } from "@/features/admin/withdrawal-actions";
import { WithdrawalMinimumCard } from "@/features/admin/withdrawal-minimum-card";
import { ActionButton } from "@/features/orders/action-button";
import { PAYOUT_METHOD_LABEL, WITHDRAWAL_KIND_LABEL } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { WithdrawalRun } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Generate payout" };

/** Admins only: generating rent and referral withdrawals, the minimum a run pays, and the runs made so far. */
export default async function WithdrawalRunsPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS)) {
    return <EmptyState title="Not available" description="Generating payouts is for admins." />;
  }
  const runs = await authFetch<WithdrawalRun[]>("/api/v1/admin/withdrawal-runs");

  return (
    <div className="space-y-6">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-6">
          <div className="grid gap-4 md:grid-cols-2">
            <GenerateCard icon={TrendingUp} title="Returns payout" tone="ink"
              text="Every eligible network investor's returns up to now. Referral stays in their wallet for its own run.">
              <ActionButton action={generateWithdrawalsAction.bind(null, "RENT")} label="Generate returns payout"
                pendingLabel="Generating…"
                confirm="Withdraw every eligible network investor's returns now? Bank withdrawals go on a bank sheet, cash on a batch handed over at the office. Referral is not included." />
            </GenerateCard>
            <GenerateCard icon={Handshake} title="Referral payout" tone="light"
              text="Every eligible network investor's referral commission up to now. Returns stay in their wallet for their own run.">
              <ActionButton action={generateWithdrawalsAction.bind(null, "REFERRAL")} label="Generate referral payout"
                pendingLabel="Generating…" variant="secondary"
                confirm="Withdraw every eligible network investor's referral commission now? Bank withdrawals go on a bank sheet, cash on a batch handed over at the office. Returns are not included." />
            </GenerateCard>
          </div>
          <WithdrawalMinimumCard permissions={session.permissions} />
        </div>
        <HowItWorks title="After you generate" points={[
          { icon: Wallet, text: "Each investor's wallet of that kind moves into withdrawal in progress." },
          { icon: Landmark, text: "Bank payouts go on a bank sheet; cash goes on a batch handed over at the office, as each investor chose." },
          { icon: ListChecks, text: <>Each withdrawal becomes a task on <Link href="/platform/finance-tasks" className="font-medium text-gold-text hover:underline">Finance tasks</Link>, where finance marks it paid or failed.</> },
          { icon: FileSpreadsheet, text: <>Bank batches have an Excel sheet to pay from, under <Link href="/platform/withdrawals/batches" className="font-medium text-gold-text hover:underline">Withdrawal files</Link>.</> },
          { icon: UserX, text: "Online investors are not included: they request their own withdrawals." },
        ]} />
      </div>

      <section aria-labelledby="runs-title" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
        <h2 id="runs-title" className="flex items-center gap-3 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
          <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
            <History className="size-[18px]" />
          </span>
          Runs <span className="text-sm font-normal text-muted">{runs.length}</span>
        </h2>
        {runs.length === 0 ? (
          <div className="p-5"><EmptyState title="No runs yet" description="Runs appear here once a payout is generated." /></div>
        ) : (
          <ul className="divide-y divide-border">
            {runs.map((run) => (
              <li key={run.id} className="grid gap-3 p-4 lg:grid-cols-[minmax(0,14rem)_minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
                <div className="flex items-center gap-3">
                  <span aria-hidden="true" className={`flex size-9 shrink-0 items-center justify-center rounded-lg ${run.kind === "REFERRAL"
                    ? "bg-gold/10 text-gold-text" : "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"}`}>
                    {run.kind === "REFERRAL" ? <Handshake className="size-4" /> : <TrendingUp className="size-4" />}
                  </span>
                  <div className="min-w-0 text-sm">
                    <p className="font-mono font-medium">{run.reference}</p>
                    <p className="text-xs text-muted">{run.kind ? WITHDRAWAL_KIND_LABEL[run.kind] : "Returns and referral"} · {formatDateTime(run.runAt)}</p>
                  </div>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Sheet icon={Landmark} label="Bank sheet" batchId={run.bankBatchId} reference={run.bankBatchReference} investors={run.bankInvestors} total={run.bankTotal} />
                  <Sheet icon={Banknote} label="Cash" batchId={run.cashBatchId} reference={run.cashBatchReference} investors={run.cashInvestors} total={run.cashTotal} />
                </div>
                <div className="text-sm">
                  {run.skipped.length === 0 ? (
                    <p className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:text-emerald-300">
                      <CheckCircle2 aria-hidden="true" className="size-3.5" /> Nobody left out
                    </p>
                  ) : (
                    <details className="rounded-xl border border-amber-500/30 bg-amber-500/5">
                      <summary className="flex cursor-pointer items-center gap-1.5 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-300">
                        <AlertTriangle aria-hidden="true" className="size-3.5" /> {run.skipped.length} left out
                      </summary>
                      <ul className="space-y-1.5 border-t border-amber-500/20 px-3 py-2 text-xs">
                        {run.skipped.map((skip, i) => (
                          <li key={`${skip.userId}-${i}`}>
                            <Link href={`/platform/users/${skip.userId}`} className="font-mono text-gold-text hover:underline">{skip.userCode ?? "Investor"}</Link>
                            <span className="text-muted"> · {PAYOUT_METHOD_LABEL[skip.payoutMethod]} · {skip.reason}</span>
                          </li>
                        ))}
                      </ul>
                    </details>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function GenerateCard({ icon: Icon, title, text, tone, children }: {
  icon: LucideIcon; title: string; text: string; tone: "ink" | "light"; children: React.ReactNode;
}) {
  const ink = tone === "ink";
  return (
    <section className={`relative isolate flex flex-col gap-4 overflow-hidden rounded-2xl p-5 shadow-sm ${ink
      ? "bg-ink text-on-ink shadow-xl shadow-ink/20" : "border border-border bg-surface"}`}>
      {ink && <div aria-hidden="true" className="absolute -top-16 -right-16 -z-10 size-44 rounded-full bg-gold/25 blur-3xl" />}
      <div className="flex items-start gap-3">
        <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${ink ? "bg-gold text-on-gold" : "bg-gold/10 text-gold-text"}`}>
          <Icon className="size-5" />
        </span>
        <div className="min-w-0">
          <h2 className="font-semibold tracking-tight">{title}</h2>
          <p className={`text-sm ${ink ? "text-on-ink/70" : "text-muted"}`}>{text}</p>
        </div>
      </div>
      <div className="mt-auto">{children}</div>
    </section>
  );
}

function Sheet({ icon: Icon, label, batchId, reference, investors, total }: {
  icon: LucideIcon;
  label: string;
  batchId: string | null;
  reference: string | null;
  investors: number;
  total: WithdrawalRun["bankTotal"];
}) {
  return (
    <div className="flex items-start gap-2 rounded-xl bg-background/60 px-3 py-2 text-sm ring-1 ring-border">
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-muted" />
      <div className="min-w-0">
        <p className="text-[11px] text-muted">{label}</p>
        {batchId ? (
          <>
            <Link href={`/platform/withdrawals/batches/${batchId}`} className="font-mono text-gold-text hover:underline">{reference}</Link>
            <p className="text-xs text-muted">{investors} investor{investors === 1 ? "" : "s"} · <span className="font-medium text-foreground tabular-nums">{formatMoney(total)}</span></p>
          </>
        ) : <p className="text-muted">Nobody</p>}
      </div>
    </div>
  );
}
