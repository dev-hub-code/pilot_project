import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { EntityLink } from "@/features/security/audit-table";
import { CATEGORY_LABEL, actionLabel, actionTone, personLabel } from "@/features/security/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { AuditLogEntry } from "@/types/security";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Audit entry" };

export default async function AuditEntryPage({ params }: PageProps<"/admin/audit/[id]">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AUDIT_VIEW)) {
    return <EmptyState title="Not available" description="The audit log needs the AUDIT_VIEW permission." />;
  }
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const entry = await authFetch<AuditLogEntry>(`/api/v1/admin/audit-logs/${id}`).catch((error: unknown) => {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  });
  const fields = [...new Set([...Object.keys(entry.oldValue ?? {}), ...Object.keys(entry.newValue ?? {})])];

  return (
    <div className="space-y-6">
      <nav aria-label="Breadcrumb" className="text-sm text-muted">
        <Link href="/admin/audit" className="hover:text-foreground">Audit log</Link> / <span className="font-mono">{entry.id.slice(0, 8)}</span>
      </nav>
      <PageHeader title={actionLabel(entry.action)} description={formatDateTime(entry.createdAt)}
        actions={<StatusBadge tone={actionTone(entry.action)}>{CATEGORY_LABEL[entry.category]}</StatusBadge>} />

      <Card>
        <dl className="grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2 [&>div]:min-w-0">
          <Detail label="Action"><span className="font-mono text-xs">{entry.action}</span></Detail>
          <Detail label="By">
            {entry.actor ? (
              <Link href={`/admin/users/${entry.actor.id}`} className="text-brand hover:underline">{personLabel(entry.actor)}</Link>
            ) : entry.action === "LOGIN_FAILED" ? "Unknown visitor (wrong password)" : "System"}
          </Detail>
          <Detail label="About">
            <EntityLink entry={entry} />
            <span className="block font-mono text-xs break-all text-muted">{humanize(entry.entityType)} · {entry.entityId ?? "—"}</span>
          </Detail>
          <Detail label="IP address"><span className="font-mono text-xs">{entry.ipAddress ?? "—"}</span></Detail>
          <Detail label="Browser / device"><span className="text-xs break-words text-muted">{entry.userAgent ?? "—"}</span></Detail>
          <Detail label="Correlation id"><span className="font-mono text-xs break-all text-muted">{entry.correlationId ?? "—"}</span></Detail>
        </dl>
      </Card>

      <Card title="What changed" description="Only the fields that matter for the action; secrets are never logged">
        {fields.length === 0 ? (
          <p className="text-sm text-muted">No values were recorded for this action.</p>
        ) : (
          <DataTable columns={["Field", "Before", "After"]}>
            {fields.map((field) => (
              <tr key={field}>
                <Cell className="font-mono text-xs">{field}</Cell>
                <Cell className="whitespace-normal font-mono text-xs break-all text-muted">{show(entry.oldValue, field)}</Cell>
                <Cell className="whitespace-normal font-mono text-xs break-all">{show(entry.newValue, field)}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </Card>
    </div>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-1">{children}</dd>
    </div>
  );
}

function show(values: Record<string, unknown> | null, field: string): string {
  if (!values || !(field in values)) return "—";
  const value = values[field];
  return typeof value === "string" ? value : JSON.stringify(value);
}
