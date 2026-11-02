import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { AnnouncementForm } from "@/features/admin/announcement-form";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import { AUDIENCE_LABEL, type Announcement } from "@/types/announcement";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Announcements" };

export default async function AnnouncementsPage({ searchParams }: PageProps<"/admin/announcements">) {
  const denied = await deniedUnlessInMenu("/admin/announcements");
  if (denied) return denied;
  await requireStaff();
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const sent = await authFetch<PageResponse<Announcement>>(`/api/v1/admin/announcements?page=${page}&size=20`);

  return (
    <div className="space-y-6">
      <PageHeader title="Announcements"
        description="Send a message to everyone, all investors, online or network investors, or staff. It appears at once in each person's notifications." />

      <Card title="New announcement">
        <AnnouncementForm />
      </Card>

      <section className="space-y-3" aria-label="Sent announcements">
        <h2 className="text-lg font-semibold tracking-tight">Sent</h2>
        {sent.content.length === 0 ? (
          <EmptyState title="No announcements yet" />
        ) : (
          <DataTable columns={["Announcement", "Sent to", "Reached", "Sent", "By"]}>
            {sent.content.map((a) => (
              <tr key={a.id} className="align-top">
                <Cell>
                  <span className="block font-medium">{a.title}</span>
                  <span className="block max-w-xl text-xs whitespace-pre-line text-muted">{a.body}</span>
                  {a.link && <span className="mt-1 block font-mono text-xs text-muted">{a.link}</span>}
                </Cell>
                <Cell>{AUDIENCE_LABEL[a.audience]}</Cell>
                <Cell className="tabular-nums">{a.recipients.toLocaleString("en-IN")}</Cell>
                <Cell className="text-muted">{formatDateTime(a.createdAt)}</Cell>
                <Cell className="text-muted">{a.createdByName ?? "—"}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
        <Pagination page={sent} basePath="/admin/announcements" params={{}} />
      </section>
    </div>
  );
}
