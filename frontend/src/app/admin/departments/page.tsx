import type { Metadata } from "next";
import Link from "next/link";
import { ArrowUpRight, Building2, Plus, ShieldCheck, UserRound, Users } from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { DepartmentForm } from "@/features/admin/department-forms";
import { createDepartmentAction } from "@/features/admin/portal-actions";
import { DEPARTMENT_ICON } from "@/features/admin/role-labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentSummary } from "@/types/admin-portal";

export const metadata: Metadata = { title: "Departments" };

export default async function DepartmentsPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.DEPARTMENT_VIEW) && !can(Permission.DEPARTMENT_MANAGE)) {
    return <EmptyState title="Not available" description="Departments need the department permission." />;
  }
  const departments = await authFetch<DepartmentSummary[]>("/api/v1/admin/departments");
  const active = departments.filter((d) => d.active);
  const totals = [
    { icon: Building2, label: "Active departments", value: active.length },
    { icon: Users, label: "Employees in them", value: departments.reduce((n, d) => n + d.employees, 0) },
    { icon: ShieldCheck, label: "Roles", value: departments.reduce((n, d) => n + d.roles, 0) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Departments" description="How staff are organised. Belonging to a department grants no permissions: roles do." />

      {departments.length > 0 && (
        <dl className="grid gap-3 sm:grid-cols-3">
          {totals.map((t) => (
            <div key={t.label} className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-text">
                <t.icon className="size-5" />
              </span>
              <div>
                <dt className="text-xs text-muted">{t.label}</dt>
                <dd className="font-display text-2xl font-semibold tracking-tight tabular-nums">{t.value.toLocaleString("en-IN")}</dd>
              </div>
            </div>
          ))}
        </dl>
      )}

      {departments.length === 0 ? <EmptyState title="No departments to show" /> : (
        <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {departments.map((d) => {
            const Icon = DEPARTMENT_ICON[d.code] ?? Building2;
            return (
              <li key={d.id}
                className={`group relative flex flex-col overflow-hidden rounded-2xl border border-border bg-surface p-5 shadow-sm transition duration-300 hover:-translate-y-0.5 hover:border-gold/40 hover:shadow-lg hover:shadow-gold/10 ${d.active ? "" : "opacity-70"}`}>
                <span aria-hidden="true" className="absolute inset-x-0 top-0 h-0.5 origin-left scale-x-0 bg-gold transition-transform duration-500 group-hover:scale-x-100" />
                <div className="flex items-start justify-between gap-3">
                  <span aria-hidden="true" className={`flex size-11 shrink-0 items-center justify-center rounded-xl transition-colors ${d.active
                    ? "bg-ink text-on-ink group-hover:bg-gold group-hover:text-on-gold dark:bg-gold dark:text-on-gold" : "bg-background text-muted ring-1 ring-border"}`}>
                    <Icon className="size-5" />
                  </span>
                  <div className="flex items-center gap-2">
                    {!d.active && <StatusBadge tone="neutral">Inactive</StatusBadge>}
                    <ArrowUpRight aria-hidden="true" className="size-4 text-muted transition duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-text" />
                  </div>
                </div>
                <Link href={`/admin/departments/${d.id}`} className="card-link mt-4 font-semibold tracking-tight group-hover:text-gold-text">{d.name}</Link>
                {d.description && <p className="mt-1 line-clamp-2 text-sm text-muted">{d.description}</p>}
                <div className="mt-auto pt-4">
                  <p className="flex items-center gap-2 border-t border-border pt-3 text-sm">
                    {d.headName ? (
                      <>
                        <span aria-hidden="true" className="flex size-7 shrink-0 items-center justify-center rounded-full bg-gold/10 text-[10px] font-semibold text-gold-text">
                          {d.headName.split(/\s+/).filter(Boolean).map((w) => w.charAt(0)).join("").slice(0, 2).toUpperCase()}
                        </span>
                        <span className="min-w-0 truncate"><span className="text-muted">Head</span> {d.headName}</span>
                      </>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 text-muted"><UserRound aria-hidden="true" className="size-4" /> No head yet</span>
                    )}
                  </p>
                  <p className="mt-2 flex gap-2 text-xs">
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 font-medium ring-1 ring-border">
                      <Users aria-hidden="true" className="size-3.5 text-gold-text" /> {d.employees} {d.employees === 1 ? "employee" : "employees"}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 font-medium ring-1 ring-border">
                      <ShieldCheck aria-hidden="true" className="size-3.5 text-gold-text" /> {d.roles} {d.roles === 1 ? "role" : "roles"}
                    </span>
                  </p>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      {can(Permission.DEPARTMENT_MANAGE) && (
        <details className="group overflow-hidden rounded-2xl border border-dashed border-border bg-surface shadow-sm open:border-solid">
          <summary className="flex cursor-pointer list-none items-center gap-3 p-5 transition-colors hover:bg-background/60 [&::-webkit-details-marker]:hidden">
            <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-xl bg-gold/10 text-gold-text transition-transform group-open:rotate-45">
              <Plus className="size-5" />
            </span>
            <span>
              <span className="block font-semibold">New department</span>
              <span className="block text-xs text-muted">Add a team; then give it a head, members and roles on its page.</span>
            </span>
          </summary>
          <div className="border-t border-border p-5">
            <DepartmentForm action={createDepartmentAction} />
          </div>
        </details>
      )}
    </div>
  );
}
