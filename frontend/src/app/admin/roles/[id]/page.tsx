import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Role } from "@/types/auth";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Role" };

export default async function RolePage({ params }: PageProps<"/admin/roles/[id]">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Roles are for admins." />;
  }
  const { id } = await params;
  let role: Role;
  try {
    role = await authFetch<Role>(`/api/v1/admin/roles/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }

  return (
    <div className="space-y-6">
      <PageHeader title={humanize(role.name)} description={role.description}
        actions={<LinkButton href="/admin/roles" variant="secondary">All roles</LinkButton>} />

      <Card title="Permissions" description={`${role.permissions.length} permission(s)`}>
        <ul className="flex flex-wrap gap-2">
          {role.permissions.map((p) => (
            <li key={p} className="border border-border px-2 py-1 font-mono text-xs">{p}</li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
