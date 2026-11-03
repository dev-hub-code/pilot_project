import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ReportsShell } from "@/features/analytics/reports-shell";
import { authFetch } from "@/lib/server/auth/session";
import type { HistoryPage } from "@/types/analytics";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Download history" };

function describe(filters: string): string {
  try {
    const parsed = JSON.parse(filters) as Record<string, string | string[]>;
    const parts = Object.entries(parsed).map(([k, v]) => `${humanize(k)}: ${Array.isArray(v) ? v.map(humanize).join(", ") : v}`);
    return parts.length ? parts.join(" · ") : "No filters";
  } catch {
    return "—";
  }
}

export default async function ReportHistoryPage({ searchParams }: PageProps<"/admin/reports/history">) {
  const denied = await deniedUnlessInMenu("/admin/reports");
  if (denied) return denied;
  const params = await searchParams;
  const page = Math.max(0, Number.parseInt(typeof params.page === "string" ? params.page : "0", 10) || 0);
  const history = await authFetch<HistoryPage>(`/api/v1/admin/analytics/history?page=${page}&size=25`);
  const pages = Math.max(1, Math.ceil(history.total / history.size));
  return (
    <ReportsShell active="history">
      <PageHeader title="Download history"
        description={history.everyone ? "Every report downloaded, by anyone (you can see the audit log)." : "The reports you have downloaded."} />
      {history.executions.length === 0 ? (
        <EmptyState title="No downloads yet" description="Reports downloaded as Excel, PDF or CSV are listed here." />
      ) : (
        <DataTable columns={["When", "Report", "Format", "Filters", "Rows", "Time", "By", "Status"]}>
          {history.executions.map((e) => (
            <tr key={e.id}>
              <Cell>{formatDateTime(e.executedAt)}</Cell>
              <Cell><Link href={`/admin/reports/${e.reportKey}`} className="text-gold-text hover:underline">{e.reportTitle}</Link></Cell>
              <Cell>{e.format === "XLSX" ? "Excel" : e.format}</Cell>
              <Cell wrap className="max-w-md text-xs text-muted">{describe(e.filters)}</Cell>
              <Cell className="text-right tabular-nums">{e.rows ?? "—"}</Cell>
              <Cell className="text-right tabular-nums">{e.durationMs === null ? "—" : `${(e.durationMs / 1000).toFixed(1)} s`}</Cell>
              <Cell>{e.executedBy}</Cell>
              <Cell>
                <StatusBadge tone={e.status === "COMPLETED" ? "success" : "danger"}>{e.status === "COMPLETED" ? "Completed" : "Failed"}</StatusBadge>
                {e.failure && <p className="mt-1 text-xs text-muted">{e.failure}</p>}
              </Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {pages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between text-sm">
          <span className="text-muted">Page {page + 1} of {pages} · {history.total} downloads</span>
          <span className="flex gap-2">
            {page > 0 && <Link href={`/admin/reports/history?page=${page - 1}`} className="rounded-full border border-foreground/70 px-4 py-1.5">Previous</Link>}
            {page + 1 < pages && <Link href={`/admin/reports/history?page=${page + 1}`} className="rounded-full border border-foreground/70 px-4 py-1.5">Next</Link>}
          </span>
        </nav>
      )}
    </ReportsShell>
  );
}
