import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { closeTicketAction, replyTicketAction } from "@/features/support/actions";
import { ReplyForm } from "@/features/support/forms";
import { CATEGORY_LABEL, TICKET_STATUS_LABEL, TICKET_TONE } from "@/features/support/labels";
import { Thread } from "@/features/support/thread";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { TicketDetail } from "@/types/support";

export const metadata: Metadata = { title: "Support request" };

export default async function TicketPage({ params }: PageProps<"/support/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  let detail: TicketDetail;
  try {
    detail = await authFetch<TicketDetail>(`/api/v1/support/tickets/${id}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const t = detail.ticket;
  const closed = t.status === "CLOSED";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <Link href="/support" className="text-sm text-muted hover:underline">Support</Link>
          <h1 className="text-3xl font-semibold tracking-tight">{t.subject}</h1>
          <p className="text-sm text-muted"><span className="font-mono">{t.reference}</span> · {CATEGORY_LABEL[t.category]}{t.relatedLabel ? ` · ${t.relatedLabel}` : ""}</p>
        </div>
        <StatusBadge tone={TICKET_TONE[t.status]}>{TICKET_STATUS_LABEL[t.status]}</StatusBadge>
      </div>
      <Thread messages={detail.messages} attachmentHref={(a) => `/support/${t.id}/attachments/${a}`} />
      {closed ? (
        <Notice>This request is closed. <Link href="/support/new" className="underline">Open a new one</Link> if you need more help.</Notice>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
          <Card title={t.status === "RESOLVED" ? "Still need help?" : "Reply"}
            description={t.status === "RESOLVED" ? "Replying reopens the request." : undefined}>
            <ReplyForm action={replyTicketAction.bind(null, t.id)} />
          </Card>
          <Card title="All sorted?">
            <ActionButton action={closeTicketAction.bind(null, t.id)} label="Close request" variant="secondary"
              confirm="Close this request? You can always open a new one." />
          </Card>
        </div>
      )}
    </div>
  );
}
