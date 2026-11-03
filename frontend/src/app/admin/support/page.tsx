import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
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
        actions={can.create ? <LinkButton href="/admin/support/new">New ticket</LinkButton> : undefined} />

      <nav aria-label="Queues" className="flex flex-wrap gap-2 text-sm">
        {views.map((v) => (
          <Link key={v.key} href={`/admin/support?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      <form action="/admin/support" className="grid gap-3 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4">
        <input type="hidden" name="view" value={view.key} />
        <label className="space-y-1 text-sm sm:col-span-2">
          <span className="block font-medium">Search</span>
          <input name="q" type="search" defaultValue={q} maxLength={100} placeholder="Reference, subject, order, customer name, email or code"
            className="w-full rounded-lg border border-border bg-surface px-3 py-2 outline-none focus:border-gold focus:ring-1 focus:ring-gold" />
        </label>
        <Select name="priority" label="Priority" value={priority} options={PRIORITY_ORDER.map((p) => [p, PRIORITY_LABEL[p]])} />
        <Select name="category" label="Category" value={category} options={CATEGORY_ORDER.map((c) => [c, CATEGORY_LABEL[c]])} />
        <Select name="status" label="Status" value={status} options={STATUS_ORDER.map((s) => [s, STAFF_STATUS_LABEL[s]])} />
        <Select name="channel" label="Channel" value={channel}
          options={(["PORTAL", "STAFF", "SYSTEM", "EMAIL"] as const).map((c) => [c, CHANNEL_LABEL[c]])} />
        <label className="space-y-1 text-sm"><span className="block font-medium">Opened from</span>
          <input type="date" name="from" defaultValue={from} className="w-full rounded-lg border border-border bg-surface px-3 py-2" /></label>
        <label className="space-y-1 text-sm"><span className="block font-medium">Opened to</span>
          <input type="date" name="to" defaultValue={to} className="w-full rounded-lg border border-border bg-surface px-3 py-2" /></label>
        <div className="flex items-end gap-3 sm:col-span-2 lg:col-span-4">
          <Button type="submit" variant="secondary">Filter</Button>
          {filtered && <Link href={`/admin/support?view=${view.key}`} className="text-sm text-muted hover:underline">Clear filters</Link>}
          <div className="ml-auto"><RecentSearches current={q} /></div>
        </div>
      </form>

      {bulk && tickets.content.length > 0 && (
        <BulkAssignForm action={bulkAssignAction} agents={agents.map((a) => ({ value: a.userId, label: a.name }))} />
      )}

      {tickets.content.length === 0 ? (
        <EmptyState title="Nothing here" description={filtered ? "No ticket matches these filters." : "This queue is empty."} />
      ) : (
        <DataTable columns={[...(bulk ? ["Select"] : []), "Request", "Customer", "Priority", "Status", "SLA", "Assignee", "Last message"]}>
          {tickets.content.map((t) => (
            <tr key={t.id} className="hover:bg-background">
              {bulk && (
                <Cell>
                  <input type="checkbox" name="ticketId" value={t.id} form={BULK_FORM} aria-label={`Select ${t.reference}`} className="size-4" />
                </Cell>
              )}
              <Cell>
                <Link href={`/admin/support/${t.id}`} className="card-link font-medium text-gold-text hover:underline">{t.subject}</Link>
                <span className="block text-xs text-muted">
                  <span className="font-mono">{t.reference}</span> · {CATEGORY_LABEL[t.category]}{t.subcategoryName ? ` › ${t.subcategoryName}` : ""}
                  {!can.financeOnly && t.team === "FINANCE" && " · With finance"}
                </span>
              </Cell>
              <Cell>{t.requesterName}</Cell>
              <Cell><StatusBadge tone={PRIORITY_TONE[t.priority]}>{PRIORITY_LABEL[t.priority]}</StatusBadge></Cell>
              <Cell>
                <StatusBadge tone={TICKET_TONE[t.status]}>{STAFF_STATUS_LABEL[t.status]}</StatusBadge>
                {(t.escalationLevel ?? 0) > 0 && <span className="ml-2 text-xs text-muted">L{t.escalationLevel}</span>}
              </Cell>
              <Cell>{t.slaState && t.slaState !== "NONE" ? <StatusBadge tone={SLA_TONE[t.slaState]}>{SLA_LABEL[t.slaState]}</StatusBadge> : <span className="text-muted">—</span>}</Cell>
              <Cell>{t.assigneeName ?? <span className="text-muted">Unassigned</span>}</Cell>
              <Cell className="text-muted">{formatDateTime(t.lastMessageAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={tickets} basePath="/admin/support" params={filters} />
    </SupportShell>
  );
}

function Select({ name, label, value, options }: { name: string; label: string; value?: string; options: [string, string][] }) {
  return (
    <label className="space-y-1 text-sm">
      <span className="block font-medium">{label}</span>
      <select name={name} defaultValue={value ?? ""} className="w-full rounded-lg border border-border bg-surface px-3 py-2 outline-none focus:border-gold focus:ring-1 focus:ring-gold">
        <option value="">Any</option>
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </label>
  );
}
