import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Notice } from "@/components/ui/notice";
import { StatusBadge } from "@/components/ui/status-badge";
import {
  escalateAction, staffReplyAction, ticketCategoryAction, ticketSettingAction, ticketTakeAction, ticketTeamAction,
} from "@/features/admin/support-actions";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { EscalateForm, SettingForm } from "@/features/admin/support-forms";
import { supportAbilities } from "@/features/admin/support-shell";
import { ReplyForm } from "@/features/support/forms";
import {
  CATEGORY_LABEL, CATEGORY_ORDER, CHANNEL_LABEL, PRIORITY_LABEL, PRIORITY_ORDER, PRIORITY_TONE, SLA_LABEL, SLA_TONE,
  STAFF_STATUS_LABEL, TICKET_TONE, TRIGGER_LABEL, nextStatuses,
} from "@/features/support/labels";
import { LiveRefresh } from "@/features/support/live-refresh";
import { Thread } from "@/features/support/thread";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type {
  Agent, ArticleSummary, EscalationLevel, KbPage, SlaTarget, SupportCategory, TicketContext, TicketDetail,
} from "@/types/support";
import { formatDate, formatDateTime, humanize } from "@/utils/format";
import { formatMoney } from "@/utils/money";
import { CURRENCY } from "@/lib/currency";

/** Amounts in the context panel are plain numbers in the platform currency. */
const rupees = (amount: number | string | null) => formatMoney(amount === null ? null : { amount: String(amount), currency: CURRENCY });

export const metadata: Metadata = { title: "Support request" };

const RELATED_HREF = { ORDER: "/admin/orders/", WITHDRAWAL: "/admin/withdrawals/", HOLDING: null } as const;

export default async function AdminTicketPage({ params, searchParams }: PageProps<"/admin/support/[id]">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const kb = String((await searchParams).kb ?? "").trim().slice(0, 200);
  const session = await requireStaff();
  const can = supportAbilities(session.permissions);
  let detail: TicketDetail;
  let context: TicketContext;
  try {
    [detail, context] = await Promise.all([
      authFetch<TicketDetail>(`/api/v1/admin/support/tickets/${id}`),
      authFetch<TicketContext>(`/api/v1/admin/support/tickets/${id}/context`),
    ]);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const t = detail.ticket;
  const canManage = can.manage;
  // Finance sees only tickets handed to it, and works on one once it is assigned to them.
  const financeOnly = !canManage && hasPermission(session.permissions, Permission.FINANCE_TICKET_HANDLE);
  const mine = t.assigneeId === session.userId;
  const canWork = canManage || (financeOnly && mine);
  const open = t.status !== "CLOSED";
  const [agents, categories, levels, articles] = await Promise.all([
    canManage ? authFetch<Agent[]>("/api/v1/admin/support/agents") : Promise.resolve([] as Agent[]),
    canManage ? authFetch<SupportCategory[]>("/api/v1/admin/support/categories") : Promise.resolve([] as SupportCategory[]),
    can.escalate && can.view ? authFetch<EscalationLevel[]>("/api/v1/admin/support/escalation-levels") : Promise.resolve([] as EscalationLevel[]),
    kb ? authFetch<KbPage<ArticleSummary>>(`/api/v1/admin/knowledge-base/search?${new URLSearchParams({ q: kb, size: "5" })}`)
      : Promise.resolve(null),
  ]);
  // A link only where the viewer may open the record: withdrawals are for admins.
  const canOpenRelated = t.relatedType === "WITHDRAWAL" ? hasPermission(session.permissions, Permission.WITHDRAWAL_VIEW)
    : t.relatedType === "ORDER" ? hasPermission(session.permissions, Permission.ORDER_VIEW) : true;
  const relatedHref = !financeOnly && canOpenRelated && t.relatedType && RELATED_HREF[t.relatedType]
    ? `${RELATED_HREF[t.relatedType]}${t.relatedId}` : null;
  const statuses = nextStatuses(t.status).filter((s) => !financeOnly || s === "RESOLVED" || s === "CLOSED" || s === "WAITING_ON_CUSTOMER");
  const categoryOptions = CATEGORY_ORDER.flatMap((c) => [
    { value: c, label: CATEGORY_LABEL[c] },
    ...categories.filter((s) => s.parentId && s.category === c && s.active).map((s) => ({ value: `${c}:${s.id}`, label: `${CATEGORY_LABEL[c]} › ${s.name}` })),
  ]);
  const agentOptions = agents.map((a) => ({ value: a.userId, label: a.name }));
  const c = context.customer;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <Link href="/admin/support" className="text-sm text-muted hover:underline">Support</Link>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{t.subject}</h1>
          <p className="text-sm text-muted">
            <span className="font-mono">{t.reference}</span> · {CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` › ${t.subcategoryName}` : ""}
            {t.channel && <> · {CHANNEL_LABEL[t.channel]}</>}
            {t.team === "FINANCE" && <> · with the finance team</>}
            {t.relatedLabel && <> · {relatedHref ? <Link href={relatedHref} className="inline-flex min-h-6 items-center text-gold-text hover:underline">{t.relatedLabel}</Link> : t.relatedLabel}</>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge tone={PRIORITY_TONE[t.priority]}>{PRIORITY_LABEL[t.priority]}</StatusBadge>
          <StatusBadge tone={TICKET_TONE[t.status]}>{STAFF_STATUS_LABEL[t.status]}</StatusBadge>
          {(t.escalationLevel ?? 0) > 0 && <StatusBadge tone="danger">Level {t.escalationLevel}</StatusBadge>}
          {context.sla.overall !== "NONE" && <StatusBadge tone={SLA_TONE[context.sla.overall]}>SLA {SLA_LABEL[context.sla.overall].toLowerCase()}</StatusBadge>}
        </div>
      </div>

      <div className="grid gap-6 xl:grid-cols-[17rem_minmax(0,1fr)_19rem]">
        {/* Left: customer and SLA context */}
        <aside aria-label="Ticket context" className="space-y-4 xl:order-1">
          <Card title="SLA" description={context.sla.policyName
            ? `Policy: ${context.sla.policyName}${context.sla.paused ? " · resolution clock paused" : ""}`
            : "Default first-response target; no resolution target."}>
            <dl className="space-y-3 text-sm">
              <SlaRow label="First response" target={context.sla.firstResponse} />
              {context.sla.nextResponse.state !== "NONE" && <SlaRow label="Next response" target={context.sla.nextResponse} />}
              <SlaRow label="Resolution" target={context.sla.resolution} />
            </dl>
          </Card>
          {c && (
            <Card title="Customer">
              <dl className="space-y-2 text-sm">
                <div>
                  <dt className="sr-only">Name</dt>
                  <dd><Link href={`/admin/users/${c.id}`} className="font-medium text-gold-text hover:underline">{c.name}</Link>
                    {c.userCode && <span className="ml-1 font-mono text-xs text-muted">{c.userCode}</span>}</dd>
                </div>
                {c.email && <Row label="Email" value={c.email} />}
                <Row label="Type" value={c.customerType === "NETWORK" ? "Network investor" : "Online investor"} />
                <Row label="Account" value={humanize(c.status)} />
                {c.kycStatus && <Row label="KYC" value={humanize(c.kycStatus)} />}
                <Row label="Customer since" value={formatDate(c.registeredAt)} />
                <Row label="Tickets" value={`${c.openTickets} open · ${c.totalTickets} total`} />
              </dl>
            </Card>
          )}
          {context.investments && (
            <Card title="Investments">
              <dl className="space-y-2 text-sm">
                <Row label="Active holdings" value={String(context.investments.activeHoldings)} />
                <Row label="Invested" value={rupees(context.investments.investedAmount)} />
                <Row label="Orders" value={String(context.investments.orders)} />
                {context.investments.lastOrderNumber && (
                  <Row label="Last order" value={`${context.investments.lastOrderNumber} · ${humanize(context.investments.lastOrderStatus ?? "")}`} />
                )}
              </dl>
            </Card>
          )}
          {context.money && (
            <Card title="Payments & withdrawals">
              <dl className="space-y-2 text-sm">
                <Row label="Pending withdrawals" value={String(context.money.pendingWithdrawals)} />
                {context.money.lastWithdrawalReference && (
                  <Row label="Last withdrawal" value={`${context.money.lastWithdrawalReference} · ${rupees(context.money.lastWithdrawalAmount)} · ${humanize(context.money.lastWithdrawalStatus ?? "")}`} />
                )}
                <Row label="Payments awaiting review" value={String(context.money.paymentsAwaiting)} />
              </dl>
            </Card>
          )}
          {context.previousTickets.length > 0 && (
            <Card title="Previous tickets">
              <ul className="space-y-2 text-sm">
                {context.previousTickets.map((p) => (
                  <li key={p.id}>
                    <Link href={`/admin/support/${p.id}`} className="text-gold-text hover:underline">{p.subject}</Link>
                    <span className="block text-xs text-muted"><span className="font-mono">{p.reference}</span> · {STAFF_STATUS_LABEL[p.status]} · {formatDate(p.createdAt)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
          {context.feedback && (
            <Card title="Customer rating">
              <p className="text-2xl font-semibold">{context.feedback.rating}/5</p>
              {context.feedback.comment && <p className="mt-1 text-sm text-muted">“{context.feedback.comment}”</p>}
            </Card>
          )}
        </aside>

        {/* Middle: the conversation */}
        <section aria-label="Conversation" className="min-w-0 space-y-6 xl:order-2">
          <Thread messages={detail.messages} events={detail.events} viewer="staff" attachmentHref={(a) => `/admin/support/${t.id}/attachments/${a}`} />
          {open && <LiveRefresh />}
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
          {context.escalations.length > 0 && (
            <Card title="Escalation history">
              <ol className="space-y-3 text-sm">
                {context.escalations.map((e) => (
                  <li key={e.id} className="border-l-2 border-rose-400 pl-3">
                    <p className="font-medium">Level {e.previousLevel} → {e.level} · {TRIGGER_LABEL[e.trigger]}</p>
                    <p className="text-muted">{e.reason}</p>
                    <p className="text-xs text-muted">{formatDateTime(e.createdAt)} · {e.escalatedByName ?? "Automatic"}{e.ownerName ? ` · owner ${e.ownerName}` : ""}</p>
                  </li>
                ))}
              </ol>
            </Card>
          )}
        </section>

        {/* Right: actions and knowledge */}
        <aside aria-label="Actions" className="space-y-4 xl:order-3">
          {financeOnly && open && (
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
          )}
          {canManage && open && (
            <Card title="Assignee">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "assign")} label="Handled by" placeholder="Unassigned"
                defaultValue={t.assigneeId ?? ""} options={agentOptions} submitLabel="Assign" />
            </Card>
          )}
          {canWork && open && statuses.length > 0 && (
            <Card title="Status" description="Customers are told when a ticket is resolved or closed. A resolved request can be reopened for 24 hours; a closed one cannot.">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "status")} label="Move to"
                defaultValue={statuses.includes("RESOLVED") ? "RESOLVED" : statuses[0]}
                options={statuses.map((s) => ({ value: s, label: STAFF_STATUS_LABEL[s] }))} submitLabel="Update" />
            </Card>
          )}
          {canWork && open && (
            <Card title="Priority" description="Sets the SLA targets.">
              <SettingForm action={ticketSettingAction.bind(null, t.id, "priority")} label="Priority" defaultValue={t.priority}
                options={PRIORITY_ORDER.map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))} submitLabel="Set priority" />
            </Card>
          )}
          {canManage && open && (
            <Card title="Category" description="Recategorising re-applies the matching SLA policy.">
              <SettingForm action={ticketCategoryAction.bind(null, t.id)} label="Category"
                defaultValue={t.subcategoryId ? `${t.category}:${t.subcategoryId}` : t.category} options={categoryOptions} submitLabel="Change" />
            </Card>
          )}
          {can.escalate && open && t.status !== "RESOLVED" && (
            <Card title="Escalate" description="Raises the level and notifies that level's contacts. Recorded permanently.">
              <EscalateForm action={escalateAction.bind(null, t.id)} levels={levels} agents={agentOptions} current={t.escalationLevel ?? 0} />
            </Card>
          )}
          {financeOnly && mine && open && (
            <Card title="Team" description="Done with the finance side? Hand it back to support, unassigned.">
              <ConfirmForm action={ticketTeamAction.bind(null, t.id, "SUPPORT")} submitLabel="Hand back to support"
                variant="secondary" confirm="Hand this ticket back to the support team?" />
            </Card>
          )}
          {canManage && open && (
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
          )}
          <Card title="Knowledge base" description="Published articles, including internal ones, to help answer.">
            <form action={`/admin/support/${t.id}`} className="flex gap-2">
              <label htmlFor="kb" className="sr-only">Search articles</label>
              <input id="kb" name="kb" type="search" defaultValue={kb} maxLength={200} placeholder="Search articles"
                className="min-w-0 flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
              <button type="submit" className="rounded-lg border border-border px-3 text-sm hover:bg-background">Search</button>
            </form>
            {articles && (
              articles.content.length === 0 ? <p className="mt-3 text-sm text-muted">No article matches.</p> : (
                <ul className="mt-3 space-y-2 text-sm">
                  {articles.content.map((a) => (
                    <li key={a.id}>
                      <Link href={`/admin/support/knowledge-base/read/${a.slug}`} target="_blank" className="text-gold-text hover:underline">{a.title}</Link>
                      <span className="block text-xs text-muted">{a.visibility === "INTERNAL" ? "Internal · " : a.visibility === "CUSTOMER" ? "Customers · " : ""}{a.summary}</span>
                      {a.visibility !== "INTERNAL" && <span className="block font-mono text-xs text-muted">/support/articles/{a.slug}</span>}
                    </li>
                  ))}
                </ul>
              )
            )}
          </Card>
        </aside>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="min-w-0 break-words text-right [overflow-wrap:anywhere]">{value}</dd>
    </div>
  );
}

function SlaRow({ label, target }: { label: string; target: SlaTarget }) {
  const left = target.minutesLeft;
  const leftText = left === null ? null : left < 0 ? `${duration(-left)} over` : `${duration(left)} left`;
  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between gap-2">
        <dt className="font-medium">{label}</dt>
        <dd><StatusBadge tone={SLA_TONE[target.state]}>{SLA_LABEL[target.state]}</StatusBadge></dd>
      </div>
      {target.dueAt && (
        <dd className="text-xs text-muted">
          {target.metAt ? `Met ${formatDateTime(target.metAt)} · due ${formatDateTime(target.dueAt)}` : `Due ${formatDateTime(target.dueAt)}`}
          {leftText && ` · ${leftText}`}{target.percentUsed !== null && ` · ${Math.min(target.percentUsed, 999)}% used`}
        </dd>
      )}
    </div>
  );
}

/** Minutes as "2d 3h", "4h 10m" or "25m" (business minutes when the policy counts business hours). */
function duration(minutes: number): string {
  const d = Math.floor(minutes / 1440);
  const h = Math.floor((minutes % 1440) / 60);
  const m = minutes % 60;
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}m` : `${m}m`;
}
