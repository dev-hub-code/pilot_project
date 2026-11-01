import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
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

const STATUSES: KycSubmissionStatus[] = ["PENDING", "APPROVED", "REJECTED"];

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
      <p className="text-sm text-muted">Oldest submissions first. Assign a review to yourself before deciding it.</p>
      <nav aria-label="Filter by status" className="flex gap-2 text-sm">
        {STATUSES.map((s) => (
          <Link key={s} href={`/admin/verification?status=${s}`} aria-current={s === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${s === status ? "border-brand text-brand" : "border-border text-muted"}`}>
            {humanize(s)}
          </Link>
        ))}
      </nav>
      {queue.content.length === 0 ? (
        <EmptyState title="Nothing here" description={status === "PENDING" ? "The review queue is empty." : undefined} />
      ) : (
        <DataTable columns={["Legal name", "Document", "Nationality", "Submitted", "Assigned to", "Status", ...(canVerifyBanks ? ["Bank account"] : [])]}>
          {queue.content.map((s) => (
            <tr key={s.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/kyc/${s.id}`} className="card-link font-medium text-brand hover:underline">
                  {s.legalFirstName} {s.legalLastName}
                </Link>
              </Cell>
              <Cell className="font-mono text-sm font-bold">{kycNumbers(s)}</Cell>
              <Cell>{s.nationality}</Cell>
              <Cell className="text-muted">{formatDateTime(s.submittedAt)}</Cell>
              <Cell className={s.assignee ? undefined : "text-muted"}>
                {s.assignee ? (s.assignee.id === session.userId ? "You" : s.assignee.name) : "Nobody yet"}
              </Cell>
              <Cell><StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge></Cell>
              {canVerifyBanks && (
                <Cell>{bankToVerify.has(s.userId) ? <StatusBadge tone="warning">To verify</StatusBadge> : <span className="text-muted">—</span>}</Cell>
              )}
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={queue} basePath="/admin/verification" params={{ status }} />
    </div>
  );
}
