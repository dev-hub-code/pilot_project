import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { staffReplyAction, ticketSettingAction } from "@/features/admin/support-actions";
import { SettingForm } from "@/features/admin/support-forms";
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
  const agents = canManage ? await authFetch<Agent[]>("/api/v1/admin/support/agents") : [];
  const relatedHref = t.relatedType && RELATED_HREF[t.relatedType] ? `${RELATED_HREF[t.relatedType]}${t.relatedId}` : null;
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
          <Thread messages={detail.messages} attachmentHref={(a) => `/admin/support/${t.id}/attachments/${a}`} />
          {canManage && open && (
            <Card title="Reply">
              <ReplyForm action={staffReplyAction.bind(null, t.id)} submitLabel="Send"
                extra={
                  <label className="flex items-center gap-3 text-sm">
                    <input type="checkbox" name="internal" className="size-4" />
                    Internal note: visible to staff only, the customer is not notified
                  </label>
                } />
            </Card>
          )}
        </div>
        {canManage && open && (
          <div className="space-y-6">
            <Card title="Assignee">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "assign")} label="Handled by" placeholder="Unassigned"
                defaultValue={t.assigneeId ?? ""} options={agents.map((a) => ({ value: a.userId, label: a.name }))} submitLabel="Assign" />
            </Card>
            <Card title="Priority" description="Sets the first-response target.">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "priority")} label="Priority" defaultValue={t.priority}
                options={["LOW", "NORMAL", "HIGH", "URGENT"].map((p) => ({ value: p, label: humanize(p) }))} submitLabel="Set priority" />
            </Card>
            <Card title="Status" description="The customer is notified. Replies reopen a resolved request; closed is final.">
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
