import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { ConfirmForm, ReasonForm } from "@/features/admin/decision-forms";
import { approveRequestAction, rejectRequestAction } from "@/features/admin/portal-actions";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Approval } from "@/types/admin-portal";
import type { PageResponse } from "@/types/api";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Approvals" };

const VIEWS = ["PENDING", "APPROVED", "REJECTED"] as const;

/** Sensitive operations waiting for a second person. The API refuses a maker deciding their own request. */
export default async function ApprovalsPage({ searchParams }: PageProps<"/admin/approvals">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.APPROVAL_VIEW) && !can(Permission.APPROVAL_DECIDE)) {
    return <EmptyState title="Not available" description="Approvals need the approval permission." />;
  }
  const params = await searchParams;
  const view = VIEWS.find((v) => v === params.status) ?? "PENDING";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const requests = await authFetch<PageResponse<Approval>>(`/api/v1/admin/approvals?status=${view}&page=${page}&size=25`);

  return (
    <div className="space-y-6">
      <PageHeader title="Approvals" description="Sensitive operations need a second person, and run only once approved." />
      <nav aria-label="Status" className="flex gap-6 border-b border-border text-sm">
        {VIEWS.map((v) => (
          <Link key={v} href={`/admin/approvals?status=${v}`} aria-current={v === view ? "page" : undefined}
            className={`-mb-px border-b-2 py-2 ${v === view ? "border-gold font-medium" : "border-transparent text-muted hover:text-foreground"}`}>
            {humanize(v)}
          </Link>
        ))}
      </nav>
      {requests.content.length === 0 ? <EmptyState title={`No ${view.toLowerCase()} requests`} /> : (
        <ul className="space-y-4">
          {requests.content.map((r) => {
            const mine = r.createdBy === session.userId;
            return (
              <li key={r.id}>
                <Card title={humanize(r.type)} description={r.summary}
                  actions={<StatusBadge tone={toneFor(r.status)}>{humanize(r.status)}</StatusBadge>}>
                  <p className="text-sm">{r.reason}</p>
                  <p className="mt-1 text-xs text-muted">Requested {formatDateTime(r.createdAt)}</p>
                  {r.decisionReason && <p className="mt-2 text-sm text-muted">Rejected: {r.decisionReason}</p>}
                  {r.status === "PENDING" && can(Permission.APPROVAL_DECIDE) && (mine
                    ? <p className="mt-3 text-sm text-muted">You made this request, so someone else must decide it.</p>
                    : (
                      <div className="mt-4 grid gap-4 border-t border-border pt-4 md:grid-cols-2">
                        <ConfirmForm action={approveRequestAction.bind(null, r.id)} submitLabel="Approve and run"
                          confirm={`Approve and run now? ${r.summary}`} />
                        <ReasonForm action={rejectRequestAction.bind(null, r.id)} label="Reason" submitLabel="Reject" />
                      </div>
                    ))}
                </Card>
              </li>
            );
          })}
        </ul>
      )}
      <Pagination page={requests} basePath="/admin/approvals" params={{ status: view }} />
    </div>
  );
}
