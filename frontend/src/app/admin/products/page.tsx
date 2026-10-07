import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { CONTAINER_TYPE_LABEL, PRODUCT_STATUS_LABEL, PRODUCT_TONE } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Product, ProductStatus } from "@/types/marketplace";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Plans" };

const STATUSES: ProductStatus[] = ["DRAFT", "OPEN", "CLOSED", "CANCELLED"];

export default async function ProductsPage({ searchParams }: PageProps<"/admin/products">) {
  const session = await requireStaff();
  const params = await searchParams;
  const status = STATUSES.find((s) => s === params.status);
  const page = Math.max(0, Number.parseInt(String(params.page ?? "0"), 10) || 0);
  const query = new URLSearchParams({ page: String(page), size: "25" });
  if (status) query.set("status", status);
  const products = await authFetch<PageResponse<Product>>(`/api/v1/admin/investment-products?${query}`);

  return (
    <div className="space-y-6">
      <PageHeader title="Investment plans" description="Investors buy whole containers under a plan and are paid rent plus capital back every month."
        actions={hasPermission(session.permissions, Permission.INVESTMENT_CREATE) ? <LinkButton href="/admin/products/new">New plan</LinkButton> : undefined} />
      <nav aria-label="Filter by status" className="flex flex-wrap gap-2 text-sm">
        {[undefined, ...STATUSES].map((s) => (
          <Link key={s ?? "all"} href={s ? `/admin/products?status=${s}` : "/admin/products"} aria-current={s === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${s === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {s ? PRODUCT_STATUS_LABEL[s] : "All"}
          </Link>
        ))}
      </nav>
      {products.content.length === 0 ? (
        <EmptyState title="No plans" />
      ) : (
        <DataTable columns={["Code", "Title", "Container", "Price", "Monthly payout", "Sold", "In stock", "Status"]}>
          {products.content.map((p) => (
            <tr key={p.id} className="hover:bg-background">
              <Cell><Link href={`/admin/products/${p.id}`} className="font-mono font-medium text-gold-text hover:underline">{p.code}</Link></Cell>
              <Cell className="max-w-xs truncate">{p.title}</Cell>
              <Cell>{CONTAINER_TYPE_LABEL[p.containerType]}</Cell>
              <Cell className="tabular-nums">{formatMoney(p.price)}</Cell>
              <Cell className="tabular-nums">
                {formatMoney(p.monthlyPayout)}
                <span className="block text-xs text-muted">{formatPercent(p.monthlyRentPercent)} rent + {formatPercent(p.monthlyCapitalReturnPercent)} capital</span>
              </Cell>
              <Cell className="tabular-nums">{p.containersSold}</Cell>
              <Cell className="tabular-nums">{p.availableContainers}</Cell>
              <Cell><StatusBadge tone={PRODUCT_TONE[p.status]}>{PRODUCT_STATUS_LABEL[p.status]}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={products} basePath="/admin/products" params={{ status }} />
    </div>
  );
}
