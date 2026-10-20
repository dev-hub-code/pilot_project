import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import { ActionButton } from "@/features/orders/action-button";
import { closeTicketAction, reopenTicketAction, replyTicketAction } from "@/features/support/actions";
import { ReopenForm, ReplyForm } from "@/features/support/forms";
import { CATEGORY_LABEL, TICKET_STATUS_LABEL, TICKET_TONE } from "@/features/support/labels";
import { LiveRefresh } from "@/features/support/live-refresh";
import { Thread } from "@/features/support/thread";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Ticket, TicketDetail } from "@/types/support";
import { formatDateTime } from "@/utils/format";

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
  // Only sent while the request can still be reopened.
  const canReopen = t.reopenableUntil !== null;
  const agent = detail.events.findLast((e) => e.type === "ASSIGNED")?.agentName ?? null;

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

      <StatusTracker ticket={t} agent={agent} canReopen={canReopen} />

      <section aria-label="Conversation and updates" className="space-y-3">
        <h2 className="text-xs font-medium uppercase tracking-[0.08em] text-muted">Conversation &amp; updates</h2>
        <Thread messages={detail.messages} events={detail.events} viewer="customer"
          attachmentHref={(a) => `/support/${t.id}/attachments/${a}`} />
        {t.status !== "CLOSED" && <LiveRefresh />}
      </section>

      {t.status === "RESOLVED" || t.status === "CLOSED" ? (
        canReopen ? (
          <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
            <Card title="Still need help?"
              description={`You can reopen this request until ${formatDateTime(t.reopenableUntil)}, when it closes automatically. Tell us what is still wrong and it goes back to our support team.`}>
              <ReopenForm action={reopenTicketAction.bind(null, t.id)} />
            </Card>
            {t.status === "RESOLVED" && (
              <Card title="All sorted?">
                <ActionButton action={closeTicketAction.bind(null, t.id)} label="Close request" variant="secondary"
                  confirm="Close this request? A closed request cannot be reopened." />
              </Card>
            )}
          </div>
        ) : (
          <Notice>This request is closed and cannot be reopened. <Link href="/support/new" className="underline">Open a new one</Link> if you need more help.</Notice>
        )
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
          <Card title="Reply">
            <ReplyForm action={replyTicketAction.bind(null, t.id)} />
          </Card>
          <Card title="All sorted?">
            <ActionButton action={closeTicketAction.bind(null, t.id)} label="Close request" variant="secondary"
              confirm="Close this request? A closed request cannot be reopened." />
          </Card>
        </div>
      )}
    </div>
  );
}

const STEPS = ["Received", "In progress", "Resolved", "Closed"] as const;

/** Where the request stands, step by step, and what happens next. */
function StatusTracker({ ticket: t, agent, canReopen }: { ticket: Ticket; agent: string | null; canReopen: boolean }) {
  const current = t.status === "CLOSED" ? 3 : t.status === "RESOLVED" ? 2
    : t.status === "WAITING_ON_CUSTOMER" || t.firstRespondedAt || agent ? 1 : 0;
  const next: Record<Ticket["status"], string> = {
    OPEN: agent ? `${agent} is looking into it and will reply here.` : "Our support team has your request and will reply here.",
    WAITING_ON_CUSTOMER: "We replied and are waiting for you. Reply below to continue.",
    RESOLVED: canReopen
      ? `We think this is sorted. If not, reopen it below before ${formatDateTime(t.reopenableUntil)}, when it closes automatically.`
      : "We think this is sorted.",
    CLOSED: "This request is closed.",
  };
  return (
    <div className="space-y-4 border border-border bg-surface p-5">
      <ol className="grid grid-cols-4 gap-2" aria-label="Request progress">
        {STEPS.map((step, index) => (
          <li key={step} aria-current={index === current ? "step" : undefined} className="space-y-2">
            <span className={`block h-1.5 ${index <= current ? "bg-gold" : "bg-border"}`} />
            <span className={`block text-xs ${index === current ? "font-semibold text-foreground" : "text-muted"}`}>{step}</span>
          </li>
        ))}
      </ol>
      <div className="flex flex-wrap justify-between gap-x-6 gap-y-1 text-sm">
        <p>{next[t.status]}</p>
        <p className="text-muted">Last update {formatDateTime(t.lastMessageAt)}</p>
      </div>
    </div>
  );
}
