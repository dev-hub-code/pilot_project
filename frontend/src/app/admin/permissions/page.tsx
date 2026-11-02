import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";
import { StatusBadge } from "@/components/ui/status-badge";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PermissionEntry } from "@/types/admin-portal";
import { humanize } from "@/utils/format";

export const metadata: Metadata = { title: "Permissions" };

export default async function PermissionsPage() {
  const session = await requireStaff();
  if (!hasPermission(session.permissions, Permission.ROLE_VIEW)) {
    return <EmptyState title="Not available" description="Permissions are for admins." />;
  }
  const catalog = await authFetch<PermissionEntry[]>("/api/v1/admin/permissions");
  const categories = [...new Set(catalog.map((p) => p.category))];

  return (
    <div className="space-y-6">
      <PageHeader title="Permissions"
        description="Everything a role can grant. Reserved permissions have no feature behind them yet, so granting one changes nothing." />
      {categories.map((category) => (
        <Card key={category} title={humanize(category)}>
          <ul className="grid gap-3 md:grid-cols-2">
            {catalog.filter((p) => p.category === category).map((p) => (
              <li key={p.code} className="text-sm">
                <span className="font-mono text-xs">{p.code}</span>
                {p.sensitive && <> <StatusBadge tone="warning">Sensitive</StatusBadge></>}
                {p.reserved && <> <StatusBadge tone="neutral">Reserved</StatusBadge></>}
                <span className="block text-xs text-muted">{p.description}</span>
              </li>
            ))}
          </ul>
        </Card>
      ))}
    </div>
  );
}
