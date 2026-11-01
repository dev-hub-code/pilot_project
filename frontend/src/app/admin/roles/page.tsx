import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";

export const metadata: Metadata = { title: "Roles" };

export default async function RolesPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Roles are for admins." />;
  }
  const roles = (await authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100")).content
    .filter((r) => !r.permissions.includes("INVESTOR_PORTAL"));

  return (
    <div className="space-y-6">
      <PageHeader title="Roles" description="Employee roles and what they can do." />
      <DataTable columns={["Role", "Description", "Permissions", "Type"]}>
        {roles.map((role) => (
          <tr key={role.id} className="hover:bg-background">
            <Cell className="font-mono text-xs font-medium">
              <Link href={`/admin/roles/${role.id}`} className="card-link text-gold-text hover:underline">{role.name}</Link>
            </Cell>
            <Cell className="text-muted">{role.description}</Cell>
            <Cell className="tabular-nums">{role.permissions.length}</Cell>
            <Cell><StatusBadge tone={role.system ? "neutral" : "success"}>{role.system ? "System" : "Custom"}</StatusBadge></Cell>
          </tr>
        ))}
      </DataTable>
    </div>
  );
}
