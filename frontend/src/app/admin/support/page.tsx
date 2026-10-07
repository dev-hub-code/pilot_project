import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { PRIORITY_TONE, STAFF_STATUS_LABEL, TICKET_TONE } from "@/features/support/labels";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Ticket, TicketStatus } from "@/types/support";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Support" };

const VIEWS = [
  { key: "queue", label: "Needs a reply", status: "OPEN" as TicketStatus },
  { key: "overdue", label: "Overdue", overdue: true },
  { key: "mine", label: "Assigned to me", assignee: "me" },
  { key: "unassigned", label: "Unassigned", assignee: "unassigned", status: "OPEN" as TicketStatus },
  { key: "waiting", label: "Waiting on customer", status: "WAITING_ON_CUSTOMER" as TicketStatus },
  { key: "resolved", label: "Resolved", status: "RESOLVED" as TicketStatus },
  { key: "closed", label: "Closed", status: "CLOSED" as TicketStatus },
] as const;

export default async function AdminSupportPage({ searchParams }: PageProps<"/admin/support">) {
  await requireStaff();
  const params = await searchParams;
  const view = VIEWS.find((v) => v.key === params.view) ?? VIEWS[0];
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if ("status" in view) query.set("status", view.status);
  if ("assignee" in view) query.set("assignee", view.assignee);
  if ("overdue" in view) query.set("overdue", "true");
  // The queue is worked oldest first; other views show the latest activity first.
  query.set("sort", view.key === "queue" || view.key === "overdue" ? "lastMessageAt,asc" : "lastMessageAt,desc");
  const tickets = await authFetch<PageResponse<Ticket>>(`/api/v1/admin/support/tickets?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Support" description="Investor requests. Overdue means no reply yet and past the response target for its priority." />
      <nav aria-label="Filter" className="flex flex-wrap gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/support?view=${v.key}`} aria-current={v === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>
      {tickets.content.length === 0 ? (
        <EmptyState title="Nothing here" />
      ) : (
        <DataTable columns={["Request", "Requester", "Priority", "Status", "Assignee", "Last message"]}>
          {tickets.content.map((t) => (
            <tr key={t.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/support/${t.id}`} className="font-medium text-gold-text hover:underline">{t.subject}</Link>
                <span className="block font-mono text-xs text-muted">{t.reference}</span>
              </Cell>
              <Cell>{t.requesterName}</Cell>
              <Cell>
                <StatusBadge tone={PRIORITY_TONE[t.priority]}>{humanize(t.priority)}</StatusBadge>
                {t.overdue && <span className="ml-2 text-xs font-medium text-rose-600 dark:text-rose-400">Overdue</span>}
              </Cell>
              <Cell><StatusBadge tone={TICKET_TONE[t.status]}>{STAFF_STATUS_LABEL[t.status]}</StatusBadge></Cell>
              <Cell>{t.assigneeName ?? <span className="text-muted">Unassigned</span>}</Cell>
              <Cell className="text-muted">{formatDateTime(t.lastMessageAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={tickets} basePath="/admin/support" params={{ view: view.key }} />
    </div>
  );
}
