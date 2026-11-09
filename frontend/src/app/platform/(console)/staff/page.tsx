import type { Metadata } from "next";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { StaffMember } from "@/types/admin-portal";
import type { PageResponse } from "@/types/api";
import type { AdminUserSummary } from "@/types/user";
import { formatDate, humanize } from "@/utils/format";
import { Building2, Clock, Search, ShieldCheck, UserPlus, X } from "lucide-react";

export const metadata: Metadata = { title: "Staff" };

const STATUS = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;

/** The one place employees are listed and created; Investors lists investors only. */
export default async function StaffPage({ searchParams }: PageProps<"/platform/staff">) {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);
  const canCreate = can(Permission.USER_ROLE_ASSIGN) && can(Permission.ROLE_VIEW);
  const newEmployee = canCreate ? <LinkButton href="/platform/staff/new" className="gap-2"><UserPlus aria-hidden="true" className="size-4" /> New employee</LinkButton> : undefined;
  if (!can(Permission.STAFF_ACCOUNT_VIEW)) {
    return (
      <div className="space-y-6">
        <PageHeader title="Staff" actions={newEmployee} />
        <EmptyState title="Not available" description="Seeing employees needs the staff permission." />
      </div>
    );
  }
  const params = await searchParams;
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : undefined,
    status: STATUS.find((s) => s === params.status),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const [staff, details] = await Promise.all([
    authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/staff/search?${query}`),
    authFetch<StaffMember[]>("/api/v1/admin/staff"),
  ]);
  const detail = new Map(details.map((d) => [d.id, d]));

  const filtered = Boolean(filters.q || filters.status);
  const link = (status?: string) => {
    const next = new URLSearchParams();
    if (filters.q) next.set("q", filters.q);
    if (status) next.set("status", status);
    const qs = next.toString();
    return qs ? `/platform/staff?${qs}` : "/platform/staff";
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Staff"
        description={`Employees, their roles and their departments · ${staff.totalElements.toLocaleString("en-IN")} ${staff.totalElements === 1 ? "employee" : "employees"}${filtered ? " match" : ""}.`}
        actions={newEmployee} />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav aria-label="Filter by status" className="inline-flex flex-wrap gap-1 rounded-xl border border-border bg-surface p-1 text-sm shadow-sm">
          {[undefined, ...STATUS].map((st) => (
            <Link key={st ?? "all"} href={link(st)} aria-current={st === filters.status ? "page" : undefined}
              className={`rounded-lg px-4 py-2 font-medium whitespace-nowrap transition-colors duration-200 ${st === filters.status
                ? "bg-ink text-on-ink shadow-sm dark:bg-gold dark:text-on-gold"
                : "text-muted hover:bg-background hover:text-foreground"}`}>
              {st ? humanize(st) : "All"}
            </Link>
          ))}
        </nav>
        <form role="search" className="flex w-full items-center gap-2 rounded-xl border border-border bg-surface p-1 shadow-sm focus-within:border-gold sm:w-auto">
          {filters.status && <input type="hidden" name="status" value={filters.status} />}
          <Search aria-hidden="true" className="ml-2.5 size-4 shrink-0 text-muted" />
          <input name="q" type="search" defaultValue={filters.q} placeholder="Email, name or ID (SL03500)" aria-label="Search employees"
            className="h-9 min-w-0 flex-1 bg-transparent px-1 text-sm outline-none sm:w-64" />
          {filters.q && (
            <Link href={link(filters.status)} aria-label="Clear search" title="Clear search"
              className="flex size-9 shrink-0 items-center justify-center rounded-lg text-muted transition-colors hover:bg-background hover:text-foreground">
              <X aria-hidden="true" className="size-4" />
            </Link>
          )}
          <button type="submit" className="h-9 rounded-lg bg-gold px-4 text-sm font-semibold text-on-gold transition-colors hover:bg-gold-hover">Search</button>
        </form>
      </div>

      {staff.content.length === 0 ? <EmptyState title="No employees match these filters" /> : (
        // A list rather than a seven-column table, so it never scrolls sideways.
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface shadow-sm">
          {staff.content.map((s) => {
            const d = detail.get(s.id);
            return (
              <li key={s.id}
                className="group grid gap-3 p-4 transition-colors hover:bg-background sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-6 sm:px-5">
                <div className="flex min-w-0 items-center gap-3">
                  <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-ink text-xs font-semibold text-on-ink transition-colors group-hover:bg-gold group-hover:text-on-gold dark:bg-gold dark:text-on-gold">
                    {(s.firstName.charAt(0) + s.lastName.charAt(0)).toUpperCase() || "?"}
                  </span>
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-baseline gap-x-2">
                      <Link href={`/platform/staff/${s.id}`} className="card-link font-medium text-foreground group-hover:text-gold-text">
                        {s.firstName} {s.lastName}
                      </Link>
                      <span className="font-mono text-xs text-muted">{s.userCode}</span>
                    </p>
                    <p className="truncate text-xs text-muted">{s.email}</p>
                    {(d?.roles.length || d?.departments.length) ? (
                      <p className="mt-1.5 flex flex-wrap gap-1.5">
                        {d.roles.map((role) => (
                          <span key={role} className="inline-flex items-center gap-1 rounded-full bg-gold/10 px-2 py-0.5 text-[11px] font-medium text-gold-text">
                            <ShieldCheck aria-hidden="true" className="size-3" /> {humanize(role)}
                          </span>
                        ))}
                        {d.departments.map((dep) => (
                          <span key={dep.name} className="inline-flex items-center gap-1 rounded-full bg-background px-2 py-0.5 text-[11px] font-medium text-muted ring-1 ring-border">
                            <Building2 aria-hidden="true" className="size-3" /> {dep.name}
                          </span>
                        ))}
                      </p>
                    ) : null}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2 pl-[3.25rem] sm:justify-end sm:pl-0">
                  <span className="inline-flex items-center gap-1 text-xs text-muted">
                    <Clock aria-hidden="true" className="size-3" /> {s.lastLoginAt ? `Signed in ${formatDate(s.lastLoginAt)}` : "Never signed in"}
                  </span>
                  <StatusBadge tone={toneFor(s.status)}>{humanize(s.status)}</StatusBadge>
                </div>
              </li>
            );
          })}
        </ul>
      )}
      <Pagination page={staff} basePath="/platform/staff" params={filters} />
    </div>
  );
}
