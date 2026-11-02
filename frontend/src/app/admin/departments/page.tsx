import type { Metadata } from "next";
import Link from "next/link";
import { Card } from "@/components/ui/card";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { DepartmentForm } from "@/features/admin/department-forms";
import { createDepartmentAction } from "@/features/admin/portal-actions";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentSummary } from "@/types/admin-portal";

export const metadata: Metadata = { title: "Departments" };

export default async function DepartmentsPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.DEPARTMENT_VIEW) && !can(Permission.DEPARTMENT_MANAGE)) {
    return <EmptyState title="Not available" description="Departments need the department permission." />;
  }
  const departments = await authFetch<DepartmentSummary[]>("/api/v1/admin/departments");

  return (
    <div className="space-y-6">
      <PageHeader title="Departments" description="How staff are organised. Belonging to a department grants no permissions." />
      {departments.length === 0 ? <EmptyState title="No departments to show" /> : (
        <DataTable columns={["Department", "Head", "Employees", "Roles", "Status"]}>
          {departments.map((d) => (
            <tr key={d.id} className="hover:bg-background">
              <Cell><Link href={`/admin/departments/${d.id}`} className="card-link font-medium text-gold-text hover:underline">{d.name}</Link></Cell>
              <Cell className="text-muted">{d.headName ?? "—"}</Cell>
              <Cell className="tabular-nums">{d.employees}</Cell>
              <Cell className="tabular-nums">{d.roles}</Cell>
              <Cell><StatusBadge tone={d.active ? "success" : "neutral"}>{d.active ? "Active" : "Inactive"}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      {can(Permission.DEPARTMENT_MANAGE) && (
        <Card title="New department"><DepartmentForm action={createDepartmentAction} /></Card>
      )}
    </div>
  );
}
