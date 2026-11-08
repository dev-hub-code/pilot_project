import Link from "next/link";
import type { AuditLogEntry } from "@/types/security";
import { humanize } from "@/utils/format";
import { entityHref } from "./labels";

/** What the entry is about: the account by email, or the record by type and id, linked where it has a page. */
export function EntityLink({ entry }: { entry: AuditLogEntry }) {
  const href = entityHref(entry.entityType, entry.entityId);
  const text = entry.subject?.email
    ?? `${humanize(entry.entityType)}${entry.entityId ? ` ${entry.entityId.length > 12 ? `${entry.entityId.slice(0, 8)}…` : entry.entityId}` : ""}`;
  return href ? (
    <Link href={href} className="text-gold-text hover:underline" title={entry.entityId ?? undefined}>{text}</Link>
  ) : (
    <span className="text-muted" title={entry.entityId ?? undefined}>{text}</span>
  );
}
