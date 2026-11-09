import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminBankAccount, KycSubmission, KycSubmissionStatus } from "@/types/user";
import { kycNumbers } from "@/features/profile/kyc-labels";
import { formatDateTime, humanize } from "@/utils/format";
import { CheckCircle2, Hourglass, Landmark, type LucideIcon, UserCheck, UserRound, XCircle } from "lucide-react";

const STATUSES = ["PENDING", "APPROVED", "REJECTED"] as const satisfies readonly KycSubmissionStatus[];
const STATUS_TAB: Record<(typeof STATUSES)[number], { label: string; icon: LucideIcon }> = {
  PENDING: { label: "To review", icon: Hourglass },
  APPROVED: { label: "Approved", icon: CheckCircle2 },
  REJECTED: { label: "Rejected", icon: XCircle },
};

/**
 * Identity reviews, the Verification page's list. Bank accounts are decided on each review, so for staff who verify
 * them it also marks investors with one waiting, including accounts added after the identity was approved.
 */
export async function KycQueue({ statusParam, page }: { statusParam: unknown; page: number }) {
  const status = STATUSES.find((s) => s === statusParam) ?? "PENDING";
  const session = await requireStaff();
  const canVerifyBanks = hasPermission(session.permissions, Permission.BANK_ACCOUNT_VERIFY);
  const [queue, pendingBanks] = await Promise.all([
    authFetch<PageResponse<KycSubmission>>(`/api/v1/admin/kyc?status=${status}&page=${page}&size=25`),
    canVerifyBanks ? authFetch<PageResponse<AdminBankAccount>>("/api/v1/admin/bank-accounts?size=100") : null,
  ]);
  const bankToVerify = new Set(pendingBanks?.content.map((b) => b.userId));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter by status" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {STATUSES.map((s) => {
            const { label, icon: Icon } = STATUS_TAB[s];
            return (
              <Link key={s} href={`/platform/verification?status=${s}`} aria-current={s === status ? "page" : undefined}
                className={`inline-flex items-center gap-1.5 rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${s === status
                  ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                  : "text-muted hover:bg-background hover:text-foreground"}`}>
                <Icon aria-hidden="true" className="size-4" /> {label}
              </Link>
            );
          })}
        </nav>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-muted">
            {queue.totalElements.toLocaleString("en-IN")} {queue.totalElements === 1 ? "submission" : "submissions"}
          </span>
          {pendingBanks && pendingBanks.totalElements > 0 && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-3 py-1 text-xs font-medium text-amber-700 ring-1 ring-amber-500/30 dark:text-amber-300">
              <Landmark aria-hidden="true" className="size-3.5" />
              {pendingBanks.totalElements.toLocaleString("en-IN")} bank {pendingBanks.totalElements === 1 ? "account" : "accounts"} to verify
            </span>
          )}
        </div>
      </div>
      <p className="flex items-start gap-2 rounded-xl bg-background/60 px-4 py-3 text-sm text-muted ring-1 ring-border">
        <UserCheck aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-gold-text" />
        Oldest submissions first. Assign a review to yourself before deciding it.
      </p>
      {queue.content.length === 0 ? (
        <EmptyState title="Nothing here" description={status === "PENDING" ? "The review queue is empty." : undefined} />
      ) : (
        // A list rather than a table: each review is a row on wide screens and a card on narrow ones, with no sideways scrolling.
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          {queue.content.map((s) => (
            <li key={s.id}
              className="group relative grid gap-3 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
              <div className="flex min-w-0 items-center gap-3">
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gold/10 text-xs font-semibold text-gold-text transition-colors group-hover:bg-gold group-hover:text-on-gold">
                  {(s.legalFirstName.charAt(0) + s.legalLastName.charAt(0)).toUpperCase() || "?"}
                </span>
                <div className="min-w-0">
                  <Link href={`/platform/kyc/${s.id}`} className="card-link block truncate font-medium text-foreground group-hover:text-gold-text">
                    {s.legalFirstName} {s.legalLastName}
                  </Link>
                  <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                    <span className="rounded-md bg-background px-1.5 py-0.5 font-mono font-semibold tracking-wide text-foreground ring-1 ring-border break-all">{kycNumbers(s)}</span>
                    <span>{s.nationality}</span>
                    <span>Submitted {formatDateTime(s.submittedAt)}</span>
                  </p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2 pl-[3.25rem] sm:justify-end sm:pl-0">
                {s.assignee ? (
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${s.assignee.id === session.userId
                    ? "bg-gold/15 text-gold-text" : "bg-background text-foreground ring-1 ring-border"}`}>
                    <UserRound aria-hidden="true" className="size-3" />
                    {s.assignee.id === session.userId ? "You" : s.assignee.name}
                  </span>
                ) : (
                  <span className="inline-flex rounded-full border border-dashed border-border px-2.5 py-0.5 text-xs text-muted">Nobody yet</span>
                )}
                <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
                {canVerifyBanks && bankToVerify.has(s.userId) && (
                  <StatusBadge tone="warning"><Landmark aria-hidden="true" className="size-3" />Bank to verify</StatusBadge>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={queue} basePath="/platform/verification" params={{ status }} />
    </div>
  );
}
