import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { ConfirmForm } from "@/features/admin/decision-forms";
import { DepartmentForm, PersonForm } from "@/features/admin/department-forms";
import {
  assignEmployeeAction, removeEmployeeAction, setDepartmentActiveAction, setDepartmentHeadAction, updateDepartmentAction,
} from "@/features/admin/portal-actions";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentDetail, StaffMember } from "@/types/admin-portal";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Department" };

export default async function DepartmentPage({ params }: PageProps<"/platform/departments/[id]">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.DEPARTMENT_VIEW) && !can(Permission.DEPARTMENT_MANAGE)) {
    return <EmptyState title="Not available" description="Departments need the department permission." />;
  }
  const { id } = await params;
  let detail: DepartmentDetail;
  try {
    detail = await authFetch<DepartmentDetail>(`/api/v1/admin/departments/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const { department: d, employees, roles, history } = detail;
  const canManage = can(Permission.DEPARTMENT_MANAGE);
  const staff = canManage && can(Permission.STAFF_ACCOUNT_VIEW) ? await authFetch<StaffMember[]>("/api/v1/admin/staff") : [];
  const members = new Set(employees.map((e) => e.userId));

  return (
    <div className="space-y-6">
      <PageHeader title={d.name} description={d.description || d.code}
        actions={<StatusBadge tone={d.active ? "success" : "neutral"}>{d.active ? "Active" : "Inactive"}</StatusBadge>} />

      <section aria-label="Employees" className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Employees ({employees.length})</h2>
        {employees.length === 0 ? <EmptyState title="No employees yet" /> : (
          <DataTable columns={["Name", "Email", "Assignment", "Since", ""]}>
            {employees.map((e) => (
              <tr key={e.userId}>
                <Cell><Link href={`/platform/staff/${e.userId}`} className="inline-flex min-h-6 items-center text-gold-text hover:underline">{e.name}</Link></Cell>
                <Cell className="text-muted">{e.email}</Cell>
                <Cell>{e.head ? "Head" : e.primary ? "Primary" : "Additional"}</Cell>
                <Cell className="text-muted">{formatDateTime(e.assignedAt)}</Cell>
                <Cell>
                  {canManage && !e.head && (
                    <ConfirmForm action={removeEmployeeAction.bind(null, d.id, e.userId)} submitLabel="Remove"
                      confirm={`Remove ${e.name} from ${d.name}?`} />
                  )}
                </Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>

      <section aria-label="Roles" className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Roles ({roles.length})</h2>
        {roles.length === 0 ? <EmptyState title="No roles in this department" /> : (
          <DataTable columns={["Role", "Description", "Employees"]}>
            {roles.map((r) => (
              <tr key={r.id}>
                <Cell className="font-mono text-xs"><Link href={`/platform/roles/${r.id}`} className="inline-flex min-h-6 items-center text-gold-text hover:underline">{r.name}</Link></Cell>
                <Cell className="text-muted">{r.description}</Cell>
                <Cell className="tabular-nums">{r.employees}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>

      {canManage && (
        <div className="grid gap-6 lg:grid-cols-2">
          <Card title="Assign an employee">
            <PersonForm action={assignEmployeeAction.bind(null, d.id)} label="Employee" submitLabel="Assign" primary
              people={staff.filter((s) => !members.has(s.id)).map((s) => ({ value: s.id, label: s.name }))} />
          </Card>
          <Card title="Department head">
            <PersonForm action={setDepartmentHeadAction.bind(null, d.id)} label="Head" submitLabel="Save head" optional
              current={d.headUserId ?? ""} people={employees.map((e) => ({ value: e.userId, label: e.name }))} />
          </Card>
          <Card title="Details">
            <DepartmentForm action={updateDepartmentAction.bind(null, d.id, d.version)} code={d.code} name={d.name}
              description={d.description} />
          </Card>
          <Card title={d.active ? "Deactivate" : "Activate"}
            description={d.active ? "An inactive department takes no new employees." : "Lets it take employees again."}>
            <ConfirmForm action={setDepartmentActiveAction.bind(null, d.id, !d.active)}
              submitLabel={d.active ? "Deactivate department" : "Activate department"}
              confirm={d.active ? `Deactivate ${d.name}?` : undefined} />
          </Card>
        </div>
      )}

      <section aria-label="History" className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">History</h2>
        {history.length === 0 ? <EmptyState title="No changes yet" /> : (
          <DataTable columns={["When", "Change", "Detail", "By"]}>
            {history.map((h, i) => (
              <tr key={`${h.at}-${i}`}>
                <Cell className="text-muted">{formatDateTime(h.at)}</Cell>
                <Cell>{humanize(h.action)}</Cell>
                <Cell className="text-muted">{h.detail || "—"}</Cell>
                <Cell>{h.actorName ?? "System"}</Cell>
              </tr>
            ))}
          </DataTable>
        )}
      </section>
    </div>
  );
}
