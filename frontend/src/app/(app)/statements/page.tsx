import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { Notice } from "@/components/ui/notice";
import { PeriodForm } from "@/features/reports/period-form";
import { ReportPreview, thisMonth } from "@/features/reports/report-preview";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch } from "@/lib/server/auth/session";
import type { ReportDocument } from "@/types/report";

export const metadata: Metadata = { title: "Statements" };

const DATE = /^\d{4}-\d{2}-\d{2}$/;

export default async function StatementsPage({ searchParams }: PageProps<"/statements">) {
  const params = await searchParams;
  const preset = thisMonth();
  const from = typeof params.from === "string" && DATE.test(params.from) ? params.from : preset.from;
  const to = typeof params.to === "string" && DATE.test(params.to) ? params.to : preset.to;
  const query = new URLSearchParams({ from, to });
  let report: ReportDocument | null = null;
  let problem: string | null = null;
  try {
    report = await authFetch<ReportDocument>(`/api/v1/reports/statement?${query}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 400) problem = error.apiError?.message ?? "Choose a valid period.";
    else throw error;
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Statements" description="Your account activity for any period of up to a year, to view or download."
        actions={report ? (
          <div className="flex gap-3 text-sm">
            <a href={`/statements/download?${query}&format=pdf`} className="rounded-full border border-foreground/70 px-4 py-1.5 hover:bg-foreground hover:text-surface">Download PDF</a>
            <a href={`/statements/download?${query}&format=csv`} className="rounded-full border border-border px-4 py-1.5 text-muted hover:text-foreground">CSV</a>
          </div>
        ) : undefined} />
      <PeriodForm action="/statements" from={from} to={to} />
      {problem && <Notice tone="warning">{problem}</Notice>}
      {report && (
        <>
          <p className="text-sm text-muted">{report.subtitle}</p>
          <ReportPreview report={report} />
        </>
      )}
    </div>
  );
}
