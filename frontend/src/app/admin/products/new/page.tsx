import { deniedUnless } from "@/components/layout/page-access";
import { Permission } from "@/lib/permissions";
import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ProductForm } from "@/features/admin/product-form";

export const metadata: Metadata = { title: "New plan" };

export default async function NewProductPage() {
  const denied = await deniedUnless(Permission.INVESTMENT_CREATE);
  if (denied) return denied;
  return (
    <div className="space-y-6">
      <PageHeader title="New plan" description="Created as a draft. Terms become binding once published." />
      <Card><ProductForm /></Card>
    </div>
  );
}
