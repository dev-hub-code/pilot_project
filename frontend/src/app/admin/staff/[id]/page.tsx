import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Permission, hasPermission } from "@/lib/permissions";
import { BackendError } from "@/lib/server/backend-client";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { StaffMember } from "@/types/admin-portal";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Employee" };

export default async function StaffMemberPage({ params }: PageProps<"/admin/staff/[id]">) {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.STAFF_ACCOUNT_VIEW)) {
    return <EmptyState title="Not available" description="Staff accounts need the staff permission." />;
  }
  const { id } = await params;
  let member: StaffMember;
  try {
    member = await authFetch<StaffMember>(`/api/v1/admin/staff/${encodeURIComponent(id)}`);
  } catch (error) {
    if (error instanceof BackendError && error.status === 404) notFound();
    throw error;
  }

  return (
    <div className="space-y-6">
      <PageHeader title={member.name} description={member.email}
        actions={<LinkButton href={`/admin/users/${member.id}`} variant="secondary">Account and roles</LinkButton>} />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Roles" description="Roles decide what this employee may do.">
          <ul className="space-y-1 text-sm">{member.roles.map((r) => <li key={r}>{humanize(r)}</li>)}</ul>
        </Card>
        <Card title="Departments" description="Departments organise staff and grant nothing.">
          {member.departments.length === 0 ? <p className="text-sm text-muted">Not in a department yet.</p> : (
            <ul className="space-y-1 text-sm">
              {member.departments.map((d) => (
                <li key={d.id}>
                  <Link href={`/admin/departments/${d.id}`} className="text-gold-text hover:underline">{d.name}</Link>
                  {d.primary && <span className="text-muted"> · Primary</span>}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
