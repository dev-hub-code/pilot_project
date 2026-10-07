import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ProductForm } from "@/features/admin/product-form";

export const metadata: Metadata = { title: "New plan" };

export default function NewProductPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="New investment plan" description="Created as a draft. Terms become binding once published." />
      <Card><ProductForm /></Card>
    </div>
  );
}
