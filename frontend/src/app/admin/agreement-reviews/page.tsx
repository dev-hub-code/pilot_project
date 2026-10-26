import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AgreementReviewTask } from "@/types/agreement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Agreement reviews" };

const VIEWS = [
  { key: "OPEN", label: "To do" },
  { key: "MINE", label: "Assigned to me" },
  { key: "DONE", label: "Done" },
] as const;
type View = (typeof VIEWS)[number]["key"];

export default async function AgreementReviewsPage({ searchParams }: PageProps<"/admin/agreement-reviews">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AGREEMENT_REVIEW)) {
    return <EmptyState title="Not available" description="Agreement reviews need the agreement review permission." />;
  }
  const params = await searchParams;
  const view: View = VIEWS.find((v) => v.key === params.view)?.key ?? "OPEN";
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({
    status: view === "DONE" ? "DONE" : "OPEN", mine: String(view === "MINE"), page: String(page), size: "50",
  });
  const tasks = await authFetch<PageResponse<AgreementReviewTask>>(`/api/v1/admin/agreement-reviews?${query}`);
  const done = view === "DONE";

  return (
    <div className="space-y-6">
      <PageHeader title="Agreement reviews"
        description="Every lease agreement issued when a container is assigned is a task here. Assign one to yourself, read the agreement, add comments as you go, then mark it done." />

      <nav aria-label="Filter" className="flex flex-wrap items-center gap-2 text-sm">
        {VIEWS.map((v) => (
          <Link key={v.key} href={`/admin/agreement-reviews?view=${v.key}`} aria-current={v.key === view ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${v.key === view ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {v.label}
          </Link>
        ))}
      </nav>

      {tasks.content.length === 0 ? (
        <EmptyState title={done ? "No reviews done yet" : view === "MINE" ? "Nothing assigned to you" : "Nothing to do"}
          description={view === "OPEN" ? "A task appears here whenever a lease agreement is issued." : undefined} />
      ) : (
        <DataTable columns={["Agreement", "Investor", "Container", done ? "Completed" : "Assigned to", "Comments"]}>
          {tasks.content.map((t) => (
            <tr key={t.id} className="align-top">
              <Cell>
                <Link href={`/admin/agreement-reviews/${t.id}`} className="font-mono text-gold-text hover:underline">
                  {t.agreement.agreementNumber}
                </Link>
                <span className="block text-xs text-muted">Issued {formatDateTime(t.agreement.generatedAt)}</span>
              </Cell>
              <Cell>
                <span className="block font-mono text-xs text-muted">{t.investor.userCode ?? "Investor"}</span>
                {t.investor.name}
              </Cell>
              <Cell className="font-mono text-sm">{t.agreement.containerNumber}</Cell>
              <Cell className="text-sm">
                {done && t.completedBy ? (
                  <>
                    {t.completedBy.name}
                    <span className="block text-xs text-muted">{formatDateTime(t.completedBy.assignedAt)}</span>
                  </>
                ) : t.assignee ? (
                  t.assignee.id === session.userId ? <StatusBadge tone="neutral">You</StatusBadge> : t.assignee.name
                ) : (
                  <StatusBadge tone="warning">Unassigned</StatusBadge>
                )}
              </Cell>
              <Cell className="tabular-nums">{t.comments}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={tasks} basePath="/admin/agreement-reviews" params={{ view }} />
    </div>
  );
}
