import type { Metadata } from "next";
import Link from "next/link";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { SupportShell } from "@/features/admin/support-shell";
import { STAFF_STATUS_LABEL, TICKET_TONE, TRIGGER_LABEL } from "@/features/support/labels";
import { Permission } from "@/lib/permissions";
import { authFetch } from "@/lib/server/auth/session";
import type { Escalation, EscalationLevel } from "@/types/support";
import { formatDateTime } from "@/utils/format";

export const metadata: Metadata = { title: "Escalations" };

export default async function EscalationsPage({ searchParams }: PageProps<"/admin/support/escalations">) {
  const denied = await deniedUnless(Permission.SUPPORT_TICKET_VIEW, Permission.SUPPORT_ANALYTICS_VIEW);
  if (denied) return denied;
  const all = (await searchParams).show === "all";
  const [rows, levels] = await Promise.all([
    authFetch<Escalation[]>(`/api/v1/admin/support/escalations?openOnly=${!all}&limit=200`),
    authFetch<EscalationLevel[]>("/api/v1/admin/support/escalation-levels").catch(() => [] as EscalationLevel[]),
  ]);
  return (
    <SupportShell active="escalations">
      <PageHeader title="Escalations" description="Every escalation is recorded permanently: who or what raised it, why, and to which level." />
      <nav className="flex gap-2 text-sm" aria-label="Show">
        <Link href="/admin/support/escalations" aria-current={!all ? "page" : undefined}
          className={`rounded-full border px-3 py-1 ${!all ? "border-foreground" : "border-border text-muted"}`}>Open tickets</Link>
        <Link href="/admin/support/escalations?show=all" aria-current={all ? "page" : undefined}
          className={`rounded-full border px-3 py-1 ${all ? "border-foreground" : "border-border text-muted"}`}>All</Link>
      </nav>
      {rows.length === 0 ? <EmptyState title="No escalations" description={all ? "Nothing has been escalated yet." : "No open ticket is escalated."} /> : (
        <DataTable columns={["Ticket", "Level", "Trigger", "Reason", "Raised by", "Owner", "When", "Ticket status"]}>
          {rows.map((e) => (
            <tr key={e.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/support/${e.ticketId}`} className="font-medium text-gold-text hover:underline">{e.subject}</Link>
                <span className="block font-mono text-xs text-muted">{e.reference}</span>
              </Cell>
              <Cell className="tabular-nums">L{e.previousLevel} → L{e.level}</Cell>
              <Cell>{TRIGGER_LABEL[e.trigger]}</Cell>
              <Cell wrap className="max-w-xs text-muted">{e.reason}</Cell>
              <Cell>{e.escalatedByName ?? <span className="text-muted">Automatic</span>}</Cell>
              <Cell>{e.ownerName ?? <span className="text-muted">—</span>}</Cell>
              <Cell className="text-muted">{formatDateTime(e.createdAt)}</Cell>
              <Cell><StatusBadge tone={TICKET_TONE[e.status]}>{STAFF_STATUS_LABEL[e.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {levels.length > 0 && (
        <Card title="Escalation levels" description="Contacts are notified when a ticket reaches their level. Configured in support settings.">
          <ol className="grid gap-4 text-sm sm:grid-cols-2 xl:grid-cols-4">
            {levels.map((l) => (
              <li key={l.level} className="space-y-1">
                <p className="font-medium">L{l.level} · {l.name}</p>
                {l.description && <p className="text-muted">{l.description}</p>}
                <p className="text-xs text-muted">{l.contacts.length === 0 ? "No contacts: the assignee and support team are told" : l.contacts.map((c) => c.name).join(", ")}</p>
              </li>
            ))}
          </ol>
        </Card>
      )}
    </SupportShell>
  );
}
