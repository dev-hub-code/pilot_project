import type { Metadata } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
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

export const metadata: Metadata = { title: "Finance tasks" };

const VIEWS = [
  { key: "OPEN", label: "To do" },
  { key: "DONE", label: "Done" },
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
  return `/admin/finance-tasks?${query}`;
}

export default async function FinanceTasksPage({ searchParams }: PageProps<"/admin/finance-tasks">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.WITHDRAWAL_PROCESS) && !can(Permission.FINANCE_TASK_PROCESS)) {
    return <EmptyState title="Not available" description="Finance tasks need the finance tasks permission." />;
  }
  // Finance pays tasks but does not see withdrawals or their batches; those links are for admins.
  const canViewWithdrawals = can(Permission.WITHDRAWAL_VIEW);
  const params = await searchParams;
  const filters: Filters = {
    view: params.view === "DONE" ? "DONE" : "OPEN",
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
  const chip = (active: boolean) =>
    `rounded-full border px-3 py-1 ${active ? "border-foreground text-foreground" : "border-border text-muted"}`;

  return (
    <div className="space-y-6">
      <PageHeader title="Finance tasks"
        description="Every withdrawal a withdrawal run makes is a task here. Assign one to yourself, pay it into the investor's bank account (or hand it over in cash at the office), then mark it paid, or failed with the reason. Paid amounts move to the investor's total withdrawn; failed ones are held and open a support ticket to resolve." />

      <form method="get" action="/admin/finance-tasks" role="search" className="flex flex-wrap items-end gap-3">
        <input type="hidden" name="view" value={filters.view} />
        {filters.method && <input type="hidden" name="method" value={filters.method} />}
        {filters.kind && <input type="hidden" name="kind" value={filters.kind} />}
        <div className="w-full max-w-md">
          <TextField label="Search" name="q" type="search" defaultValue={filters.q}
            placeholder="User code (SL03501), name, email, WD- or WB- reference" />
        </div>
        <Button type="submit">Search</Button>
        {filters.q && <Link href={href(filters, { q: undefined })} className="text-sm text-muted hover:underline">Clear</Link>}
      </form>

      <nav aria-label="Filter" className="flex flex-wrap items-center gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={href(filters, { view: v.key })} aria-current={v.key === filters.view ? "page" : undefined}
            className={chip(v.key === filters.view)}>{v.label}</Link>
        ))}
        <span className="mx-2 h-5 w-px bg-border" aria-hidden="true" />
        <Link href={href(filters, { method: undefined })} className={chip(!filters.method)}>Bank and cash</Link>
        {METHODS.map((m) => (
          <Link key={m} href={href(filters, { method: m })} className={chip(filters.method === m)}>{PAYOUT_METHOD_LABEL[m]}</Link>
        ))}
        <span className="mx-2 h-5 w-px bg-border" aria-hidden="true" />
        <Link href={href(filters, { kind: undefined })} className={chip(!filters.kind)}>Rent and referral</Link>
        {KINDS.map((k) => (
          <Link key={k} href={href(filters, { kind: k })} className={chip(filters.kind === k)}>{WITHDRAWAL_KIND_LABEL[k]}</Link>
        ))}
      </nav>

      {open && tasks.content.some((t) => t.payoutMethod === "BANK_TRANSFER") && (
        <Notice>Full account numbers are shown so you can pay; every time this list is opened is recorded.</Notice>
      )}

      {tasks.content.length === 0 ? (
        filters.q ? (
          <EmptyState title="No tasks match" description={`Nothing ${open ? "to do" : "done"} matches “${filters.q}” with these filters.`} />
        ) : (
          <EmptyState title={open ? "Nothing to do" : "No tasks done yet"}
            description={open ? "Tasks appear here when an admin generates rent or referral withdrawals." : undefined} />
        )
      ) : (
        <DataTable columns={["Task", "Investor", "Pay to", "Amount", open ? "Update" : "Outcome"]}>
          {tasks.content.map((t) => {
            const cash = t.payoutMethod === "OFFICE_CASH";
            return (
              <tr key={t.id} className="align-top">
                <Cell>
                  {canViewWithdrawals
                    ? <Link href={`/admin/withdrawals/${t.id}`} className="font-mono text-gold-text hover:underline">{t.reference}</Link>
                    : <span className="font-mono">{t.reference}</span>}
                  <span className="block text-xs text-muted">
                    {WITHDRAWAL_KIND_LABEL[t.kind]} · {PAYOUT_METHOD_LABEL[t.payoutMethod]}
                  </span>
                  {canViewWithdrawals ? (
                    <Link href={`/admin/withdrawals/batches/${t.batchId}`} className="block font-mono text-xs text-muted hover:underline">
                      {t.batchReference}
                    </Link>
                  ) : <span className="block font-mono text-xs text-muted">{t.batchReference}</span>}
                  <span className="block text-xs text-muted">{formatDateTime(t.createdAt)}</span>
                </Cell>
                <Cell>
                  <Link href={`/admin/users/${t.userId}`} className="font-mono text-xs text-gold-text hover:underline">{t.userCode ?? "Investor"}</Link>
                  <span className="block">{t.name}</span>
                </Cell>
                <Cell className="text-sm">
                  {cash ? (
                    <>
                      Cash
                      <span className="block text-xs text-muted">{t.phone ?? "No phone on file"}</span>
                    </>
                  ) : (
                    <>
                      {t.accountHolder}
                      <span className="block font-mono text-xs">{t.accountNumber}</span>
                      <span className="block text-xs text-muted">{t.ifsc ? `IFSC ${t.ifsc}` : ""}{t.bankName ? ` · ${t.bankName}` : ""}</span>
                    </>
                  )}
                </Cell>
                <Cell className="tabular-nums font-medium">{formatMoney(t.amount)}</Cell>
                <Cell>
                  {open ? (
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
                      {t.payoutReference && <span className="block text-xs text-muted">{t.payoutReference}</span>}
                      {t.failureReason && <span className="block max-w-xs text-xs text-muted">{t.failureReason}</span>}
                      {t.closedAt && <span className="block text-xs text-muted">{formatDateTime(t.closedAt)}</span>}
                      {t.status === "FAILED" && canViewWithdrawals && (
                        <Link href={`/admin/withdrawals/${t.id}`} className="block text-xs text-gold-text hover:underline">Resolve</Link>
                      )}
                    </>
                  )}
                </Cell>
              </tr>
            );
          })}
        </DataTable>
      )}
      <Pagination page={tasks} basePath="/admin/finance-tasks" params={filters} />
    </div>
  );
}
