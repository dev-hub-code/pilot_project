import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { CreateStaffForm } from "@/features/admin/staff-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";

export const metadata: Metadata = { title: "New staff member" };

export default async function NewStaffPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.USER_ROLE_ASSIGN) || !hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    redirect("/admin/users");
  }
  const roles = await authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100");
  // Investors register themselves; staff accounts only get staff roles.
  const staffRoles = roles.content.filter((r) => !r.permissions.includes(Permission.INVESTOR_PORTAL));
  return (
    <div className="space-y-6">
      <PageHeader title="New staff member"
        description="They receive a temporary password to replace at first sign-in. You can only grant roles whose permissions you hold." />
      <Card><CreateStaffForm roles={staffRoles} /></Card>
    </div>
  );
}
