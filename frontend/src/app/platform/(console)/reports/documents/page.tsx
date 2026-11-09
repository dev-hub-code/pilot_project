import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { ReportsShell } from "@/features/analytics/reports-shell";
import { PeriodForm } from "@/features/reports/period-form";
import { ReportPreview, thisMonth } from "@/features/reports/report-preview";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { ReportDocument } from "@/types/report";
import { EmailText } from "@/components/ui/email-text";
import { DownloadLink } from "@/features/analytics/report-ui";
import { BarChart3, Layers, type LucideIcon, Search, UserRound } from "lucide-react";

export const metadata: Metadata = { title: "Statements & summaries" };

const REPORTS = [
  { key: "financial-summary", label: "Financial summary", description: "Returns, fees, commissions, withdrawals and balances owed, per currency." },
  { key: "offerings", label: "Plans & payouts", description: "Investments per plan, and payouts due and not yet paid." },
  { key: "statement", label: "Investor statement", description: "One investor's account activity, holdings and withdrawals." },
] as const;
const REPORT_ICON: Record<(typeof REPORTS)[number]["key"], LucideIcon> = {
  "financial-summary": BarChart3, offerings: Layers, statement: UserRound,
};
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export default async function ReportsPage({ searchParams }: PageProps<"/platform/reports/documents">) {
  const denied = await deniedUnlessInMenu("/platform/reports");
  if (denied) return denied;
  const session = await requireStaff();
  const canDownload = hasPermission(session.permissions, Permission.REPORT_GENERATE);
  const params = await searchParams;
  const report = REPORTS.find((r) => r.key === params.report) ?? REPORTS[0];
  const preset = thisMonth();
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : preset.from;
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : preset.to;
  const userId = typeof params.userId === "string" && isUuid(params.userId) ? params.userId : null;

  const query = new URLSearchParams(report.key === "offerings" ? {} : { from, to });
  if (report.key === "statement" && userId) query.set("userId", userId);
  const ready = report.key !== "statement" || userId !== null;
  let document: ReportDocument | null = null;
  let problem: string | null = null;
  if (ready) {
    try {
      document = await authFetch<ReportDocument>(`/api/v1/admin/reports/${report.key}?${query}`);
    } catch (error) {
      if (error instanceof BackendError && (error.status === 400 || error.status === 404)) {
        problem = error.apiError?.message ?? "Check the report's parameters.";
      } else throw error;
    }
  }
  const download = (format: "pdf" | "csv") => `/platform/reports/download?${new URLSearchParams({ report: report.key, format, ...Object.fromEntries(query) })}`;

  return (
    <ReportsShell active="documents">
      <PageHeader title="Statements & summaries" description={report.description}
        actions={document && canDownload ? (
          <div className="flex flex-wrap gap-2">
            <DownloadLink href={download("pdf")} format="pdf" primary label="Download PDF" />
            <DownloadLink href={download("csv")} format="csv" />
          </div>
        ) : undefined} />
      <nav aria-label="Report" className="grid gap-3 sm:grid-cols-3">
        {REPORTS.map((r) => {
          const Icon = REPORT_ICON[r.key];
          const on = r === report;
          return (
            <Link key={r.key} href={`/platform/reports/documents?report=${r.key}`} aria-current={on ? "page" : undefined}
              className={`group flex items-start gap-3 rounded-2xl border p-4 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:shadow-md ${on
                ? "border-gold bg-gold/5 ring-2 ring-gold/25" : "border-border bg-surface hover:border-gold/40"}`}>
              <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-xl transition-colors ${on
                ? "bg-ink text-on-ink dark:bg-gold dark:text-on-gold" : "bg-gold/10 text-gold-text"}`}>
                <Icon className="size-5" />
              </span>
              <span className="min-w-0">
                <span className={`block font-semibold ${on ? "text-gold-text" : ""}`}>{r.label}</span>
                <span className="block text-xs text-muted">{r.description}</span>
              </span>
            </Link>
          );
        })}
      </nav>
      {report.key === "statement" && (
        <form action="/platform/reports/documents" role="search"
          className="flex w-full max-w-xl items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold">
          <input type="hidden" name="report" value="statement" />
          <input type="hidden" name="from" value={from} />
          <input type="hidden" name="to" value={to} />
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="userId" defaultValue={userId ?? ""} autoComplete="off" placeholder="Investor user id" aria-label="Investor user id"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 font-mono text-sm outline-none" />
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Load</button>
        </form>
      )}
      {report.key !== "offerings" && (
        <PeriodForm action="/platform/reports/documents" from={from} to={to}
          hidden={{ report: report.key, ...(userId ? { userId } : {}) }} />
      )}
      {!canDownload && <p className="text-xs text-muted">Downloading PDF or CSV needs the REPORT_GENERATE permission.</p>}
      {!ready && <Notice>Enter an investor&apos;s user id (from their page under Users).</Notice>}
      {problem && <Notice tone="warning">{problem}</Notice>}
      {document && (
        <>
          <p className="text-sm text-muted"><EmailText>{document.subtitle}</EmailText></p>
          <ReportPreview report={document} />
        </>
      )}
    </ReportsShell>
  );
}
