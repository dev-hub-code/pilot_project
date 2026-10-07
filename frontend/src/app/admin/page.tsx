import type { Metadata } from "next";
import Link from "next/link";
import { DataTable, Cell } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";

export const metadata: Metadata = { title: "Admin" };

export default async function AdminOverviewPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);

  const [pendingKyc, pendingBanks, roles] = await Promise.all([
    can(Permission.KYC_REVIEW) ? authFetch<PageResponse<unknown>>("/api/v1/admin/kyc?size=1") : null,
    can(Permission.BANK_ACCOUNT_VERIFY) ? authFetch<PageResponse<unknown>>("/api/v1/admin/bank-accounts?size=1") : null,
    can(Permission.ROLE_VIEW) ? authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=50") : null,
  ]);

  return (
    <div className="space-y-8">
      <PageHeader title="Administration" description={`Signed in as ${session.roles.join(", ")}`} />

      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="Work queues">
        {pendingKyc && <QueueCard href="/admin/kyc" label="KYC awaiting review" count={pendingKyc.totalElements} />}
        {pendingBanks && (
          <QueueCard href="/admin/bank-accounts" label="Bank accounts to verify" count={pendingBanks.totalElements} />
        )}
      </section>

      {roles && (
        <section className="space-y-3">
          <h2 className="font-medium">Roles &amp; permissions</h2>
          <DataTable columns={["Role", "Description", "Permissions", "Type"]}>
            {roles.content.map((role) => (
              <tr key={role.id}>
                <Cell className="font-mono text-xs font-medium">{role.name}</Cell>
                <Cell className="text-muted">{role.description}</Cell>
                <Cell className="tabular-nums">{role.permissions.length}</Cell>
                <Cell>
                  <StatusBadge tone={role.system ? "neutral" : "success"}>{role.system ? "System" : "Custom"}</StatusBadge>
                </Cell>
              </tr>
            ))}
          </DataTable>
        </section>
      )}
    </div>
  );
}

function QueueCard({ href, label, count }: { href: string; label: string; count: number }) {
  return (
    <Link href={href} className="border border-border bg-surface p-6 hover:border-brand">
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-2 text-3xl font-semibold tabular-nums">{count}</p>
    </Link>
  );
}
