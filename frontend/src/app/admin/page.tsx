import type { Metadata } from "next";
import Link from "next/link";
import { DataTable, Cell } from "@/components/ui/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Role } from "@/types/auth";
import type { KpiTile } from "@/types/report";

export const metadata: Metadata = { title: "Admin" };

export default async function AdminOverviewPage() {
  const session = await requireStaff();
  const can = (p: string) => hasPermission(session.permissions, p);

  const [tiles, pendingKyc, pendingBanks, roles] = await Promise.all([
    authFetch<KpiTile[]>("/api/v1/admin/dashboard"),
    can(Permission.KYC_REVIEW) ? authFetch<PageResponse<unknown>>("/api/v1/admin/kyc?size=1") : null,
    can(Permission.BANK_ACCOUNT_VERIFY) ? authFetch<PageResponse<unknown>>("/api/v1/admin/bank-accounts?size=1") : null,
    can(Permission.ROLE_VIEW) ? authFetch<PageResponse<Role>>("/api/v1/admin/roles?size=50") : null,
  ]);

  return (
    <div className="space-y-8">
      <PageHeader title="Administration" description={`Signed in as ${session.roles.join(", ")}`} />

      {tiles.length > 0 && (
        <section className="grid gap-px bg-border sm:grid-cols-2 lg:grid-cols-4" aria-label="Key figures">
          {tiles.map((tile) => (
            <Link key={tile.key} href={tile.link} className="space-y-2 bg-surface p-5 hover:bg-background">
              <p className="text-xs uppercase tracking-[0.1em] text-muted">{tile.label}</p>
              {tile.values.map((value) => (
                <p key={value} className="font-display text-2xl font-semibold tabular-nums">{value}</p>
              ))}
              <p className="text-xs text-muted">{tile.note}</p>
            </Link>
          ))}
        </section>
      )}

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
