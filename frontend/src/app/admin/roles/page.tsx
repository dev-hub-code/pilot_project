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
import type { RoleDashboardCards } from "@/types/report";

export const metadata: Metadata = { title: "Roles" };

export default async function RolesPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Roles are for admins." />;
  }
  const roles = (await authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100")).content
    .filter((r) => !r.permissions.includes("INVESTOR_PORTAL"));
  const cards = await Promise.all(roles.map((r) =>
    authFetch<RoleDashboardCards>(`/api/v1/admin/roles/${r.id}/dashboard-cards`)));

  return (
    <div className="space-y-6">
      <PageHeader title="Roles"
        description="Employee roles and what they can do. Open a role to choose which dashboard cards it shows." />
      <DataTable columns={["Role", "Description", "Permissions", "Dashboard cards", "Type"]}>
        {roles.map((role, i) => (
          <tr key={role.id} className="hover:bg-background">
            <Cell className="font-mono text-xs font-medium">
              <Link href={`/admin/roles/${role.id}`} className="card-link text-gold-text hover:underline">{role.name}</Link>
            </Cell>
            <Cell className="text-muted">{role.description}</Cell>
            <Cell className="tabular-nums">{role.permissions.length}</Cell>
            <Cell>{cards[i]?.configured ? `${cards[i]?.cards.length} chosen` : <span className="text-muted">All cards</span>}</Cell>
            <Cell><StatusBadge tone={role.system ? "neutral" : "success"}>{role.system ? "System" : "Custom"}</StatusBadge></Cell>
          </tr>
        ))}
      </DataTable>
    </div>
  );
}
