import type { Metadata } from "next";
import { deniedUnless } from "@/components/layout/page-access";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { staffOpenTicketAction } from "@/features/admin/support-actions";
import { SupportShell, supportAbilities } from "@/features/admin/support-shell";
import { StaffTicketForm } from "@/features/admin/staff-ticket-form";
import { Permission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { Agent, SupportCategory } from "@/types/support";

export const metadata: Metadata = { title: "New support ticket" };

export default async function NewStaffTicketPage() {
  const denied = await deniedUnless(Permission.SUPPORT_TICKET_MANAGE, Permission.SUPPORT_CREATE);
  if (denied) return denied;
  const can = supportAbilities((await requireStaff()).permissions);
  const [categories, agents] = await Promise.all([
    can.view ? authFetch<SupportCategory[]>("/api/v1/admin/support/categories") : Promise.resolve([] as SupportCategory[]),
    can.manage ? authFetch<Agent[]>("/api/v1/admin/support/agents") : Promise.resolve([] as Agent[]),
  ]);
  return (
    <SupportShell active="queue">
      <PageHeader title="New ticket" description="Open a ticket for a customer, for example after a phone call or an email." />
      <Card><StaffTicketForm action={staffOpenTicketAction} categories={categories} agents={agents.map((a) => ({ value: a.userId, label: a.name }))} /></Card>
    </SupportShell>
  );
}
