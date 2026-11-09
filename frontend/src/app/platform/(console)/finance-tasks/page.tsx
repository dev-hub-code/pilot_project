import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { financeTaskAssignmentAction } from "@/features/admin/withdrawal-actions";
import { FinanceTaskForm } from "@/features/admin/withdrawal-forms";
import { PAYOUT_METHOD_LABEL, WITHDRAWAL_KIND_LABEL, WITHDRAWAL_STATUS_LABEL, WITHDRAWAL_TONE } from "@/features/withdrawals/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { FinanceTask, WithdrawalKind, WithdrawalPayoutMethod } from "@/types/withdrawal";
import { formatDateTime } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { formatPhone } from "@/lib/phone";
import { Banknote, CheckCircle2, ClipboardList, Landmark, Layers, ListTodo, Search, UserCheck, X } from "lucide-react";
import { MetricCard } from "@/features/withdrawals/ui";

export const metadata: Metadata = { title: "Finance tasks" };

const VIEWS = [
  { key: "OPEN", label: "To do" },
  { key: "DONE", label: "Done" },
  { key: "ALL", label: "All" },
] as const;
const METHODS: readonly WithdrawalPayoutMethod[] = ["BANK_TRANSFER", "OFFICE_CASH"];
const KINDS: readonly WithdrawalKind[] = ["RENT", "REFERRAL"];

type Filters = { view: (typeof VIEWS)[number]["key"]; method?: WithdrawalPayoutMethod; kind?: WithdrawalKind; q?: string };

function href(filters: Filters, change: Partial<Filters>) {
  const next = { ...filters, ...change };
  const query = new URLSearchParams({ view: next.view });
  if (next.method) query.set("method", next.method);
  if (next.kind) query.set("kind", next.kind);
  if (next.q) query.set("q", next.q);
  return `/platform/finance-tasks?${query}`;
}

export default async function FinanceTasksPage({ searchParams }: PageProps<"/platform/finance-tasks">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.WITHDRAWAL_PROCESS) && !can(Permission.FINANCE_TASK_PROCESS)) {
    return <EmptyState title="Not available" description="Finance tasks need the finance tasks permission." />;
  }
  // Finance pays tasks but does not see withdrawals or their batches; those links are for admins.
  const canViewWithdrawals = can(Permission.WITHDRAWAL_VIEW);
  const params = await searchParams;
  const filters: Filters = {
    view: VIEWS.find((v) => v.key === params.view)?.key ?? "OPEN",
    method: METHODS.find((m) => m === params.method),
    kind: KINDS.find((k) => k === params.kind),
    q: typeof params.q === "string" && params.q.trim() ? params.q.trim().slice(0, 100) : undefined,
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ view: filters.view, page: String(page), size: "50" });
  if (filters.method) query.set("method", filters.method);
  if (filters.kind) query.set("kind", filters.kind);
  if (filters.q) query.set("q", filters.q);
  const tasks = await authFetch<PageResponse<FinanceTask>>(`/api/v1/admin/finance-tasks?${query}`);
  const open = filters.view === "OPEN";
  const all = filters.view === "ALL";
  /** Still to pay: shown with the update form; anything else shows how it ended. */
  const isOpen = (t: FinanceTask) => t.status === "BATCHED" || t.status === "PROCESSING";
  const pill = (active: boolean) =>
    `inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 font-medium whitespace-nowrap transition-colors duration-200 ${active
      ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
      : "text-muted hover:bg-background hover:text-foreground"}`;
  const group = "inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm";

  return (
    <div className="space-y-6">
      <PageHeader title="Finance tasks"
        description="Pay each withdrawal from a withdrawal run by bank or in cash at the office, then mark it paid, or failed with the reason. Failed ones are held and open a support ticket." />

      <section aria-label="This page at a glance" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard icon={ClipboardList} label={open ? "Tasks to do" : all ? "All tasks" : "Tasks done"} value={tasks.totalElements} note="With these filters" />
        <MetricCard icon={UserCheck} label="Assigned to you" accent="text-emerald-700 bg-emerald-500/10 dark:text-emerald-400"
          value={tasks.content.filter((t) => t.assignee?.id === session.userId).length} note="On this page" />
        <MetricCard icon={Landmark} label="Bank transfers" accent="text-sky-700 bg-sky-500/10 dark:text-sky-400"
          value={tasks.content.filter((t) => t.payoutMethod === "BANK_TRANSFER").length} note="On this page" />
        <MetricCard icon={Banknote} label="Cash at the office" accent="text-amber-700 bg-amber-500/10 dark:text-amber-400"
          value={tasks.content.filter((t) => t.payoutMethod === "OFFICE_CASH").length} note="On this page" />
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Tasks" className={group}>
          {VIEWS.map((v) => (
            <Link key={v.key} href={href(filters, { view: v.key })} aria-current={v.key === filters.view ? "page" : undefined}
              className={`${pill(v.key === filters.view)} px-4 py-2`}>
              {v.key === "OPEN" ? <ListTodo aria-hidden="true" className="size-4" />
                : v.key === "DONE" ? <CheckCircle2 aria-hidden="true" className="size-4" /> : <Layers aria-hidden="true" className="size-4" />}
              {v.label}
            </Link>
          ))}
        </nav>
        <form method="get" action="/platform/finance-tasks" role="search"
          className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:w-auto">
          <input type="hidden" name="view" value={filters.view} />
          {filters.method && <input type="hidden" name="method" value={filters.method} />}
          {filters.kind && <input type="hidden" name="kind" value={filters.kind} />}
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={filters.q} placeholder="User code, name, email or reference"
            aria-label="Search by user code (SL03501), name, email, or a WD- or WB- reference"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none sm:w-72" />
          {filters.q && (
            <Link href={href(filters, { q: undefined })} aria-label="Clear search" title="Clear search"
              className="flex size-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-background hover:text-foreground">
              <X aria-hidden="true" className="size-4" />
            </Link>
          )}
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </form>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <nav aria-label="Pay by" className={group}>
          <Link href={href(filters, { method: undefined })} aria-current={!filters.method ? "true" : undefined} className={pill(!filters.method)}>Bank and cash</Link>
          {METHODS.map((m) => (
            <Link key={m} href={href(filters, { method: m })} aria-current={filters.method === m ? "true" : undefined} className={pill(filters.method === m)}>
              {m === "OFFICE_CASH" ? <Banknote aria-hidden="true" className="size-4" /> : <Landmark aria-hidden="true" className="size-4" />}
              {PAYOUT_METHOD_LABEL[m]}
            </Link>
          ))}
        </nav>
        <nav aria-label="Kind" className={group}>
          <Link href={href(filters, { kind: undefined })} aria-current={!filters.kind ? "true" : undefined} className={pill(!filters.kind)}>Returns and referral</Link>
          {KINDS.map((k) => (
            <Link key={k} href={href(filters, { kind: k })} aria-current={filters.kind === k ? "true" : undefined} className={pill(filters.kind === k)}>
              {WITHDRAWAL_KIND_LABEL[k]}
            </Link>
          ))}
        </nav>
      </div>

      {tasks.content.some((t) => isOpen(t) && t.payoutMethod === "BANK_TRANSFER") && (
        <Notice>Full account numbers are shown so you can pay; every time this list is opened is recorded.</Notice>
      )}

      {tasks.content.length === 0 ? (
        filters.q ? (
          <EmptyState title="No tasks match" description={`Nothing ${open ? "to do " : all ? "" : "done "}matches “${filters.q}” with these filters.`} />
        ) : (
          <EmptyState title={open ? "Nothing to do" : all ? "No tasks yet" : "No tasks done yet"}
            description={open || all ? "Tasks appear here when an admin generates a returns or referral payout." : undefined} />
        )
      ) : (
        <DataTable columns={["Task", "Investor", "Pay to", "Amount", open ? "Update" : all ? "Update or outcome" : "Outcome"]}>
          {tasks.content.map((t) => {
            const cash = t.payoutMethod === "OFFICE_CASH";
            return (
              <tr key={t.id} className="group align-top hover:bg-background">
                <Cell>
                  <div className="flex gap-3">
                    <span aria-hidden="true" className={`flex size-9 shrink-0 items-center justify-center rounded-lg transition-transform duration-300 group-hover:scale-105 ${cash
                      ? "bg-amber-500/10 text-amber-700 dark:text-amber-400" : "bg-sky-500/10 text-sky-700 dark:text-sky-400"}`}>
                      {cash ? <Banknote className="size-4" /> : <Landmark className="size-4" />}
                    </span>
                    <div className="min-w-0">
                      {canViewWithdrawals
                        ? <Link href={`/platform/withdrawals/${t.id}`} className="font-mono font-medium text-gold-text hover:underline">{t.reference}</Link>
                        : <span className="font-mono font-medium">{t.reference}</span>}
                      <span className="mt-0.5 flex flex-wrap gap-1">
                        <span className="rounded-full bg-background px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-border">{WITHDRAWAL_KIND_LABEL[t.kind]}</span>
                        <span className="rounded-full bg-background px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-border">{PAYOUT_METHOD_LABEL[t.payoutMethod]}</span>
                      </span>
                      <span className="mt-1 block text-xs text-muted">
                        {canViewWithdrawals ? (
                          <Link href={`/platform/withdrawals/batches/${t.batchId}`} className="font-mono hover:text-gold-text hover:underline">{t.batchReference}</Link>
                        ) : <span className="font-mono">{t.batchReference}</span>}
                        {" · "}{formatDateTime(t.createdAt)}
                      </span>
                    </div>
                  </div>
                </Cell>
                <Cell>
                  <div className="flex items-center gap-2.5">
                    <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[11px] font-semibold text-gold-text">
                      {initials(t.name)}
                    </span>
                    <div className="min-w-0">
                      <span className="block font-medium">{t.name}</span>
                      <Link href={`/platform/users/${t.userId}`} className="inline-flex min-h-6 items-center font-mono text-xs text-gold-text hover:underline">{t.userCode ?? "Investor"}</Link>
                    </div>
                  </div>
                </Cell>
                <Cell className="text-sm">
                  <div className="min-w-48 rounded-xl bg-background/60 p-3 ring-1 ring-border">
                    {cash ? (
                      <>
                        <span className="block font-medium">Cash at the office</span>
                        <span className="block text-xs text-muted">{t.phone ? formatPhone(t.phone) : "No phone on file"}</span>
                      </>
                    ) : (
                      <>
                        <span className="block font-medium">{t.accountHolder}</span>
                        <span className="block font-mono text-xs tracking-wide">{t.accountNumber}</span>
                        <span className="block text-xs text-muted">{t.ifsc ? `IFSC ${t.ifsc}` : ""}{t.bankName ? ` · ${t.bankName}` : ""}</span>
                      </>
                    )}
                  </div>
                </Cell>
                <Cell className="text-base font-semibold tabular-nums">{formatMoney(t.amount)}</Cell>
                <Cell>
                  {isOpen(t) ? (
                    <ReviewAssignment assignee={t.assignee} viewerId={session.userId}
                      assign={financeTaskAssignmentAction.bind(null, t.id, true)}
                      unassign={financeTaskAssignmentAction.bind(null, t.id, false)}>
                      <FinanceTaskForm withdrawalId={t.id} reference={t.reference} amount={formatMoney(t.amount)} cash={cash} />
                    </ReviewAssignment>
                  ) : (
                    <>
                      <StatusBadge tone={WITHDRAWAL_TONE[t.status]}>
                        {cash && t.status === "PAID" ? "Collected" : WITHDRAWAL_STATUS_LABEL[t.status]}
                      </StatusBadge>
                      {t.payoutReference && <span className="mt-1 block font-mono text-xs text-muted">{t.payoutReference}</span>}
                      {t.failureReason && <span className="block max-w-xs text-xs text-muted">{t.failureReason}</span>}
                      {t.closedAt && <span className="block text-xs text-muted">{formatDateTime(t.closedAt)}</span>}
                      {t.status === "FAILED" && canViewWithdrawals && (
                        <Link href={`/platform/withdrawals/${t.id}`}
                          className="mt-2 inline-flex min-h-8 items-center rounded-lg border border-rose-500/30 px-2.5 text-xs font-medium text-rose-700 transition-colors hover:bg-rose-500/10 dark:text-rose-400">Resolve</Link>
                      )}
                    </>
                  )}
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <Pagination page={tasks} basePath="/platform/finance-tasks" params={filters} />
    </div>
  );
}

/** Up to two initials from a name, for the avatar. */
function initials(name: string | null | undefined): string {
  const parts = (name ?? "").trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.charAt(0) ?? "") + (parts.length > 1 ? (parts.at(-1)?.charAt(0) ?? "") : "")).toUpperCase() || "?";
}
