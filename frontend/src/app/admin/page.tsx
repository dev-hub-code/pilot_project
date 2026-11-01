import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Admin" };

export default async function AdminOverviewPage() {
  const session = await requireStaff();
  return <PageHeader title="Administration" description={`Signed in as ${session.roles.join(", ")}`} />;
}
