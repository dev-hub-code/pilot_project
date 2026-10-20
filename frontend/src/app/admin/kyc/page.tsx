import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { KycSubmission, KycSubmissionStatus } from "@/types/user";
import { kycNumbers } from "@/features/profile/kyc-labels";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "KYC reviews" };

const STATUSES: KycSubmissionStatus[] = ["PENDING", "APPROVED", "REJECTED"];

export default async function KycQueuePage({ searchParams }: PageProps<"/admin/kyc">) {
  const params = await searchParams;
  const status = STATUSES.find((s) => s === params.status) ?? "PENDING";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const session = await requireStaff();
  const queue = await authFetch<PageResponse<KycSubmission>>(`/api/v1/admin/kyc?status=${status}&page=${page}&size=25`);

  return (
    <div className="space-y-6">
      <PageHeader title="KYC reviews" description="Oldest submissions first. Assign a review to yourself before deciding it." />
      <nav aria-label="Filter by status" className="flex gap-2 text-sm">
        {STATUSES.map((s) => (
          <Link key={s} href={`/admin/kyc?status=${s}`} aria-current={s === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${s === status ? "border-brand text-brand" : "border-border text-muted"}`}>
            {humanize(s)}
          </Link>
        ))}
      </nav>
      {queue.content.length === 0 ? (
        <EmptyState title="Nothing here" description={status === "PENDING" ? "The review queue is empty." : undefined} />
      ) : (
        <DataTable columns={["Legal name", "Document", "Nationality", "Submitted", "Assigned to", "Status"]}>
          {queue.content.map((s) => (
            <tr key={s.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/kyc/${s.id}`} className="card-link font-medium text-brand hover:underline">
                  {s.legalFirstName} {s.legalLastName}
                </Link>
              </Cell>
              <Cell className="font-mono text-xs">{kycNumbers(s)}</Cell>
              <Cell>{s.nationality}</Cell>
              <Cell className="text-muted">{formatDateTime(s.submittedAt)}</Cell>
              <Cell className={s.assignee ? undefined : "text-muted"}>
                {s.assignee ? (s.assignee.id === session.userId ? "You" : s.assignee.name) : "Nobody yet"}
              </Cell>
              <Cell><StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={queue} basePath="/admin/kyc" params={{ status }} />
    </div>
  );
}
