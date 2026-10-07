import type { Metadata } from "next";
import Link from "next/link";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { TextField } from "@/components/ui/text-field";
import { Button } from "@/components/ui/button";
import { PeriodForm } from "@/features/reports/period-form";
import { ReportPreview, thisMonth } from "@/features/reports/report-preview";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { ReportDocument } from "@/types/report";

export const metadata: Metadata = { title: "Reports" };

const REPORTS = [
  { key: "financial-summary", label: "Financial summary", description: "Rent, fees, commissions, withdrawals and balances owed, per currency." },
  { key: "offerings", label: "Plans & payouts", description: "Containers sold and in stock per plan, and payouts due and not yet paid." },
  { key: "statement", label: "Investor statement", description: "One investor's account activity, holdings and withdrawals." },
] as const;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export default async function ReportsPage({ searchParams }: PageProps<"/admin/reports">) {
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
  const download = (format: "pdf" | "csv") => `/admin/reports/download?${new URLSearchParams({ report: report.key, format, ...Object.fromEntries(query) })}`;

  return (
    <div className="space-y-6">
      <PageHeader title="Reports" description={report.description}
        actions={document && canDownload ? (
          <div className="flex gap-3 text-sm">
            <a href={download("pdf")} className="rounded-full border border-foreground/70 px-4 py-1.5 hover:bg-foreground hover:text-surface">Download PDF</a>
            <a href={download("csv")} className="rounded-full border border-border px-4 py-1.5 text-muted hover:text-foreground">CSV</a>
          </div>
        ) : undefined} />
      <nav aria-label="Report" className="flex flex-wrap gap-2 text-sm">
        {REPORTS.map((r) => (
          <Link key={r.key} href={`/admin/reports?report=${r.key}`} aria-current={r === report ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${r === report ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {r.label}
          </Link>
        ))}
      </nav>
      {report.key === "statement" && (
        <form action="/admin/reports" className="flex flex-wrap items-end gap-3">
          <input type="hidden" name="report" value="statement" />
          <input type="hidden" name="from" value={from} />
          <input type="hidden" name="to" value={to} />
          <div className="w-96"><TextField label="Investor user id" name="userId" defaultValue={userId ?? ""} autoComplete="off" /></div>
          <Button type="submit" variant="secondary">Load</Button>
        </form>
      )}
      {report.key !== "offerings" && (
        <PeriodForm action="/admin/reports" from={from} to={to}
          hidden={{ report: report.key, ...(userId ? { userId } : {}) }} />
      )}
      {!canDownload && <p className="text-xs text-muted">Downloading PDF or CSV needs the REPORT_GENERATE permission.</p>}
      {!ready && <Notice>Enter an investor&apos;s user id (from their page under Users).</Notice>}
      {problem && <Notice tone="warning">{problem}</Notice>}
      {document && (
        <>
          <p className="text-sm text-muted">{document.subtitle}</p>
          <ReportPreview report={document} />
        </>
      )}
    </div>
  );
}
