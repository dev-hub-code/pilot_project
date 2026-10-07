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

export const metadata: Metadata = { title: "Users" };

const options = (values: readonly string[]) => values.map((value) => ({ value, label: humanize(value) }));
const STATUS = ["ACTIVE", "SUSPENDED", "DISABLED"] as const;
const KYC = ["NOT_SUBMITTED", "PENDING", "APPROVED", "REJECTED"] as const;

function pick<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : undefined;
}

export default async function UsersPage({ searchParams }: PageProps<"/admin/users">) {
  const session = await requireStaff();
  const params = await searchParams;
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 100) : undefined,
    status: pick(params.status, STATUS),
    kycStatus: pick(params.kycStatus, KYC),
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const users = await authFetch<PageResponse<AdminUserSummary>>(`/api/v1/admin/users?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Users" description="Investors and staff accounts."
        actions={hasPermission(session.permissions, Permission.USER_ROLE_ASSIGN) && hasPermission(session.permissions, Permission.ROLE_VIEW)
          ? <LinkButton href="/admin/users/new">New staff member</LinkButton> : undefined} />
      <form className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-5" role="search">
        <div className="sm:col-span-2">
          <TextField label="Search" name="q" type="search" placeholder="Email or name" defaultValue={filters.q} />
        </div>
        <SelectField label="Status" name="status" options={options(STATUS)} placeholder="Any" defaultValue={filters.status ?? ""} />
        <SelectField label="KYC" name="kycStatus" options={options(KYC)} placeholder="Any" defaultValue={filters.kycStatus ?? ""} />
        <div className="flex items-end gap-2">
          <div className="flex-1">
          </div>
          <Button type="submit">Filter</Button>
        </div>
      </form>

      {users.content.length === 0 ? (
        <EmptyState title="No users match these filters" />
      ) : (
        <DataTable columns={["Name", "Email", "Status", "KYC", "Type", "Joined", "Last sign-in"]}>
          {users.content.map((user) => (
            <tr key={user.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/users/${user.id}`} className="font-medium text-brand hover:underline">
                  {user.firstName} {user.lastName}
                </Link>
              </Cell>
              <Cell className="text-muted">{user.email}</Cell>
              <Cell><StatusBadge tone={toneFor(user.status)}>{humanize(user.status)}</StatusBadge></Cell>
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
