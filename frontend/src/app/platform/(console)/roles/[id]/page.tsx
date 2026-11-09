import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { Notice } from "@/components/ui/notice";
import { PageHeader } from "@/components/ui/page-header";
import { RoleEditor } from "@/features/admin/role-editor";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentSummary, PermissionEntry, RoleRevision } from "@/types/admin-portal";
import type { Role } from "@/types/auth";
import { formatDateTime, humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Role" };

export default async function RolePage({ params }: PageProps<"/platform/roles/[id]">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Roles are for admins." />;
  }
  const { id } = await params;
  const path = `/api/v1/admin/roles/${encodeURIComponent(id)}`;
  let role: Role;
  try {
    role = await authFetch<Role>(path);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }
  const [catalog, revisions, departments] = await Promise.all([
    authFetch<PermissionEntry[]>("/api/v1/admin/permissions"),
    authFetch<RoleRevision[]>(`${path}/revisions`),
    can(Permission.DEPARTMENT_MANAGE) ? authFetch<DepartmentSummary[]>("/api/v1/admin/departments") : [],
  ]);
  const readOnly = !can(Permission.ROLE_MANAGE) || role.protectedRole
    || role.permissions.includes(Permission.INVESTOR_PORTAL);

  return (
    <div className="space-y-6">
      <PageHeader title={humanize(role.name)} description={`${role.employees} employee(s) · version ${role.version}`} />
      {role.protectedRole && <Notice tone="info">This role is protected and cannot be changed.</Notice>}
      <RoleEditor role={role} catalog={catalog} departments={departments} readOnly={readOnly} />
      <Card title="Revisions" description="Each saved version of this role.">
        {revisions.length === 0 ? <p className="text-sm text-muted">No saved changes yet.</p> : (
          <ul className="space-y-2 text-sm">
            {revisions.map((r) => (
              <li key={r.revision}>
                <span className="font-medium">Revision {r.revision}</span>
                <span className="text-muted"> · {formatDateTime(r.changedAt)} · {r.changedBy ?? "System"} · {r.permissions.length} permission(s)</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
