import type { Metadata } from "next";
import Link from "next/link";
import { Building2, ChevronRight, KeyRound, Lock, Plus, Users } from "lucide-react";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { DEPARTMENT_ICON, roleTitle } from "@/features/admin/role-labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { DepartmentSummary } from "@/types/admin-portal";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";

export const metadata: Metadata = { title: "Roles" };

export default async function RolesPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  if (!can(Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Roles are for admins." />;
  }
  const [all, departments] = await Promise.all([
    authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=100"),
    // Only to group the roles: without it they are listed together.
    can(Permission.DEPARTMENT_VIEW) || can(Permission.DEPARTMENT_MANAGE)
      ? authFetch<DepartmentSummary[]>("/api/v1/admin/departments").catch(() => [] as DepartmentSummary[])
      : Promise.resolve([] as DepartmentSummary[]),
  ]);
  const roles = all.content.filter((r) => !r.permissions.includes("INVESTOR_PORTAL"));
  const known = new Set(departments.map((d) => d.id));
  const groups = [
    ...departments.map((d) => ({ key: d.id, name: d.name, code: d.code, roles: roles.filter((r) => r.departmentId === d.id) })),
    { key: "other", name: departments.length ? "Other roles" : "All roles", code: "",
      roles: roles.filter((r) => !r.departmentId || !known.has(r.departmentId)) },
  ].filter((g) => g.roles.length > 0);

  return (
    <div className="space-y-6">
      <PageHeader title="Roles"
        description={`Employee roles and what they can do · ${roles.length} ${roles.length === 1 ? "role" : "roles"}, grouped by department.`}
        actions={can(Permission.ROLE_MANAGE)
          ? <LinkButton href="/platform/roles/new" className="gap-2"><Plus aria-hidden="true" className="size-4" /> New role</LinkButton> : undefined} />

      {groups.length > 1 && (
        <nav aria-label="Departments" className="flex flex-wrap gap-2 text-sm">
          {groups.map((g) => {
            const Icon = DEPARTMENT_ICON[g.code] ?? Building2;
            return (
              <a key={g.key} href={`#dept-${g.key}`}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1.5 font-medium text-muted shadow-sm transition-colors hover:border-gold/40 hover:text-foreground">
                <Icon aria-hidden="true" className="size-3.5 text-gold-text" /> {g.name}
                <span className="rounded-full bg-background px-1.5 text-[11px] tabular-nums ring-1 ring-border">{g.roles.length}</span>
              </a>
            );
          })}
        </nav>
      )}

      {groups.map((g) => {
        const Icon = DEPARTMENT_ICON[g.code] ?? Building2;
        return (
          <section key={g.key} id={`dept-${g.key}`} aria-labelledby={`dept-${g.key}-title`} className="scroll-mt-6 space-y-3">
            <h2 id={`dept-${g.key}-title`} className="flex items-center gap-3 text-lg font-semibold tracking-tight">
              <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-xl bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold">
                <Icon className="size-[18px]" />
              </span>
              {g.name}
              <span className="text-sm font-normal text-muted">{g.roles.length} {g.roles.length === 1 ? "role" : "roles"}</span>
              <span aria-hidden="true" className="h-px flex-1 bg-gradient-to-r from-border to-transparent" />
            </h2>
            <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
              {g.roles.map((role) => (
                <li key={role.id}
                  className="group grid gap-2 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2">
                      <Link href={`/platform/roles/${role.id}`} className="card-link font-medium text-foreground group-hover:text-gold-text">{roleTitle(role)}</Link>
                      {role.protectedRole && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/10 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">
                          <Lock aria-hidden="true" className="size-3" /> Protected
                        </span>
                      )}
                      <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${role.system
                        ? "bg-background text-muted ring-1 ring-border" : "bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"}`}>
                        {role.system ? "System" : "Custom"}
                      </span>
                    </p>
                    <p className="mt-0.5 font-mono text-xs text-muted">{role.name}</p>
                  </div>
                  <div className="flex items-center gap-2 text-xs sm:justify-end">
                    <span className="inline-flex items-center gap-1 rounded-full bg-background px-2.5 py-1 font-medium ring-1 ring-border">
                      <KeyRound aria-hidden="true" className="size-3.5 text-gold-text" /> {role.permissions.length} {role.permissions.length === 1 ? "permission" : "permissions"}
                    </span>
                    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 font-medium ring-1 ${role.employees > 0
                      ? "bg-gold/10 text-gold-text ring-gold/25" : "bg-background text-muted ring-border"}`}>
                      <Users aria-hidden="true" className="size-3.5" /> {role.employees} {role.employees === 1 ? "person" : "people"}
                    </span>
                    <ChevronRight aria-hidden="true" className="hidden size-4 text-muted transition-transform group-hover:translate-x-0.5 sm:block" />
                  </div>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}
