import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusBadge } from "@/components/ui/status-badge";
import { agreementReviewAssignmentAction, commentOnAgreementReviewAction, completeAgreementReviewAction }
  from "@/features/admin/agreement-review-actions";
import { AgreementReviewNoteForm } from "@/features/admin/agreement-review-forms";
import { ReviewAssignment } from "@/features/admin/review-assignment";
import { AgreementText } from "@/features/agreement/agreement-text";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { AgreementReviewDetail } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Agreement review" };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default async function AgreementReviewPage({ params }: PageProps<"/admin/agreement-reviews/[id]">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AGREEMENT_REVIEW)) {
    return <EmptyState title="Not available" description="Agreement reviews need the agreement review permission." />;
  }
  const { id } = await params;
  if (!UUID.test(id)) notFound();
  let detail: AgreementReviewDetail;
  try {
    detail = await authFetch<AgreementReviewDetail>(`/api/v1/admin/agreement-reviews/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { task, wording, comments } = detail;
  const { agreement, investor } = task;
  const open = task.status === "OPEN";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="font-mono text-2xl font-semibold tracking-tight">{agreement.agreementNumber}</h1>
        <StatusBadge tone={open ? "warning" : "success"}>{open ? "To do" : "Done"}</StatusBadge>
        {agreement.acceptanceRequired && (
          <StatusBadge tone={agreement.acceptedAt ? "success" : "neutral"}>
            {agreement.acceptedAt ? `Accepted ${formatDateTime(agreement.acceptedAt)}` : "Awaiting the investor's acceptance"}
          </StatusBadge>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-6">
          <Card title="Agreement" description={`Issued ${formatDateTime(agreement.generatedAt)} for container ${agreement.containerNumber}.`}
            actions={<Link href={`/admin/agreement-reviews/${task.id}/pdf`} className="text-sm font-medium text-gold-text hover:underline">Download PDF</Link>}>
            <AgreementText title={agreement.title} body={wording} />
          </Card>
        </div>

        <div className="space-y-6">
          <Card title="Investor">
            <dl className="space-y-2 text-sm">
              <div><dt className="text-muted">Name</dt><dd>{investor.name}</dd></div>
              <div><dt className="text-muted">User ID</dt><dd className="font-mono">{investor.userCode ?? "—"}</dd></div>
              <div><dt className="text-muted">Email</dt><dd className="break-all">{investor.email}</dd></div>
            </dl>
          </Card>

          <Card title="Task" description={`Opened ${formatDateTime(task.createdAt)}.`}>
            {open ? (
              <ReviewAssignment assignee={task.assignee} viewerId={session.userId}
                assign={agreementReviewAssignmentAction.bind(null, task.id, true)}
                unassign={agreementReviewAssignmentAction.bind(null, task.id, false)}>
                <AgreementReviewNoteForm action={completeAgreementReviewAction.bind(null, task.id)}
                  label="Closing comment (optional)" submitLabel="Mark as done" variant="primary"
                  confirm="Mark this review as done? It cannot be reopened." />
              </ReviewAssignment>
            ) : (
              <p className="text-sm">
                Done by {task.completedBy?.name ?? "a reviewer"}
                {task.completedBy && <> on {formatDateTime(task.completedBy.assignedAt)}</>}.
              </p>
            )}
          </Card>

          <Card title="Comments" description={comments.length ? undefined : "No comments yet."}>
            <div className="space-y-5">
              {comments.length > 0 && (
                <ol className="space-y-4">
                  {comments.map((c) => (
                    <li key={c.id} className="border-l-2 border-border pl-3">
                      <p className="text-xs text-muted">
                        <span className="font-medium text-foreground">{c.authorId === session.userId ? "You" : c.authorName}</span>
                        {" · "}{formatDateTime(c.createdAt)}
                      </p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-sm">{c.body}</p>
                    </li>
                  ))}
                </ol>
              )}
              <AgreementReviewNoteForm action={commentOnAgreementReviewAction.bind(null, task.id)}
                label="Add a comment" submitLabel="Comment" />
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
}
