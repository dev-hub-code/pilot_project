import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { configTabs } from "@/components/layout/admin-nav";
import { ConfigTabs } from "@/components/layout/config-tabs";
import { SlidersHorizontal } from "lucide-react";
import { requireStaff } from "@/lib/server/auth/session";

export const metadata: Metadata = { title: "Platform config" };

/** Everything that sets how the platform runs, in one place; each tab shows only to those who may open it. */
export default async function PlatformConfigLayout({ children }: LayoutProps<"/admin/platform-config">) {
  const session = await requireStaff();
  const tabs = configTabs(session.permissions);
  if (tabs.length === 0) redirect("/admin");
  return (
    <div className="space-y-6">
      <section className="relative isolate overflow-hidden rounded-3xl bg-ink px-6 py-7 text-on-ink shadow-xl shadow-ink/20 sm:px-8">
        <div aria-hidden="true" className="absolute -top-24 -right-16 -z-10 size-72 rounded-full bg-gold/25 blur-3xl" />
        <div className="flex items-center gap-4">
          <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gold text-on-gold">
            <SlidersHorizontal className="size-6" />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight">Platform config</h1>
            <p className="text-sm text-on-ink/70">How the platform runs: withdrawals, returns, referral rates, the payment gateway and company accounts.</p>
          </div>
        </div>
      </section>
      <ConfigTabs tabs={tabs} />
      {children}
    </div>
  );
}
