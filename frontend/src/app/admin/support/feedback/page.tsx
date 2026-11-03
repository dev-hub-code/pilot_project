import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnless } from "@/components/layout/page-access";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { SupportShell } from "@/features/admin/support-shell";
import { PeriodForm } from "@/features/reports/period-form";
import { thisMonth } from "@/features/reports/report-preview";
import { CATEGORY_LABEL } from "@/features/support/labels";
import { Permission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { FeedbackRow, TicketCategory } from "@/types/support";
import { formatDate, formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Customer feedback" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;

export default async function FeedbackPage({ searchParams }: PageProps<"/admin/support/feedback">) {
  const denied = await deniedUnless(Permission.SUPPORT_TICKET_VIEW, Permission.SUPPORT_ANALYTICS_VIEW, Permission.REPORT_VIEW);
  if (denied) return denied;
  const params = await searchParams;
  const preset = thisMonth();
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : preset.from;
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : preset.to;
  const low = params.low === "1";
  const query = new URLSearchParams({ from, to, limit: "500" });
  if (low) query.set("maxRating", "2");
  const rows = await authFetch<FeedbackRow[]>(`/api/v1/admin/support/feedback?${query}`);
  const average = rows.length ? rows.reduce((s, r) => s + r.rating, 0) / rows.length : null;
  const satisfied = rows.length ? (rows.filter((r) => r.rating >= 4).length * 100) / rows.length : null;
  const byAgent = new Map<string, { name: string; sum: number; n: number }>();
  for (const r of rows) {
    const key = r.assigneeId ?? "none";
    const e = byAgent.get(key) ?? { name: r.assigneeName ?? "Unassigned", sum: 0, n: 0 };
    e.sum += r.rating;
    e.n += 1;
    byAgent.set(key, e);
  }
  const toggle = new URLSearchParams({ from, to, ...(low ? {} : { low: "1" }) });
  return (
    <SupportShell active="feedback">
      <PageHeader title="Customer feedback" description="Ratings customers gave resolved and closed tickets, one per ticket." />
      <PeriodForm action="/admin/support/feedback" from={from} to={to} hidden={low ? { low: "1" } : {}} />
      <div className="flex flex-wrap items-center gap-6 text-sm">
        <p><span className="text-muted">Ratings</span> <span className="ml-1 text-lg font-semibold tabular-nums">{rows.length}</span></p>
        <p><span className="text-muted">Average</span> <span className="ml-1 text-lg font-semibold tabular-nums">{average === null ? "—" : `${average.toFixed(2)} / 5`}</span></p>
        {!low && <p><span className="text-muted">Satisfied (4–5)</span> <span className="ml-1 text-lg font-semibold tabular-nums">{satisfied === null ? "—" : `${satisfied.toFixed(1)}%`}</span></p>}
        <Link href={`/admin/support/feedback?${toggle}`} className="ml-auto text-gold-text hover:underline">{low ? "Show all ratings" : "Only low ratings (1–2)"}</Link>
      </div>
      {byAgent.size > 0 && (
        <DataTable columns={["Agent", "Ratings", "Average"]}>
          {[...byAgent.entries()].sort((a, b) => b[1].n - a[1].n).map(([key, e]) => (
            <tr key={key}><Cell>{e.name}</Cell><Cell className="tabular-nums">{e.n}</Cell><Cell className="tabular-nums">{(e.sum / e.n).toFixed(2)}</Cell></tr>
          ))}
        </DataTable>
      )}
      {rows.length === 0 ? <EmptyState title="No ratings" description={`None between ${formatDate(from)} and ${formatDate(to)}.`} /> : (
        <DataTable columns={["Ticket", "Rating", "Comment", "Customer", "Agent", "When"]}>
          {rows.map((r) => (
            <tr key={r.ticketId} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/support/${r.ticketId}`} className="font-medium text-gold-text hover:underline">{r.subject}</Link>
                <span className="block text-xs text-muted"><span className="font-mono">{r.reference}</span> · {CATEGORY_LABEL[r.category as TicketCategory] ?? r.category}</span>
              </Cell>
              <Cell><StatusBadge tone={r.rating >= 4 ? "success" : r.rating === 3 ? "neutral" : "danger"}>{r.rating} / 5</StatusBadge></Cell>
              <Cell wrap className="max-w-sm text-muted">{r.comment ?? "—"}</Cell>
              <Cell>{r.customerName}</Cell>
              <Cell>{r.assigneeName ?? <span className="text-muted">—</span>}</Cell>
              <Cell className="text-muted">{formatDateTime(r.createdAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
    </SupportShell>
  );
}
