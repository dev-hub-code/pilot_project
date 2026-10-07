import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { CATEGORY_LABEL, TICKET_STATUS_LABEL, TICKET_TONE } from "@/features/support/labels";
import { authFetch } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Ticket } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Support" };

export default async function SupportPage({ searchParams }: PageProps<"/support">) {
  const page = Math.max(0, Number.parseInt(String((await searchParams).page ?? "0"), 10) || 0);
  const tickets = await authFetch<PageResponse<Ticket>>(`/api/v1/support/tickets?page=${page}&size=20`);
  return (
    <div className="space-y-6">
      <PageHeader title="Support" description="Questions about your account, investments or payouts. We usually reply within a day."
        actions={<LinkButton href="/support/new">New request</LinkButton>} />
      {tickets.content.length === 0 ? (
        <EmptyState title="No support requests" description="Open one whenever you need help." />
      ) : (
        <>
          <DataTable columns={["Request", "About", "Status", "Last update"]}>
            {tickets.content.map((t) => (
              <tr key={t.id} className="hover:bg-background">
                <Cell>
                  <Link href={`/support/${t.id}`} className="font-medium text-gold-text hover:underline">{t.subject}</Link>
                  <span className="block font-mono text-xs text-muted">{t.reference}</span>
                </Cell>
                <Cell className="text-muted">{CATEGORY_LABEL[t.category]}{t.relatedLabel ? ` · ${t.relatedLabel}` : ""}</Cell>
                <Cell><StatusBadge tone={TICKET_TONE[t.status]}>{TICKET_STATUS_LABEL[t.status]}</StatusBadge></Cell>
                <Cell className="text-muted">{formatDateTime(t.lastMessageAt)}</Cell>
              </tr>
            ))}
          </DataTable>
          <Pagination page={tickets} basePath="/support" params={{}} />
        </>
      )}
    </div>
  );
}
