import type { Metadata } from "next";
import Link from "next/link";
import { Cell, DataTable } from "@/components/ui/data-table";
import { EmptyState } from "@/components/ui/empty-state";
import { LinkButton } from "@/components/ui/link-button";
import { PageHeader } from "@/components/ui/page-header";
import { Pagination } from "@/components/ui/pagination";
import { StatusBadge } from "@/components/ui/status-badge";
import { PRODUCT_TONE } from "@/features/marketplace/labels";
import { Permission, hasPermission } from "@/lib/permissions";
import { authFetch, requireStaff } from "@/lib/server/auth/session";
import type { PageResponse } from "@/types/api";
import type { Product, ProductStatus } from "@/types/marketplace";
import { humanize } from "@/utils/format";
import { formatMoney, formatPercent } from "@/utils/money";

export const metadata: Metadata = { title: "Offerings" };

const STATUSES: ProductStatus[] = ["DRAFT", "OPEN", "FUNDED", "ACTIVE", "CANCELLED"];

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
      <PageHeader title="Offerings" description="Investment products backed by containers."
        actions={hasPermission(session.permissions, Permission.INVESTMENT_CREATE) ? <LinkButton href="/admin/products/new">New offering</LinkButton> : undefined} />
      <nav aria-label="Filter by status" className="flex flex-wrap gap-2 text-sm">
        {[undefined, ...STATUSES].map((s) => (
          <Link key={s ?? "all"} href={s ? `/admin/products?status=${s}` : "/admin/products"} aria-current={s === status ? "page" : undefined}
            className={`rounded-full border px-3 py-1 ${s === status ? "border-foreground text-foreground" : "border-border text-muted"}`}>
            {s ? humanize(s) : "All"}
          </Link>
        ))}
      </nav>
      {products.content.length === 0 ? (
        <EmptyState title="No offerings" />
      ) : (
        <DataTable columns={["Code", "Title", "Type", "Price", "Yield", "Funded", "Status"]}>
          {products.content.map((p) => (
            <tr key={p.id} className="hover:bg-background">
              <Cell><Link href={`/admin/products/${p.id}`} className="font-mono font-medium text-gold-text hover:underline">{p.code}</Link></Cell>
              <Cell className="max-w-xs truncate">{p.title}</Cell>
              <Cell>{p.investmentType}</Cell>
              <Cell>{formatMoney(p.price)}</Cell>
              <Cell>{formatPercent(p.expectedAnnualReturnPercent)}</Cell>
              <Cell>{formatPercent(p.capacity.fundedPercent, 0)}</Cell>
              <Cell><StatusBadge tone={PRODUCT_TONE[p.status]}>{humanize(p.status)}</StatusBadge></Cell>
            </tr>
          ))}
        </DataTable>
      )}
      <Pagination page={products} basePath="/admin/products" params={{ status }} />
    </div>
  );
}
