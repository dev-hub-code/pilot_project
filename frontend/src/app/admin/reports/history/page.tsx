import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnlessInMenu } from "@/components/layout/page-access";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ReportsShell } from "@/features/analytics/reports-shell";
import { authFetch } from "@/lib/server/auth/session";
import type { HistoryPage } from "@/types/analytics";
import { formatDateTime, humanize } from "@/utils/format";
import { PagerLinks } from "@/features/analytics/report-ui";
import { Clock, FileDown, FileSpreadsheet, FileText, Rows3, Timer, UserRound } from "lucide-react";

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
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          {history.executions.map((e) => {
            const format = e.format === "XLSX" ? "xlsx" : e.format === "PDF" ? "pdf" : "csv";
            const Icon = format === "xlsx" ? FileSpreadsheet : format === "pdf" ? FileText : FileDown;
            const failed = e.status !== "COMPLETED";
            return (
              <li key={e.id} className="grid gap-3 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
                <div className="flex min-w-0 items-start gap-3">
                  <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl ${failed
                    ? "bg-rose-100 text-rose-700 dark:bg-rose-500/15 dark:text-rose-300" : "bg-gold/10 text-gold-text"}`}>
                    <Icon className="size-5" />
                  </span>
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2">
                      <Link href={`/admin/reports/${e.reportKey}`} className="font-medium hover:text-gold-text hover:underline">{e.reportTitle}</Link>
                      <span className="rounded-full bg-background px-2 py-0.5 text-[11px] font-semibold text-muted ring-1 ring-border">{e.format === "XLSX" ? "Excel" : e.format}</span>
                    </p>
                    <p className="mt-0.5 text-xs break-words text-muted">{describe(e.filters)}</p>
                    <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted">
                      <span className="inline-flex items-center gap-1"><Clock aria-hidden="true" className="size-3" />{formatDateTime(e.executedAt)}</span>
                      <span className="inline-flex items-center gap-1"><UserRound aria-hidden="true" className="size-3" />{e.executedBy}</span>
                      {e.rows !== null && <span className="inline-flex items-center gap-1"><Rows3 aria-hidden="true" className="size-3" />{e.rows.toLocaleString("en-IN")} rows</span>}
                      {e.durationMs !== null && <span className="inline-flex items-center gap-1"><Timer aria-hidden="true" className="size-3" />{(e.durationMs / 1000).toFixed(1)} s</span>}
                    </p>
                    {e.failure && <p className="mt-1 text-xs text-rose-700 dark:text-rose-400">{e.failure}</p>}
                  </div>
                </div>
                <div className="pl-[3.25rem] sm:pl-0">
                  <StatusBadge tone={failed ? "danger" : "success"}>{failed ? "Failed" : "Completed"}</StatusBadge>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <PagerLinks page={page} pages={pages} summary={`${history.total.toLocaleString("en-IN")} downloads`}
        prev={page > 0 ? `/admin/reports/history?page=${page - 1}` : null}
        next={page + 1 < pages ? `/admin/reports/history?page=${page + 1}` : null} />
    </ReportsShell>
  );
}
