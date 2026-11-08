import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
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
import {
  ArrowLeft, BadgeCheck, BookOpen, Briefcase, CalendarDays, ChevronRight, Flag, Gauge, History, Landmark, Layers, Link2, type LucideIcon,
  Mail, MessageSquareReply, Network, Radio, Search, Star, Tag, Ticket, TrendingUp, UserCog, UserRound, Users, Wallet,
} from "lucide-react";
import type { SlaState } from "@/types/support";
import { widthClass } from "@/utils/width-class";

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

  const settings = open && (canManage || financeOnly || canWork || can.escalate);
  const initials = c ? c.name.split(/\s+/).filter(Boolean).map((w) => w.charAt(0)).join("").slice(0, 2).toUpperCase() || "?" : "?";

  return (
    <div className="space-y-6">
      {/* ------------------------------------------------------------------ banner */}
      <section aria-labelledby="ticket-title" className="relative isolate overflow-hidden rounded-3xl bg-ink text-on-ink shadow-xl shadow-ink/20">
        <div aria-hidden="true" className="absolute -top-24 -right-24 -z-10 size-72 rounded-full bg-gold/30 blur-3xl" />
        <div aria-hidden="true" className="absolute -bottom-32 -left-16 -z-10 size-64 rounded-full bg-on-ink/5 blur-3xl" />
        <div className="space-y-4 p-6 sm:p-8">
          <Link href="/admin/support" className="inline-flex min-h-8 items-center gap-1.5 text-sm text-on-ink/70 transition-colors hover:text-gold-on-ink">
            <ArrowLeft aria-hidden="true" className="size-4" /> Support tickets
          </Link>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="min-w-0 flex-1 space-y-3">
              <h1 id="ticket-title" className="text-2xl font-semibold tracking-tight break-words sm:text-3xl">{t.subject}</h1>
              <p className="flex flex-wrap items-center gap-2 text-xs">
                <Chip icon={Ticket}><span className="font-mono">{t.reference}</span></Chip>
                <Chip icon={Tag}>{CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` › ${t.subcategoryName}` : ""}</Chip>
                {t.channel && <Chip icon={Radio}>{CHANNEL_LABEL[t.channel]}</Chip>}
                {t.team === "FINANCE" && <Chip icon={Landmark}>With the finance team</Chip>}
                {t.relatedLabel && (relatedHref ? (
                  <Link href={relatedHref} className="inline-flex items-center gap-1.5 rounded-full border border-gold-on-ink/40 bg-gold/20 px-2.5 py-1 font-medium text-on-ink backdrop-blur-md transition-colors hover:bg-gold/30">
                    <Link2 aria-hidden="true" className="size-3.5" /> {t.relatedLabel}
                  </Link>
                ) : <Chip icon={Link2}>{t.relatedLabel}</Chip>)}
              </p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge tone={PRIORITY_TONE[t.priority]}>{PRIORITY_LABEL[t.priority]}</StatusBadge>
              <StatusBadge tone={TICKET_TONE[t.status]}>{STAFF_STATUS_LABEL[t.status]}</StatusBadge>
              {(t.escalationLevel ?? 0) > 0 && <StatusBadge tone="danger">Level {t.escalationLevel}</StatusBadge>}
              {context.sla.overall !== "NONE" && <StatusBadge tone={SLA_TONE[context.sla.overall]}>SLA {SLA_LABEL[context.sla.overall].toLowerCase()}</StatusBadge>}
            </div>
          </div>
        </div>
      </section>

      {/* On narrow screens the conversation comes first, then the actions, then the context. */}
      <div className="grid gap-6 xl:grid-cols-[17rem_minmax(0,1fr)_19rem]">
        {/* Left: customer and SLA context */}
        <aside aria-label="Ticket context" className="order-3 min-w-0 space-y-4 xl:order-1">
          <Panel icon={Gauge} title="SLA">
            <p className="mb-3 text-xs text-muted">
              {context.sla.policyName
                ? `Policy: ${context.sla.policyName}${context.sla.paused ? " · resolution clock paused" : ""}`
                : "Default first-response target; no resolution target."}
            </p>
            <dl className="space-y-4 text-sm">
              <SlaRow label="First response" target={context.sla.firstResponse} />
              {context.sla.nextResponse.state !== "NONE" && <SlaRow label="Next response" target={context.sla.nextResponse} />}
              <SlaRow label="Resolution" target={context.sla.resolution} />
            </dl>
          </Panel>
          {c && (
            <Panel icon={UserRound} title="Customer">
              <div className="mb-3 flex items-center gap-3">
                <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-gold/10 text-xs font-semibold text-gold-text">{initials}</span>
                <div className="min-w-0">
                  <Link href={`/admin/users/${c.id}`} className="block truncate font-medium text-gold-text hover:underline">{c.name}</Link>
                  {c.userCode && <span className="block font-mono text-xs text-muted">{c.userCode}</span>}
                </div>
              </div>
              <dl className="space-y-2 text-sm">
                {c.email && <Row icon={Mail} label="Email" value={c.email} />}
                <Row icon={c.customerType === "NETWORK" ? Network : UserRound} label="Type" value={c.customerType === "NETWORK" ? "Network investor" : "Online investor"} />
                <Row icon={BadgeCheck} label="Account" value={humanize(c.status)} />
                {c.kycStatus && <Row icon={BadgeCheck} label="KYC" value={humanize(c.kycStatus)} />}
                <Row icon={CalendarDays} label="Customer since" value={formatDate(c.registeredAt)} />
                <Row icon={Ticket} label="Tickets" value={`${c.openTickets} open · ${c.totalTickets} total`} />
              </dl>
            </Panel>
          )}
          {(context.investments || context.money) && (
            <Panel icon={Briefcase} title="Money">
              <div className="grid grid-cols-2 gap-2">
                {context.investments && (
                  <>
                    <Stat label="Active holdings" value={String(context.investments.activeHoldings)} />
                    <Stat label="Invested" value={rupees(context.investments.investedAmount)} />
                    <Stat label="Orders" value={String(context.investments.orders)} />
                  </>
                )}
                {context.money && (
                  <>
                    <Stat label="Pending withdrawals" value={String(context.money.pendingWithdrawals)} />
                    <Stat label="Payments to review" value={String(context.money.paymentsAwaiting)} />
                  </>
                )}
              </div>
              {(context.investments?.lastOrderNumber || context.money?.lastWithdrawalReference) && (
                <dl className="mt-3 space-y-2 border-t border-border pt-3 text-sm">
                  {context.investments?.lastOrderNumber && (
                    <Row icon={Layers} label="Last order" value={`${context.investments.lastOrderNumber} · ${humanize(context.investments.lastOrderStatus ?? "")}`} />
                  )}
                  {context.money?.lastWithdrawalReference && (
                    <Row icon={Wallet} label="Last withdrawal" value={`${context.money.lastWithdrawalReference} · ${rupees(context.money.lastWithdrawalAmount)} · ${humanize(context.money.lastWithdrawalStatus ?? "")}`} />
                  )}
                </dl>
              )}
            </Panel>
          )}
          {context.previousTickets.length > 0 && (
            <Panel icon={History} title="Previous tickets">
              <ul className="-mx-2 space-y-1 text-sm">
                {context.previousTickets.map((p) => (
                  <li key={p.id}>
                    <Link href={`/admin/support/${p.id}`} className="group flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-background">
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium group-hover:text-gold-text">{p.subject}</span>
                        <span className="block text-xs text-muted"><span className="font-mono">{p.reference}</span> · {STAFF_STATUS_LABEL[p.status]} · {formatDate(p.createdAt)}</span>
                      </span>
                      <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted" />
                    </Link>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
          {context.feedback && (
            <Panel icon={Star} title="Customer rating">
              <p className="flex items-center gap-1" aria-label={`${context.feedback.rating} out of 5`}>
                {[1, 2, 3, 4, 5].map((n) => (
                  <Star key={n} aria-hidden="true" className={`size-5 ${n <= context.feedback!.rating ? "fill-gold text-gold" : "text-border"}`} />
                ))}
                <span className="ml-2 font-semibold">{context.feedback.rating}/5</span>
              </p>
              {context.feedback.comment && <p className="mt-2 text-sm text-muted italic">“{context.feedback.comment}”</p>}
            </Panel>
          )}
        </aside>

        {/* Middle: the conversation */}
        <section aria-label="Conversation" className="order-1 min-w-0 space-y-6 xl:order-2">
          <Thread messages={detail.messages} events={detail.events} viewer="staff" attachmentHref={(a) => `/admin/support/${t.id}/attachments/${a}`} />
          {open && <LiveRefresh />}
          {canWork && open && (
            <section aria-labelledby="reply-title" className="rounded-2xl border border-border bg-surface p-5 shadow-sm sm:p-6">
              <h2 id="reply-title" className="mb-4 flex items-center gap-2 font-semibold tracking-tight">
                <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><MessageSquareReply className="size-4" /></span>
                Reply
              </h2>
              <ReplyForm action={staffReplyAction.bind(null, t.id)} submitLabel="Send"
                extra={
                  <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-dashed border-amber-500/40 bg-amber-500/5 px-3 py-2.5 text-sm">
                    <input type="checkbox" name="internal" className="mt-0.5 size-4 accent-[var(--gold)]" />
                    <span>
                      <span className="block font-medium">Internal note</span>
                      <span className="block text-xs text-muted">Visible to employees only; the customer is not notified.</span>
                    </span>
                  </label>
                } />
            </section>
          )}
          {context.escalations.length > 0 && (
            <Panel icon={TrendingUp} title="Escalation history">
              <ol className="relative space-y-4 border-l-2 border-rose-200 pl-5 text-sm dark:border-rose-500/30">
                {context.escalations.map((e) => (
                  <li key={e.id} className="relative">
                    <span aria-hidden="true" className="absolute top-1 -left-[1.6rem] size-3 rounded-full bg-rose-500 ring-4 ring-surface" />
                    <p className="font-medium">Level {e.previousLevel} → {e.level} · {TRIGGER_LABEL[e.trigger]}</p>
                    <p className="text-muted">{e.reason}</p>
                    <p className="text-xs text-muted">{formatDateTime(e.createdAt)} · {e.escalatedByName ?? "Automatic"}{e.ownerName ? ` · owner ${e.ownerName}` : ""}</p>
                  </li>
                ))}
              </ol>
            </Panel>
          )}
        </section>

        {/* Right: actions and knowledge */}
        <aside aria-label="Actions" className="order-2 min-w-0 space-y-4 xl:order-3">
          {settings && (
            <section aria-labelledby="manage-title" className="overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
              <h2 id="manage-title" className="flex items-center gap-2 border-b border-border bg-background/40 px-5 py-4 font-semibold tracking-tight">
                <span aria-hidden="true" className="h-4 w-1 rounded-full bg-gold" /> Manage ticket
              </h2>
              <div className="divide-y divide-border">
                {financeOnly && (
                  <Setting icon={UserCog} title="Assignee">
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
                  </Setting>
                )}
                {canManage && (
                  <Setting icon={UserCog} title="Assignee">
                    <SettingForm action={ticketSettingAction.bind(null, t.id, "assign")} label="Handled by" placeholder="Unassigned"
                      defaultValue={t.assigneeId ?? ""} options={agentOptions} submitLabel="Assign" />
                  </Setting>
                )}
                {canWork && statuses.length > 0 && (
                  <Setting icon={BadgeCheck} title="Status"
                    description="Customers are told when a ticket is resolved or closed. A resolved request can be reopened for 24 hours; a closed one cannot.">
                    <SettingForm action={ticketSettingAction.bind(null, t.id, "status")} label="Move to"
                      defaultValue={statuses.includes("RESOLVED") ? "RESOLVED" : statuses[0]}
                      options={statuses.map((s) => ({ value: s, label: STAFF_STATUS_LABEL[s] }))} submitLabel="Update" />
                  </Setting>
                )}
                {canWork && (
                  <Setting icon={Flag} title="Priority" description="Sets the SLA targets.">
                    <SettingForm action={ticketSettingAction.bind(null, t.id, "priority")} label="Priority" defaultValue={t.priority}
                      options={PRIORITY_ORDER.map((p) => ({ value: p, label: PRIORITY_LABEL[p] }))} submitLabel="Set priority" />
                  </Setting>
                )}
                {canManage && (
                  <Setting icon={Tag} title="Category" description="Recategorising re-applies the matching SLA policy.">
                    <SettingForm action={ticketCategoryAction.bind(null, t.id)} label="Category"
                      defaultValue={t.subcategoryId ? `${t.category}:${t.subcategoryId}` : t.category} options={categoryOptions} submitLabel="Change" />
                  </Setting>
                )}
                {can.escalate && t.status !== "RESOLVED" && (
                  <Setting icon={TrendingUp} title="Escalate" description="Raises the level and notifies that level's contacts. Recorded permanently.">
                    <EscalateForm action={escalateAction.bind(null, t.id)} levels={levels} agents={agentOptions} current={t.escalationLevel ?? 0} />
                  </Setting>
                )}
                {financeOnly && mine && (
                  <Setting icon={Users} title="Team" description="Done with the finance side? Hand it back to support, unassigned.">
                    <ConfirmForm action={ticketTeamAction.bind(null, t.id, "SUPPORT")} submitLabel="Hand back to support"
                      variant="secondary" confirm="Hand this ticket back to the support team?" />
                  </Setting>
                )}
                {canManage && (
                  <Setting icon={Users} title="Team" description={t.team === "FINANCE"
                    ? "With the finance team: one of them assigns it to themselves and works on it."
                    : "Needs finance to sort it out? Hand it to the finance team, unassigned."}>
                    {t.team === "FINANCE" ? (
                      <ConfirmForm action={ticketTeamAction.bind(null, t.id, "SUPPORT")} submitLabel="Take back to support"
                        variant="secondary" confirm="Take this ticket back from the finance team?" />
                    ) : (
                      <ConfirmForm action={ticketTeamAction.bind(null, t.id, "FINANCE")} submitLabel="Hand to finance"
                        confirm="Hand this ticket to the finance team? It is unassigned and the finance team is notified." />
                    )}
                  </Setting>
                )}
              </div>
            </section>
          )}
          <Panel icon={BookOpen} title="Knowledge base">
            <p className="mb-3 text-xs text-muted">Published articles, including internal ones, to help answer.</p>
            <form action={`/admin/support/${t.id}`} role="search"
              className="flex items-center gap-1 rounded-xl border border-border bg-background/60 p-1 focus-within:border-gold">
              <label htmlFor="kb" className="sr-only">Search articles</label>
              <Search aria-hidden="true" className="ml-2 size-4 shrink-0 text-muted" />
              <input id="kb" name="kb" type="search" defaultValue={kb} maxLength={200} placeholder="Search articles"
                className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
              <button type="submit" className="h-9 rounded-lg bg-gold px-3 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
            </form>
            {articles && (
              articles.content.length === 0 ? <p className="mt-3 text-sm text-muted">No article matches.</p> : (
                <ul className="-mx-2 mt-3 space-y-1 text-sm">
                  {articles.content.map((a) => (
                    <li key={a.id}>
                      <Link href={`/admin/support/knowledge-base/read/${a.slug}`} target="_blank"
                        className="group block rounded-lg px-2 py-1.5 transition-colors hover:bg-background">
                        <span className="block font-medium group-hover:text-gold-text">{a.title}</span>
                        <span className="block text-xs text-muted">{a.visibility === "INTERNAL" ? "Internal · " : a.visibility === "CUSTOMER" ? "Customers · " : ""}{a.summary}</span>
                        {a.visibility !== "INTERNAL" && <span className="block font-mono text-xs break-all text-muted">/support/articles/{a.slug}</span>}
                      </Link>
                    </li>
                  ))}
                </ul>
              )
            )}
          </Panel>
        </aside>
      </div>
    </div>
  );
}

/** A side-column card with an icon heading. */
function Panel({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children: React.ReactNode }) {
  return (
    <section aria-label={title} className="rounded-2xl border border-border bg-surface p-5 shadow-sm">
      <h2 className="mb-3 flex items-center gap-2 font-semibold tracking-tight">
        <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-gold/10 text-gold-text"><Icon className="size-4" /></span>
        {title}
      </h2>
      {children}
    </section>
  );
}

/** One setting inside the Manage ticket panel. */
function Setting({ icon: Icon, title, description, children }: { icon: LucideIcon; title: string; description?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-3 p-5">
      <div>
        <h3 className="flex items-center gap-2 text-sm font-semibold">
          <Icon aria-hidden="true" className="size-4 text-gold-text" /> {title}
        </h3>
        {description && <p className="mt-1 text-xs text-muted">{description}</p>}
      </div>
      {children}
    </div>
  );
}

function Chip({ icon: Icon, children }: { icon: LucideIcon; children: React.ReactNode }) {
  return (
    <span className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-on-ink/20 bg-on-ink/10 px-2.5 py-1 font-medium text-on-ink backdrop-blur-md">
      <Icon aria-hidden="true" className="size-3.5 shrink-0" /> <span className="min-w-0 break-words">{children}</span>
    </span>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-xl bg-background/60 p-3">
      <p className="truncate text-[11px] text-muted">{label}</p>
      <p className="mt-0.5 truncate font-semibold tabular-nums">{value}</p>
    </div>
  );
}

const SLA_BAR: Record<SlaState, string> = {
  BREACHED: "bg-rose-500", AT_RISK: "bg-amber-500", PAUSED: "bg-slate-400", ON_TRACK: "bg-emerald-500", MET: "bg-emerald-500", NONE: "bg-border",
};

function Row({ icon: Icon, label, value }: { icon: LucideIcon; label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="flex shrink-0 items-center gap-1.5 text-muted"><Icon aria-hidden="true" className="size-3.5" />{label}</dt>
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
      {target.percentUsed !== null && (
        <dd aria-hidden="true" className="h-1.5 overflow-hidden rounded-full bg-background ring-1 ring-border">
          <span className={`block h-full rounded-full ${SLA_BAR[target.state]} ${widthClass(target.percentUsed)}`} />
        </dd>
      )}
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
