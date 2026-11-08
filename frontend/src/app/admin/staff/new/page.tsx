import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { CreateStaffForm } from "@/features/admin/staff-forms";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";
import type { DepartmentSummary } from "@/types/admin-portal";
import { KeyRound, Mail, ShieldCheck, UserPlus } from "lucide-react";

export const metadata: Metadata = { title: "New employee" };

const STEPS = [
  { icon: UserPlus, title: "Enter their details", text: "Their name and work email: the email is how they sign in." },
  { icon: ShieldCheck, title: "Choose their roles", text: "Roles decide what they can see and do. You can only grant roles whose permissions you hold." },
  { icon: Mail, title: "They are emailed", text: "Their sign-in details, with a temporary password, go to their work email." },
  { icon: KeyRound, title: "First sign-in", text: "They replace the temporary password before it expires. It is shown to you once, to copy if the email fails." },
];

export default async function NewStaffPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.USER_ROLE_ASSIGN) || !hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    redirect("/admin/staff");
  }
  const canSeeDepartments = hasPermission(session.permissions, Permission.DEPARTMENT_VIEW)
    || hasPermission(session.permissions, Permission.DEPARTMENT_MANAGE);
  const [roles, departments] = await Promise.all([
    authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100"),
    // Only to group the roles: without it they are listed together.
    canSeeDepartments ? authFetch<DepartmentSummary[]>("/api/v1/admin/departments").catch(() => []) : Promise.resolve([]),
  ]);
  // Investors register themselves; staff accounts only get staff roles.
  const staffRoles = roles.content.filter((r) => !r.permissions.includes(Permission.INVESTOR_PORTAL));
  return (
    <div className="space-y-6">
      <PageHeader title="New employee"
        description="They are emailed a temporary password to replace at first sign-in. You can only grant roles whose permissions you hold." />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Card title="Employee details" className="min-w-0"><CreateStaffForm roles={staffRoles} departments={departments.filter((d) => d.active)} /></Card>
        <aside className="relative isolate overflow-hidden rounded-2xl bg-ink p-6 text-on-ink shadow-xl shadow-ink/20 lg:sticky lg:top-6 lg:self-start">
          <div aria-hidden="true" className="absolute -top-20 -right-20 -z-10 size-56 rounded-full bg-gold/30 blur-3xl" />
          <h2 className="font-semibold tracking-tight">How it works</h2>
          <ol className="relative mt-5 space-y-5 border-l border-on-ink/15 pl-6">
            {STEPS.map((step, i) => (
              <li key={step.title} className="relative">
                <span aria-hidden="true" className="absolute top-0 -left-[2.3rem] flex size-7 items-center justify-center rounded-full bg-gold text-on-gold ring-4 ring-ink">
                  <step.icon className="size-3.5" />
                </span>
                <p className="text-xs font-medium tracking-[0.12em] text-on-ink/50 uppercase">Step {i + 1}</p>
                <p className="font-medium">{step.title}</p>
                <p className="text-sm text-on-ink/70">{step.text}</p>
              </li>
            ))}
          </ol>
        </aside>
      </div>
    </div>
  );
}
