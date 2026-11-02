import type { Metadata } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { TextField } from "@/components/ui/text-field";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { StaffMember } from "@/types/admin-portal";
import type { PageResponse } from "@/types/api";
import type { AdminUserSummary } from "@/types/user";
import { formatDate, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Staff" };

const STATUS = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;

/** The one place employees are listed and created; Investors lists investors only. */
export default async function StaffPage({ searchParams }: PageProps<"/admin/staff">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const canCreate = can(Permission.USER_ROLE_ASSIGN) && can(Permission.ROLE_VIEW);
  const newEmployee = canCreate ? <LinkButton href="/admin/staff/new">New employee</LinkButton> : undefined;
  if (!can(Permission.STAFF_ACCOUNT_VIEW)) {
    return (
      <div className="space-y-6">
        <PageHeader title="Staff" actions={newEmployee} />
        <EmptyState title="Not available" description="Seeing employees needs the staff permission." />
      </div>
    );
  }
  const params = await searchParams;
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : undefined,
    status: STATUS.find((s) => s === params.status),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const [staff, details] = await Promise.all([
    authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/staff/search?${query}`),
    authFetch<StaffMember[]>("/api/v1/admin/staff"),
  ]);
  const detail = new Map(details.map((d) => [d.id, d]));

  return (
    <div className="space-y-6">
      <PageHeader title="Staff" description="Employees, their roles and their departments." actions={newEmployee} />
      <form className="grid items-end gap-3 rounded-xl border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-4" role="search">
        <div className="sm:col-span-2">
          <TextField label="Search" name="q" type="search" placeholder="Email, name or ID (SL03500)" defaultValue={filters.q} />
        </div>
        <SelectField label="Status" name="status" placeholder="Any" defaultValue={filters.status ?? ""}
          options={STATUS.map((value) => ({ value, label: humanize(value) }))} />
        <Button type="submit">Filter</Button>
      </form>
      {staff.content.length === 0 ? <EmptyState title="No employees match these filters" /> : (
        <DataTable columns={["ID", "Name", "Email", "Roles", "Departments", "Status", "Last sign-in"]}>
          {staff.content.map((s) => (
            <tr key={s.id} className="hover:bg-background">
              <Cell className="font-mono text-xs">{s.userCode}</Cell>
              <Cell>
                <Link href={`/admin/staff/${s.id}`} className="card-link font-medium text-gold-text hover:underline">
                  {s.firstName} {s.lastName}
                </Link>
              </Cell>
              <Cell className="text-muted">{s.email}</Cell>
              <Cell className="text-xs">{detail.get(s.id)?.roles.map(humanize).join(", ") ?? "—"}</Cell>
              <Cell className="text-xs">{detail.get(s.id)?.departments.map((d) => d.name).join(", ") || "—"}</Cell>
              <Cell><StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge></Cell>
              <Cell className="text-muted">{formatDate(s.lastLoginAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={staff} basePath="/admin/staff" params={filters} />
    </div>
  );
}
