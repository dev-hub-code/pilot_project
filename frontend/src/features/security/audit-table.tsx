import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { StatusBadge } from "@/components/ui/status-badge";
import type { AuditLogEntry } from "@/types/security";
import { formatDateTime, humanize } from "@/utils/format";
import { actionLabel, actionTone, entityHref, personLabel } from "./labels";

/** Audit entries, newest first; each row opens the entry. */
export function AuditTable({ entries, showIp = true }: { entries: readonly AuditLogEntry[]; showIp?: boolean }) {
  return (
    <DataTable columns={["Time", "Action", "By", "About", ...(showIp ? ["IP address"] : [])]}>
      {entries.map((entry) => (
        <tr key={entry.id} className="hover:bg-background">
          <Cell className="text-muted tabular-nums">
            <Link href={`/admin/audit/${entry.id}`} className="hover:text-foreground hover:underline">
              {formatDateTime(entry.createdAt)}
            </Link>
          </Cell>
          <Cell>
            <Link href={`/admin/audit/${entry.id}`} className="hover:underline">
              <StatusBadge tone={actionTone(entry.action)}>{actionLabel(entry.action)}</StatusBadge>
            </Link>
          </Cell>
          <Cell className="max-w-64 truncate">
            {entry.actor ? (
              <Link href={`/admin/users/${entry.actor.id}`} className="text-brand hover:underline" title={personLabel(entry.actor)}>
                {entry.actor.email ?? "Deleted account"}
              </Link>
            ) : (
              <span className="text-muted">{entry.action === "LOGIN_FAILED" ? "Unknown visitor" : "System"}</span>
            )}
          </Cell>
          <Cell className="max-w-72 truncate">
            <EntityLink entry={entry} />
          </Cell>
          {showIp && <Cell className="font-mono text-xs text-muted">{entry.ipAddress ?? "—"}</Cell>}
        </tr>
      ))}
    </DataTable>
  );
}

/** What the entry is about: the account by email, or the record by type and id, linked where it has a page. */
export function EntityLink({ entry }: { entry: AuditLogEntry }) {
  const href = entityHref(entry.entityType, entry.entityId);
  const text = entry.subject?.email
    ?? `${humanize(entry.entityType)}${entry.entityId ? ` ${entry.entityId.length > 12 ? `${entry.entityId.slice(0, 8)}…` : entry.entityId}` : ""}`;
  return href ? (
    <Link href={href} className="text-brand hover:underline" title={entry.entityId ?? undefined}>{text}</Link>
  ) : (
    <span className="text-muted" title={entry.entityId ?? undefined}>{text}</span>
  );
}
