import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { staffReplyAction, ticketSettingAction, ticketTakeAction, ticketTeamAction } from "@/features/admin/support-actions";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { SettingForm } from "@/features/admin/support-forms";
import { Notice } from "@/components/ui/notice";
import { ReplyForm } from "@/features/support/forms";
import { CATEGORY_LABEL, PRIORITY_TONE, STAFF_STATUS_LABEL, TICKET_TONE } from "@/features/support/labels";
import { Thread } from "@/features/support/thread";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Agent, TicketDetail } from "@/types/support";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Support request" };

const RELATED_HREF = { ORDER: "/admin/orders/", WITHDRAWAL: "/admin/withdrawals/", HOLDING: null } as const;

export default async function AdminTicketPage({ params }: PageProps<"/admin/support/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const session = await requireStaff();
  let detail: TicketDetail;
  try {
    detail = await authFetch<TicketDetail>(`/api/v1/admin/support/tickets/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const t = detail.ticket;
  const canManage = hasPermission(session.permissions, Permission.SUPPORT_TICKET_MANAGE);
  // Finance sees only tickets handed to it, and works on one once it is assigned to them.
  const financeOnly = !canManage && hasPermission(session.permissions, Permission.FINANCE_TICKET_HANDLE);
  const mine = t.assigneeId === session.userId;
  const canWork = canManage || (financeOnly && mine);
  const agents = canManage ? await authFetch<Agent[]>("/api/v1/admin/support/agents") : [];
  // A link only where the viewer may open the record: withdrawals are for admins.
  const canOpenRelated = t.relatedType === "WITHDRAWAL" ? hasPermission(session.permissions, Permission.WITHDRAWAL_VIEW)
    : t.relatedType === "ORDER" ? hasPermission(session.permissions, Permission.ORDER_VIEW) : true;
  const relatedHref = !financeOnly && canOpenRelated && t.relatedType && RELATED_HREF[t.relatedType]
    ? `${RELATED_HREF[t.relatedType]}${t.relatedId}` : null;
  const open = t.status !== "CLOSED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href="/admin/support" className="text-sm text-muted hover:underline">Support</Link>
          <h1 className="text-3xl font-semibold tracking-tight">{t.subject}</h1>
          <p className="text-sm text-muted">
            <span className="font-mono">{t.reference}</span> · {CATEGORY_LABEL[t.category]} · from{" "}
            <Link href={`/admin/users/${t.requesterId}`} className="text-gold-text hover:underline">{t.requesterName}</Link>
            {t.team === "FINANCE" && <> · with the finance team</>}
            {t.relatedLabel && <> · {relatedHref ? <Link href={relatedHref} className="text-gold-text hover:underline">{t.relatedLabel}</Link> : t.relatedLabel}</>}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge tone={PRIORITY_TONE[t.priority]}>{humanize(t.priority)}</StatusBadge>
          <StatusBadge tone={TICKET_TONE[t.status]}>{STAFF_STATUS_LABEL[t.status]}</StatusBadge>
        </div>
      </div>
      <p className="text-sm text-muted">
        {t.firstRespondedAt ? `First reply ${formatDateTime(t.firstRespondedAt)}` : `First reply due ${formatDateTime(t.firstResponseDueAt)}`}
        {t.overdue && <span className="ml-2 font-medium text-rose-600 dark:text-rose-400">Overdue</span>}
      </p>

      <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
        <div className="space-y-6">
          <Thread messages={detail.messages} events={detail.events} viewer="staff" attachmentHref={(a) => `/admin/support/${t.id}/attachments/${a}`} />
          {canWork && open && (
            <Card title="Reply">
              <ReplyForm action={staffReplyAction.bind(null, t.id)} submitLabel="Send"
                extra={
                  <label className="flex items-center gap-3 text-sm">
                    <input type="checkbox" name="internal" className="size-4" />
                    Internal note: visible to employees only, the customer is not notified
                  </label>
                } />
            </Card>
          )}
        </div>
        {financeOnly && open && (
          <div className="space-y-6">
            <Card title="Assignee">
              {!t.assigneeId ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted">Not assigned yet. Assign it to yourself to work on it.</p>
                  <ConfirmForm action={ticketTakeAction.bind(null, t.id, session.userId)} submitLabel="Assign to me" />
                </div>
              ) : mine ? (
                <div className="space-y-3">
                  <p className="text-sm text-muted">Assigned to you.</p>
                  <ConfirmForm action={ticketTakeAction.bind(null, t.id, null)} submitLabel="Return to queue" variant="secondary"
                    confirm="Return this ticket to the finance queue for someone else to take?" />
                </div>
              ) : (
                <Notice tone="info">Assigned to {t.assigneeName}. Only they can work on it.</Notice>
              )}
            </Card>
            {mine && (
              <>
                <Card title="Priority" description="Sets the first-response target.">
                  <SettingForm action={ticketSettingAction.bind(null, t.id, "priority")} label="Priority" defaultValue={t.priority}
                    options={["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => ({ value: p, label: humanize(p) }))} submitLabel="Set priority" />
                </Card>
                <Card title="Status" description="The customer is notified. They can reopen a resolved request for 24 hours, after which it closes automatically.">
                  <SettingForm action={ticketSettingAction.bind(null, t.id, "status")} label="Mark as"
                    defaultValue={t.status === "RESOLVED" ? "CLOSED" : "RESOLVED"}
                    options={[{ value: "RESOLVED", label: "Resolved" }, { value: "CLOSED", label: "Closed" }]} submitLabel="Update" />
                </Card>
                <Card title="Team" description="Done with the finance side? Hand it back to support, unassigned.">
                  <ConfirmForm action={ticketTeamAction.bind(null, t.id, "SUPPORT")} submitLabel="Hand back to support"
                    variant="secondary" confirm="Hand this ticket back to the support team?" />
                </Card>
              </>
            )}
          </div>
        )}
        {canManage && open && (
          <div className="space-y-6">
            <Card title="Team" description={t.team === "FINANCE"
              ? "With the finance team: one of them assigns it to themselves and works on it."
              : "Needs finance to sort it out? Hand it to the finance team, unassigned."}>
              {t.team === "FINANCE" ? (
                <ConfirmForm action={ticketTeamAction.bind(null, t.id, "SUPPORT")} submitLabel="Take back to support"
                  variant="secondary" confirm="Take this ticket back from the finance team?" />
              ) : (
                <ConfirmForm action={ticketTeamAction.bind(null, t.id, "FINANCE")} submitLabel="Hand to finance"
                  confirm="Hand this ticket to the finance team? It is unassigned and the finance team is notified." />
              )}
            </Card>
            <Card title="Assignee">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "assign")} label="Handled by" placeholder="Unassigned"
                defaultValue={t.assigneeId ?? ""} options={agents.map((a) => ({ value: a.userId, label: a.name }))} submitLabel="Assign" />
            </Card>
            <Card title="Priority" description="Sets the first-response target.">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "priority")} label="Priority" defaultValue={t.priority}
                options={["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => ({ value: p, label: humanize(p) }))} submitLabel="Set priority" />
            </Card>
            <Card title="Status" description="The customer is notified. They can reopen a resolved request for 24 hours, after which it closes automatically. A closed request cannot be reopened.">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "status")} label="Mark as"
                defaultValue={t.status === "RESOLVED" ? "CLOSED" : "RESOLVED"}
                options={[{ value: "RESOLVED", label: "Resolved" }, { value: "CLOSED", label: "Closed" }]} submitLabel="Update" />
            </Card>
          </div>
        )}
      </div>
    </div>
  );
}
