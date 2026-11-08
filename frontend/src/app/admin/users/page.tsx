import { deniedUnlessInMenu } from "@/components/layout/page-access";
import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { toneFor } from "@/components/ui/status-tones";
import { TextField } from "@/components/ui/text-field";
import { LinkButton } from "@/components/ui/link-button";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { AdminUserSummary } from "@/types/user";
import { formatDate, humanize } from "@/utils/format";
import { EmailText } from "@/components/ui/email-text";
import { Filter, UserPlus, X } from "lucide-react";

export const metadata: Metadata = { title: "Investors" };

const options = (values: readonly string[]) => values.map((value) => ({ value, label: humanize(value) }));
const STATUS = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;
const KYC = ["NOT_SUBMITTED", "PENDING", "APPROVED", "REJECTED"] as const;
const EMAIL = ["true", "false"] as const;

function pick<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : undefined;
}

export default async function UsersPage({ searchParams }: PageProps<"/admin/users">) {
  const denied = await deniedUnlessInMenu("/admin/users");
  if (denied) return denied;
  const session = await requireStaff();
  const params = await searchParams;
  // Employees have their own page; this keeps old links to the employees tab working.
  if (params.kind === "EMPLOYEE") redirect("/admin/staff");
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : undefined,
    kind: "INVESTOR",
    status: pick(params.status, STATUS),
    kycStatus: pick(params.kycStatus, KYC),
    emailVerified: pick(params.emailVerified, EMAIL),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const users = await authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?${query}`);
  const filtered = Boolean(filters.q || filters.status || filters.kycStatus || filters.emailVerified);
  /** A quick filter: the current search kept, with one status filter. */
  const quick = (extra: Record<string, string>) => {
    const next = new URLSearchParams(filters.q ? { q: filters.q } : {});
    for (const [key, value] of Object.entries(extra)) next.set(key, value);
    const qs = next.toString();
    return qs ? `/admin/users?${qs}` : "/admin/users";
  };
  const chips = [
    { label: "All investors", href: quick({}), on: !filters.status && !filters.kycStatus && !filters.emailVerified },
    { label: "KYC to review", href: quick({ kycStatus: "PENDING" }), on: filters.kycStatus === "PENDING" && !filters.status && !filters.emailVerified },
    { label: "KYC not submitted", href: quick({ kycStatus: "NOT_SUBMITTED" }), on: filters.kycStatus === "NOT_SUBMITTED" && !filters.status && !filters.emailVerified },
    { label: "Email not verified", href: quick({ emailVerified: "false" }), on: filters.emailVerified === "false" && !filters.status && !filters.kycStatus },
    { label: "Suspended", href: quick({ status: "SUSPENDED" }), on: filters.status === "SUSPENDED" && !filters.kycStatus && !filters.emailVerified },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Investors"
        description={`${users.totalElements.toLocaleString("en-IN")} ${users.totalElements === 1 ? "investor" : "investors"}${filtered ? " match these filters" : " on the platform"}.`}
        actions={(
          <div className="flex flex-wrap gap-2">
            {hasPermission(session.permissions, Permission.INVESTOR_ACCOUNT_CREATE)
              && <LinkButton href="/admin/users/new-investor" className="gap-2"><UserPlus aria-hidden="true" className="size-4" /> New investor</LinkButton>}
          </div>
        )} />

      <nav aria-label="Quick filters" className="flex flex-wrap gap-2 text-sm">
        {chips.map((chip) => (
          <Link key={chip.label} href={chip.href} aria-current={chip.on ? "page" : undefined}
            className={`inline-flex min-h-9 items-center rounded-full border px-4 font-medium transition-colors duration-200 ${chip.on
              ? "border-ink bg-ink text-on-ink dark:border-gold dark:bg-gold dark:text-on-gold"
              : "border-border bg-surface text-muted hover:border-gold/40 hover:text-foreground"}`}>
            {chip.label}
          </Link>
        ))}
      </nav>

      <form className="rounded-2xl border border-border bg-surface p-4 shadow-sm sm:p-5" role="search">
        <div className="grid items-end gap-3 sm:grid-cols-2 lg:grid-cols-6">
          <div className="sm:col-span-2">
            <TextField label="Search" name="q" type="search" placeholder="Email, name or ID (SL03500)" defaultValue={filters.q} />
          </div>
          <SelectField label="Status" name="status" options={options(STATUS)} placeholder="Any" defaultValue={filters.status ?? ""} />
          <SelectField label="KYC" name="kycStatus" options={options(KYC)} placeholder="Any" defaultValue={filters.kycStatus ?? ""} />
          <SelectField label="Email" name="emailVerified" placeholder="Any" defaultValue={filters.emailVerified ?? ""}
            options={[{ value: "true", label: "Verified" }, { value: "false", label: "Not verified" }]} />
          <div className="flex items-center gap-2">
            <Button type="submit" className="flex-1 gap-2"><Filter aria-hidden="true" className="size-4" /> Filter</Button>
            {filtered && (
              <Link href="/admin/users" aria-label="Clear filters" title="Clear filters"
                className="flex size-11 shrink-0 items-center justify-center rounded-lg border border-border text-muted transition-colors hover:border-gold hover:text-gold-text">
                <X aria-hidden="true" className="size-4" />
              </Link>
            )}
          </div>
        </div>
      </form>

      {users.content.length === 0 ? (
        <EmptyState title="No investors match these filters" />
      ) : (
        <DataTable columns={["Investor", "Type", "Status", "KYC", "Joined", "Last sign-in"]}>
          {users.content.map((user) => (
            <tr key={user.id} className="group hover:bg-background">
              <Cell className="min-w-[16rem]">
                <span className="flex items-center gap-3">
                  <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-gold/10 text-xs font-semibold text-gold-text transition-colors group-hover:bg-gold group-hover:text-on-gold">
                    {(user.firstName.charAt(0) + user.lastName.charAt(0)).toUpperCase() || "?"}
                  </span>
                  <span className="min-w-0">
                    <Link href={`/admin/users/${user.id}`} className="card-link block font-medium text-foreground group-hover:text-gold-text">
                      {user.firstName} {user.lastName}
                    </Link>
                    <span className="block text-xs text-muted"><EmailText>{user.email}</EmailText> · <span className="font-mono">{user.userCode}</span></span>
                  </span>
                </span>
              </Cell>
              <Cell>
                {user.investorType && (
                  <StatusBadge tone="neutral">{humanize(user.investorType)}</StatusBadge>
                )}
              </Cell>
              <Cell>
                <span className="inline-flex flex-wrap gap-1.5">
                  <StatusBadge tone={toneFor(user.status)}>{humanize(user.status)}</StatusBadge>
                  {!user.emailVerifiedAt && <StatusBadge tone="warning">Email not verified</StatusBadge>}
                </span>
              </Cell>
              <Cell><StatusBadge tone={toneFor(user.kycStatus)}>{humanize(user.kycStatus)}</StatusBadge></Cell>
              <Cell className="text-muted">{formatDate(user.createdAt)}</Cell>
              <Cell className="text-muted">{formatDate(user.lastLoginAt)}</Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={users} basePath="/admin/users" params={{ ...filters, kind: undefined }} />
    </div>
  );
}
