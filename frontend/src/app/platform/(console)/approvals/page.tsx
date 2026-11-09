import type { Metadata } from "next";
import Link from "next/link";
import {
  BookOpen, CheckCircle2, Clock, Hash, Hourglass, type LucideIcon, RotateCcw, ShieldCheck, UserCheck, Users, XCircle,
} from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { approveRequestAction, rejectRequestAction } from "@/features/admin/portal-actions";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Approval } from "@/types/admin-portal";
import type { PageResponse } from "@/types/api";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Approvals" };

const VIEWS = ["PENDING", "APPROVED", "REJECTED"] as const;

const VIEW_ICON: Record<(typeof VIEWS)[number], LucideIcon> = { PENDING: Hourglass, APPROVED: CheckCircle2, REJECTED: XCircle };

const TYPE_ICON: Record<Approval["type"], LucideIcon> = { LEDGER_ADJUSTMENT: BookOpen, PAYMENT_REFUND: RotateCcw };

const STATUS_STYLE: Record<Approval["status"], { chip: string; edge: string }> = {
  PENDING: { chip: "bg-amber-500/10 text-amber-700 dark:text-amber-300", edge: "border-l-amber-500" },
  APPROVED: { chip: "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300", edge: "border-l-emerald-500" },
  REJECTED: { chip: "bg-red-500/10 text-red-700 dark:text-red-300", edge: "border-l-red-500" },
};

const RULES = [
  { icon: Users, text: "Two people: whoever asks can't decide." },
  { icon: ShieldCheck, text: "Nothing runs until someone approves it." },
  { icon: CheckCircle2, text: "Approving runs it straight away." },
  { icon: XCircle, text: "Rejecting needs a reason, kept with the request." },
];

/** Sensitive operations waiting for a second person. The API refuses a maker deciding their own request. */
export default async function ApprovalsPage({ searchParams }: PageProps<"/platform/approvals">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.APPROVAL_VIEW) && !can(Permission.APPROVAL_DECIDE)) {
    return <EmptyState title="Not available" description="Approvals need the approval permission." />;
  }
  const params = await searchParams;
  const view = VIEWS.find((v) => v === params.status) ?? "PENDING";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const requests = await authFetch<PageResponse<Approval>>(`/api/v1/admin/approvals?status=${view}&page=${page}&size=25`);
  const canDecide = can(Permission.APPROVAL_DECIDE);

  return (
    <div className="space-y-6">
      <PageHeader title="Approvals" description="Sensitive operations need a second person, and run only once approved." />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_280px]">
        <div className="min-w-0 space-y-4">
          <nav aria-label="Status" className="inline-flex gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
            {VIEWS.map((v) => {
              const Icon = VIEW_ICON[v];
              return (
                <Link key={v} href={`/platform/approvals?status=${v}`} aria-current={v === view ? "page" : undefined}
                  className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${v === view
                    ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                    : "text-muted hover:bg-background hover:text-foreground"}`}>
                  <Icon aria-hidden="true" className="size-4" /> {humanize(v)}
                  {v === view && <span className="text-xs tabular-nums opacity-70">{requests.totalElements}</span>}
                </Link>
              );
            })}
          </nav>

          {requests.content.length === 0 ? (
            <EmptyState title={`No ${view.toLowerCase()} requests`}
              description={view === "PENDING" ? "Nothing is waiting for a second person." : undefined} />
          ) : (
            <ul className="space-y-4">
              {requests.content.map((r) => {
                const mine = r.createdBy === session.userId;
                const Icon = TYPE_ICON[r.type] ?? ShieldCheck;
                const style = STATUS_STYLE[r.status];
                return (
                  <li key={r.id} className={`overflow-hidden rounded-2xl border border-l-4 border-border bg-surface shadow-sm ${style.edge}`}>
                    <div className="flex flex-wrap items-start gap-3 p-5">
                      <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                        <Icon className="size-5" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="font-semibold tracking-tight">{humanize(r.type)}</h2>
                          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${style.chip}`}>{humanize(r.status)}</span>
                          {mine && <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[11px] font-medium text-gold-text">Your request</span>}
                        </div>
                        <p className="mt-1 text-sm font-medium break-words">{r.summary}</p>
                        <p className="mt-2 rounded-xl bg-background/60 px-3 py-2 text-sm break-words text-muted ring-1 ring-border">
                          <span className="font-medium text-foreground">Why: </span>{r.reason}
                        </p>
                        <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted">
                          <li className="inline-flex items-center gap-1"><Clock aria-hidden="true" className="size-3.5" /> Requested {formatDateTime(r.createdAt)}</li>
                          {r.decidedAt && (
                            <li className="inline-flex items-center gap-1"><UserCheck aria-hidden="true" className="size-3.5" /> Decided {formatDateTime(r.decidedAt)}</li>
                          )}
                          {r.transactionReference && (
                            <li className="inline-flex items-center gap-1"><Hash aria-hidden="true" className="size-3.5" />
                              <span className="font-mono">{r.transactionReference}</span></li>
                          )}
                        </ul>
                        {r.decisionReason && (
                          <p className="mt-3 rounded-xl bg-red-500/5 px-3 py-2 text-sm break-words text-red-700 ring-1 ring-red-500/20 dark:text-red-300">
                            <span className="font-medium">Rejected: </span>{r.decisionReason}
                          </p>
                        )}
                      </div>
                    </div>
                    {r.status === "PENDING" && canDecide && (mine ? (
                      <p className="flex items-center gap-2 border-t border-border bg-background/40 px-5 py-3 text-sm text-muted">
                        <Users aria-hidden="true" className="size-4 shrink-0" /> You made this request, so someone else must decide it.
                      </p>
                    ) : (
                      <div className="grid gap-4 border-t border-border bg-background/40 p-5 md:grid-cols-2">
                        <div className="space-y-2">
                          <p className="text-xs font-medium tracking-wide text-muted uppercase">Approve</p>
                          <ConfirmForm action={approveRequestAction.bind(null, r.id)} submitLabel="Approve and run"
                            confirm={`Approve and run now? ${r.summary}`} />
                        </div>
                        <div className="space-y-2">
                          <p className="text-xs font-medium tracking-wide text-muted uppercase">Reject</p>
                          <ReasonForm action={rejectRequestAction.bind(null, r.id)} label="Reason" submitLabel="Reject" />
                        </div>
                      </div>
                    ))}
                  </li>
                );
              })}
            </ul>
          )}
          <Pagination page={requests} basePath="/platform/approvals" params={{ status: view }} />
        </div>

        <aside className="relative isolate h-fit overflow-hidden rounded-2xl bg-ink p-5 text-on-ink shadow-xl shadow-ink/20 lg:sticky lg:top-6">
          <div aria-hidden="true" className="absolute -top-16 -right-16 -z-10 size-40 rounded-full bg-gold/25 blur-3xl" />
          <h2 className="flex items-center gap-2 font-semibold"><ShieldCheck aria-hidden="true" className="size-5 text-gold-on-ink" /> How approvals work</h2>
          <ul className="mt-4 space-y-3 text-sm">
            {RULES.map(({ icon: Icon, text }) => (
              <li key={text} className="flex gap-3">
                <span aria-hidden="true" className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-white/10 text-gold-on-ink">
                  <Icon className="size-4" />
                </span>
                <span className="pt-1 text-on-ink/80">{text}</span>
              </li>
            ))}
          </ul>
          <p className="mt-4 border-t border-white/10 pt-3 text-xs text-on-ink/60">Covers ledger adjustments and payment refunds.</p>
        </aside>
      </div>
    </div>
  );
}
