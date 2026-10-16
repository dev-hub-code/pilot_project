import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { resetRoleCardsAction } from "@/features/admin/role-card-actions";
import { RoleCardForm } from "@/features/admin/role-card-form";
import { ActionButton } from "@/features/orders/action-button";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Role } from "@/types/auth";
import type { DashboardCardInfo, RoleDashboardCards } from "@/types/report";
import { formatDateTime, humanize } from "@/utils/format";

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
  const [catalog, cards] = await Promise.all([
    authFetch<DashboardCardInfo[]>("/api/v1/admin/dashboard/cards"),
    authFetch<RoleDashboardCards>(`/api/v1/admin/roles/${encodeURIComponent(id)}/dashboard-cards`),
  ]);
  const editable = hasPermission(session.permissions, Permission.ROLE_MANAGE);

  return (
    <div className="space-y-6">
      <PageHeader title={humanize(role.name)} description={role.description}
        actions={<LinkButton href="/admin/roles" variant="secondary">All roles</LinkButton>} />

      <Card title="Dashboard cards"
        description="The cards people with this role see on their dashboard. Each person still sees only cards their permissions allow, and can reorder or hide them. Someone with several roles sees every card any of their roles shows."
        actions={editable && cards.configured ? (
          <ActionButton action={resetRoleCardsAction.bind(null, role.id)} label="Show all cards" variant="quiet"
            confirm="Let this role show every card again?" />
        ) : undefined}>
        <p className="mb-4 text-sm text-muted">
          {cards.configured
            ? `Chosen by an admin${cards.updatedAt ? ` on ${formatDateTime(cards.updatedAt)}` : ""}.`
            : "Not chosen yet: this role shows every card."}
          {!editable && " Changing them needs the manage-roles permission."}
        </p>
        <RoleCardForm key={`${cards.configured}-${cards.updatedAt}`} roleId={role.id} catalog={catalog}
          chosen={cards.cards} editable={editable} />
      </Card>

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
