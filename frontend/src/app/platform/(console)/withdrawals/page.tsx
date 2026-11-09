import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import {
  AlertTriangle, ArrowRight, BadgeCheck, CircleCheck, Download, FileStack, FileSearch, Hourglass, Inbox, Search, SlidersHorizontal, Timer, X,
} from "lucide-react";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { BULK_PAY_FORM, BulkPayBar } from "@/features/admin/withdrawal-bulk-pay";
import { EARNINGS_BALANCE_LABEL } from "@/features/earnings/labels";
import { WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { MetricCard, STATUS_ICON, age, olderThanDays } from "@/features/withdrawals/ui";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Withdrawal, WithdrawalDashboard, WithdrawalFigure, WithdrawalStatus } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";

export const metadata: Metadata = { title: "Withdrawals" };

const STATUSES: readonly WithdrawalStatus[] = [
  "SUBMITTED", "UNDER_REVIEW", "ON_HOLD", "PENDING_APPROVAL", "APPROVED", "BATCHED", "PROCESSING", "FAILED",
  "PAID", "RETURNED", "REJECTED", "CANCELLED",
];
const SORTS = {
  "createdAt,asc": "Oldest first",
  "createdAt,desc": "Newest first",
  "amount,desc": "Largest first",
  "amount,asc": "Smallest first",
  "statusChangedAt,asc": "Waiting longest",
} as const;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/;
const INPUT = "h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold";

function one(value: string | string[] | undefined): string {
  return typeof value === "string" ? value.trim().slice(0, 100) : "";
}

export default async function AdminWithdrawalsPage({ searchParams }: PageProps<"/platform/withdrawals">) {
  const denied = await deniedUnlessInMenu("/platform/withdrawals");
  if (denied) return denied;
  const session = await requireStaff();
  const params = await searchParams;
  // The review queue first: new requests wait on finance.
  const status = one(params.status) === "ALL" ? "" : (STATUSES.find((s) => s === params.status) ?? (one(params.status) ? "" : "SUBMITTED"));
  const q = one(params.q);
  const from = DATE.test(one(params.from)) ? one(params.from) : "";
  const to = DATE.test(one(params.to)) ? one(params.to) : "";
  const minAmount = AMOUNT.test(one(params.minAmount)) ? one(params.minAmount) : "";
  const maxAmount = AMOUNT.test(one(params.maxAmount)) ? one(params.maxAmount) : "";
  const mine = one(params.mine) === "true";
  const sort = (Object.keys(SORTS) as (keyof typeof SORTS)[]).find((s) => s === params.sort) ?? "createdAt,asc";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const advanced = Boolean(from || to || minAmount || maxAmount || mine || sort !== "createdAt,asc");

  const filters: Record<string, string> = Object.fromEntries(Object.entries({
    status: status || "ALL", q, from, to, minAmount, maxAmount, mine: mine ? "true" : "", sort,
  }).filter(([, v]) => v));
  const query = new URLSearchParams({
    ...Object.fromEntries(Object.entries(filters).filter(([k]) => k !== "status")),
    ...(status ? { status } : {}), page: String(page), size: "25",
  });
  const [withdrawals, dashboard] = await Promise.all([
    authFetch<PageResponse<Withdrawal>>(`/api/v1/admin/withdrawals?${query}`),
    authFetch<WithdrawalDashboard>("/api/v1/admin/withdrawals/dashboard"),
  ]);
  const canPay = hasPermission(session.permissions, Permission.WITHDRAWAL_PROCESS);
  const payable = canPay && status === "APPROVED"
    ? withdrawals.content.filter((w) => w.origin === "REQUESTED" && w.userId !== session.userId) : [];

  const tabs: { status: WithdrawalStatus | "ALL"; label: string; figure?: WithdrawalFigure }[] = [
    { status: "SUBMITTED", label: "New", figure: dashboard.submitted },
    { status: "UNDER_REVIEW", label: "In review", figure: dashboard.underReview },
    { status: "ON_HOLD", label: "On hold", figure: dashboard.onHold },
    { status: "PENDING_APPROVAL", label: "2nd approval", figure: dashboard.pendingSecondApproval },
    { status: "APPROVED", label: "To pay", figure: dashboard.approved },
    { status: "PROCESSING", label: "With bank", figure: dashboard.inPayment },
    { status: "FAILED", label: "Failed", figure: dashboard.failed },
    { status: "PAID", label: "Paid" },
    { status: "ALL", label: "All" },
  ];
  const awaitingDecision = dashboard.submitted.count + dashboard.underReview.count + dashboard.pendingSecondApproval.count;

  return (
    <div className="space-y-6">
      <PageHeader title="Withdrawal management"
        description="Review, approve and pay investors' withdrawal requests. Withdrawal runs pay network investors."
        actions={
          <div className="flex flex-wrap gap-2">
            <LinkButton href="/platform/reports/withdrawal-register" variant="secondary"><Download className="size-4" aria-hidden="true" /> Export</LinkButton>
            <LinkButton href="/platform/withdrawals/batches" variant="secondary"><FileStack className="size-4" aria-hidden="true" /> Withdrawal files</LinkButton>
          </div>
        } />

      <section aria-label="At a glance" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={Inbox} label="Awaiting decision" value={awaitingDecision}
          note={`${formatMoney(sum(dashboard.submitted, dashboard.underReview, dashboard.pendingSecondApproval))} requested`}
          accent="text-amber-700 bg-amber-500/10 dark:text-amber-400" />
        <MetricCard icon={BadgeCheck} label="Approved, to pay" value={dashboard.approved.count}
          note={formatMoney(dashboard.approved.amount)} />
        <MetricCard icon={CircleCheck} label="Paid this month" value={formatMoney(dashboard.paidThisMonth.amount)}
          note={`${dashboard.paidThisMonth.count} withdrawals · ${dashboard.paidToday.count} today`}
          accent="text-emerald-700 bg-emerald-500/10 dark:text-emerald-400" />
        <MetricCard icon={Timer} label="Average time to pay" value={dashboard.avgHoursToPay == null ? "—" : `${dashboard.avgHoursToPay} h`}
          note="From request to paid, last 30 days" accent="text-slate-600 bg-slate-500/10 dark:text-slate-300" />
      </section>

      {(dashboard.failed.count > 0 || dashboard.onHold.count > 0) && (
        <div className="grid gap-3 sm:grid-cols-2">
          {dashboard.failed.count > 0 && (
            <Link href="/platform/withdrawals?status=FAILED"
              className="group flex items-center gap-3 rounded-2xl border border-rose-500/30 bg-rose-500/5 p-4 text-sm transition-colors hover:bg-rose-500/10">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-rose-500/10 text-rose-700 dark:text-rose-400">
                <AlertTriangle className="size-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-rose-700 dark:text-rose-400">
                  {dashboard.failed.count} failed payment{dashboard.failed.count === 1 ? "" : "s"} to resolve
                </span>
                <span className="block text-xs text-muted">{formatMoney(dashboard.failed.amount)} the bank did not pay</span>
              </span>
              <ArrowRight aria-hidden="true" className="size-4 shrink-0 text-rose-700 transition-transform group-hover:translate-x-0.5 dark:text-rose-400" />
            </Link>
          )}
          {dashboard.onHold.count > 0 && (
            <Link href="/platform/withdrawals?status=ON_HOLD"
              className="group flex items-center gap-3 rounded-2xl border border-amber-500/30 bg-amber-500/5 p-4 text-sm transition-colors hover:bg-amber-500/10">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-amber-500/10 text-amber-700 dark:text-amber-400">
                <Hourglass className="size-5" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-amber-700 dark:text-amber-400">{dashboard.onHold.count} on hold</span>
                <span className="block text-xs text-muted">{formatMoney(dashboard.onHold.amount)} waiting on a decision</span>
              </span>
              <ArrowRight aria-hidden="true" className="size-4 shrink-0 text-amber-700 transition-transform group-hover:translate-x-0.5 dark:text-amber-400" />
            </Link>
          )}
        </div>
      )}

      <div className="space-y-3">
        {/* Pipeline tabs: one row, scrolling sideways on narrow screens. */}
        <nav aria-label="Queue"
          className="flex max-w-full gap-1 overflow-x-auto rounded-xl border border-border bg-surface p-1 text-sm shadow-sm [scrollbar-width:none]">
          {tabs.map((t) => {
            const selected = (status || "ALL") === t.status;
            return (
              <Link key={t.status} href={`/platform/withdrawals?status=${t.status}`} aria-current={selected ? "page" : undefined}
                className={`flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${selected
                  ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                  : "text-muted hover:bg-background hover:text-foreground"}`}>
                {t.label}
                {t.figure && t.figure.count > 0 && (
                  <span className={`rounded-full px-2 py-0.5 text-[11px] font-semibold tabular-nums ${
                    t.status === "FAILED" ? "bg-rose-500/15 text-rose-700 dark:text-rose-300"
                      : selected ? "bg-on-ink/15 text-on-ink dark:bg-on-gold/15 dark:text-on-gold" : "bg-background text-muted ring-1 ring-border"}`}>
                    {t.figure.count.toLocaleString("en-IN")}
                  </span>
                )}
              </Link>
            );
          })}
        </nav>

        {/* Filters */}
        <form action="/platform/withdrawals" role="search" className="space-y-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
          <input type="hidden" name="status" value={status || "ALL"} />
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex min-w-0 flex-1 basis-64 items-center gap-2 rounded-xl border border-border bg-background/60 p-1 focus-within:border-gold">
              <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
              <input name="q" type="search" defaultValue={q} placeholder="Reference, investor ID or email" aria-label="Search withdrawals"
                className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
              <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
            </div>
          </div>
          <details open={advanced} className="group">
            <summary className="inline-flex min-h-8 cursor-pointer list-none items-center gap-1.5 rounded-lg border border-border px-3 text-xs font-medium text-muted transition-colors hover:border-gold hover:text-foreground group-open:border-gold/50 group-open:text-foreground">
              <SlidersHorizontal className="size-3.5" aria-hidden="true" /> More filters
              {advanced && <span className="rounded-full bg-gold px-1.5 text-[10px] font-semibold text-on-gold">Active</span>}
            </summary>
            <div className="mt-3 grid gap-3 border-t border-border pt-3 sm:grid-cols-2 lg:grid-cols-6">
              <Field label="Requested from"><input type="date" name="from" defaultValue={from} className={INPUT} /></Field>
              <Field label="Requested to"><input type="date" name="to" defaultValue={to} className={INPUT} /></Field>
              <Field label="Amount from (₹)"><input name="minAmount" inputMode="decimal" defaultValue={minAmount} className={INPUT} /></Field>
              <Field label="Amount to (₹)"><input name="maxAmount" inputMode="decimal" defaultValue={maxAmount} className={INPUT} /></Field>
              <Field label="Sort">
                <select name="sort" defaultValue={sort} className={INPUT}>
                  {Object.entries(SORTS).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
              </Field>
              <label className="flex h-10 cursor-pointer items-center gap-2 self-end rounded-lg border border-border px-3 text-sm transition-colors hover:border-gold">
                <input type="checkbox" name="mine" value="true" defaultChecked={mine} className="size-4 accent-[var(--gold)]" /> Reviewing myself
              </label>
            </div>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <button type="submit" className="h-9 rounded-lg bg-ink px-4 text-sm font-medium text-on-ink transition-opacity hover:opacity-90 dark:bg-gold dark:text-on-gold">Apply filters</button>
              {advanced && (
                <Link href={`/platform/withdrawals?status=${status || "ALL"}`}
                  className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-border px-3 text-sm text-muted transition-colors hover:border-gold hover:text-gold-text">
                  <X aria-hidden="true" className="size-3.5" /> Clear filters
                </Link>
              )}
            </div>
          </details>
        </form>
      </div>

      {payable.length > 0 && <BulkPayBar count={payable.length} />}

      {withdrawals.content.length === 0 ? (
        <EmptyState title={status === "SUBMITTED" ? "No new requests" : "No withdrawals found"}
          description={status === "SUBMITTED" ? "New requests from investors appear here." : "Nothing matches these filters."} />
      ) : (
        <DataTable columns={[...(payable.length > 0 ? ["Pay"] : []), "Withdrawal", "Investor", "Amount", "Pays to", "Status", "Waiting"]}>
          {withdrawals.content.map((w) => {
            const { icon: Icon, tone } = STATUS_ICON[w.status];
            const open = !["PAID", "RETURNED", "REJECTED", "CANCELLED"].includes(w.status);
            const waiting = age(w.statusChangedAt);
            const late = open && olderThanDays(w.statusChangedAt, 2);
            return (
              <tr key={w.id} className="group hover:bg-background">
                {payable.length > 0 && (
                  <Cell>
                    {payable.some((p) => p.id === w.id) && (
                      <input type="checkbox" name="withdrawalId" value={w.id} form={BULK_PAY_FORM} className="size-4"
                        aria-label={`Pay ${w.reference}`} />
                    )}
                  </Cell>
                )}
                <Cell>
                  <div className="flex items-center gap-3">
                    <span className={`flex size-9 shrink-0 items-center justify-center rounded-lg transition-transform duration-300 group-hover:scale-105 ${tone}`}><Icon className="size-4" aria-hidden="true" /></span>
                    <div>
                      <Link href={`/platform/withdrawals/${w.id}`} className="card-link font-mono font-medium text-gold-text">{w.reference}</Link>
                      <p className="text-xs text-muted">{formatDateTime(w.createdAt)} · {w.origin === "SCHEDULED" ? "Run" : w.origin === "OFFICE" ? "Office" : "Request"}</p>
                    </div>
                  </div>
                </Cell>
                <Cell>
                  <div className="flex items-center gap-2.5">
                    <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[11px] font-semibold text-gold-text">
                      {initials(w.investorName)}
                    </span>
                    <div className="min-w-0">
                      <p className="font-medium">{w.investorName || "—"}</p>
                      <p className="font-mono text-xs text-muted">{w.investorCode}</p>
                    </div>
                  </div>
                </Cell>
                <Cell className="tabular-nums">
                  <p className="font-semibold">{formatMoney(w.amount)}</p>
                  <p className="text-xs text-muted">pays {formatMoney(w.net)} · {EARNINGS_BALANCE_LABEL[w.balance]}</p>
                </Cell>
                <Cell>
                  {w.payoutMethod === "OFFICE_CASH" ? "Cash at the office" : (
                    <>
                      <p>{w.bankName}</p>
                      <p className="font-mono text-xs text-muted">{w.bankAccountMasked}</p>
                    </>
                  )}
                </Cell>
                <Cell>
                  <StatusBadge tone={WITHDRAWAL_TONE[w.status]}>{WITHDRAWAL_STATUS_LABEL[w.status]}</StatusBadge>
                  {w.requiredApprovals > 1 && open && <p className="mt-1 text-xs text-muted">{w.approvals}/{w.requiredApprovals} approvals</p>}
                  {w.reviewerId && open && <p className="mt-1 flex items-center gap-1 text-xs text-muted"><FileSearch className="size-3" aria-hidden="true" />{w.reviewerId === session.userId ? "You" : "Assigned"}</p>}
                </Cell>
                <Cell className="tabular-nums">
                  {open ? (
                    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ${late
                      ? "bg-rose-500/10 text-rose-700 dark:text-rose-400" : "bg-background text-muted ring-1 ring-border"}`}>
                      <Timer aria-hidden="true" className="size-3" />{waiting}
                    </span>
                  ) : <span className="text-muted">—</span>}
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <Pagination page={withdrawals} basePath="/platform/withdrawals" params={filters} />
    </div>
  );
}

function sum(...figures: WithdrawalFigure[]): { amount: string; currency: string } {
  const currency = figures[0]?.amount.currency ?? "INR";
  return { amount: figures.reduce((t, f) => t + Number(f.amount.amount), 0).toFixed(2), currency };
}

/** Up to two initials from a name, for the avatar. */
function initials(name: string | null | undefined): string {
  const parts = (name ?? "").trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.charAt(0) ?? "") + (parts.length > 1 ? (parts.at(-1)?.charAt(0) ?? "") : "")).toUpperCase() || "?";
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="space-y-1 text-xs text-muted">
      <span className="block">{label}</span>
      {children}
    </label>
  );
}
