import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { ProgressBar } from "@/components/ui/progress-bar";
import { StatusBadge } from "@/components/ui/status-badge";
import { runPayoutsAction } from "@/features/admin/ledger-actions";
import { ActionButton } from "@/features/orders/action-button";
import { LEASE_STATUS_LABEL, LEASE_TONE, leaseNote, leasePercent } from "@/features/earnings/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { DuePayouts, LeaseRent, LeaseRentStatus } from "@/types/earning";
import { formatDate } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { creditLabel, getRentCredit } from "@/lib/server/rent-credit";
import { CheckCircle2, Clock, UserRound } from "lucide-react";

export const metadata: Metadata = { title: "Returns" };

const VIEWS: readonly { status: LeaseRentStatus; label: string }[] = [
  { status: "RUNNING", label: "Earning now" },
  { status: "COMPLETED", label: "Completed" },
];

export default async function PayoutsPage({ searchParams }: PageProps<"/admin/payouts">) {
  const denied = await deniedUnlessInMenu("/admin/payouts");
  if (denied) return denied;
  const session = await requireStaff();
  const params = await searchParams;
  const view = VIEWS.find((v) => v.status === params.status) ?? VIEWS[0]!;
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25", status: view.status });
  const [leases, due, rentCredit] = await Promise.all([
    authFetch<PageResponse<LeaseRent>>(`/api/v1/admin/payouts?${query}`),
    authFetch<DuePayouts[]>("/api/v1/admin/payouts/due"),
    getRentCredit(),
  ]);
  const credit = creditLabel(rentCredit);
  const dueCount = due.reduce((n, d) => n + d.count, 0);
  // Capital back applies only to investments from before plans became returns-only: the column shows only then.
  const showCapital = leases.content.some((l) => Number(l.capitalTotal.amount) > 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Returns"
        description={`Every investment's returns are credited to its investor's wallet ${credit} throughout its term, from the full hour after the investor accepts its agreement: the monthly returns × the term months elapsed, so the whole term pays exactly monthly returns × tenure. Referral commission on it is credited with it. Investments made before plans became returns-only also get part of the amount back, the same way.`} />

      <section aria-labelledby="due-title"
        className={`flex flex-wrap items-center gap-4 rounded-2xl border p-5 shadow-sm ${dueCount === 0
          ? "border-emerald-200 bg-emerald-50/70 dark:border-emerald-500/30 dark:bg-emerald-500/[0.07]"
          : "border-amber-300/70 bg-amber-50/70 dark:border-amber-500/30 dark:bg-amber-500/[0.07]"}`}>
        <span aria-hidden="true" className={`flex size-11 shrink-0 items-center justify-center rounded-xl ${dueCount === 0
          ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"
          : "bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300"}`}>
          {dueCount === 0 ? <CheckCircle2 className="size-5" /> : <Clock className="size-5" />}
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="due-title" className="font-semibold">
            {dueCount === 0 ? "All caught up" : "Returns not yet credited"}
          </h2>
          <p className="text-sm text-muted">
            {dueCount === 0
              ? "Nothing is waiting: all returns due so far have been credited."
              : due.map((d) => `${d.count} term(s) behind · ${formatMoney(d.total)}`).join(" · ")}
            {" "}Each interval that has ended is credited automatically within a minute or so.
          </p>
        </div>
        {dueCount > 0 && hasPermission(session.permissions, Permission.PAYOUT_PROCESS) && (
          <ActionButton action={runPayoutsAction} label="Credit returns now" pendingLabel="Crediting…"
            confirm="Credit all returns due so far to investors' wallets now?" />
        )}
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter" className="inline-flex gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {VIEWS.map((v) => (
            <Link key={v.status} href={`/admin/payouts?status=${v.status}`} aria-current={v === view ? "page" : undefined}
              className={`rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${v === view
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              {v.label}
            </Link>
          ))}
        </nav>
        {leases.totalElements > 0 && (
          <p className="text-sm text-muted">{leases.totalElements.toLocaleString("en-IN")} {leases.totalElements === 1 ? "term" : "terms"}</p>
        )}
      </div>

      {leases.content.length === 0 ? (
        <EmptyState title={view.status === "RUNNING" ? "No terms earning returns" : "No completed terms"} />
      ) : (
        <DataTable columns={["Plan", "Term", "Monthly returns", "Returns credited", ...(showCapital ? ["Capital back"] : []), "Progress", "Status", "Investor"]}>
          {leases.content.map((l) => (
            <tr key={l.holdingId} className="align-top">
              <Cell>
                <span className="block font-medium">{l.productTitle ?? l.productCode}</span>
                {l.productTitle && <span className="block font-mono text-xs text-muted">{l.productCode}</span>}
              </Cell>
              <Cell className="whitespace-nowrap text-xs text-muted">
                {formatDate(l.startsAt)} – {formatDate(l.endsAt)}
                <span className="block">{l.tenureMonths} months</span>
              </Cell>
              <Cell className="tabular-nums">{formatMoney(l.monthlyRent)}</Cell>
              <Cell className="tabular-nums">
                <span className="font-medium">{formatMoney(l.rentCredited)}</span>
                <span className="block text-xs text-muted">of {formatMoney(l.totalRent)}</span>
              </Cell>
              {showCapital && (
                <Cell className="tabular-nums">
                  {formatMoney(l.capitalCredited)}
                  <span className="block text-xs text-muted">of {formatMoney(l.capitalTotal)}</span>
                </Cell>
              )}
              <Cell className="min-w-40">
                <span className="mb-1.5 flex justify-end">
                  <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[11px] font-semibold text-gold-text tabular-nums">{Math.round(leasePercent(l))}%</span>
                </span>
                <ProgressBar percent={leasePercent(l)} label={`${l.productCode} returns credited`} />
                <span className="mt-1 block text-xs text-muted">
                  {leaseNote(l)}
                </span>
              </Cell>
              <Cell><StatusBadge tone={LEASE_TONE[l.status]}>{LEASE_STATUS_LABEL[l.status]}</StatusBadge></Cell>
              <Cell>
                {l.userId && (
                  <Link href={`/admin/users/${l.userId}`}
                    className="inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-border px-2.5 text-xs font-medium transition-colors hover:border-gold hover:text-gold-text">
                    <UserRound aria-hidden="true" className="size-3.5" /> View
                  </Link>
                )}
              </Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={leases} basePath="/admin/payouts" params={{ status: view.status }} />
    </div>
  );
}
