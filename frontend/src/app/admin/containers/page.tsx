import type { Metadata } from "next";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { SelectField } from "@/components/ui/select-field";
import { StatusBadge } from "@/components/ui/status-badge";
import { TextField } from "@/components/ui/text-field";
import { CONDITION_LABEL, CONTAINER_STATUS_LABEL, CONTAINER_STATUS_TONE, CONTAINER_TYPE_LABEL, CONTAINER_TYPE_OPTIONS } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { ContainerDetail, ContainerStatus } from "@/types/marketplace";

export const metadata: Metadata = { title: "Containers" };

const STATUSES = (Object.keys(CONTAINER_STATUS_LABEL) as ContainerStatus[]).map((value) => ({ value, label: CONTAINER_STATUS_LABEL[value] }));
const STATUS_TONE = CONTAINER_STATUS_TONE;

export default async function ContainersPage({ searchParams }: PageProps<"/admin/containers">) {
  const session = await requireStaff();
  const params = await searchParams;
  const filters = {
    q: typeof params.q === "string" ? params.q.slice(0, 50) : undefined,
    status: STATUSES.find((s) => s.value === params.status)?.value,
    containerType: CONTAINER_TYPE_OPTIONS.find((o) => o.value === params.containerType)?.value,
  };
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value);
  const containers = await authFetch<PageResponse<ContainerDetail>>(`/api/v1/admin/containers?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Containers" description="Inventory investors buy under plans. Available containers are assigned to investors when their payment is confirmed."
        actions={hasPermission(session.permissions, Permission.INVESTMENT_CREATE)
          ? <LinkButton href="/admin/containers/new">Register container</LinkButton> : undefined} />
      <form role="search" className="grid items-end gap-3 border border-border bg-surface p-4 sm:grid-cols-4">
        <TextField label="Search" name="q" type="search" placeholder="Number or location" defaultValue={filters.q} />
        <SelectField label="Status" name="status" options={STATUSES} placeholder="Any" defaultValue={filters.status ?? ""} />
        <SelectField label="Type" name="containerType" options={CONTAINER_TYPE_OPTIONS} placeholder="Any" defaultValue={filters.containerType ?? ""} />
        <Button type="submit">Filter</Button>
      </form>
      {containers.content.length === 0 ? (
        <EmptyState title="No containers yet" description="Register containers so investors can buy them under a plan." />
      ) : (
        <DataTable columns={["Number", "Type", "Condition", "Location", "Built", "Status"]}>
          {containers.content.map(({ container: c }) => (
            <tr key={c.id} className="hover:bg-background">
              <Cell>
                <Link href={`/admin/containers/${c.id}`} className="font-mono font-medium text-gold-text hover:underline">{c.containerNumber}</Link>
              </Cell>
              <Cell>{CONTAINER_TYPE_LABEL[c.containerType]}</Cell>
              <Cell>{CONDITION_LABEL[c.condition]}</Cell>
              <Cell className="text-muted">{c.currentLocation}, {c.locationCountry}</Cell>
              <Cell>{c.manufactureYear}</Cell>
              <Cell><StatusBadge tone={STATUS_TONE[c.status]}>{CONTAINER_STATUS_LABEL[c.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={containers} basePath="/admin/containers" params={filters} />
    </div>
  );
}
