import type { Metadata } from "next";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { RoleEditor } from "@/features/admin/role-editor";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentSummary, PermissionEntry } from "@/types/admin-portal";

export const metadata: Metadata = { title: "New role" };

export default async function NewRolePage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_MANAGE)) {
    return <EmptyState title="Not available" description="Creating roles is for admins." />;
  }
  const catalog = await authFetch<PermissionEntry[]>("/api/v1/admin/permissions");
  const departments = hasPermission(session.permissions, Permission.DEPARTMENT_MANAGE)
    ? await authFetch<DepartmentSummary[]>("/api/v1/admin/departments") : [];
  return (
    <div className="space-y-6">
      <PageHeader title="New role" description="Choose only what this job needs." />
      <RoleEditor catalog={catalog} departments={departments} readOnly={false} />
    </div>
  );
}
