import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { containerOptions } from "@/features/admin/container-options";
import { ProductForm } from "@/features/admin/product-form";

export const metadata: Metadata = { title: "New offering" };

export default async function NewProductPage({ searchParams }: PageProps<"/admin/products/new">) {
  const params = await searchParams;
  const preselected = typeof params.containerId === "string" ? params.containerId : undefined;
  return (
    <div className="space-y-6">
      <PageHeader title="New offering" description="Created as a draft. Terms become binding once published." />
      <Card><ProductForm containers={await containerOptions()} preselectedContainer={preselected} /></Card>
    </div>
  );
}
