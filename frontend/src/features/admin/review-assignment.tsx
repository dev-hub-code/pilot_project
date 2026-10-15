import { Notice } from "@/components/ui/notice";
import { ConfirmForm } from "@/features/admin/decision-forms";
import type { FormState } from "@/validators/form-state";
import type { Assignee } from "@/types/user";
import { formatDateTime } from "@/utils/format";

/**
 * A review or finance task waiting for someone is assigned by a staff member to themselves, who only then sees the
 * forms to act on it ({@code children}). The API enforces this; the page only mirrors it.
 */
export function ReviewAssignment({ assignee, viewerId, assign, unassign, children }: {
  assignee: Assignee | null;
  viewerId: string;
  assign: () => Promise<FormState>;
  unassign: () => Promise<FormState>;
  children: React.ReactNode;
}) {
  if (!assignee) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-muted">Not assigned yet. Assign it to yourself to work on it.</p>
        <ConfirmForm action={assign} submitLabel="Assign to me" />
      </div>
    );
  }
  if (assignee.id !== viewerId) {
    return (
      <Notice tone="info">
        Assigned to {assignee.name} since {formatDateTime(assignee.assignedAt)}. Only they can work on it.
      </Notice>
    );
  }
  return (
    <div className="space-y-6">
      <p className="text-sm text-muted">Assigned to you since {formatDateTime(assignee.assignedAt)}.</p>
      {children}
      <ConfirmForm action={unassign} submitLabel="Return to queue" variant="secondary"
        confirm="Return this to the queue, unfinished, for someone else to take?" />
    </div>
  );
}
