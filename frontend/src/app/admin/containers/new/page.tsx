import type { Metadata } from "next";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import { ContainerForm } from "@/features/admin/container-form";

export const metadata: Metadata = { title: "Register container" };

export default function NewContainerPage() {
  return (
    <div className="space-y-6">
      <PageHeader title="Register container" description="The container number is validated against its ISO 6346 check digit." />
      <Card><ContainerForm /></Card>
    </div>
  );
}
