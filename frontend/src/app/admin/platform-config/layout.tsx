import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { configTabs } from "@/components/layout/admin-nav";
import { ConfigTabs } from "@/components/layout/config-tabs";
import { PageHeader } from "@/components/ui/page-header";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Platform config" };

/** Everything that sets how the platform runs, in one place; each tab shows only to those who may open it. */
export default async function PlatformConfigLayout({ children }: LayoutProps<"/admin/platform-config">) {
  const session = await requireStaff();
  const tabs = configTabs(session.permissions);
  if (tabs.length === 0) redirect("/admin");
  return (
    <div className="space-y-6">
      <PageHeader title="Platform config" description="How the platform runs: withdrawals, returns, referral rates and company accounts." />
      <ConfigTabs tabs={tabs} />
      {children}
    </div>
  );
}
