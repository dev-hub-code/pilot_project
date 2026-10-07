import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ProductForm } from "@/features/admin/product-form";
import { authFetch } from "@/lib/server/auth/session";
import { isUuid } from "@/lib/server/routes/document-proxy";
import type { Product } from "@/types/marketplace";

export const metadata: Metadata = { title: "Edit plan" };

export default async function EditProductPage({ params }: PageProps<"/admin/products/[id]/edit">) {
  const { id } = await params;
  if (!isUuid(id)) notFound();
  const product = await authFetch<Product>(`/api/v1/admin/investment-products/${id}`);
  if (product.status !== "DRAFT") redirect(`/admin/products/${id}`);
  return (
    <div className="space-y-6">
      <PageHeader title={`Edit ${product.code}`} />
      <Card><ProductForm existing={product} /></Card>
    </div>
  );
}
