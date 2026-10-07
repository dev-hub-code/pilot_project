import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { LeadForm } from "@/features/admin/lead-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Assignee } from "@/types/lead";

export const metadata: Metadata = { title: "New lead" };

export default async function NewLeadPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.LEAD_CREATE)) redirect("/admin/leads");
  const assignees = hasPermission(session.permissions, Permission.LEAD_ASSIGN)
    ? await authFetch<Assignee[]>("/api/v1/admin/leads/assignees")
    : undefined;
  return (
    <div className="space-y-6">
      <PageHeader title="New lead" description={assignees ? "Assign it now or leave it in the unassigned pool." : "The lead will be yours."} />
      <Card><LeadForm assignees={assignees} /></Card>
    </div>
  );
}
