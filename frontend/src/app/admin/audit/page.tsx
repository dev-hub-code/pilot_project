import type { Metadata } from "next";
import { Button, buttonBase, buttonVariants } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { TextField } from "@/components/ui/text-field";
import { AuditTable } from "@/features/security/audit-table";
import { ACTIONS_BY_CATEGORY, CATEGORY_LABEL, actionLabel } from "@/features/security/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AuditCategory, AuditLogEntry } from "@/types/security";

export const metadata: Metadata = { title: "Audit log" };

const CATEGORIES = Object.keys(CATEGORY_LABEL) as AuditCategory[];
const DATE = /^\d{4}-\d{2}-\d{2}$/;

function text(value: unknown, max: number): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim().slice(0, max) : undefined;
}

export default async function AuditLogPage({ searchParams }: PageProps<"/admin/audit">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.AUDIT_VIEW)) {
    return <EmptyState title="Not available" description="The audit log needs the AUDIT_VIEW permission." />;
  }
  const params = await searchParams;
  const category = CATEGORIES.find((c) => c === params.category);
  const actions = category ? ACTIONS_BY_CATEGORY[category] : CATEGORIES.flatMap((c) => ACTIONS_BY_CATEGORY[c]);
  const filters = {
    category,
    action: actions.find((a) => a === params.action),
    actor: text(params.actor, 320),
    entityType: text(params.entityType, 64),
    entityId: text(params.entityId, 64),
    ip: text(params.ip, 45),
    from: typeof params.from === "string" && DATE.test(params.from) ? params.from : undefined,
    to: typeof params.to === "string" && DATE.test(params.to) ? params.to : undefined,
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const entries = await authFetch<PageResponse<AuditLogEntry>>(`/api/v1/admin/audit-logs?${query}&page=${page}&size=50`);
  const filtered = query.size > 0;

  return (
    <div className="space-y-6">
      <PageHeader title="Audit log"
        description="Every sign-in, permission change, document opened and money movement, newest first. Entries cannot be changed or deleted."
        actions={
          // A plain link: a prefetch would run (and audit) an export nobody asked for.
          <a href={`/admin/audit/export?${query}`} className={`${buttonBase} ${buttonVariants.secondary}`}>Download CSV</a>
        } />

      <form className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4" role="search">
        <SelectField label="Category" name="category" placeholder="Any" defaultValue={filters.category ?? ""}
          options={CATEGORIES.map((c) => ({ value: c, label: CATEGORY_LABEL[c] }))} />
        <SelectField label="Action" name="action" placeholder="Any" defaultValue={filters.action ?? ""}
          options={[...actions].sort((a, b) => actionLabel(a).localeCompare(actionLabel(b))).map((a) => ({ value: a, label: actionLabel(a) }))} />
        <TextField label="By (email or user id)" name="actor" type="search" placeholder="name@example.com" defaultValue={filters.actor} />
        <TextField label="IP address" name="ip" type="search" placeholder="203.0.113.7" defaultValue={filters.ip} />
        <TextField label="Record type" name="entityType" type="search" placeholder="USER, ORDER, KYC_SUBMISSION…" defaultValue={filters.entityType} />
        <TextField label="Record id" name="entityId" type="search" placeholder="The record's id" defaultValue={filters.entityId} />
        <TextField label="From (UTC)" name="from" type="date" defaultValue={filters.from} />
        <TextField label="To (UTC)" name="to" type="date" defaultValue={filters.to} />
        <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
          <Button type="submit">Search</Button>
          {filtered && <LinkButton href="/admin/audit" variant="quiet">Clear</LinkButton>}
        </div>
      </form>

      {entries.content.length === 0 ? (
        <EmptyState title={filtered ? "No entries match these filters" : "The audit log is empty"} />
      ) : (
        <AuditTable entries={entries.content} />
      )}
      <Pagination page={entries} basePath="/admin/audit" params={filters} />
    </div>
  );
}
