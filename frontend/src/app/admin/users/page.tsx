import type { Metadata } from "next";
import Link from "next/link";
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

export const metadata: Metadata = { title: "Users" };

const options = (values: readonly string[]) => values.map((value) => ({ value, label: humanize(value) }));
const STATUS = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;
const KYC = ["NOT_SUBMITTED", "PENDING", "APPROVED", "REJECTED"] as const;
const EMAIL = ["true", "false"] as const;
const KIND = ["INVESTOR", "EMPLOYEE"] as const;

function pick<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : undefined;
}

export default async function UsersPage({ searchParams }: PageProps<"/admin/users">) {
  const session = await requireStaff();
  const params = await searchParams;
  // Only viewers who may see employees get the investor / employee filter; the rest only ever see investors.
  const seesEmployees = hasPermission(session.permissions, Permission.STAFF_ACCOUNT_VIEW);
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : undefined,
    kind: seesEmployees ? pick(params.kind, KIND) : undefined,
    status: pick(params.status, STATUS),
    kycStatus: pick(params.kycStatus, KYC),
    emailVerified: pick(params.emailVerified, EMAIL),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const users = await authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Users"
        description={seesEmployees ? "Investors and employees." : "Investors."}
        actions={hasPermission(session.permissions, Permission.USER_ROLE_ASSIGN) && hasPermission(session.permissions, Permission.ROLE_VIEW)
          ? <LinkButton href="/admin/users/new">New employee</LinkButton> : undefined} />
      {seesEmployees && (
        <nav aria-label="Show" className="flex flex-wrap gap-2">
          {[{ value: undefined, label: "All users" }, { value: "INVESTOR", label: "Investors" }, { value: "EMPLOYEE", label: "Employees" }]
            .map(({ value, label }) => {
              const href = new URLSearchParams(Object.entries({ ...filters, kind: value })
                .filter((entry): entry is [string, string] => Boolean(entry[1])));
              const current = filters.kind === value;
              return (
                <Link key={label} href={`/admin/users${href.size ? `?${href}` : ""}`} aria-current={current ? "page" : undefined}
                  className={`rounded-full border px-4 py-1.5 text-sm transition-colors ${current
                    ? "border-gold bg-gold text-on-gold" : "border-border bg-surface hover:border-gold"}`}>
                  {label}
                </Link>
              );
            })}
        </nav>
      )}
      <form className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-2 lg:grid-cols-6" role="search">
        <div className="sm:col-span-2">
          <TextField label="Search" name="q" type="search" placeholder="Email, name or ID (SL03500)" defaultValue={filters.q} />
        </div>
        {/* Searching keeps the tab chosen above. */}
        {filters.kind && <input type="hidden" name="kind" value={filters.kind} />}
        <SelectField label="Status" name="status" options={options(STATUS)} placeholder="Any" defaultValue={filters.status ?? ""} />
        <SelectField label="KYC" name="kycStatus" options={options(KYC)} placeholder="Any" defaultValue={filters.kycStatus ?? ""} />
        <SelectField label="Email" name="emailVerified" placeholder="Any" defaultValue={filters.emailVerified ?? ""}
          options={[{ value: "true", label: "Verified" }, { value: "false", label: "Not verified" }]} />
        <Button type="submit">Filter</Button>
      </form>

      {users.content.length === 0 ? (
        <EmptyState title="No users match these filters" />
      ) : (
        <DataTable columns={["ID", "Name", "Email", "Status", "KYC", "Joined", "Last sign-in"]}>
          {users.content.map((user) => (
            <tr key={user.id} className="hover:bg-background">
              <Cell className="font-mono text-xs">{user.userCode}</Cell>
              <Cell>
                <Link href={`/admin/users/${user.id}`} className="card-link font-medium text-brand hover:underline">
                  {user.firstName} {user.lastName}
                </Link>
                {user.kind === "EMPLOYEE" && <> <StatusBadge tone="neutral">Employee</StatusBadge></>}
              </Cell>
              <Cell className="text-muted"><EmailText>{user.email}</EmailText></Cell>
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
      <Pagination page={users} basePath="/admin/users" params={filters} />
    </div>
  );
}
