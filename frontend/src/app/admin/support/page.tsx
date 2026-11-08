import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { bulkAssignAction } from "@/features/admin/support-actions";
import { BULK_FORM, BulkAssignForm, RecentSearches } from "@/features/admin/support-queue";
import { SupportShell, supportAbilities } from "@/features/admin/support-shell";
import {
  CATEGORY_LABEL, CATEGORY_ORDER, CHANNEL_LABEL, PRIORITY_LABEL, PRIORITY_ORDER, PRIORITY_TONE, SLA_LABEL, SLA_TONE,
  STAFF_STATUS_LABEL, STATUS_ORDER, TICKET_TONE,
} from "@/features/support/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Agent, Ticket, TicketCategory, TicketChannel, TicketPriority, TicketStatus } from "@/types/support";
import { formatDateTime } from "@/utils/format";
import {
  AlertOctagon, Archive, BadgeCheck, CircleDot, Clock, Inbox, Landmark, Layers, type LucideIcon, MessageSquare, Search, ShieldAlert,
  Plus, SlidersHorizontal, Sparkles, TrendingUp, UserRound, UserX, X,
} from "lucide-react";

const VIEW_ICON: Record<(typeof VIEWS)[number]["key"], LucideIcon> = {
  open: Inbox, new: Sparkles, unassigned: UserX, mine: UserRound, "at-risk": Clock, breached: AlertOctagon, escalated: TrendingUp,
  waiting: MessageSquare, resolved: BadgeCheck, closed: Archive, finance: Landmark, all: Layers,
};

/** The colour of a ticket's row marker, from how urgent it is. */
const PRIORITY_MARK: Record<TicketPriority, string> = {
  LOW: "bg-slate-300 dark:bg-slate-600",
  NORMAL: "bg-sky-400",
  HIGH: "bg-amber-500",
  URGENT: "bg-rose-500",
};

export const metadata: Metadata = { title: "Support" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Saved queues: each is a server-side filter, worked oldest-first where a reply is owed. */
const VIEWS = [
  { key: "open", label: "All open", query: { view: "open" }, oldestFirst: true },
  { key: "new", label: "New", query: { status: "NEW" }, oldestFirst: true },
  { key: "unassigned", label: "Unassigned", query: { view: "unassigned" }, oldestFirst: true },
  { key: "mine", label: "Assigned to me", query: { view: "open", assignee: "me" }, oldestFirst: true },
  { key: "at-risk", label: "At risk", query: { view: "at-risk" }, oldestFirst: true },
  { key: "breached", label: "SLA breached", query: { view: "breached" }, oldestFirst: true },
  { key: "escalated", label: "Escalated", query: { view: "escalated" }, oldestFirst: true },
  { key: "waiting", label: "Waiting", query: { view: "waiting" }, oldestFirst: false },
  { key: "resolved", label: "Resolved", query: { status: "RESOLVED" }, oldestFirst: false },
  { key: "closed", label: "Closed", query: { status: "CLOSED" }, oldestFirst: false },
  { key: "finance", label: "With finance", query: { team: "FINANCE", view: "open" }, oldestFirst: true },
  { key: "all", label: "All", query: {}, oldestFirst: false },
] as const;

function pick<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return allowed.find((a) => a === value);
}

export default async function AdminSupportPage({ searchParams }: PageProps<"/admin/support">) {
  const denied = await deniedUnlessInMenu("/admin/support");
  if (denied) return denied;
  const session = await requireStaff();
  const can = supportAbilities(session.permissions);
  // Finance sees only the tickets support hands it (the API filters them), so "With finance" is support's view.
  const views = VIEWS.filter((v) => !(can.financeOnly && v.key === "finance"));
  const params = await searchParams;
  const view = views.find((v) => v.key === params.view) ?? VIEWS[0];
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const q = typeof params.q === "string" ? params.q.trim().slice(0, 100) : "";
  const priority = pick<TicketPriority>(params.priority, PRIORITY_ORDER);
  const category = pick<TicketCategory>(params.category, CATEGORY_ORDER);
  const status = pick<TicketStatus>(params.status, STATUS_ORDER);
  const channel = pick<TicketChannel>(params.channel, ["PORTAL", "STAFF", "SYSTEM", "EMAIL"]);
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : "";
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : "";

  const query = new URLSearchParams({ page: String(page), size: "25", ...view.query });
  if (q) query.set("q", q);
  if (priority) query.set("priority", priority);
  if (category) query.set("category", category);
  if (status) query.set("status", status);
  if (channel) query.set("channel", channel);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  query.set("sort", view.oldestFirst ? "lastMessageAt,asc" : "lastMessageAt,desc");
  const [tickets, agents] = await Promise.all([
    authFetch<PageResponse<Ticket>>(`/api/v1/admin/support/tickets?${query}`),
    can.manage ? authFetch<Agent[]>("/api/v1/admin/support/agents") : Promise.resolve([] as Agent[]),
  ]);
  const filters = { view: view.key, q, priority, category, status, channel, from, to };
  const filtered = Boolean(q || priority || category || status || channel || from || to);
  const bulk = can.bulkAssign && !can.financeOnly;

  return (
    <SupportShell active="queue">
      <PageHeader title="Support tickets" description={can.financeOnly
        ? "Requests the support team handed to finance. Assign one to yourself before working on it."
        : "Customer requests by queue. SLA shows the worst of the first-response, next-response and resolution targets."}
        actions={can.create ? <LinkButton href="/admin/support/new" className="gap-2"><Plus aria-hidden="true" className="size-4" /> New ticket</LinkButton> : undefined} />

      {/* Queues: one row, scrolling sideways on narrow screens. */}
      <nav aria-label="Queues"
        className="flex max-w-full gap-1 overflow-x-auto rounded-xl border border-border bg-surface p-1 text-sm shadow-sm [scrollbar-width:none]">
        {views.map((v) => {
          const Icon = VIEW_ICON[v.key];
          return (
            <Link key={v.key} href={`/admin/support?view=${v.key}`} aria-current={v === view ? "page" : undefined}
              className={`inline-flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${v === view
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              <Icon aria-hidden="true" className="size-4" /> {v.label}
            </Link>
          );
        })}
      </nav>

      <form action="/admin/support" role="search" className="space-y-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
        <input type="hidden" name="view" value={view.key} />
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex min-w-0 flex-1 basis-72 items-center gap-2 rounded-xl border border-border bg-background/60 p-1 focus-within:border-gold">
            <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
            <input name="q" type="search" defaultValue={q} maxLength={100} placeholder="Reference, subject, order, customer name, email or code"
              aria-label="Search tickets" className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none" />
            <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
          </div>
          <RecentSearches current={q} />
        </div>
        <details open={Boolean(priority || category || status || channel || from || to)} className="group">
          <summary className="inline-flex min-h-8 cursor-pointer list-none items-center gap-1.5 rounded-lg border border-border px-3 text-xs font-medium text-muted transition-colors hover:border-gold hover:text-foreground group-open:border-gold/50 group-open:text-foreground">
            <SlidersHorizontal aria-hidden="true" className="size-3.5" /> More filters
            {filtered && <span className="rounded-full bg-gold px-1.5 text-[10px] font-semibold text-on-gold">Active</span>}
          </summary>
          <div className="mt-3 grid gap-3 border-t border-border pt-3 sm:grid-cols-2 lg:grid-cols-6">
            <Select name="priority" label="Priority" value={priority} options={PRIORITY_ORDER.map((p) => [p, PRIORITY_LABEL[p]])} />
            <Select name="category" label="Category" value={category} options={CATEGORY_ORDER.map((c) => [c, CATEGORY_LABEL[c]])} />
            <Select name="status" label="Status" value={status} options={STATUS_ORDER.map((s) => [s, STAFF_STATUS_LABEL[s]])} />
            <Select name="channel" label="Channel" value={channel}
              options={(["PORTAL", "STAFF", "SYSTEM", "EMAIL"] as const).map((c) => [c, CHANNEL_LABEL[c]])} />
            <label className="space-y-1 text-xs text-muted"><span className="block">Opened from</span>
              <input type="date" name="from" defaultValue={from} className={INPUT} /></label>
            <label className="space-y-1 text-xs text-muted"><span className="block">Opened to</span>
              <input type="date" name="to" defaultValue={to} className={INPUT} /></label>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <button type="submit" className="h-9 rounded-lg bg-ink px-4 text-sm font-medium text-on-ink transition-opacity hover:opacity-90 dark:bg-gold dark:text-on-gold">Apply filters</button>
          </div>
        </details>
        {filtered && (
          <Link href={`/admin/support?view=${view.key}`}
            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-border px-3 text-xs text-muted transition-colors hover:border-gold hover:text-gold-text">
            <X aria-hidden="true" className="size-3.5" /> Clear filters
          </Link>
        )}
      </form>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">
          {tickets.totalElements.toLocaleString("en-IN")} {tickets.totalElements === 1 ? "ticket" : "tickets"} · {view.oldestFirst ? "oldest reply owed first" : "latest first"}
        </p>
      </div>

      {bulk && tickets.content.length > 0 && (
        <BulkAssignForm action={bulkAssignAction} agents={agents.map((a) => ({ value: a.userId, label: a.name }))} />
      )}

      {tickets.content.length === 0 ? (
        <EmptyState title="Nothing here" description={filtered ? "No ticket matches these filters." : "This queue is empty."} />
      ) : (
        // A list rather than a wide table: one row per ticket on wide screens, a card on narrow ones, no sideways scrolling.
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          {tickets.content.map((t) => (
            <li key={t.id}
              className="group relative grid gap-3 py-4 pr-4 pl-5 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:pr-5">
              <span aria-hidden="true" title={PRIORITY_LABEL[t.priority]} className={`absolute inset-y-3 left-0 w-1 rounded-r-full ${PRIORITY_MARK[t.priority]}`} />
              <div className="flex min-w-0 items-start gap-3">
                {bulk && (
                  <input type="checkbox" name="ticketId" value={t.id} form={BULK_FORM} aria-label={`Select ${t.reference}`}
                    className="mt-1 size-4 shrink-0 accent-[var(--gold)]" />
                )}
                <div className="min-w-0">
                  <Link href={`/admin/support/${t.id}`} className="card-link block font-medium break-words text-foreground group-hover:text-gold-text">{t.subject}</Link>
                  <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                    <span className="font-mono">{t.reference}</span>
                    <span>{CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` › ${t.subcategoryName}` : ""}</span>
                    <span className="inline-flex items-center gap-1"><UserRound aria-hidden="true" className="size-3.5" />{t.requesterName}</span>
                    <span className="inline-flex items-center gap-1"><MessageSquare aria-hidden="true" className="size-3.5" />{formatDateTime(t.lastMessageAt)}</span>
                    {!can.financeOnly && t.team === "FINANCE" && (
                      <span className="inline-flex items-center gap-1 text-gold-text"><Landmark aria-hidden="true" className="size-3.5" />With finance</span>
                    )}
                  </p>
                </div>
              </div>
              <div className={`flex flex-wrap items-center gap-2 sm:justify-end ${bulk ? "pl-7" : ""} sm:pl-0`}>
                <StatusBadge tone={PRIORITY_TONE[t.priority]}>{PRIORITY_LABEL[t.priority]}</StatusBadge>
                <StatusBadge tone={TICKET_TONE[t.status]}>
                  {STAFF_STATUS_LABEL[t.status]}{(t.escalationLevel ?? 0) > 0 ? ` · L${t.escalationLevel}` : ""}
                </StatusBadge>
                {t.slaState && t.slaState !== "NONE" && (
                  <StatusBadge tone={SLA_TONE[t.slaState]}>
                    {t.slaState === "BREACHED" ? <ShieldAlert aria-hidden="true" className="size-3" /> : <CircleDot aria-hidden="true" className="size-3" />}
                    {SLA_LABEL[t.slaState]}
                  </StatusBadge>
                )}
                {t.assigneeName ? (
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-background px-2.5 py-0.5 text-xs font-medium ring-1 ring-border">
                    <UserRound aria-hidden="true" className="size-3" /> {t.assigneeName}
                  </span>
                ) : (
                  <span className="inline-flex rounded-full border border-dashed border-border px-2.5 py-0.5 text-xs text-muted">Unassigned</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={tickets} basePath="/admin/support" params={filters} />
    </SupportShell>
  );
}

const INPUT = "h-10 w-full rounded-lg border border-border bg-surface px-3 text-sm text-foreground outline-none focus:border-gold focus:ring-1 focus:ring-gold";

function Select({ name, label, value, options }: { name: string; label: string; value?: string; options: [string, string][] }) {
  return (
    <label className="space-y-1 text-xs text-muted">
      <span className="block">{label}</span>
      <select name={name} defaultValue={value ?? ""} className={INPUT}>
        <option value="">Any</option>
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  );
}
